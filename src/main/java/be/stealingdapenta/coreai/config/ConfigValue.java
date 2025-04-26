package be.stealingdapenta.coreai.config;

import be.stealingdapenta.coreai.CoreAI;
import java.util.function.BiFunction;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Generic, type-safe wrapper for a config entry.
 *
 * @param <T> the value type
 */
public class ConfigValue<T> {

    private final String path;
    private final T defaultValue;
    private final BiFunction<FileConfiguration, String, T> reader;

    public ConfigValue(String path, T defaultValue, BiFunction<FileConfiguration, String, T> reader) {
        this.path = path;
        this.defaultValue = defaultValue;
        this.reader = reader;
    }

    /**
     * Returns the configuration key path, e.g. "openai.api-key".
     */
    public String path() {
        return path;
    }

    /**
     * Retrieves the value from config or returns the default.
     */
    public T get() {
        FileConfiguration cfg = CoreAI.getInstance()
                                      .getConfig();
        if (cfg.contains(path)) {
            return reader.apply(cfg, path);
        }
        return defaultValue;
    }

    /**
     * Returns the default in-code value.
     */
    public T getDefault() {
        return defaultValue;
    }
}
