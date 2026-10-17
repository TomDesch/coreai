package be.stealingdapenta.coreai.map;

import be.stealingdapenta.coreai.storage.AtomicFileWriter;
import be.stealingdapenta.coreai.storage.PersistenceService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public enum LastSeenTracker {
    LAST_SEEN_TRACKER;

    private static final String FILE_NAME = "map_seen.yml";
    private static final String KEY = "seen";
    private static final int MAX_TRACKED_MAPS = 16_384;

    private final ConcurrentMap<Integer, Long> lastSeen = new ConcurrentHashMap<>();
    private final Object lock = new Object();
    private volatile PersistenceService persistence;
    private volatile Logger logger;
    private Path file;
    private long version;
    private long persistedVersion;
    private long scheduledVersion;
    private CompletableFuture<Void> writeInFlight;

    public CompletableFuture<Void> loadAsync(Path dataDirectory, PersistenceService persistence, Logger logger) {
        this.persistence = persistence;
        this.logger = logger;
        this.file = dataDirectory.resolve(FILE_NAME);
        return persistence.submitIo(() -> {
            Map<Integer, Long> loaded = readTimestamps(file);
            lastSeen.clear();
            lastSeen.putAll(loaded);
            synchronized (lock) {
                version = 0;
                persistedVersion = 0;
                scheduledVersion = 0;
                writeInFlight = null;
            }
            return null;
        });
    }

    private Map<Integer, Long> readTimestamps(Path path) throws IOException {
        Map<Integer, Long> result = new HashMap<>();
        if (Files.notExists(path)) {
            return result;
        }
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new IOException("Map usage data file cannot be read.");
        }

        FileConfiguration configuration = new YamlConfiguration();
        try {
            configuration.load(path.toFile());
        } catch (InvalidConfigurationException exception) {
            throw new IOException("Map usage data file contains invalid YAML.", exception);
        }
        if (!configuration.contains(KEY)) {
            return result;
        }
        if (configuration.getConfigurationSection(KEY) == null) {
            throw new IOException("Map usage data has an invalid structure.");
        }
        for (String idText : configuration.getConfigurationSection(KEY).getKeys(false)) {
            if (result.size() >= MAX_TRACKED_MAPS) {
                throw new IOException("Map usage data exceeds the supported entry limit.");
            }
            try {
                int mapId = Integer.parseInt(idText);
                Object rawTimestamp = configuration.get(KEY + "." + idText);
                if (mapId < 0 || !(rawTimestamp instanceof Number timestamp) || timestamp.longValue() <= 0L
                        || timestamp.doubleValue() != timestamp.longValue()) {
                    throw new IOException("Map usage data contains an invalid entry.");
                }
                result.put(mapId, timestamp.longValue());
            } catch (NumberFormatException exception) {
                throw new IOException("Map usage data contains an invalid map identifier.", exception);
            }
        }
        return result;
    }

    public long markSeen(int mapId) {
        if (mapId < 0) {
            return 0L;
        }
        synchronized (lock) {
            Long previous = lastSeen.get(mapId);
            long nextTimestamp = previous == null ? 1L
                    : previous == Long.MAX_VALUE ? Long.MAX_VALUE : previous + 1L;
            long timestamp = Math.max(System.currentTimeMillis(), nextTimestamp);
            if (previous == null || timestamp > previous) {
                lastSeen.put(mapId, timestamp);
                version++;
            }
            return timestamp;
        }
    }

    public static boolean isStale(long timestamp, long now, long maxAgeMillis) {
        return timestamp > 0L && maxAgeMillis >= 0L && now >= timestamp
                && now - timestamp > maxAgeMillis;
    }

    public long getLastSeen(int mapId) {
        return lastSeen.getOrDefault(mapId, 0L);
    }

    public void forget(int mapId) {
        synchronized (lock) {
            if (lastSeen.remove(mapId) != null) {
                version++;
            }
        }
    }

    public boolean forgetIfUnchanged(int mapId, long expectedTimestamp) {
        synchronized (lock) {
            if (Objects.equals(lastSeen.get(mapId), expectedTimestamp)) {
                lastSeen.remove(mapId);
                version++;
                return true;
            }
            return false;
        }
    }

    public boolean deleteIfStale(int mapId, long now, long maxAgeMillis, StaleMapDeletion deletion)
            throws IOException {
        synchronized (lock) {
            Long observedTimestamp = lastSeen.get(mapId);
            if (observedTimestamp == null || !isStale(observedTimestamp, now, maxAgeMillis)) {
                return false;
            }
            if (!deletion.delete()) {
                return false;
            }
            if (Objects.equals(lastSeen.get(mapId), observedTimestamp)) {
                lastSeen.remove(mapId);
                version++;
            }
            return true;
        }
    }

    public CompletableFuture<Void> saveAsync() {
        synchronized (lock) {
            if (writeInFlight != null && !writeInFlight.isDone()) {
                if (scheduledVersion >= version) {
                    return writeInFlight;
                }
            }
            if (version <= persistedVersion) {
                return CompletableFuture.completedFuture(null);
            }

            long snapshotVersion = version;
            Map<Integer, Long> snapshot = Map.copyOf(lastSeen);
            CompletableFuture<Void> write = persistence.submitIo(() -> {
                FileConfiguration configuration = new YamlConfiguration();
                for (Map.Entry<Integer, Long> entry : snapshot.entrySet()) {
                    configuration.set(KEY + "." + entry.getKey(), entry.getValue());
                }
                AtomicFileWriter.write(file, configuration.saveToString().getBytes(StandardCharsets.UTF_8));
                return null;
            });
            writeInFlight = write;
            scheduledVersion = snapshotVersion;
            write.whenComplete((ignored, error) -> {
                synchronized (lock) {
                    if (error == null) {
                        persistedVersion = Math.max(persistedVersion, snapshotVersion);
                    }
                    if (writeInFlight == write) {
                        writeInFlight = null;
                    }
                }
                if (error != null && this.logger != null) {
                    this.logger.severe("Failed to persist map usage data. The in-memory timestamps remain active.");
                }
            });
            return write;
        }
    }

    @FunctionalInterface
    public interface StaleMapDeletion {

        boolean delete() throws IOException;
    }
}
