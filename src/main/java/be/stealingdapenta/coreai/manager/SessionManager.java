package be.stealingdapenta.coreai.manager;

import be.stealingdapenta.coreai.service.ChatAgent;
import be.stealingdapenta.coreai.storage.AtomicFileWriter;
import be.stealingdapenta.coreai.storage.PersistenceService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Manages chat agents and persistent per-player overrides.
 */
public enum SessionManager implements Listener {
    SESSION_MANAGER;

    private static final String OVERRIDES_FILENAME = "player_models.yml";
    private static final String KEY_FILE_NAME = "secret.key";
    private static final String KEYS_FILE_NAME = "playerkeys.yml";
    private static final int MAX_API_KEY_LENGTH = 512;

    private final Map<UUID, ChatAgent> agents = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerKeys = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerChosenModels = new ConcurrentHashMap<>();
    private final Object keyLock = new Object();
    private final Object modelLock = new Object();

    private volatile CryptoUtil crypto;
    private volatile PersistenceService persistence;
    private volatile Logger logger;
    private volatile String defaultApiKey = "";
    private volatile String defaultModel;
    private volatile int timeoutMs;
    private volatile boolean initialized;
    private Path keyFile;
    private Path keysFile;
    private Path overridesFile;
    private long keysVersion;
    private long persistedKeysVersion;
    private long modelsVersion;
    private long persistedModelsVersion;
    private CompletableFuture<Void> keyWriteInFlight;
    private CompletableFuture<Void> modelWriteInFlight;

    /**
     * Loads the encryption key and player overrides on the persistence executor.
     * Malformed files fail initialization instead of being treated as empty data.
     */
    public CompletableFuture<Void> initialize(
            Path dataDirectory,
            PersistenceService persistence,
            Logger logger,
            String defaultApiKey,
            String defaultModel,
            int timeoutMs) {
        this.persistence = persistence;
        this.logger = logger;
        this.defaultApiKey = defaultApiKey;
        this.defaultModel = defaultModel;
        this.timeoutMs = timeoutMs;
        this.keyFile = dataDirectory.resolve(KEY_FILE_NAME);
        this.keysFile = dataDirectory.resolve(KEYS_FILE_NAME);
        this.overridesFile = dataDirectory.resolve(OVERRIDES_FILENAME);
        initialized = false;

        return persistence.submitIo(() -> {
            CryptoUtil loadedCrypto = new CryptoUtil(keyFile);
            Map<UUID, String> loadedModels = readPlayerData(overridesFile, false);
            Map<UUID, String> encryptedKeys = readPlayerData(keysFile, true);
            Map<UUID, String> loadedKeys = new HashMap<>();
            for (Map.Entry<UUID, String> entry : encryptedKeys.entrySet()) {
                loadedKeys.put(entry.getKey(), loadedCrypto.decrypt(entry.getValue()));
            }

            crypto = loadedCrypto;
            playerChosenModels.clear();
            playerChosenModels.putAll(loadedModels);
            playerKeys.clear();
            playerKeys.putAll(loadedKeys);
            synchronized (keyLock) {
                keysVersion = 0;
                persistedKeysVersion = 0;
                keyWriteInFlight = null;
            }
            synchronized (modelLock) {
                modelsVersion = 0;
                persistedModelsVersion = 0;
                modelWriteInFlight = null;
            }
            initialized = true;
            return null;
        });
    }

