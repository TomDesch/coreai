package be.stealingdapenta.coreai.config;

import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.bukkit.configuration.file.FileConfiguration;

public final class ConfigSnapshot {

    private static final String DEFAULT_API_KEY = "";
    private static final String DEFAULT_MODEL = "gpt-6-astra";
    private static final int DEFAULT_TIMEOUT_MS = 60_000;
    private static final int DEFAULT_IMAGE_TIMEOUT_MS = 300_000;
    private static final boolean DEFAULT_AUTO_CLEANUP_ENABLED = false;
    private static final int DEFAULT_CLEANUP_MAX_AGE_DAYS = 30;
    private static final int MAX_API_KEY_LENGTH = 512;
    private static final int MIN_TIMEOUT_MS = 1_000;
    private static final int MAX_CHAT_TIMEOUT_MS = 120_000;
    private static final int MAX_IMAGE_TIMEOUT_MS = 300_000;
    private static final int MAX_CLEANUP_AGE_DAYS = 3_650;
    private static final Pattern MODEL_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,255}");

    private final String apiKey;
    private final String model;
    private final int timeoutMs;
    private final int imageTimeoutMs;
    private final boolean autoCleanupEnabled;
    private final int cleanupMaxAgeDays;

    private ConfigSnapshot(
            String apiKey,
            String model,
            int timeoutMs,
            int imageTimeoutMs,
            boolean autoCleanupEnabled,
            int cleanupMaxAgeDays) {
        this.apiKey = apiKey;
        this.model = model;
        this.timeoutMs = timeoutMs;
        this.imageTimeoutMs = imageTimeoutMs;
        this.autoCleanupEnabled = autoCleanupEnabled;
        this.cleanupMaxAgeDays = cleanupMaxAgeDays;
    }

    public static ConfigSnapshot load(FileConfiguration configuration, Logger logger) {
        String apiKey = readString(configuration, logger, "openai.api-key",
                DEFAULT_API_KEY, MAX_API_KEY_LENGTH, value -> value.isEmpty()
                        || (value.equals(value.trim()) && value.chars().noneMatch(Character::isISOControl)));
        String model = readString(configuration, logger, "openai.model",
                DEFAULT_MODEL, 256, value -> MODEL_ID.matcher(value).matches());
        int timeoutMs = readInteger(configuration, logger, "openai.timeout-ms",
                DEFAULT_TIMEOUT_MS, MIN_TIMEOUT_MS, MAX_CHAT_TIMEOUT_MS);
        int imageTimeoutMs = readInteger(configuration, logger, "openai.timeout-image-ms",
                DEFAULT_IMAGE_TIMEOUT_MS, MIN_TIMEOUT_MS, MAX_IMAGE_TIMEOUT_MS);
        boolean autoCleanupEnabled = readBoolean(configuration, logger, "cleanup.auto-enabled",
                DEFAULT_AUTO_CLEANUP_ENABLED);
        int cleanupMaxAgeDays = readInteger(configuration, logger, "cleanup.max-age-days",
                DEFAULT_CLEANUP_MAX_AGE_DAYS, 1, MAX_CLEANUP_AGE_DAYS);
        return new ConfigSnapshot(apiKey, model, timeoutMs, imageTimeoutMs,
                autoCleanupEnabled, cleanupMaxAgeDays);
    }

    private static String readString(
            FileConfiguration configuration,
            Logger logger,
            String path,
            String defaultValue,
            int maxLength,
            StringCheck check) {
        if (!configuration.contains(path)) {
            return defaultValue;
        }
        Object value = configuration.get(path);
        if (value instanceof String stringValue && stringValue.length() <= maxLength && check.accepts(stringValue)) {
            return stringValue;
        }
        logger.warning("Invalid configuration value for " + path + "; using the built-in default.");
        return defaultValue;
    }

    private static int readInteger(
            FileConfiguration configuration,
            Logger logger,
            String path,
            int defaultValue,
            int minimum,
            int maximum) {
        if (!configuration.contains(path)) {
            return defaultValue;
        }
        Object value = configuration.get(path);
        if (value instanceof Number number && number.doubleValue() == number.intValue()
                && number.intValue() >= minimum && number.intValue() <= maximum) {
            return number.intValue();
        }
        logger.warning("Invalid configuration value for " + path + "; using the built-in default.");
        return defaultValue;
    }

    private static boolean readBoolean(
            FileConfiguration configuration,
            Logger logger,
            String path,
            boolean defaultValue) {
        if (!configuration.contains(path)) {
            return defaultValue;
        }
        Object value = configuration.get(path);
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        logger.warning("Invalid configuration value for " + path + "; using the built-in default.");
        return defaultValue;
    }

    public String apiKey() {
        return apiKey;
    }

    public String model() {
        return model;
    }

    public int timeoutMs() {
        return timeoutMs;
    }

    public int imageTimeoutMs() {
        return imageTimeoutMs;
    }

    public boolean autoCleanupEnabled() {
        return autoCleanupEnabled;
    }

    public int cleanupMaxAgeDays() {
        return cleanupMaxAgeDays;
    }

    @FunctionalInterface
    private interface StringCheck {

        boolean accepts(String value);
    }
}
