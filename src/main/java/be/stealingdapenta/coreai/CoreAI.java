package be.stealingdapenta.coreai;

import be.stealingdapenta.coreai.command.ChatCommand;
import be.stealingdapenta.coreai.command.SetApiKeyCommand;
import be.stealingdapenta.coreai.service.ChatGPTService;
import java.util.Objects;
import java.util.logging.Logger;
import org.bukkit.plugin.java.JavaPlugin;

public class CoreAI extends JavaPlugin {

    public static Logger CORE_AI_LOGGER;

    @Override
    public void onEnable() {
        // Ensure default config is created
        saveDefaultConfig();
        CORE_AI_LOGGER = this.getLogger();

        // Load config values
        String openAiKey = getConfig().getString("openai.api-key", "")
                                      .trim();
        String openAiModel = getConfig().getString("openai.model", "gpt-3.5-turbo");
        int openAiTimeout = getConfig().getInt("openai.timeout-ms", 60000);

        // Override from environment if present
        String envKey = System.getenv("OPENAI_API_KEY");
        if (envKey != null && !envKey.isBlank()) {
            openAiKey = envKey.trim();
            CORE_AI_LOGGER.info("Using OpenAI key from environment");
        }

        // Validate API key
        if (openAiKey.isEmpty()) {
            CORE_AI_LOGGER.severe("No OpenAI API key set! Please fill openai.api-key in config.yml or set OPENAI_API_KEY env var.");
            getServer().getPluginManager()
                       .disablePlugin(this);
            return;
        }

        // Initialize default ChatGPT service
        ChatGPTService defaultService = new ChatGPTService(openAiKey, openAiModel, openAiTimeout, CORE_AI_LOGGER);

        // Register commands with proper executors
        Objects.requireNonNull(getCommand("chat"))
               .setExecutor(new ChatCommand(this, defaultService));
        Objects.requireNonNull(getCommand("setapikey"))
               .setExecutor(new SetApiKeyCommand(this));

        CORE_AI_LOGGER.info("CoreAI ready to roll!");
    }

    @Override
    public void onDisable() {
        CORE_AI_LOGGER.info("CoreAI disabled.");
    }
}