    private Map<UUID, String> readPlayerData(Path file, boolean encryptedKeys) throws IOException {
        Map<UUID, String> result = new HashMap<>();
        if (Files.notExists(file)) {
            return result;
        }
        if (!Files.isRegularFile(file) || !Files.isReadable(file)) {
            throw new IOException("Player data file cannot be read: " + file.getFileName());
        }

        FileConfiguration configuration = new YamlConfiguration();
        try {
            configuration.load(file.toFile());
        } catch (InvalidConfigurationException exception) {
            throw new IOException("Player data file contains invalid YAML: " + file.getFileName(), exception);
        }
        for (String key : configuration.getKeys(false)) {
            UUID playerId;
            try {
                playerId = UUID.fromString(key);
            } catch (IllegalArgumentException exception) {
                throw new IOException("Player data file contains an invalid player identifier: " + file.getFileName(), exception);
            }

            Object value = configuration.get(key);
            if (!(value instanceof String stringValue) || stringValue.isBlank()) {
                throw new IOException("Player data file contains an invalid value: " + file.getFileName());
            }
            if (encryptedKeys && stringValue.length() > 4096) {
                throw new IOException("Encrypted player key exceeds the allowed size.");
            }
            if (!encryptedKeys && stringValue.length() > 256) {
                throw new IOException("Player model override exceeds the allowed size.");
            }
            result.put(playerId, stringValue);
        }
        return result;
    }

    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Retrieves or creates the player's chat agent after stored credentials are loaded.
     */
    public ChatAgent getAgent(UUID uuid) {
        if (!initialized) {
            throw new IllegalStateException("Player data has not finished loading.");
        }
        return agents.computeIfAbsent(uuid, id -> new ChatAgent(
                playerKeys.getOrDefault(id, defaultApiKey),
                playerChosenModels.getOrDefault(id, defaultModel),
                timeoutMs));
    }

    public boolean isCurrentAgent(UUID uuid, ChatAgent agent) {
        return agents.get(uuid) == agent;
    }

