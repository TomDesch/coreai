package be.stealingdapenta.coreai.service;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Service for interacting with OpenAI's Chat Completion API (e.g., ChatGPT).
 */
public class ChatGPTService {

    private static final String OPENAI_CHAT_URL = "https://api.openai.com/v1/chat/completions";

    private final OkHttpClient client;
    private final String apiKey;
    private final String model;
    private final int timeoutMs;
    private final Logger logger;
    private final JsonAdapter<Map<String, Object>> mapAdapter;

    /**
     * @param apiKey    OpenAI API key
     * @param model     Chat completion model (e.g., gpt-3.5-turbo)
     * @param timeoutMs Request timeout in milliseconds
     * @param logger    Logger for plugin messages
     */
    public ChatGPTService(String apiKey, String model, int timeoutMs, Logger logger) {
        this.apiKey = apiKey;
        this.model = model;
        this.timeoutMs = timeoutMs;
        this.logger = logger;

        this.client = new OkHttpClient.Builder().connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                                                .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                                                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                                                .build();

        Type mapType = Types.newParameterizedType(Map.class, String.class, Object.class);
        this.mapAdapter = new Moshi.Builder().build()
                                             .adapter(mapType);
    }

    /**
     * Returns the model name used by this service.
     */
    public String getModel() {
        return model;
    }

    /**
     * Returns the request timeout in milliseconds.
     */
    public int getTimeout() {
        return timeoutMs;
    }

    /**
     * Sends a full conversation history to the OpenAI Chat API.
     *
     * @param messages list of messages (role and content) to include in the context
     * @return The assistant’s reply
     * @throws IOException on network or parsing errors
     */
    public String sendChat(List<Map<String, Object>> messages) throws IOException {
        Map<String, Object> payload = Map.of("model", model, "messages", messages);
        String jsonPayload = mapAdapter.toJson(payload);
        RequestBody body = RequestBody.create(jsonPayload, MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder().url(OPENAI_CHAT_URL)
                                               .addHeader("Authorization", "Bearer " + apiKey)
                                               .post(body)
                                               .build();

        try (Response response = client.newCall(request)
                                       .execute()) {
            if (!response.isSuccessful()) {
                String errBody = response.body() != null ? response.body()
                                                                   .string() : "";
                logger.severe("OpenAI Chat API error: HTTP " + response.code() + " - " + errBody);
                throw new IOException("OpenAI API returned HTTP " + response.code());
            }

            assert response.body() != null;
            String respJson = response.body()
                                      .string();
            Map<String, Object> respMap = mapAdapter.fromJson(respJson);
            if (respMap == null) {
                throw new IOException("Received empty response from OpenAI");
            }

            // Extract the first choice using a Deque for readability
            Object choicesObj = respMap.get("choices");
            if (!(choicesObj instanceof List<?> choicesList)) {
                throw new IOException("Unexpected response format: missing choices");
            }
            Deque<?> choicesDeque = new LinkedList<>(choicesList);
            if (choicesDeque.isEmpty()) {
                throw new IOException("No choices returned from OpenAI");
            }
            Object firstChoice = choicesDeque.getFirst();
            if (!(firstChoice instanceof Map<?, ?> choiceMap)) {
                throw new IOException("Unexpected response format: choice is not an object");
            }

            Object messageObj = choiceMap.get("message");
            if (!(messageObj instanceof Map<?, ?> messageMap)) {
                throw new IOException("Unexpected response format: missing message");
            }
            Object contentObj = messageMap.get("content");
            return contentObj != null ? contentObj.toString()
                                                  .trim() : "";
        }
    }
}
