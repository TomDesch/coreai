package be.stealingdapenta.coreai.service;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Base64;
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
    private static final String IMAGE_MODEL = "gpt-image-2.5-flare";
    private static final int MAX_IMAGE_RESPONSE_BYTES = SafeImageDownloader.MAX_DOWNLOAD_BYTES;
    private static final int MAX_IMAGE_RESPONSE_JSON_BYTES = 12 * 1024 * 1024;

    private final OkHttpClient client;
    private final JsonAdapter<Map<String, Object>> mapAdapter;

    OpenAIApi() {
        client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .callTimeout(30, TimeUnit.SECONDS)
                .build();
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
            String json = resp.body() != null ? resp.body().string() : "";
            if (!resp.isSuccessful()) {
                throw parseOpenAiError(resp.code(), json);
            }
            List<Map<String, Object>> data = extractListFromJsonField(json, DATA);
            List<String> modelIds = new ArrayList<>();
            for (Map<String, Object> model : data) {
                Object id = model.get(ID);
                if (!(id instanceof String modelId) || modelId.isBlank()) {
                    throw new IOException("OpenAI returned a model without a valid ID.");
                }
                modelIds.add(modelId);
            }
            return List.copyOf(modelIds);
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
        Map<String, Object> root = readJsonObject(json);
        if (root == null) {
            throw new IOException("Failed to parse JSON: root is null.");
        }
        Object fieldValue = root.get(fieldName);
        if (!(fieldValue instanceof List<?> list)) {
            throw new IOException("Expected top-level field '" + fieldName + "' to contain a list.");
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Object entry : list) {
            if (!(entry instanceof Map<?, ?> mapEntry)) {
                throw new IOException("Expected top-level field '" + fieldName + "' to contain only object entries.");
            }
            result.add(normalizeStringKeys(mapEntry));
        }
        return result;
    }

    private Map<String, Object> readJsonObject(String json) throws IOException {
        Map<String, Object> root = mapAdapter.fromJson(json);
        if (root == null) {
            return null;
        }
        return normalizeStringKeys(root);
    }

    private Map<String, Object> normalizeStringKeys(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private static String safeString(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }

    OpenAiException parseOpenAiError(int status, String body) {
        try {
            Map<String, Object> errRoot = readJsonObject(body);
            if (errRoot == null) {
                return new OpenAiException(status, "http_error", "OpenAI request failed (HTTP " + status + ")");
            }

            Object errorField = errRoot.get("error");
            if (errorField instanceof Map<?, ?> err) {
                Map<String, Object> errMap = normalizeStringKeys(err);
                return new OpenAiException(status,
                        safeString(errMap.get("code"), "unknown_error"),
                        safeString(errMap.get("message"), "OpenAI request failed"));
            }
            if (errorField != null) {
                return new OpenAiException(status, "http_error", safeString(errorField, "OpenAI request failed"));
            }
        } catch (IOException ignored) {
        }
        return new OpenAiException(status, "http_error", "OpenAI request failed (HTTP " + status + ")");
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
            String respBody = resp.body() != null ? resp.body().string() : "";
            if (!resp.isSuccessful()) {
                throw parseOpenAiError(resp.code(), respBody);
            }

            // Parse the response
            return parseChatResponse(respBody);
        }
    }

    String parseChatResponse(String json) throws IOException {
        List<Map<String, Object>> choices = extractListFromJsonField(json, CHOICES);
        if (choices.isEmpty()) {
            throw new IOException("OpenAI returned no chat completion choices.");
        }
        Object messageValue = choices.getFirst().get(MESSAGE);
        if (!(messageValue instanceof Map<?, ?> message)) {
            throw new IOException("OpenAI returned a chat choice without a message.");
        }
        Object contentValue = message.get(CONTENT);
        if (!(contentValue instanceof String content) || content.isBlank()) {
            throw new IOException("OpenAI returned an empty or unsupported chat response.");
        }
        return content.trim();
    }

    /**
     * Parses an OpenAI error response and throws OpenAiException.
     */
    private void throwOpenAiError(int status, String body) throws OpenAiException {
        throw parseOpenAiError(status, body);
    }

    /**
     * Generates an image based on the given prompt and returns the image URL.
     *
     * @param prompt The prompt to send to OpenAI for image generation.
     * @param width  Desired image width in pixels (will be scaled down if unsupported).
     * @param height Desired image height in pixels (will be scaled down if unsupported).
     * @param apiKey OpenAI API key
     * @return A direct URL to the generated image.
     * @throws IOException     On network or API failure.
     * @throws OpenAiException If OpenAI returns an error.
     */
    public byte[] generateImage(String prompt, String apiKey, int timeoutMs) throws IOException, OpenAiException {
        Map<String, Object> payload = Map.of(
                "model", IMAGE_MODEL,
                "prompt", prompt,
                "n", 1,
                "size", "1024x1024");

        String json = mapAdapter.toJson(payload);
        RequestBody body = RequestBody.create(json, MediaType.get(CONTENT_TYPE));

        Request request = new Request.Builder().url("https://api.openai.com/v1/images/generations")
                                               .addHeader(AUTHORIZATION, BEARER + apiKey)
                                               .post(body)
                                               .build();

        OkHttpClient timed = client.newBuilder()
                                                       .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                                                       .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                                                       .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                                                       .build();

        try (Response response = timed.newCall(request)
                                      .execute()) {
            if (!response.isSuccessful()) {
                String respBody = response.body() != null ? response.body().string() : "";
                throwOpenAiError(response.code(), respBody);
            }
            if (response.body() == null) {
                throw new IOException("OpenAI returned an empty image response.");
            }
            byte[] jsonBytes = SafeImageDownloader.readLimited(response.body().byteStream(), MAX_IMAGE_RESPONSE_JSON_BYTES);
            return parseGeneratedImage(jsonBytes);
        }
    }

    byte[] parseGeneratedImage(byte[] jsonBytes) throws IOException {
        String json = new String(jsonBytes, StandardCharsets.UTF_8);
        List<Map<String, Object>> data = extractListFromJsonField(json, DATA);
        if (data.isEmpty()) {
            throw new IOException("OpenAI returned no generated image.");
        }
        Object encodedValue = data.getFirst().get("b64_json");
        if (!(encodedValue instanceof String encoded) || encoded.isBlank()
                || encoded.length() > ((MAX_IMAGE_RESPONSE_BYTES + 2L) / 3L) * 4L) {
            throw new IOException("OpenAI returned an invalid generated image.");
        }
        byte[] image;
        try {
            image = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException exception) {
            throw new IOException("OpenAI returned an invalid generated image.", exception);
        }
        if (image.length == 0 || image.length > MAX_IMAGE_RESPONSE_BYTES) {
            throw new IOException("OpenAI returned an image that exceeds the supported size.");
        }
        return image;
    }

}