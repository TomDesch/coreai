package be.stealingdapenta.coreai.map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import be.stealingdapenta.coreai.storage.PersistenceService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class LastSeenTrackerTest {

    @Test
    void marksFreshEntriesAsNotStale() {
        long now = 1_000L;
        long maxAgeMillis = 60_000L;

        assertFalse(LastSeenTracker.LAST_SEEN_TRACKER.isStale(now, now + 10_000L, maxAgeMillis));
    }

    @Test
    void marksOldEntriesAsStale() {
        long now = 200_000L;
        long maxAgeMillis = 60_000L;

        assertTrue(LastSeenTracker.LAST_SEEN_TRACKER.isStale(now - 61_000L, now, maxAgeMillis));
    }

    @Test
    void neverMarksUnsetEntriesAsStale() {
        long now = 1_000L;
        long maxAgeMillis = 60_000L;

        assertFalse(LastSeenTracker.LAST_SEEN_TRACKER.isStale(0L, now, maxAgeMillis));
    }

    @Test
    void preservesFreshObservationArrivingDuringStaleDeletion() throws Exception {
        Path dataDirectory = Path.of("target", "test-data", "last-seen")
                .resolve(UUID.randomUUID().toString());
        Files.createDirectories(dataDirectory);
        Files.writeString(dataDirectory.resolve("map_seen.yml"), "seen:\n  '77': 1\n");
        PersistenceService persistence = new PersistenceService();
        try {
            LastSeenTracker tracker = LastSeenTracker.LAST_SEEN_TRACKER;
            tracker.loadAsync(dataDirectory, persistence, Logger.getAnonymousLogger()).join();

            assertTrue(tracker.deleteIfStale(77, 1_000L, 10L, () -> {
                tracker.markSeen(77);
                return true;
            }));
            assertTrue(tracker.getLastSeen(77) > 1L);
            assertFalse(tracker.deleteIfStale(77, 1_000L, 10L, () -> true));
        } finally {
            persistence.shutdown();
            try (Stream<Path> paths = Files.walk(dataDirectory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }
}
