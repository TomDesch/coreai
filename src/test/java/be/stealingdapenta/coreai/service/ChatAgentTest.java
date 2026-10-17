package be.stealingdapenta.coreai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ChatAgentTest {

    @Test
    void toStringRedactsApiKey() {
        ChatAgent agent = new ChatAgent("sk-1234567890", "gpt-6-astra", 5000);

        String value = agent.toString();

        assertTrue(value.contains("apiKey=<set>"));
        assertFalse(value.contains("sk-1234567890"));
        assertFalse(value.contains("sk-1"));
    }

    @Test
    void toStringHandlesEmptyToken() {
        ChatAgent agent = new ChatAgent("", "gpt-6-astra", 5000);

        assertTrue(agent.toString().contains("apiKey=<unset>"));
    }

    @Test
    void rollsBackUserMessageWhenRequestFails() throws IOException {
        AtomicInteger calls = new AtomicInteger();
        ChatAgent agent = new ChatAgent("secret", "gpt-6-astra", 5000, (key, model, timeout, messages) -> {
            if (calls.getAndIncrement() == 0) {
                throw new IOException("request failed");
            }
            assertEquals(1, messages.size());
            assertEquals("recovered", messages.getFirst().get("content"));
            return "reply";
        });

        assertThrows(IOException.class, () -> agent.chat("failed"));
        assertEquals("reply", agent.chat("recovered"));
    }

    @Test
    void commitsConcurrentRequestsAsCompleteTurns() throws Exception {
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        List<List<Map<String, Object>>> requests = new ArrayList<>();
        AtomicInteger callNumber = new AtomicInteger();
        ChatAgent agent = new ChatAgent("secret", "gpt-6-astra", 5000, (key, model, timeout, messages) -> {
            synchronized (requests) {
                requests.add(messages);
            }
            if (callNumber.getAndIncrement() == 0) {
                firstEntered.countDown();
                try {
                    releaseFirst.await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IOException("interrupted", exception);
                }
            }
            return "reply-" + callNumber.get();
        });
        ExecutorService callers = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = callers.submit(() -> agent.chat("first"));
            assertTrue(firstEntered.await(2, TimeUnit.SECONDS));
            Future<String> second = callers.submit(() -> {
                secondStarted.countDown();
                return agent.chat("second");
            });
            assertTrue(secondStarted.await(2, TimeUnit.SECONDS));
            releaseFirst.countDown();
            assertTrue(first.get(2, TimeUnit.SECONDS).startsWith("reply-"));
            assertTrue(second.get(2, TimeUnit.SECONDS).startsWith("reply-"));
        } finally {
            releaseFirst.countDown();
            callers.shutdownNow();
        }

        assertEquals(2, requests.size());
        assertEquals(1, requests.getFirst().size());
        assertEquals(List.of("user", "assistant", "user"),
                requests.get(1).stream().map(message -> (String) message.get("role")).toList());
    }

    @Test
    void limitsStoredHistoryToTwentyMessages() throws IOException {
        AtomicInteger lastRequestSize = new AtomicInteger();
        ChatAgent agent = new ChatAgent("secret", "gpt-6-astra", 5000, (key, model, timeout, messages) -> {
            lastRequestSize.set(messages.size());
            return "reply";
        });

        for (int i = 0; i < 12; i++) {
            agent.chat("prompt-" + i);
        }

        assertTrue(lastRequestSize.get() <= 20);
        assertTrue(lastRequestSize.get() > 0);
    }
}
