package be.stealingdapenta.coreai;

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
import be.stealingdapenta.coreai.config.ConfigSnapshot;
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
import org.bukkit.scheduler.BukkitTask;
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
    private MapUsageTrackerListener mapUsageTracker;
    private ConfigSnapshot configSnapshot;
    private BukkitTask persistenceRetryTask;
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

        configSnapshot = ConfigSnapshot.load(getConfig(), CORE_AI_LOGGER);
        validateDefaultAPIKey();

        apiKeyListener = new AsyncApiKeyListener();
        modelSelectorGUI = new ModelSelectorGUI();
        getServer().getPluginManager().registerEvents(apiKeyListener, this);
        getServer().getPluginManager().registerEvents(modelSelectorGUI, this);

        Path dataDirectory = getDataFolder().toPath();
        SESSION_MANAGER.initialize(dataDirectory, persistence, CORE_AI_LOGGER,
                configSnapshot.apiKey(), configSnapshot.model(), configSnapshot.timeoutMs())
                .thenCompose(ignored -> LAST_SEEN_TRACKER.loadAsync(dataDirectory, persistence, CORE_AI_LOGGER))
                .thenCompose(ignored -> MAP_STORAGE.initializeAsync(dataDirectory, persistence, CORE_AI_LOGGER))
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
        persistenceRetryTask = Bukkit.getScheduler().runTaskTimer(this,
                () -> SESSION_MANAGER.retryPendingWrites(), 1_200L, 1_200L);
        mapUsageTracker = new MapUsageTrackerListener(this);
        getServer().getPluginManager().registerEvents(mapUsageTracker, this);
        mapUsageTracker.start().whenComplete((ignored, error) -> {
            if (error == null) {
                runAutoMapCleaner();
            } else {
                CORE_AI_LOGGER.warning("Initial map usage scan did not finish; automatic cleanup was skipped.");
            }
        });

        CORE_AI_LOGGER.info("Loaded stored map images; active map renderers will be restored as maps are observed.");

        Objects.requireNonNull(getCommand("setapikey")).setExecutor(new SetApiKeyCommand(apiKeyListener));
        Objects.requireNonNull(getCommand("chat")).setExecutor(new ChatCommand());
        Objects.requireNonNull(getCommand("models")).setExecutor(new ModelCommand(modelSelectorGUI));
        Objects.requireNonNull(getCommand("modelinfo")).setExecutor(new ModelInfoCommand());
        Objects.requireNonNull(getCommand("imagemap")).setExecutor(new ImageMapCommand());
        Objects.requireNonNull(getCommand("imagegenmap")).setExecutor(new ImageGenMapCommand());
        Objects.requireNonNull(getCommand("cleanup")).setExecutor(new ImageCleanupCommand(mapUsageTracker));

        CORE_AI_LOGGER.info(ANSI_GREEN + "CoreAI ready to roll!" + ANSI_RESET);
    }

    private void validateDefaultAPIKey() {
        if (configSnapshot.apiKey().isEmpty()) {
            CORE_AI_LOGGER.info(ANSI_RED + "No OpenAI API key set! Please fill openai.api-key in config.yml, or have players specify them individually!" + ANSI_RESET);
        }
    }

    /**
     * Triggered on launch. If auto cleanup is enabled, schedule a task to clean up unused images after a delay.
     */
    private void runAutoMapCleaner() {
        if (configSnapshot.autoCleanupEnabled()) {
            CORE_AI_LOGGER.warning("Automatic cleanup is enabled. Age-based cleanup can include maps in offline inventories or unloaded containers.");
            getServer().getScheduler()
                       .runTaskLater(this, () ->
                               mapUsageTracker.refreshLoadedUsage()
                                       .thenCompose(ignored -> MAP_STORAGE.cleanUpUnusedImages())
                                       .whenComplete((count, error) -> {
                                           if (error != null) {
                                               CORE_AI_LOGGER.warning("Automatic map cleanup could not finish.");
                                           } else {
                                               CORE_AI_LOGGER.info("Automatic map cleanup removed "
                                                       + count + " stale map image file(s).");
                                           }
                                       }), 60L);
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

    public ConfigSnapshot getConfigSnapshot() {
        return configSnapshot;
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
        if (persistenceRetryTask != null) {
            persistenceRetryTask.cancel();
        }
        if (SESSION_MANAGER.isInitialized()) {
            SESSION_MANAGER.retryPendingWrites();
            SESSION_MANAGER.clearAgents();
        }
        if (mapUsageTracker != null) {
            mapUsageTracker.stop();
        }
        LAST_SEEN_TRACKER.saveAsync();
        if (persistence != null) {
            persistence.shutdown();
        }
        CORE_AI_LOGGER.info(ANSI_RED + "CoreAI disabled." + ANSI_RESET);
    }
}
