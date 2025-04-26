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
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * /chat command: maintains conversation context per player, with per-player API key override, using Adventure API for message formatting.
 */
public class ChatCommand implements CommandExecutor {

    private final Plugin plugin;
    private final Logger logger;

    // per-player conversation history: up to MAX_HISTORY_PAIRS user+assistant messages
    private final ConcurrentHashMap<UUID, Deque<Map<String, Object>>> histories = new ConcurrentHashMap<>();
    private static final int MAX_HISTORY_PAIRS = 10;

    public ChatCommand(@NotNull Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return true;
        }
        if (!player.hasPermission("coreai.chat")) {
            player.sendMessage(Component.text("You don't have permission to use this.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(Component.text("Usage: /" + label + " <message>", NamedTextColor.RED));
            return true;
        }

        String prompt = String.join(" ", args);
        player.sendMessage(Component.text("[CoreAI] Thinking...", NamedTextColor.GRAY));

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
                      // Determine API key: player override or fallback to config/env
                      String key = SetApiKeyCommand.getKey(uuid);
                      if (key == null || key.isBlank()) {
                          key = plugin.getConfig()
                                      .getString("openai.api-key", "")
                                      .trim();
                          String envKey = System.getenv("OPENAI_API_KEY");
                          if (key.isBlank() && envKey != null && !envKey.isBlank()) {
                              key = envKey.trim();
                          }
                      }

                      // Determine model and timeout
                      String model = plugin.getConfig()
                                           .getString("openai.model", "gpt-3.5-turbo");
                      int timeout = plugin.getConfig()
                                          .getInt("openai.timeout-ms", 60000);

                      // Instantiate a service for this invocation
                      ChatGPTService service = new ChatGPTService(key, model, timeout, logger);
                      String response = service.sendChat(context);

                      // Append assistant response and notify player on the main thread
                      plugin.getServer()
                            .getScheduler()
                            .runTask(plugin, () -> {
                                history.addLast(Map.of("role", "assistant", "content", response));
                                while (history.size() > MAX_HISTORY_PAIRS * 2) {
                                    history.removeFirst();
                                }
                                player.sendMessage(Component.text("[CoreAI] " + response, NamedTextColor.GREEN));
                            });
                  } catch (Exception e) {
                      plugin.getServer()
                            .getScheduler()
                            .runTask(plugin, () -> player.sendMessage(Component.text("[CoreAI] Error: " + e.getMessage(), NamedTextColor.RED)));
                      logger.log(Level.SEVERE, "Error in ChatCommand", e);
                  }
              });

        return true;
    }
}
