package be.stealingdapenta.coreai.manager;

import static be.stealingdapenta.coreai.CoreAI.CORE_AI_LOGGER;
import static be.stealingdapenta.coreai.config.Config.API_KEY;
import static be.stealingdapenta.coreai.config.Config.MODEL;
import static be.stealingdapenta.coreai.config.Config.TIMEOUT_MS;
import static net.kyori.adventure.text.format.NamedTextColor.RED;

import be.stealingdapenta.coreai.CoreAI;
import be.stealingdapenta.coreai.service.ChatAgent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

/**
 * Singleton manager for ChatAgent sessions and per-player overrides.
 */
public enum SessionManager implements Listener {
    /**
     * Singleton instance.
     */
    SESSION_MANAGER;

    private CryptoUtil crypto;


    private static final String OVERRIDES_FILENAME = "player_models.yml";

    private final Map<UUID, ChatAgent> agents = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, String> playerKeys = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerChosenModels = new ConcurrentHashMap<>();
    private static final String KEY_FILE_NAME = "secret.key";
    private static final String KEYS_FILE_NAME = "playerkeys.yml";
    private static final String ENCRYPTION_ALGORITHM = "AES/GCM/NoPadding";

    private File overridesFile;
    private FileConfiguration overridesConfig;

    /**
     * Initializes the session manager: load persisted models and keys, and register listener.
     */
    public void initialize() {
        loadModelOverrides();
        loadStoredAPIKeys();
        this.crypto = new CryptoUtil(new File(CoreAI.getInstance()
                                                    .getDataFolder(), KEY_FILE_NAME));
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void loadModelOverrides() {
        Plugin plugin = CoreAI.getInstance();
        overridesFile = new File(plugin.getDataFolder(), OVERRIDES_FILENAME);
        if (!overridesFile.exists()) {
            try {
                plugin.getDataFolder()
                      .mkdirs();
                overridesFile.createNewFile();
            } catch (IOException e) {
                CORE_AI_LOGGER.severe("Could not create " + OVERRIDES_FILENAME + ": " + e.getMessage());
            }
        }
        overridesConfig = YamlConfiguration.loadConfiguration(overridesFile);
        for (String key : overridesConfig.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                String model = overridesConfig.getString(key);
                if (model != null) {
                    playerChosenModels.put(id, model);
                }
            } catch (IllegalArgumentException ignore) {
            }
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void loadStoredAPIKeys() {
        Plugin plugin = CoreAI.getInstance();
        plugin.getDataFolder()
              .mkdirs();

        CryptoUtil crypto = new CryptoUtil(new File(plugin.getDataFolder(), KEY_FILE_NAME));
        File keysFile = new File(plugin.getDataFolder(), KEYS_FILE_NAME);
        FileConfiguration keysConfig = YamlConfiguration.loadConfiguration(keysFile);

        // Load existing encrypted keys
        for (String uuidStr : keysConfig.getKeys(false)) {
            String encrypted = keysConfig.getString(uuidStr);

            if (encrypted == null || encrypted.isBlank()) {
                continue;
            }

            try {
                String decrypted = crypto.decrypt(encrypted);
                setPlayerAPIKey(UUID.fromString(uuidStr), decrypted);
            } catch (Exception e) {
                CORE_AI_LOGGER.log(Level.SEVERE, Component.text("Failed to decrypt API key for player " + uuidStr, RED)
                                                          .toString(), e);
            }
        }
    }

    /**
     * Retrieves or creates a ChatAgent for the player, applying model and API key overrides.
     *
     * @param uuid Player UUID
     * @return ChatAgent instance
     */
    public ChatAgent getAgent(UUID uuid) {
        return agents.computeIfAbsent(uuid, id -> {
            String apiKey = playerKeys.getOrDefault(id, API_KEY.get());
            String model = playerChosenModels.getOrDefault(id, MODEL.get());
            int timeout = TIMEOUT_MS.get();
            return new ChatAgent(id, apiKey, model, timeout);
        });
    }

    /**
     * Persists the selected model for a player.
     *
     * @param uuid  Player UUID
     * @param model Model ID chosen
     */
    public void setPlayerModel(UUID uuid, String model) {
        playerChosenModels.put(uuid, model);
        ChatAgent agent = agents.get(uuid);
        if (agent != null) {
            agent.setModel(model);
        }

        // save to disk
        overridesConfig.set(uuid.toString(), model);
        try {
            overridesConfig.save(overridesFile);
        } catch (IOException e) {
            CORE_AI_LOGGER.severe("Failed to save " + OVERRIDES_FILENAME + ": " + e.getMessage());
        }
    }

    /**
     * Sets and persists the player's API key securely.
     *
     * @param uuid   Player UUID
     * @param apiKey The raw API key
     */
    public void setPlayerAPIKey(UUID uuid, String apiKey) {
        playerKeys.put(uuid, apiKey);

        ChatAgent agent = agents.get(uuid);
        if (agent != null) {
            agent.setApiKey(apiKey);
        }

        // Encrypt and save the API key to disk
        try {
            Plugin plugin = CoreAI.getInstance();
            File keysFile = new File(plugin.getDataFolder(), KEYS_FILE_NAME);
            FileConfiguration keysConfig = YamlConfiguration.loadConfiguration(keysFile);

            String encrypted = crypto.encrypt(apiKey);

            keysConfig.set(uuid.toString(), encrypted);
            keysConfig.save(keysFile);
        } catch (IOException e) {
            CORE_AI_LOGGER.log(Level.SEVERE, "Failed to save player API key for " + uuid, e);
        }
    }


    /**
     * Clears the agent when a player quits to free memory.
     *
     * @param event Quit event
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer()
                       .getUniqueId();
        agents.remove(id);
    }

    /**
     * Utility for AES-GCM encryption/decryption with a key stored in a file.
     */
    private static class CryptoUtil {

        private static final String ALGO = ENCRYPTION_ALGORITHM;
        private static final int KEY_SIZE = 256;
        private static final int TAG_SIZE = 128;
        private static final int IV_SIZE = 12;

        private final SecretKey key;
        private final SecureRandom random = new SecureRandom();

        public CryptoUtil(File keyFile) {
            if (keyFile.exists()) {
                try {
                    byte[] b64 = java.nio.file.Files.readAllBytes(keyFile.toPath());
                    byte[] raw = Base64.getDecoder()
                                       .decode(b64);
                    this.key = new SecretKeySpec(raw, "AES");
                } catch (IOException e) {
                    throw new RuntimeException("Failed to load encryption key", e);
                }
            } else {
                try {
                    KeyGenerator gen = KeyGenerator.getInstance("AES");
                    gen.init(KEY_SIZE);
                    SecretKey sk = gen.generateKey();
                    byte[] raw = sk.getEncoded();
                    byte[] b64 = Base64.getEncoder()
                                       .encode(raw);
                    java.nio.file.Files.write(keyFile.toPath(), b64);
                    this.key = sk;
                } catch (Exception e) {
                    throw new RuntimeException("Failed to generate encryption key", e);
                }
            }
        }

        public String encrypt(String plaintext) {
            try {
                byte[] iv = new byte[IV_SIZE];
                random.nextBytes(iv);
                GCMParameterSpec spec = new GCMParameterSpec(TAG_SIZE, iv);
                Cipher cipher = Cipher.getInstance(ALGO);
                cipher.init(Cipher.ENCRYPT_MODE, key, spec);
                byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
                byte[] combined = new byte[iv.length + ct.length];
                System.arraycopy(iv, 0, combined, 0, iv.length);
                System.arraycopy(ct, 0, combined, iv.length, ct.length);
                return Base64.getEncoder()
                             .encodeToString(combined);
            } catch (Exception e) {
                throw new RuntimeException("Encryption error", e);
            }
        }

        public String decrypt(String cipherText) {
            try {
                byte[] all = Base64.getDecoder()
                                   .decode(cipherText);
                byte[] iv = new byte[IV_SIZE];
                byte[] ct = new byte[all.length - IV_SIZE];
                System.arraycopy(all, 0, iv, 0, IV_SIZE);
                System.arraycopy(all, IV_SIZE, ct, 0, ct.length);
                GCMParameterSpec spec = new GCMParameterSpec(TAG_SIZE, iv);
                Cipher cipher = Cipher.getInstance(ALGO);
                cipher.init(Cipher.DECRYPT_MODE, key, spec);
                byte[] pt = cipher.doFinal(ct);
                return new String(pt, StandardCharsets.UTF_8);
            } catch (Exception e) {
                throw new RuntimeException("Decryption error", e);
            }
        }
    }
}