    /**
     * Updates a player's model immediately and returns the result of its atomic save.
     */
    public CompletableFuture<Void> setPlayerModel(UUID uuid, String model) {
        ensureInitialized();
        if (model == null || model.isBlank() || model.length() > 256) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid model identifier."));
        }
        playerChosenModels.put(uuid, model);
        ChatAgent agent = agents.get(uuid);
        if (agent != null) {
            agent.setModel(model);
        }
        synchronized (modelLock) {
            modelsVersion++;
            return persistModelsLocked(modelsVersion);
        }
    }

    /**
     * Updates a player's credential immediately and returns the result of its atomic save.
     */
    public CompletableFuture<Void> setPlayerAPIKey(UUID uuid, String apiKey) {
        ensureInitialized();
        if (uuid == null || apiKey == null || apiKey.isBlank() || apiKey.length() > MAX_API_KEY_LENGTH) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid API key input."));
        }
        playerKeys.put(uuid, apiKey);
        ChatAgent agent = agents.get(uuid);
        if (agent != null) {
            agent.setApiKey(apiKey);
        }
        synchronized (keyLock) {
            keysVersion++;
            return persistKeysLocked(keysVersion);
        }
    }

    private CompletableFuture<Void> persistKeysLocked(long version) {
        Map<UUID, String> snapshot = Map.copyOf(playerKeys);
        CompletableFuture<Void> write = persistence.submitIo(() -> {
            FileConfiguration configuration = new YamlConfiguration();
            for (Map.Entry<UUID, String> entry : snapshot.entrySet()) {
                configuration.set(entry.getKey().toString(), crypto.encrypt(entry.getValue()));
            }
            AtomicFileWriter.write(keysFile, configuration.saveToString().getBytes(StandardCharsets.UTF_8));
            return null;
        });
        keyWriteInFlight = write;
        write.whenComplete((ignored, error) -> {
            synchronized (keyLock) {
                if (error == null) {
                    persistedKeysVersion = Math.max(persistedKeysVersion, version);
                }
                if (keyWriteInFlight == write) {
                    keyWriteInFlight = null;
                }
            }
            if (error != null && logger != null) {
                logger.severe("Failed to persist player API keys. The in-memory update remains active.");
            }
        });
        return write;
    }

    private CompletableFuture<Void> persistModelsLocked(long version) {
        Map<UUID, String> snapshot = Map.copyOf(playerChosenModels);
        CompletableFuture<Void> write = persistence.submitIo(() -> {
            FileConfiguration configuration = new YamlConfiguration();
            for (Map.Entry<UUID, String> entry : snapshot.entrySet()) {
                configuration.set(entry.getKey().toString(), entry.getValue());
            }
            AtomicFileWriter.write(overridesFile, configuration.saveToString().getBytes(StandardCharsets.UTF_8));
            return null;
        });
        modelWriteInFlight = write;
        write.whenComplete((ignored, error) -> {
            synchronized (modelLock) {
                if (error == null) {
                    persistedModelsVersion = Math.max(persistedModelsVersion, version);
                }
                if (modelWriteInFlight == write) {
                    modelWriteInFlight = null;
                }
            }
            if (error != null && logger != null) {
                logger.severe("Failed to persist player model overrides. The in-memory update remains active.");
            }
        });
        return write;
    }

    /**
     * Requeues the latest snapshots for writes that failed earlier.
     */
    public CompletableFuture<Void> retryPendingWrites() {
        ensureInitialized();
        CompletableFuture<Void> keys = CompletableFuture.completedFuture(null);
        CompletableFuture<Void> models = CompletableFuture.completedFuture(null);
        synchronized (keyLock) {
            if (keysVersion > persistedKeysVersion && (keyWriteInFlight == null || keyWriteInFlight.isDone())) {
                keys = persistKeysLocked(keysVersion);
            } else if (keyWriteInFlight != null) {
                keys = keyWriteInFlight;
            }
        }
        synchronized (modelLock) {
            if (modelsVersion > persistedModelsVersion && (modelWriteInFlight == null || modelWriteInFlight.isDone())) {
                models = persistModelsLocked(modelsVersion);
            } else if (modelWriteInFlight != null) {
                models = modelWriteInFlight;
            }
        }
        return CompletableFuture.allOf(keys, models);
    }

    public void clearAgents() {
        agents.clear();
    }

    private void ensureInitialized() {
        if (!initialized) {
            throw new IllegalStateException("Player data has not finished loading.");
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        agents.remove(event.getPlayer().getUniqueId());
    }

    private static final class CryptoUtil {

        private static final String ALGORITHM = "AES/GCM/NoPadding";
        private static final int KEY_SIZE = 256;
        private static final int TAG_SIZE = 128;
        private static final int IV_SIZE = 12;

        private final SecretKey key;
        private final SecureRandom random = new SecureRandom();

        private CryptoUtil(Path keyFile) throws IOException {
            if (!Files.notExists(keyFile)) {
                if (!Files.isRegularFile(keyFile) || !Files.isReadable(keyFile)) {
                    throw new IOException("Stored encryption key cannot be read.");
                }
                byte[] encoded;
                try {
                    encoded = Base64.getDecoder().decode(Files.readAllBytes(keyFile));
                } catch (IllegalArgumentException exception) {
                    throw new IOException("Stored encryption key is invalid.", exception);
                }
                if (encoded.length != KEY_SIZE / Byte.SIZE) {
                    throw new IOException("Stored encryption key has an invalid size.");
                }
                key = new SecretKeySpec(encoded, "AES");
                return;
            }

            try {
                KeyGenerator generator = KeyGenerator.getInstance("AES");
                generator.init(KEY_SIZE);
                key = generator.generateKey();
                AtomicFileWriter.write(keyFile, Base64.getEncoder().encode(key.getEncoded()));
            } catch (GeneralSecurityException exception) {
                throw new IOException("Could not create the stored encryption key.", exception);
            }
        }

        private String encrypt(String plaintext) throws IOException {
            try {
                byte[] iv = new byte[IV_SIZE];
                random.nextBytes(iv);
                Cipher cipher = Cipher.getInstance(ALGORITHM);
                cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_SIZE, iv));
                byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
                byte[] combined = new byte[iv.length + encrypted.length];
                System.arraycopy(iv, 0, combined, 0, iv.length);
                System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
                return Base64.getEncoder().encodeToString(combined);
            } catch (GeneralSecurityException exception) {
                throw new IOException("Could not encrypt a stored player credential.", exception);
            }
        }

        private String decrypt(String ciphertext) throws IOException {
            try {
                byte[] combined = Base64.getDecoder().decode(ciphertext);
                if (combined.length <= IV_SIZE + TAG_SIZE / Byte.SIZE) {
                    throw new IOException("Stored player credential is malformed.");
                }
                byte[] iv = new byte[IV_SIZE];
                byte[] encrypted = new byte[combined.length - IV_SIZE];
                System.arraycopy(combined, 0, iv, 0, IV_SIZE);
                System.arraycopy(combined, IV_SIZE, encrypted, 0, encrypted.length);
                Cipher cipher = Cipher.getInstance(ALGORITHM);
                cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_SIZE, iv));
                return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException | GeneralSecurityException exception) {
                throw new IOException("Stored player credential could not be decrypted.", exception);
            }
        }
    }
}
