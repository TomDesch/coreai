package be.stealingdapenta.coreai.service;

import static be.stealingdapenta.coreai.CoreAI.CORE_AI_LOGGER;
import static be.stealingdapenta.coreai.service.OpenAIApi.OPEN_AI_API;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * Represents a per-player conversation agent.
 */
public class ChatAgent {

    private final Deque<Map<String, Object>> history = new ArrayDeque<>();
    private volatile String apiKey;
    private volatile String model;
    private final int timeoutMs;
    private final Object historyLock = new Object();

    public ChatAgent(String apiKey, String model, int timeoutMs) {
        this.apiKey = apiKey;
        this.model = model;
        this.timeoutMs = timeoutMs;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public String getApiKey() {
        return apiKey;
    }

    /**
     * Tests whether the current API key is valid by listing models.
     *
     * @throws OpenAiException if the API returns an HTTP error (e.g., invalid_api_key)
     * @throws IOException     for network or parsing issues
     */
    public void testKey() throws OpenAiException, IOException {
        OPEN_AI_API.listModelIds(apiKey);
    }

    /**
     * Lists available models for this agent's API key.
     *
     * @return List of model IDs
     * @throws OpenAiException if authentication or API errors occur
     */
    public List<String> listModels() throws OpenAiException {
        try {
            return OPEN_AI_API.listModelIds(apiKey);
        } catch (IOException e) {
            CORE_AI_LOGGER.severe("Failed to fetch model list: " + e.getMessage());
            throw new RuntimeException("Failed to fetch models", e);
        }
    }

    /**
     * Sends user prompt and returns assistant reply, preserving context.
     *
     * @param prompt User message
     * @return Assistant response
     * @throws IOException if network or parsing errors occur
     */
    public String chat(String prompt) throws IOException {
        synchronized (historyLock) {
            history.addLast(Map.of("role", "user", "content", prompt));
            List<Map<String, Object>> contextHistory = List.copyOf(history);
            String reply;
            try {
                reply = OPEN_AI_API.chat(apiKey, model, timeoutMs, contextHistory);
            } catch (IOException e) {
                history.removeLast();
                throw e;
            }
            history.addLast(Map.of("role", "assistant", "content", reply));
            while (history.size() > 20) {
                history.removeFirst();
                history.removeFirst();
            }
            return reply;
        }
    }

    @Override
    public String toString() {
        synchronized (historyLock) {
            return "ChatAgent{historyEntries=" + history.size() + ", apiKey=" + redact(apiKey) + ", model='" + model + '\'' + ", timeoutMs=" + timeoutMs + '}';
        }
    }

    private static String redact(String value) {
        if (value == null || value.isBlank()) {
            return "<unset>";
        }
        if (value.length() <= 4) {
            return "****";
        }
        return value.substring(0, 4) + "...";
    }
}
