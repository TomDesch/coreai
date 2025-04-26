package be.stealingdapenta.coreai.command;

import be.stealingdapenta.coreai.service.ChatGPTService;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * /chat command: maintains conversation context per player, with per-player API key override.
 */
public class ChatCommand implements CommandExecutor {

    private final Plugin plugin;
    private final Logger logger;
    private static final int MAX_HISTORY_PAIRS = 10;
    private final ChatGPTService defaultService;
    // per-player conversation history: up to MAX_HISTORY_PAIRS user+assistant messages
    private final ConcurrentHashMap<UUID, Deque<Map<String, Object>>> histories = new ConcurrentHashMap<>();

    public ChatCommand(@NotNull Plugin plugin, @NotNull ChatGPTService defaultService) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.defaultService = defaultService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }
        if (!player.hasPermission("coreai.chat")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use this.");
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(ChatColor.RED + "Usage: /" + label + " <message>");
            return true;
        }

        String prompt = String.join(" ", args);
        player.sendMessage(ChatColor.GRAY + "[CoreAI] Thinking...");

        UUID uuid = player.getUniqueId();
        Deque<Map<String, Object>> history = histories.computeIfAbsent(uuid, k -> new ArrayDeque<>());

        // Add a user message to history on the main thread
        history.addLast(Map.of("role", "user", "content", prompt));
        while (history.size() > MAX_HISTORY_PAIRS * 2) {
            history.removeFirst();
        }

        // Prepare context copy for async call
        List<Map<String, Object>> context = new ArrayList<>(history);

        plugin.getServer()
              .getScheduler()
              .runTaskAsynchronously(plugin, () -> {
                  try {
                      // Determine API key: player override or fallback to default service's key
                      String overrideKey = SetApiKeyCommand.getKey(uuid);
                      ChatGPTService service = defaultService;
                      if (overrideKey != null && !overrideKey.isBlank()) {
                          // create a new service instance with a player-specific key
                          service = new ChatGPTService(overrideKey, defaultService.getModel(), defaultService.getTimeout(), logger);
                      }

                      String response = service.sendChat(context);

                      // Append assistant response and notify player on the main thread
                      plugin.getServer()
                            .getScheduler()
                            .runTask(plugin, () -> {
                                history.addLast(Map.of("role", "assistant", "content", response));
                                while (history.size() > MAX_HISTORY_PAIRS * 2) {
                                    history.removeFirst();
                                }
                                player.sendMessage(ChatColor.GREEN + "[CoreAI] " + response);
                            });
                  } catch (Exception e) {
                      plugin.getServer()
                            .getScheduler()
                            .runTask(plugin, () -> player.sendMessage(ChatColor.RED + "[CoreAI] Error: " + e.getMessage()));
                      logger.log(Level.SEVERE, "Error in ChatCommand", e);
                  }
              });

        return true;
    }
}