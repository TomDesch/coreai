package be.stealingdapenta.coreai.service;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for ChatAgent instances, one per player.
 */
public class ChatAgentFactory {

    private static final ConcurrentHashMap<UUID, ChatAgent> AGENTS = new ConcurrentHashMap<>();

    /**
     * Retrieve or create ChatAgent for a player.
     */
    public static ChatAgent getAgent(UUID playerId, String apiKey, String reasoningModel, int timeoutMs) {
        return AGENTS.computeIfAbsent(playerId, id -> new ChatAgent(id, apiKey, reasoningModel, timeoutMs));
    }
}