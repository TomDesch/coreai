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

    private File file;
    private final Map<Integer, Long> lastSeen = new HashMap<>();

    private File getDataFile() {
        if (file == null) {
            if (CoreAI.getInstance() != null) {
                file = new File(CoreAI.getInstance().getDataFolder(), FILE_NAME);
            } else {
                file = new File(FILE_NAME);
            }
        }
        return file;
    }

    /**
     * Loads the last seen timestamps from disk.
     */
    public void load() {
        File trackerFile = getDataFile();
        if (!trackerFile.exists()) {
            return;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(trackerFile);
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
     * Determines whether a map record should be considered stale for cleanup purposes.
     */
    public static boolean isStale(long lastSeen, long now, long maxAgeMillis) {
        return lastSeen > 0L && now - lastSeen > maxAgeMillis;
    }

    /**
     * @param mapId The map ID
     * @return Millisecond timestamp of last seen, or 0 if never seen.
     */
    public long getLastSeen(int mapId) {
        return lastSeen.getOrDefault(mapId, 0L);
    }

    /**
     * Removes a map from the tracking cache once it is deleted or no longer relevant.
     */
    public void forget(int mapId) {
        lastSeen.remove(mapId);
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
            File trackerFile = getDataFile();
            if (trackerFile.getParentFile() != null && !trackerFile.getParentFile().exists()) {
                trackerFile.getParentFile().mkdirs();
            }
            config.save(trackerFile);
        } catch (IOException e) {
            CORE_AI_LOGGER.warning("Failed to save last seen data: " + e.getMessage());
        }
    }
}
