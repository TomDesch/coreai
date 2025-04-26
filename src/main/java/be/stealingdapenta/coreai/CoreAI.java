package be.stealingdapenta.coreai;

import be.stealingdapenta.coreai.command.ChatCommand;
import be.stealingdapenta.coreai.command.SetApiKeyCommand;
import be.stealingdapenta.coreai.service.ChatGPTService;
import java.util.Objects;
import java.util.logging.Logger;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * CoreAI main plugin class. Uses ANSI escape codes for colored console output.
 */
public class CoreAI extends JavaPlugin {

    public static Logger CORE_AI_LOGGER;

    // ANSI color codes for console output
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_RED = "\u001B[31m";

    @Override
    public void onEnable() {
        // Ensure default config is created
        saveDefaultConfig();
        CORE_AI_LOGGER = this.getLogger();

        // Colored enable banner
        CORE_AI_LOGGER.info(ANSI_YELLOW + "[CoreAI] Enabling CoreAI" + ANSI_RESET);

        // Load config values
        String openAiKey = getConfig().getString("openai.api-key", "")
                                      .trim();
        String openAiModel = getConfig().getString("openai.model", "gpt-3.5-turbo");
        int openAiTimeout = getConfig().getInt("openai.timeout-ms", 60000);

        // Override from environment if present
        String envKey = System.getenv("OPENAI_API_KEY");
        if (envKey != null && !envKey.isBlank()) {
            openAiKey = envKey.trim();
            CORE_AI_LOGGER.info(ANSI_GREEN + "[CoreAI] Using OpenAI key from environment" + ANSI_RESET);
        }

        // Validate API key
        if (openAiKey.isEmpty()) {
            CORE_AI_LOGGER.severe(ANSI_RED + "[CoreAI] No OpenAI API key set! Please fill openai.api-key in config.yml or set OPENAI_API_KEY env var." + ANSI_RESET);
            getServer().getPluginManager()
                       .disablePlugin(this);
            return;
        }

        // Register commands with proper executors
        Objects.requireNonNull(getCommand("chat"))
               .setExecutor(new ChatCommand(this));
        Objects.requireNonNull(getCommand("setapikey"))
               .setExecutor(new SetApiKeyCommand(this));

        // Colored ready message
        CORE_AI_LOGGER.info(ANSI_GREEN + "[CoreAI] CoreAI ready to roll!" + ANSI_RESET);
    }

    /**
     * This method returns a default ChatGPT service instance with the API key, model, and timeout from the plugin's config.
     *
     * @return Default ChatGPT service instance with the API key, model, and timeout from config.
     */
    public ChatGPTService getDefaultService() {
        // Return the default ChatGPT service
        return new ChatGPTService(getConfig().getString("openai.api-key", "")
                                             .trim(), getConfig().getString("openai.model", "gpt-3.5-turbo"), getConfig().getInt("openai.timeout-ms", 60000), CORE_AI_LOGGER);
    }

    @Override
    public void onDisable() {
        // Colored disable message
        CORE_AI_LOGGER.info(ANSI_RED + "[CoreAI] CoreAI disabled." + ANSI_RESET);
    }
}
