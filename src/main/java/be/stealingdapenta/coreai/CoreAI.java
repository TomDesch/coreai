package be.stealingdapenta.coreai;

import be.stealingdapenta.coreai.command.ChatCommand;
import be.stealingdapenta.coreai.command.SetApiKeyCommand;
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

        CORE_AI_LOGGER.info(ANSI_YELLOW + "Enabling CoreAI" + ANSI_RESET);

        // Load an API key strictly from config.yml
        String openAiKey = getConfig().getString("openai.api-key", "")
                                      .trim();

        // Validate API key
        if (openAiKey.isEmpty()) {
            CORE_AI_LOGGER.severe(ANSI_RED + "No OpenAI API key set! Please fill openai.api-key in config.yml, or have players specify them individually!" + ANSI_RESET);
        }

        // Register commands with proper executors
        Objects.requireNonNull(getCommand("chat"))
               .setExecutor(new ChatCommand(this));
        Objects.requireNonNull(getCommand("setapikey"))
               .setExecutor(new SetApiKeyCommand(this));

        // Colored ready message
        CORE_AI_LOGGER.info(ANSI_GREEN + "[CoreAI] CoreAI ready to roll!" + ANSI_RESET);
    }

    @Override
    public void onDisable() {
        // Colored disable message
        CORE_AI_LOGGER.info(ANSI_RED + "[CoreAI] CoreAI disabled." + ANSI_RESET);
    }
}
