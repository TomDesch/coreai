package be.stealingdapenta.coreai.map;

import static be.stealingdapenta.coreai.CoreAI.CORE_AI_LOGGER;
import static be.stealingdapenta.coreai.map.LastSeenTracker.LAST_SEEN_TRACKER;
import static be.stealingdapenta.coreai.config.Config.CLEANUP_MAX_DAYS;
import static be.stealingdapenta.coreai.map.MapImageService.MAP_IMAGE_SERVICE;

import be.stealingdapenta.coreai.CoreAI;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import org.bukkit.Bukkit;
import org.bukkit.map.MapView;

/**
 * Singleton service for storing and restoring custom-rendered map images.
 */
public enum MapStorage {
    /**
     * The single instance.
     */
    MAP_STORAGE;

    private static final String DIR_NAME = "maps";

    private final Map<Integer, BufferedImage> mapImages = new HashMap<>();
    private File dir;

    private File getMapDirectory() {
        if (dir == null) {
            if (CoreAI.getInstance() != null) {
                dir = new File(CoreAI.getInstance().getDataFolder(), DIR_NAME);
            } else {
                dir = new File(DIR_NAME);
            }
        }
        return dir;
    }

    /**
     * Loads previously saved map images on startup.
     */
    public void initialize() {
        File mapDir = getMapDirectory();
        if (!mapDir.exists()) {
            return;
        }
        File[] files = mapDir.listFiles((d, name) -> name.startsWith("map_") && name.endsWith(".png"));
        if (files == null) {
            return;
        }

        for (File file : files) {
            try {
                int id = Integer.parseInt(file.getName()
                                              .substring(4, file.getName()
                                                                .length() - 4));
                BufferedImage img = ImageIO.read(file);
                if (img != null) {
                    mapImages.put(id, img);
                    LAST_SEEN_TRACKER.markSeen(id);
                }
            } catch (Exception e) {
                CORE_AI_LOGGER.warning("Failed to load stored map: " + file.getName() + " (" + e.getMessage() + ")");
            }
        }
    }

    /**
     * Saves a rendered map image to disk.
     *
     * @param view  The MapView to identify
     * @param image The image rendered on the map
     */
    public void saveNewMap(MapView view, BufferedImage image) {
        int id = view.getId();
        File mapDir = getMapDirectory();
        File out = new File(mapDir, "map_" + id + ".png");
        try {
            mapDir.mkdirs();
            ImageIO.write(image, "png", out);
            mapImages.put(id, image);
            LAST_SEEN_TRACKER.markSeen(id);
        } catch (IOException e) {
            CORE_AI_LOGGER.warning("Failed to store image for map " + id + ": " + e.getMessage());
        }
    }

    /**
     * Restores renderers for all previously stored map IDs.
     */
    public void restoreAllMapRenderers() {
        for (Map.Entry<Integer, BufferedImage> entry : mapImages.entrySet()) {
            int id = entry.getKey();
            BufferedImage image = entry.getValue();
            MapView view = Bukkit.getMap(id);
            if (view != null) {
                view.getRenderers()
                    .clear();
                view.addRenderer(MAP_IMAGE_SERVICE.rendererFrom(image, view));
            }
        }
    }

    /**
     * This method cleans up unused map images that have not been seen for a specified number of days. The 'max allowed days' is configurable in the config.yml file. The last seen timestamp is tracked in the LastSeenTracker class. Method also logs
     * the number of deleted files, or any errors encountered during deletion.
     */
    public void cleanUpUnusedImages() {
        int deleted = 0;
        long now = System.currentTimeMillis();
        long maxAgeMillis = CLEANUP_MAX_DAYS.get() * 24L * 60 * 60 * 1000L;

        File mapDir = getMapDirectory();
        File[] files = mapDir.listFiles((d, name) -> name.startsWith("map_") && name.endsWith(".png"));
        if (files == null) {
            return;
        }

        for (File file : files) {
            try {
                int id = Integer.parseInt(file.getName()
                                              .substring(4, file.getName()
                                                                .length() - 4));

                if (mapImages.containsKey(id)) {
                    LAST_SEEN_TRACKER.markSeen(id);
                    continue;
                }

                long lastSeen = LAST_SEEN_TRACKER.getLastSeen(id);
                if (lastSeen == 0L) {
                    continue;
                }

                if (LAST_SEEN_TRACKER.isStale(lastSeen, now, maxAgeMillis)) {
                    if (file.delete()) {
                        mapImages.remove(id);
                        LAST_SEEN_TRACKER.forget(id);
                        deleted++;
                    }
                }
            } catch (Exception e) {
                CORE_AI_LOGGER.warning("Error while checking map image for cleanup: " + file.getName() + " (" + e.getMessage() + ")");
            }
        }

        CORE_AI_LOGGER.info("Cleaned up " + deleted + " orphaned map image file(s).");
    }
}
