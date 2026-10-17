package be.stealingdapenta.coreai;

import static be.stealingdapenta.coreai.config.Config.API_KEY;
import static be.stealingdapenta.coreai.config.Config.AUTO_CLEANUP_ENABLED;
import static be.stealingdapenta.coreai.config.Config.MODEL;
import static be.stealingdapenta.coreai.config.Config.TIMEOUT_MS;
import static be.stealingdapenta.coreai.manager.SessionManager.SESSION_MANAGER;
import static be.stealingdapenta.coreai.map.LastSeenTracker.LAST_SEEN_TRACKER;
import static be.stealingdapenta.coreai.map.MapStorage.MAP_STORAGE;

import be.stealingdapenta.coreai.command.ChatCommand;
import be.stealingdapenta.coreai.command.ImageCleanupCommand;
import be.stealingdapenta.coreai.command.ImageGenMapCommand;
import be.stealingdapenta.coreai.command.ImageMapCommand;
import be.stealingdapenta.coreai.command.ModelCommand;
import be.stealingdapenta.coreai.command.ModelInfoCommand;
import be.stealingdapenta.coreai.command.SetApiKeyCommand;
import be.stealingdapenta.coreai.gui.ModelSelectorGUI;
import be.stealingdapenta.coreai.listener.AsyncApiKeyListener;
import be.stealingdapenta.coreai.listener.MapUsageTrackerListener;
import be.stealingdapenta.coreai.service.BackgroundTaskService;
import be.stealingdapenta.coreai.storage.PersistenceService;
import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
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
    private BackgroundTaskService backgroundTasks;
    private PersistenceService persistence;
    private AsyncApiKeyListener apiKeyListener;
    private ModelSelectorGUI modelSelectorGUI;
    private volatile boolean shuttingDown;
    private static final int WORKER_THREADS = 4;
    private static final int MAX_QUEUED_TASKS = 16;

    public static CoreAI getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        shuttingDown = false;
        backgroundTasks = new BackgroundTaskService(WORKER_THREADS, MAX_QUEUED_TASKS);
        persistence = new PersistenceService();

        saveDefaultConfig();

        CORE_AI_LOGGER = this.getLogger();
        CORE_AI_LOGGER.info(ANSI_YELLOW + "Enabling CoreAI" + ANSI_RESET);

        validateDefaultAPIKey();

        apiKeyListener = new AsyncApiKeyListener();
        modelSelectorGUI = new ModelSelectorGUI();
        getServer().getPluginManager().registerEvents(apiKeyListener, this);
        getServer().getPluginManager().registerEvents(modelSelectorGUI, this);

        Path dataDirectory = getDataFolder().toPath();
        SESSION_MANAGER.initialize(dataDirectory, persistence, CORE_AI_LOGGER,
                API_KEY.get(), MODEL.get(), TIMEOUT_MS.get())
                .whenComplete((ignored, error) -> runMain(() -> {
                    if (error != null) {
                        CORE_AI_LOGGER.severe("Failed to load CoreAI player data. Plugin startup has been stopped to protect stored credentials.");
                        getServer().getPluginManager().disablePlugin(this);
                        return;
                    }
                    finishInitialization();
                }));
    }

    private void finishInitialization() {
        getServer().getPluginManager().registerEvents(SESSION_MANAGER, this);
        getServer().getPluginManager().registerEvents(new MapUsageTrackerListener(), this);

        LAST_SEEN_TRACKER.load();
        MAP_STORAGE.initialize();
        Bukkit.getScheduler().runTask(this, () -> {
            try {
                MAP_STORAGE.restoreAllMapRenderers();
                runAutoMapCleaner();
                CORE_AI_LOGGER.info("Restored all map renderers.");
            } catch (Exception exception) {
                CORE_AI_LOGGER.severe("Failed to initialize map storage: " + exception.getMessage());
            }
        });

        Objects.requireNonNull(getCommand("setapikey")).setExecutor(new SetApiKeyCommand(apiKeyListener));
        Objects.requireNonNull(getCommand("chat")).setExecutor(new ChatCommand());
        Objects.requireNonNull(getCommand("models")).setExecutor(new ModelCommand(modelSelectorGUI));
        Objects.requireNonNull(getCommand("modelinfo")).setExecutor(new ModelInfoCommand());
        Objects.requireNonNull(getCommand("imagemap")).setExecutor(new ImageMapCommand());
        Objects.requireNonNull(getCommand("imagegenmap")).setExecutor(new ImageGenMapCommand());
        Objects.requireNonNull(getCommand("cleanup")).setExecutor(new ImageCleanupCommand());

        CORE_AI_LOGGER.info(ANSI_GREEN + "CoreAI ready to roll!" + ANSI_RESET);
    }

    private void validateDefaultAPIKey() {
        if (API_KEY.get()
                   .isEmpty()) {
            CORE_AI_LOGGER.info(ANSI_RED + "No OpenAI API key set! Please fill openai.api-key in config.yml, or have players specify them individually!" + ANSI_RESET);
        }
    }

    /**
     * Triggered on launch. If auto cleanup is enabled, schedule a task to clean up unused images after a delay.
     */
    private void runAutoMapCleaner() {
        if (AUTO_CLEANUP_ENABLED.get()) {
            getServer().getScheduler()
                       .runTaskLater(this, MAP_STORAGE::cleanUpUnusedImages, 60L); // 3 seconds after startup
        }
    }

    public boolean submitAsync(Runnable task) {
        return !shuttingDown && backgroundTasks != null && backgroundTasks.submit(task);
    }

    public void runMain(Runnable task) {
        if (shuttingDown) {
            return;
        }
        Bukkit.getScheduler().runTask(this, () -> {
            if (!shuttingDown) {
                task.run();
            }
        });
    }

    public PersistenceService getPersistence() {
        return persistence;
    }

    public boolean isShuttingDown() {
        return shuttingDown;
    }

    @Override
    public void onDisable() {
        shuttingDown = true;
        if (backgroundTasks != null) {
            backgroundTasks.shutdown(5, TimeUnit.SECONDS);
        }
        if (apiKeyListener != null) {
            apiKeyListener.clearPendingInputs();
        }
        if (SESSION_MANAGER.isInitialized()) {
            SESSION_MANAGER.retryPendingWrites();
            SESSION_MANAGER.clearAgents();
        }
        LAST_SEEN_TRACKER.save();
        if (persistence != null) {
            persistence.shutdown();
        }
        CORE_AI_LOGGER.info(ANSI_RED + "CoreAI disabled." + ANSI_RESET);
    }
}
