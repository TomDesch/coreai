package be.stealingdapenta.coreai.map;

import static be.stealingdapenta.coreai.map.LastSeenTracker.LAST_SEEN_TRACKER;
import static be.stealingdapenta.coreai.map.MapImageService.MAP_IMAGE_SERVICE;

import be.stealingdapenta.coreai.CoreAI;
import be.stealingdapenta.coreai.storage.PersistenceService;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.bukkit.Bukkit;
import org.bukkit.map.MapView;

/**
 * Stores image data separately from map-use observations and restores renderers in bounded tick batches.
 */
public enum MapStorage {
    MAP_STORAGE;

    private static final String DIR_NAME = "maps";
    private static final String FILE_PREFIX = "map_";
    private static final String FILE_SUFFIX = ".png";
    private static final int MAP_SIZE = 128;
    private static final int MAX_STORED_MAPS = 4096;
    private static final long MAX_PNG_BYTES = 1_048_576L;
    private final ConcurrentMap<Integer, BufferedImage> mapImages = new ConcurrentHashMap<>();
    private final Set<Integer> storedMapIds = ConcurrentHashMap.newKeySet();
    private final Set<Integer> restoredMapIds = ConcurrentHashMap.newKeySet();
    private volatile PersistenceService persistence;
    private volatile Logger logger;
    private Path directory;

    public CompletableFuture<Void> initializeAsync(
            Path dataDirectory,
            PersistenceService persistence,
            Logger logger) {
        this.persistence = persistence;
        this.logger = logger;
        directory = dataDirectory.resolve(DIR_NAME);
        return persistence.submitIo(this::readStoredImages)
                .thenAccept(images -> {
                    mapImages.clear();
                    storedMapIds.clear();
                    restoredMapIds.clear();
                    mapImages.putAll(images);
                    storedMapIds.addAll(images.keySet());
                });
    }

    private Map<Integer, BufferedImage> readStoredImages() throws IOException {
        Map<Integer, BufferedImage> loaded = new HashMap<>();
        if (Files.notExists(directory)) {
            return loaded;
        }
        if (!Files.isDirectory(directory) || !Files.isReadable(directory)) {
            throw new IOException("Map image directory cannot be read.");
        }

        try (Stream<Path> files = Files.list(directory)) {
            Iterator<Path> iterator = files.iterator();
            while (iterator.hasNext()) {
                Path file = iterator.next();
                String filename = file.getFileName().toString();
                if (!filename.startsWith(FILE_PREFIX) || !filename.endsWith(FILE_SUFFIX)) {
                    continue;
                }
                if (loaded.size() >= MAX_STORED_MAPS) {
                    logger.warning("Stored map image limit reached; additional files were left untouched.");
                    break;
                }
                try {
                    int mapId = parseMapId(filename);
                    loaded.put(mapId, readMapImage(file));
                } catch (IOException exception) {
                    logger.warning("Failed to load stored map image " + filename + ": " + exception.getMessage());
                }
            }
        }
        return loaded;
    }

    private int parseMapId(String filename) throws IOException {
        try {
            int mapId = Integer.parseInt(filename.substring(FILE_PREFIX.length(),
                    filename.length() - FILE_SUFFIX.length()));
            if (mapId < 0) {
                throw new NumberFormatException();
            }
            return mapId;
        } catch (NumberFormatException exception) {
            throw new IOException("Stored map image has an invalid filename.", exception);
        }
    }

    private BufferedImage readMapImage(Path file) throws IOException {
        long size = Files.size(file);
        if (size <= 0L || size > MAX_PNG_BYTES) {
            throw new IOException("Stored map image has an invalid file size.");
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(file.toFile())) {
            if (input == null) {
                throw new IOException("Stored map image could not be opened.");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IOException("Stored map image format is not supported.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                if (!"png".equalsIgnoreCase(reader.getFormatName())) {
                    throw new IOException("Stored map image is not a PNG.");
                }
                if (reader.getWidth(0) != MAP_SIZE || reader.getHeight(0) != MAP_SIZE) {
                    throw new IOException("Stored map image dimensions are invalid.");
                }
                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw new IOException("Stored map image could not be decoded.");
                }
                return image;
            } finally {
                reader.dispose();
            }
        }
    }

    public CompletableFuture<Void> saveNewMap(MapView view, BufferedImage image) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Map views must be saved from the server thread.");
        }
        if (image.getWidth() != MAP_SIZE || image.getHeight() != MAP_SIZE) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Map tile dimensions must be 128x128."));
        }

        int id = view.getId();
        if (!storedMapIds.contains(id) && storedMapIds.size() >= MAX_STORED_MAPS) {
            return CompletableFuture.failedFuture(new IOException("Stored map image limit reached."));
        }
        mapImages.put(id, image);
        storedMapIds.add(id);
        restoredMapIds.add(id);
        LAST_SEEN_TRACKER.markSeen(id);

        Path imageFile = mapPath(id);
        return persistence.writeImage(imageFile, image).whenComplete((ignored, error) -> {
            if (error != null) {
                mapImages.remove(id, image);
                storedMapIds.remove(id);
                restoredMapIds.remove(id);
                LAST_SEEN_TRACKER.forget(id);
                logger.warning("Failed to persist image data for map " + id + ".");
            }
        });
    }

    public void restoreRenderer(MapView view) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Map renderers must be restored from the server thread.");
        }
        int id = view.getId();
        BufferedImage image = mapImages.get(id);
        if (image != null && restoredMapIds.add(id)) {
            view.getRenderers().clear();
            view.addRenderer(MAP_IMAGE_SERVICE.rendererFrom(image));
        }
    }

    public CompletableFuture<Integer> cleanUpUnusedImages() {
        int maxAgeDays = CoreAI.getInstance().getConfigSnapshot().cleanupMaxAgeDays();
        long maxAgeMillis = Math.multiplyExact(Math.multiplyExact((long) maxAgeDays, 24L), 60L * 60L * 1000L);
        long now = System.currentTimeMillis();
        Set<Integer> candidates = new HashSet<>(storedMapIds);

        return persistence.submitIo(() -> {
            List<Integer> deletedIds = new ArrayList<>();
            for (int id : candidates) {
                long lastSeen = LAST_SEEN_TRACKER.getLastSeen(id);
                if (!LastSeenTracker.isStale(lastSeen, now, maxAgeMillis)) {
                    continue;
                }
                try {
                    if (Files.deleteIfExists(mapPath(id))) {
                        deletedIds.add(id);
                    }
                } catch (IOException exception) {
                    logger.warning("Could not remove stale map image " + id + ": " + exception.getMessage());
                }
            }
            return deletedIds;
        }).thenCompose(deletedIds -> {
            for (int id : deletedIds) {
                storedMapIds.remove(id);
                mapImages.remove(id);
                restoredMapIds.remove(id);
                LAST_SEEN_TRACKER.forget(id);
            }
            return LAST_SEEN_TRACKER.saveAsync().thenApply(ignored -> deletedIds.size());
        });
    }

    private Path mapPath(int id) {
        return directory.resolve(FILE_PREFIX + id + FILE_SUFFIX);
    }
}
