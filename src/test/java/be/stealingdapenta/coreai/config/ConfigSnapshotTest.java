package be.stealingdapenta.coreai.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ConfigSnapshotTest {

    @Test
    void usesCurrentDefaultsWhenValuesAreMissing() {
        ConfigSnapshot snapshot = ConfigSnapshot.load(new YamlConfiguration(), Logger.getAnonymousLogger());

        assertEquals("", snapshot.apiKey());
        assertEquals("gpt-6-astra", snapshot.model());
        assertEquals(60_000, snapshot.timeoutMs());
        assertEquals(300_000, snapshot.imageTimeoutMs());
        assertFalse(snapshot.autoCleanupEnabled());
        assertEquals(30, snapshot.cleanupMaxAgeDays());
    }

    @Test
    void replacesInvalidValuesWithDefaultsAndWarnsWithoutExposingSecrets() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("openai.api-key", "test-only-secret");
        configuration.set("openai.model", "");
        configuration.set("openai.timeout-ms", 0);
        configuration.set("openai.timeout-image-ms", "infinite");
        configuration.set("cleanup.auto-enabled", "yes");
        configuration.set("cleanup.max-age-days", 100_000);
        RecordingHandler handler = new RecordingHandler();
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);

        ConfigSnapshot snapshot = ConfigSnapshot.load(configuration, logger);

        assertEquals("test-only-secret", snapshot.apiKey());
        assertEquals("gpt-6-astra", snapshot.model());
        assertEquals(60_000, snapshot.timeoutMs());
        assertEquals(300_000, snapshot.imageTimeoutMs());
        assertFalse(snapshot.autoCleanupEnabled());
        assertEquals(30, snapshot.cleanupMaxAgeDays());
        assertEquals(5, handler.messages.size());
        assertTrue(handler.messages.stream().noneMatch(message -> message.contains("test-only-secret")));
    }

    @Test
    void retainsValidOperatorModelAndBoundedTimeouts() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("openai.model", "custom-chat-model:v2");
        configuration.set("openai.timeout-ms", 90_000);
        configuration.set("openai.timeout-image-ms", 120_000);
        configuration.set("cleanup.auto-enabled", true);
        configuration.set("cleanup.max-age-days", 365);

        ConfigSnapshot snapshot = ConfigSnapshot.load(configuration, Logger.getAnonymousLogger());

        assertEquals("custom-chat-model:v2", snapshot.model());
        assertEquals(90_000, snapshot.timeoutMs());
        assertEquals(120_000, snapshot.imageTimeoutMs());
        assertTrue(snapshot.autoCleanupEnabled());
        assertEquals(365, snapshot.cleanupMaxAgeDays());
    }

    private static final class RecordingHandler extends Handler {

        private final List<String> messages = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            messages.add(record.getMessage());
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
