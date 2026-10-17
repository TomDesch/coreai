package be.stealingdapenta.coreai.map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import be.stealingdapenta.coreai.storage.PersistenceService;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MapImageServiceTest {

    private Path dataDirectory;
    private PersistenceService persistence;

    @BeforeEach
    void setUp() throws Exception {
        dataDirectory = Path.of("target", "test-data", "map-image-service")
                .resolve(UUID.randomUUID().toString());
        Files.createDirectories(dataDirectory);
        persistence = new PersistenceService();
        LastSeenTracker.LAST_SEEN_TRACKER.loadAsync(
                dataDirectory, persistence, Logger.getAnonymousLogger()).join();
        MapStorage.MAP_STORAGE.initializeAsync(
                dataDirectory, persistence, Logger.getAnonymousLogger()).join();
    }

    @AfterEach
    void tearDown() throws Exception {
        persistence.shutdown();
        try (Stream<Path> paths = Files.walk(dataDirectory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    @Test
    void rollsBackEverySavedTileWhenOneWriteFails() throws Exception {
        MapStorage.SaveHandle first = save(1_101);
        MapStorage.SaveHandle second = save(1_102);
        CompletableFuture<Void> pendingWrite = new CompletableFuture<>();
        MapStorage.SaveHandle pendingFirst = new MapStorage.SaveHandle(
                first.id(), first.generation(), first.seenAt(), pendingWrite);
        MapStorage.SaveHandle failedSecond = new MapStorage.SaveHandle(
                second.id(),
                second.generation(),
                second.seenAt(),
                CompletableFuture.failedFuture(new IOException("simulated write failure")));

        CompletableFuture<MapImageService.MapItemBatch> batch =
                MapImageService.completeBatch(List.of(pendingFirst, failedSecond), List.of());
        assertFalse(batch.isDone());
        assertTrue(mapFileExists(first.id()));
        assertTrue(mapFileExists(second.id()));
        pendingWrite.complete(null);
        assertThrows(CompletionException.class,
                batch::join);

        assertFalse(mapFileExists(first.id()));
        assertFalse(mapFileExists(second.id()));
        assertEquals(0L, LastSeenTracker.LAST_SEEN_TRACKER.getLastSeen(first.id()));
        assertEquals(0L, LastSeenTracker.LAST_SEEN_TRACKER.getLastSeen(second.id()));
        String persistedUsage = Files.readString(dataDirectory.resolve("map_seen.yml"));
        assertFalse(persistedUsage.contains(Integer.toString(first.id())));
        assertFalse(persistedUsage.contains(Integer.toString(second.id())));
    }

    @Test
    void rollsBackPersistedTilesWhenThePlayerIdentityIsNoLongerCurrent() throws Exception {
        MapStorage.SaveHandle save = save(1_103);
        MapImageService.MapItemBatch batch = MapImageService.completeBatch(List.of(save), List.of()).join();
        AtomicBoolean delivered = new AtomicBoolean();

        assertFalse(MapImageService.deliverIfCurrent(batch, () -> false, items -> delivered.set(true)));
        persistence.submitIo(() -> null).join();

        assertFalse(delivered.get());
        assertFalse(mapFileExists(save.id()));
        assertEquals(0L, LastSeenTracker.LAST_SEEN_TRACKER.getLastSeen(save.id()));
    }

    private MapStorage.SaveHandle save(int id) {
        MapStorage.SaveHandle save = MapStorage.MAP_STORAGE.saveNewMap(id,
                new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB));
        save.write().join();
        return save;
    }

    private boolean mapFileExists(int id) {
        return Files.exists(dataDirectory.resolve("maps").resolve("map_" + id + ".png"));
    }
}
