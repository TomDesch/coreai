package be.stealingdapenta.coreai.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static net.kyori.adventure.text.format.NamedTextColor.AQUA;
import static net.kyori.adventure.text.format.NamedTextColor.GREEN;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

class TextBuilderTest {

    @Test
    void createsIndependentBuildersForConcurrentMessages() throws Exception {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch continueFirst = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Component> first = executor.submit(() -> {
                TextBuilder.Builder builder = TextBuilder.TEXT_BUILDER.reset().append("first", AQUA);
                firstStarted.countDown();
                if (!continueFirst.await(2, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("test coordination timed out");
                }
                return builder.append(" tail", GREEN).build();
            });
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS));
            Future<Component> second = executor.submit(() ->
                    TextBuilder.TEXT_BUILDER.reset().append("second", AQUA).build());
            assertEquals("second", ChatMessages.plain(second.get(2, TimeUnit.SECONDS)));
            continueFirst.countDown();
            assertEquals("first tail", ChatMessages.plain(first.get(2, TimeUnit.SECONDS)));
        } finally {
            continueFirst.countDown();
            executor.shutdownNow();
        }
    }
}
