package be.stealingdapenta.coreai;

import static be.stealingdapenta.coreai.config.Config.API_KEY;
import static be.stealingdapenta.coreai.manager.SessionManager.SESSION_MANAGER;

import be.stealingdapenta.coreai.command.ChatCommand;
import be.stealingdapenta.coreai.command.ModelCommand;
import be.stealingdapenta.coreai.command.ModelInfoCommand;
import be.stealingdapenta.coreai.gui.ModelSelectorGUI;
import be.stealingdapenta.coreai.listener.AsyncApiKeyListener;
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

    private static CoreAI instance;

    public static CoreAI getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        // Ensure default config is created
        saveDefaultConfig();

        CORE_AI_LOGGER = this.getLogger();
        CORE_AI_LOGGER.info(ANSI_YELLOW + "Enabling CoreAI" + ANSI_RESET);

        validateDefaultAPIKey();

        // Initialize the SessionManager
        SESSION_MANAGER.initialize();

        // Register events
        getServer().getPluginManager()
                   .registerEvents(new AsyncApiKeyListener(), this); // this listener prevents the raw API key from appearing in the console when players run setapikey 'command'
        ModelSelectorGUI gui = new ModelSelectorGUI();
        getServer().getPluginManager()
                   .registerEvents(gui, this);
        getServer().getPluginManager()
                   .registerEvents(SESSION_MANAGER, this);

        // Register commands with proper executors
        Objects.requireNonNull(getCommand("chat"))
               .setExecutor(new ChatCommand());
        Objects.requireNonNull(getCommand("models"))
               .setExecutor(new ModelCommand(gui));
        Objects.requireNonNull(getCommand("modelinfo"))
               .setExecutor(new ModelInfoCommand());

        CORE_AI_LOGGER.info(ANSI_GREEN + "CoreAI ready to roll!" + ANSI_RESET);
    }

    private void validateDefaultAPIKey() {
        if (API_KEY.get()
                   .isEmpty()) {
            CORE_AI_LOGGER.severe(ANSI_RED + "No OpenAI API key set! Please fill openai.api-key in config.yml, or have players specify them individually!" + ANSI_RESET);
        }
    }

    @Override
    public void onDisable() {
        CORE_AI_LOGGER.info(ANSI_RED + "CoreAI disabled." + ANSI_RESET);
    }
}
