package be.stealingdapenta.coreai.service;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a per-player conversation agent.
 */
public class ChatAgent {

    private static final String USER = "user";
    private static final String ASSISTANT = "assistant";
    private static final String ROLE = "role";
    private static final String CONTENT = "content";
    private static final int MAX_HISTORY = 20;

    private final UUID playerId;
    private final Deque<Map<String, Object>> history = new ArrayDeque<>();
    private String apiKey;
    private String model;
    private final int timeoutMs;

    public ChatAgent(UUID playerId, String apiKey, String model, int timeoutMs) {
        this.playerId = playerId;
        this.apiKey = apiKey;
        this.model = model;
        this.timeoutMs = timeoutMs;
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
        history.addLast(Map.of(ROLE, USER, CONTENT, prompt));
        List<Map<String, Object>> contextHistory = List.copyOf(history);
        String reply = OpenAIApi.OPEN_AI_API.chat(apiKey, model, timeoutMs, contextHistory);
        history.addLast(Map.of(ROLE, ASSISTANT, CONTENT, reply));
        if (history.size() > MAX_HISTORY) {
            // trim oldest two messages
            history.removeFirst();
            history.removeFirst();
        }
        return reply;
    }
}