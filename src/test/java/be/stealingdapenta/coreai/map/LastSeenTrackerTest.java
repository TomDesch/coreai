package be.stealingdapenta.coreai.map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
