

// ChatAgent.java
package be.stealingdapenta.coreai.service;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Represents a per-player conversation agent.
 */
public class ChatAgent {

    private final UUID playerId;
    private final Logger logger;
    private final Deque<Map<String, Object>> history = new ArrayDeque<>();
    private String apiKey;
    private String model;
    private int timeoutMs;

    public ChatAgent(UUID playerId, String apiKey, String model, int timeoutMs, Logger logger) {
        this.playerId = playerId;
        this.apiKey = apiKey;
        this.model = model;
        this.timeoutMs = timeoutMs;
        this.logger = logger;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * Sends user prompt and returns assistant reply, preserving context.
     */
    public String chat(String prompt) throws IOException {
        history.addLast(Map.of("role", "user", "content", prompt));
        List<Map<String, Object>> ctx = List.copyOf(history);
        String reply = OpenAIApi.OPEN_AI_API.chat(apiKey, model, timeoutMs, logger, ctx);
        history.addLast(Map.of("role", "assistant", "content", reply));
        if (history.size() > 20) {
            // trim oldest two messages
            history.removeFirst();
            history.removeFirst();
        }
        return reply;
    }
}