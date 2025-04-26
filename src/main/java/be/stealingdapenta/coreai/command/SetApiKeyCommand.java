package be.stealingdapenta.coreai.command;

import static be.stealingdapenta.coreai.CoreAI.CORE_AI_LOGGER;
import static be.stealingdapenta.coreai.config.Config.API_KEY;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.RED;

import be.stealingdapenta.coreai.CoreAI;
import be.stealingdapenta.coreai.permission.PermissionNode;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * /setapikey command: allows players to store their personal OpenAI API key encrypted on disk. Keys are stored in <plugin>/playerkeys.yml and encrypted with AES-GCM using a secret.key file.
 */
public class SetApiKeyCommand implements CommandExecutor {

    // In-memory map of player UUID -> a decrypted API key
    private static final ConcurrentHashMap<UUID, String> playerKeys = new ConcurrentHashMap<>();
    private static final String KEY_FILE_NAME = "secret.key";
    private static final String KEYS_FILE_NAME = "playerkeys.yml";
    private static final String ENCRYPTION_ALGORITHM = "AES/GCM/NoPadding";

    private final CryptoUtil crypto;
    private final File keysFile;
    private final FileConfiguration keysConfig;

    /**
     * Constructor: initializes the encryption utility and loads existing keys from disk.
     */
    public SetApiKeyCommand() {
        Plugin plugin = CoreAI.getInstance();
        plugin.getDataFolder()
              .mkdirs();

        this.crypto = new CryptoUtil(new File(plugin.getDataFolder(), KEY_FILE_NAME));
        this.keysFile = new File(plugin.getDataFolder(), KEYS_FILE_NAME);
        this.keysConfig = YamlConfiguration.loadConfiguration(keysFile);

        // Load existing encrypted keys
        for (String uuidStr : keysConfig.getKeys(false)) {
            String encrypted = keysConfig.getString(uuidStr);

            if (encrypted == null || encrypted.isBlank()) {
                continue;
            }

            try {
                String decrypted = crypto.decrypt(encrypted);
                playerKeys.put(UUID.fromString(uuidStr), decrypted);
            } catch (Exception e) {
                CORE_AI_LOGGER.log(Level.SEVERE, Component.text("Failed to decrypt API key for player " + uuidStr, RED)
                                                          .toString(), e);
            }
        }
    }

    /**
     * Retrieve the stored API key for a player, or fallback to the server default.
     */
    public static String getKey(UUID playerUuid) {
        String key = playerKeys.get(playerUuid);
        if (key == null || key.isBlank()) {
            // fallback to server default
            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null) {
                player.sendMessage(Component.text("No API key set, using server default.", GRAY));
            }
            return API_KEY.get();
        }
        return key;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can set their API key.", RED));
            return true;
        }

        if (!player.hasPermission(PermissionNode.SET_API_KEY.node())) {
            player.sendMessage(Component.text("You don't have permission to set your API key.", RED));
            return true;
        }

        if (args.length != 1) {
            player.sendMessage(Component.text("Usage: /" + label + " <your-api-key>", RED));
            return true;
        }

        String apiKey = args[0].trim();
        playerKeys.put(player.getUniqueId(), apiKey);

        // Encrypt and save to disk
        String encrypted = crypto.encrypt(apiKey);
        keysConfig.set(player.getUniqueId()
                             .toString(), encrypted);
        try {
            keysConfig.save(keysFile);
        } catch (IOException e) {
            CORE_AI_LOGGER.log(Level.SEVERE, Component.text("Failed to save encrypted API keys to disk", RED)
                                                      .toString(), e);
            player.sendMessage(Component.text("Error saving your API key, please try again.", RED));
            return true;
        }

        // Obfuscate feedback
        String obf = apiKey.length() <= 4 ? "****" : "****" + apiKey.substring(apiKey.length() - 4);
        player.sendMessage(Component.text("Your API key has been stored securely: " + obf, NamedTextColor.GREEN));
        return true;
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