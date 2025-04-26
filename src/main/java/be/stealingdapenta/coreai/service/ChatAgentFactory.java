package be.stealingdapenta.coreai.service;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Factory for ChatAgent instances, one per player.
 */
public class ChatAgentFactory {

    private static final ConcurrentHashMap<UUID, ChatAgent> AGENTS = new ConcurrentHashMap<>();

    /**
     * Retrieve or create ChatAgent for a player.
     */
    public static ChatAgent getAgent(UUID playerId, String defaultKey, String defaultModel, int timeoutMs, Logger logger) {
        return AGENTS.computeIfAbsent(playerId, id -> new ChatAgent(id, defaultKey, defaultModel, timeoutMs, logger));
    }
}