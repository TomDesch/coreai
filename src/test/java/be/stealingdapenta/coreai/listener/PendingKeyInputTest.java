package be.stealingdapenta.coreai.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PendingKeyInputTest {

    @Test
    void claimConsumesAnActiveRequestOnce() {
        PendingKeyInput pending = new PendingKeyInput();
        UUID playerId = UUID.randomUUID();
        pending.begin(playerId, 1_000L);

        assertEquals(PendingKeyInput.Claim.ACTIVE, pending.claim(playerId, 1_001L));
        assertEquals(PendingKeyInput.Claim.NONE, pending.claim(playerId, 1_002L));
    }

    @Test
    void claimReportsExpiredRequestSoInputCanStillBeSuppressed() {
        PendingKeyInput pending = new PendingKeyInput();
        UUID playerId = UUID.randomUUID();
        pending.begin(playerId, 1_000L);

        assertEquals(PendingKeyInput.Claim.EXPIRED, pending.claim(playerId, 121_000L));
    }

    @Test
    void beginRejectsRequestsWhenTheBoundIsReachedAndReclaimsExpiredEntries() {
        PendingKeyInput pending = new PendingKeyInput();
        for (int index = 0; index < 256; index++) {
            UUID playerId = UUID.randomUUID();
            assertTrue(pending.begin(playerId, 1_000L));
        }

        assertFalse(pending.begin(UUID.randomUUID(), 1_001L));
        assertTrue(pending.begin(UUID.randomUUID(), 121_000L));
    }
}
