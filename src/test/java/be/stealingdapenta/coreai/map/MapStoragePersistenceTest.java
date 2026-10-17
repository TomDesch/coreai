package be.stealingdapenta.coreai.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import be.stealingdapenta.coreai.storage.PersistenceService;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class MapStoragePersistenceTest {

    @Test
    void loadingCachedImagesDoesNotMarkThemAsRecentlyObserved() throws Exception {
        Path dataDirectory = createDataDirectory();
        Path imageDirectory = Files.createDirectories(dataDirectory.resolve("maps"));
        Path mapFile = imageDirectory.resolve("map_42.png");
        ImageIO.write(new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB), "png", mapFile.toFile());
        PersistenceService persistence = new PersistenceService();
        try {
            LastSeenTracker.LAST_SEEN_TRACKER.loadAsync(
                    dataDirectory, persistence, Logger.getAnonymousLogger()).join();
            MapStorage.MAP_STORAGE.initializeAsync(
                    dataDirectory, persistence, Logger.getAnonymousLogger()).join();

            assertEquals(0L, LastSeenTracker.LAST_SEEN_TRACKER.getLastSeen(42));
        } finally {
            persistence.shutdown();
            deleteTree(dataDirectory);
        }
    }

    @Test
    void oversizedMapDimensionsAreIgnoredWithoutDeletingTheSource() throws Exception {
        Path dataDirectory = createDataDirectory();
        Path imageDirectory = Files.createDirectories(dataDirectory.resolve("maps"));
        Path mapFile = imageDirectory.resolve("map_43.png");
        ImageIO.write(new BufferedImage(256, 128, BufferedImage.TYPE_INT_RGB), "png", mapFile.toFile());
        PersistenceService persistence = new PersistenceService();
        try {
            LastSeenTracker.LAST_SEEN_TRACKER.loadAsync(
                    dataDirectory, persistence, Logger.getAnonymousLogger()).join();
            MapStorage.MAP_STORAGE.initializeAsync(
                    dataDirectory, persistence, Logger.getAnonymousLogger()).join();

            assertTrue(Files.exists(mapFile));
            assertEquals(0L, LastSeenTracker.LAST_SEEN_TRACKER.getLastSeen(43));
        } finally {
            persistence.shutdown();
            deleteTree(dataDirectory);
        }
    }

    private static Path createDataDirectory() throws Exception {
        Path parent = Path.of("target", "test-data", "map-storage");
        Files.createDirectories(parent);
        Path directory = parent.resolve(UUID.randomUUID().toString());
        Files.createDirectory(directory);
        return directory;
    }

    private static void deleteTree(Path directory) throws Exception {
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
