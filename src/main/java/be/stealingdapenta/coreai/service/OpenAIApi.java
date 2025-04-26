package be.stealingdapenta.coreai.service;

import static be.stealingdapenta.coreai.CoreAI.CORE_AI_LOGGER;

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
            Map<String, Object> root = mapAdapter.fromJson(json);
            assert root != null;

            @SuppressWarnings("unchecked") List<Map<String, Object>> data = (List<Map<String, Object>>) root.get(DATA);

            return data.stream()
                       .map(m -> m.get(ID)
                                  .toString())
                       .toList();
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
                CORE_AI_LOGGER.severe("OpenAI HTTP " + resp.code() + ": " + respBody);
                throw new IOException("OpenAI HTTP " + resp.code());
            }
            Map<String, Object> root = mapAdapter.fromJson(respBody);
            assert root != null;

            @SuppressWarnings("unchecked") List<Map<String, Object>> choices = (List<Map<String, Object>>) root.get(CHOICES);

            Deque<Map<String, Object>> dq = new LinkedList<>(choices);
            Map<String, Object> first = dq.getFirst();

            @SuppressWarnings("unchecked") Map<String, Object> message = (Map<String, Object>) first.get(MESSAGE);

            return message.get(CONTENT)
                          .toString()
                          .trim();
        }
    }
}