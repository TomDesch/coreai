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
 * /chat command: maintains conversation context per player.
 */
public class ChatCommand implements CommandExecutor {

    private final Plugin plugin;
    private final ChatGPTService chatService;
    private static final int MAX_HISTORY_PAIRS = 10;
    private final Logger logger;
    // per-player conversation history: up to MAX_HISTORY_PAIRS user+assistant messages
    private final Map<UUID, Deque<Map<String, Object>>> histories = new ConcurrentHashMap<>();

    public ChatCommand(Plugin plugin, ChatGPTService chatService) {
        this.plugin = plugin;
        this.chatService = chatService;
        this.logger = plugin.getLogger();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {
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
        // Initialize history for player if absent
        Deque<Map<String, Object>> history = histories.computeIfAbsent(uuid, id -> new ArrayDeque<>());

        // Create user message entry
        Map<String, Object> userMsg = Map.of("role", "user", "content", prompt);

        // Update history on main thread
        history.addLast(userMsg);
        // Trim oldest if exceeding max pairs
        while (history.size() > MAX_HISTORY_PAIRS * 2) {
            history.removeFirst();
        }

        // Copy context for use in async task
        List<Map<String, Object>> context = new ArrayList<>(history);

        // Perform API call asynchronously
        plugin.getServer()
              .getScheduler()
              .runTaskAsynchronously(plugin, () -> {
                  try {
                      String response = chatService.sendChat(context);

                      // Schedule appending assistant response and sending to player
                      plugin.getServer()
                            .getScheduler()
                            .runTask(plugin, () -> {
                                // Append assistant message to history
                                Map<String, Object> assistantMsg = Map.of("role", "assistant", "content", response);
                                history.addLast(assistantMsg);
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
