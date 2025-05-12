package be.stealingdapenta.coreai.map;

import static be.stealingdapenta.coreai.CoreAI.CORE_AI_LOGGER;

import be.stealingdapenta.coreai.CoreAI;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public enum LastSeenTracker {
    LAST_SEEN_TRACKER;

    private static final String FILE_NAME = "map_seen.yml";
    private static final String KEY = "seen";

    private final File file = new File(CoreAI.getInstance()
                                             .getDataFolder(), FILE_NAME);
    private final Map<Integer, Long> lastSeen = new HashMap<>();

    /**
     * Loads the last seen timestamps from disk.
     */
    public void load() {
        if (!file.exists()) {
            return;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        lastSeen.clear();

        if (config.contains(KEY)) {
            for (String key : Objects.requireNonNull(config.getConfigurationSection(KEY))
                                     .getKeys(false)) {
                try {
                    int mapId = Integer.parseInt(key);
                    long ts = config.getLong(KEY + "." + key);
                    lastSeen.put(mapId, ts);
                } catch (NumberFormatException ignore) {
                }
            }
        }
    }

    /**
     * Marks a map ID as seen *now* (updates timestamp). The time is the current timestamp in milliseconds.
     */
    public void markSeen(int mapId) {
        lastSeen.put(mapId, System.currentTimeMillis());
    }

    /**
     * @param mapId The map ID
     * @return Millisecond timestamp of last seen, or 0 if never seen.
     */
    public long getLastSeen(int mapId) {
        return lastSeen.getOrDefault(mapId, 0L);
    }

    /**
     * Saves all last seen timestamps to disk.
     */
    public void save() {
        FileConfiguration config = new YamlConfiguration();
        for (Map.Entry<Integer, Long> entry : lastSeen.entrySet()) {
            config.set(KEY + "." + entry.getKey(), entry.getValue());
        }

        try {
            config.save(file);
        } catch (IOException e) {
            CORE_AI_LOGGER.warning("Failed to save last seen data: " + e.getMessage());
        }
    }
}
