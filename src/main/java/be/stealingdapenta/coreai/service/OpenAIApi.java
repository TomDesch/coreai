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
 * Singleton entrypoint for raw OpenAI API calls.
 */
public enum OpenAIApi {
    OPEN_AI_API;

    private static final String CHAT_URL = "https://api.openai.com/v1/chat/completions";
    private static final String MODELS_URL = "https://api.openai.com/v1/models";

    private final OkHttpClient client;
    private final JsonAdapter<Map<String, Object>> mapAdapter;
    private final JsonAdapter<List<Map<String, Object>>> listAdapter;

    OpenAIApi() {
        client = new OkHttpClient();
        Type mapType = Types.newParameterizedType(Map.class, String.class, Object.class);
        Type listType = Types.newParameterizedType(List.class, Types.newParameterizedType(Map.class, String.class, Object.class));
        mapAdapter = new Moshi.Builder().build()
                                        .adapter(mapType);
        listAdapter = new Moshi.Builder().build()
                                         .adapter(listType);
    }

    /**
     * Fetch available model IDs for the given API key.
     */
    public List<String> listModelIds(String apiKey) throws IOException {
        Request req = new Request.Builder().url(MODELS_URL)
                                           .addHeader("Authorization", "Bearer " + apiKey)
                                           .get()
                                           .build();
        try (Response resp = client.newCall(req)
                                   .execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("HTTP " + resp.code());
            }
            String json = resp.body()
                              .string();
            Map<String, Object> root = mapAdapter.fromJson(json);
            @SuppressWarnings("unchecked") List<Map<String, Object>> data = (List<Map<String, Object>>) root.get("data");
            return data.stream()
                       .map(m -> m.get("id")
                                  .toString())
                       .toList();
        }
    }

    /**
     * Send a chat completion request and return the assistant response.
     */
    public String chat(String apiKey, String model, int timeoutMs, Logger logger, List<Map<String, Object>> messages) throws IOException {
        OkHttpClient c = client.newBuilder()
                               .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                               .build();
        Map<String, Object> payload = Map.of("model", model, "messages", messages);
        String bodyJson = mapAdapter.toJson(payload);
        RequestBody body = RequestBody.create(bodyJson, MediaType.get("application/json; charset=utf-8"));
        Request req = new Request.Builder().url(CHAT_URL)
                                           .addHeader("Authorization", "Bearer " + apiKey)
                                           .post(body)
                                           .build();
        try (Response resp = c.newCall(req)
                              .execute()) {
            assert resp.body() != null;
            String respBody = resp.body()
                                  .string();
            if (!resp.isSuccessful()) {
                logger.severe("OpenAI HTTP " + resp.code() + ": " + respBody);
                throw new IOException("OpenAI HTTP " + resp.code());
            }
            Map<String, Object> root = mapAdapter.fromJson(respBody);
            @SuppressWarnings("unchecked") List<Map<String, Object>> choices = (List<Map<String, Object>>) root.get("choices");
            Deque<Map<String, Object>> dq = new LinkedList<>(choices);
            Map<String, Object> first = dq.getFirst();
            @SuppressWarnings("unchecked") Map<String, Object> message = (Map<String, Object>) first.get("message");
            return message.get("content")
                          .toString()
                          .trim();
        }
    }
}