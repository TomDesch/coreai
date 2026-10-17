package be.stealingdapenta.coreai.service;

import static be.stealingdapenta.coreai.service.OpenAIApi.OPEN_AI_API;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * Represents a per-player conversation agent.
 */
public class ChatAgent {

    private static final int MAX_HISTORY_MESSAGES = 20;
    private final Deque<Map<String, Object>> history = new ArrayDeque<>();
    private volatile String apiKey;
    private volatile String model;
    private final int timeoutMs;
    private final Object historyLock = new Object();
    private final ChatTransport chatTransport;

    public ChatAgent(String apiKey, String model, int timeoutMs) {
        this(apiKey, model, timeoutMs, OPEN_AI_API::chat);
    }

    ChatAgent(String apiKey, String model, int timeoutMs, ChatTransport chatTransport) {
        this.apiKey = apiKey;
        this.model = model;
        this.timeoutMs = timeoutMs;
        this.chatTransport = chatTransport;
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
    public List<String> listModels() throws IOException {
        return OPEN_AI_API.listModelIds(apiKey);
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
            List<Map<String, Object>> candidateHistory = new ArrayList<>(history);
            candidateHistory.add(Map.of("role", "user", "content", prompt));
            while (candidateHistory.size() > MAX_HISTORY_MESSAGES) {
                candidateHistory.removeFirst();
                candidateHistory.removeFirst();
            }
            String currentKey = apiKey;
            String currentModel = model;
            String reply = chatTransport.chat(currentKey, currentModel, timeoutMs, List.copyOf(candidateHistory));
            if (reply == null || reply.isBlank()) {
                throw new IOException("OpenAI returned an empty chat response.");
            }
            candidateHistory.add(Map.of("role", "assistant", "content", reply));
            while (candidateHistory.size() > MAX_HISTORY_MESSAGES) {
                candidateHistory.removeFirst();
                candidateHistory.removeFirst();
            }
            history.clear();
            history.addAll(candidateHistory);
            return reply;
        }
    }

    @Override
    public String toString() {
        synchronized (historyLock) {
            return "ChatAgent{historyEntries=" + history.size() + ", apiKey=" + redact(apiKey)
                    + ", model='" + model + '\'' + ", timeoutMs=" + timeoutMs + '}';
        }
    }

    private static String redact(String value) {
        return value == null || value.isBlank() ? "<unset>" : "<set>";
    }

    @FunctionalInterface
    interface ChatTransport {

        String chat(String apiKey, String model, int timeoutMs, List<Map<String, Object>> messages) throws IOException;
    }
}
