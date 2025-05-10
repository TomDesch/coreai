package be.stealingdapenta.coreai.config;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * A registry of all CoreAI plugin’s config entries, with strong typing and defaults.
 */
public final class Config {

    public static final ConfigValue<String> API_KEY = new ConfigValue<>("openai.api-key", "", FileConfiguration::getString);
    public static final ConfigValue<String> MODEL = new ConfigValue<>("openai.model", "gpt-3.5-turbo", FileConfiguration::getString);
    public static final ConfigValue<Integer> TIMEOUT_MS = new ConfigValue<>("openai.timeout-ms", 60_000, FileConfiguration::getInt);
    public static final ConfigValue<Integer> TIMEOUT_IMAGE_MS = new ConfigValue<>("openai.timeout-image-ms", 300_000, FileConfiguration::getInt);

}
