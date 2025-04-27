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
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Singleton entrypoint for raw OpenAI API calls.
 */
public enum OpenAIApi {
    OPEN_AI_API;

    private static final String CHAT_URL = "https://api.openai.com/v1/chat/completions";
    private static final String MODELS_URL = "https://api.openai.com/v1/models";

    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER = "Bearer ";
    private static final String CONTENT_TYPE = "application/json; charset=utf-8";
    private static final String DATA = "data";
    private static final String ID = "id";
    private static final String CHOICES = "choices";
    private static final String MESSAGE = "message";
    private static final String CONTENT = "content";
    private static final String MODEL = "model";
    private static final String MESSAGES = "messages";
    private static final String HTTP = "HTTP ";
    private static final String BASE_URL = "https://api.openai.com/v1";
    private static final String MODEL_INFO_PATH = "/models/%s";

    private final OkHttpClient client;
    private final JsonAdapter<Map<String, Object>> mapAdapter;

    OpenAIApi() {
        client = new OkHttpClient();
        Type mapType = Types.newParameterizedType(Map.class, String.class, Object.class);
        mapAdapter = new Moshi.Builder().build()
                                        .adapter(mapType);

    }

    /**
     * Fetch available model IDs for the given API key.
     *
     * @param apiKey OpenAI API key
     * @return the list of available model IDs
     */
    public List<String> listModelIds(String apiKey) throws IOException {
        Request req = new Request.Builder().url(MODELS_URL)
                                           .addHeader(AUTHORIZATION, BEARER + apiKey)
                                           .get()
                                           .build();
        try (Response resp = client.newCall(req)
                                   .execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException(HTTP + resp.code());
            }
            assert resp.body() != null;
            String json = resp.body()
                              .string();

            List<Map<String, Object>> data = extractListFromJsonField(json, DATA);

            return data.stream()
                       .map(m -> m.get(ID)
                                  .toString())
                       .toList();
        }
    }

    /**
     * Parses a JSON string and retrieves a list of maps located under a specified top-level field.
     *
     * @param json      The JSON string to parse.
     * @param fieldName The field name whose value should be retrieved (expected to be a list).
     * @return A list of maps extracted from the specified field.
     * @throws IOException If parsing fails or if the JSON is invalid.
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractListFromJsonField(String json, String fieldName) throws IOException {
        Map<String, Object> root = mapAdapter.fromJson(json);
        if (root == null) {
            throw new IOException("Failed to parse JSON: root is null.");
        }
        return (List<Map<String, Object>>) root.get(fieldName);
    }


    /**
     * Fetches detailed information for a specific model.
     *
     * @param apiKey  Bearer key
     * @param modelId ID of the model
     * @return raw JSON as Map
     * @throws IOException     on network error
     * @throws OpenAiException on API error
     */
    public Map<String, Object> getModelInfo(String apiKey, String modelId) throws IOException, OpenAiException {
        String path = String.format(MODEL_INFO_PATH, modelId);
        Request req = new Request.Builder().url(BASE_URL + path)
                                           .addHeader("Authorization", "Bearer " + apiKey)
                                           .get()
                                           .build();
        try (Response resp = client.newCall(req)
                                   .execute()) {
            String body = resp.body() != null ? resp.body()
                                                    .string() : "";
            if (!resp.isSuccessful()) {
                throwOpenAiError(resp.code(), body);
            }
            Map<String, Object> json = mapAdapter.fromJson(body);
            if (json == null) {
                throw new OpenAiException(resp.code(), "invalid_response", "Empty response");
            }
            return json;
        }
    }

    /**
     * Send a chat completion request and return the assistant response.
     *
     * @param apiKey    OpenAI API key
     * @param model     Model ID to use for the request
     * @param timeoutMs Timeout in milliseconds
     * @param messages  List of messages in the conversation
     * @return Assistant response
     * @throws IOException if an error occurs during the request
     */
    public String chat(String apiKey, String model, int timeoutMs, List<Map<String, Object>> messages) throws IOException {
        OkHttpClient c = client.newBuilder()
                               .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                               .build();
        Map<String, Object> payload = Map.of(MODEL, model, MESSAGES, messages);
        String bodyJson = mapAdapter.toJson(payload);
        RequestBody body = RequestBody.create(bodyJson, MediaType.get(CONTENT_TYPE));
        Request req = new Request.Builder().url(CHAT_URL)
                                           .addHeader(AUTHORIZATION, BEARER + apiKey)
                                           .post(body)
                                           .build();
        try (Response resp = c.newCall(req)
                              .execute()) {
            assert resp.body() != null;
            String respBody = resp.body()
                                  .string();
            if (!resp.isSuccessful()) {
                var errRoot = mapAdapter.fromJson(respBody);
                assert errRoot != null;
                String apiCode = ((Map<?, ?>) errRoot.get("error")).get("code")
                                                                   .toString();
                String msg = ((Map<?, ?>) errRoot.get("error")).get("message")
                                                               .toString();
                throw new OpenAiException(resp.code(), apiCode, msg);
            }

            // Parse the response
            Deque<Map<String, Object>> dq = new LinkedList<>(extractListFromJsonField(respBody, CHOICES));
            Map<String, Object> first = dq.getFirst();

            @SuppressWarnings("unchecked") Map<String, Object> message = (Map<String, Object>) first.get(MESSAGE);

            return message.get(CONTENT)
                          .toString()
                          .trim();
        }
    }

    /**
     * Parses an OpenAI error response and throws OpenAiException.
     */
    private void throwOpenAiError(int status, String body) throws OpenAiException {
        try {
            Map<String, Object> errRoot = mapAdapter.fromJson(body);
            if (errRoot != null && errRoot.get("error") instanceof Map<?, ?> err) {
                String msg = err.get("message")
                                .toString();
                String code = err.get("code") != null ? err.get("code")
                                                           .toString() : "unknown_error";
                throw new OpenAiException(status, code, msg);
            }
        } catch (IOException ignored) {
        }
        throw new OpenAiException(status, "http_error", "HTTP " + status);
    }

}