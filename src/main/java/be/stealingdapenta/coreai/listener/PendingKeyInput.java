package be.stealingdapenta.coreai.listener;

import java.util.UUID;
import java.util.HashMap;
import java.util.Map;

final class PendingKeyInput {

    private static final long EXPIRATION_MILLIS = 120_000L;
    private static final int MAX_PENDING_PLAYERS = 256;

    private final Map<UUID, Long> expirations = new HashMap<>();

    synchronized boolean begin(UUID playerId, long nowMillis) {
        expirations.entrySet().removeIf(entry -> nowMillis >= entry.getValue());
        if (!expirations.containsKey(playerId) && expirations.size() >= MAX_PENDING_PLAYERS) {
            return false;
        }
        expirations.put(playerId, nowMillis + EXPIRATION_MILLIS);
        return true;
    }

    synchronized Claim claim(UUID playerId, long nowMillis) {
        Long expiresAt = expirations.remove(playerId);
        if (expiresAt == null) {
            return Claim.NONE;
        }
        return nowMillis >= expiresAt ? Claim.EXPIRED : Claim.ACTIVE;
    }

    synchronized void remove(UUID playerId) {
        expirations.remove(playerId);
    }

    synchronized void clear() {
        expirations.clear();
    }

    enum Claim {
        NONE,
        ACTIVE,
        EXPIRED
    }
}
