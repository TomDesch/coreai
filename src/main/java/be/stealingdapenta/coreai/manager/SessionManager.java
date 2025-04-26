package be.stealingdapenta.coreai.manager;

import static be.stealingdapenta.coreai.CoreAI.CORE_AI_LOGGER;
import static be.stealingdapenta.coreai.config.Config.API_KEY;
import static be.stealingdapenta.coreai.config.Config.MODEL;
import static be.stealingdapenta.coreai.config.Config.TIMEOUT_MS;

import be.stealingdapenta.coreai.CoreAI;
import be.stealingdapenta.coreai.command.SetApiKeyCommand;
import be.stealingdapenta.coreai.service.ChatAgent;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
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

    private static final String OVERRIDES_FILENAME = "player_models.yml";

    private final Map<UUID, ChatAgent> agents = new ConcurrentHashMap<>();
    private final Map<UUID, String> modelOverrides = new ConcurrentHashMap<>();

    private File overridesFile;
    private FileConfiguration overridesConfig;

    /**
     * Initializes the session manager: load persisted models and register listener.
     */
    public void initialize() {
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
                    modelOverrides.put(id, model);
                }
            } catch (IllegalArgumentException ignore) {
            }
        }
        Bukkit.getPluginManager()
              .registerEvents(this, plugin);
    }

    /**
     * Retrieves or creates a ChatAgent for the player, applying model and API key overrides.
     *
     * @param uuid Player UUID
     * @return ChatAgent instance
     */
    public ChatAgent getAgent(UUID uuid) {
        return agents.computeIfAbsent(uuid, id -> {
            String apiKey = SetApiKeyCommand.getKey(id);
            if (apiKey == null || apiKey.isBlank()) {
                apiKey = API_KEY.get();
            }
            String model = modelOverrides.getOrDefault(id, MODEL.get());
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
        modelOverrides.put(uuid, model);
        overridesConfig.set(uuid.toString(), model);
        try {
            overridesConfig.save(overridesFile);
        } catch (IOException e) {
            CORE_AI_LOGGER.severe("Failed to save " + OVERRIDES_FILENAME + ": " + e.getMessage());
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
}
