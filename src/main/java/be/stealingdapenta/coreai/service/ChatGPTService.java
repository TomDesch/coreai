package be.stealingdapenta.coreai.service;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;
import java.io.IOException;
import java.lang.reflect.Type;
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
        this.logger = logger;

        this.client = new OkHttpClient.Builder().connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                                                .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                                                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                                                .build();

        // Define the generic Map<String, Object> type for Moshi
        Type mapType = Types.newParameterizedType(Map.class, String.class, Object.class);
        this.mapAdapter = new Moshi.Builder().build()
                                             .adapter(mapType);
    }

    /**
     * Sends a user prompt to the OpenAI Chat API and returns the assistant's reply.
     *
     * @param userPrompt The prompt text from the user
     * @return The assistant's response text
     * @throws IOException If network or parsing errors occur
     */
    public String sendMessage(String userPrompt) throws IOException {
        // Build request payload
        Map<String, Object> message = Map.of("role", "user", "content", userPrompt);
        Map<String, Object> payload = Map.of("model", model, "messages", List.of(message));

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

            String respJson = response.body()
                                      .string();
            Map<String, Object> respMap = mapAdapter.fromJson(respJson);
            if (respMap == null) {
                throw new IOException("Received empty response from OpenAI");
            }

            // Extract the first choice's message content
            Object choicesObj = respMap.get("choices");
            if (!(choicesObj instanceof List<?> choices)) {
                throw new IOException("Unexpected response format: missing choices");
            }
            if (choices.isEmpty()) {
                throw new IOException("No choices returned from OpenAI");
            }
            Object firstChoice = choices.getFirst();
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
