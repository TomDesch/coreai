package be.stealingdapenta.coreai.command;

import static be.stealingdapenta.coreai.CoreAI.CORE_AI_LOGGER;

import be.stealingdapenta.coreai.service.ChatGPTService;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * /chat command: sends a prompt to ChatGPT and returns the AI's reply.
 */
public class ChatCommand implements CommandExecutor {

    private final Plugin plugin;
    private final ChatGPTService chatService;

    public ChatCommand(Plugin plugin, ChatGPTService chatService) {
        this.plugin = plugin;
        this.chatService = chatService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }
        if (!player.hasPermission("coreai.chat")) {
            player.sendMessage(ChatColor.RED + "You don’t have permission to use this.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage("§cUsage: /" + label + " <message>");
            return true;
        }

        String prompt = String.join(" ", args);

        if (prompt.length() > 500) {
            player.sendMessage(ChatColor.RED + "Your message is too long (max 500 chars).");
            return true;
        }

        player.sendMessage("§7[CoreAI] Thinking…");

        // Run API call asynchronously
        plugin.getServer()
              .getScheduler()
              .runTaskAsynchronously(plugin, () -> {
                  try {
                      String response = chatService.sendMessage(prompt);
                      // Send a result back on the main thread
                      plugin.getServer()
                            .getScheduler()
                            .runTask(plugin, () -> player.sendMessage("§a[CoreAI] " + response));
                  } catch (Exception e) {
                      plugin.getServer()
                            .getScheduler()
                            .runTask(plugin, () -> player.sendMessage("§c[CoreAI] Error: " + e.getMessage()));
                      CORE_AI_LOGGER.severe("Error in ChatCommand" + e);

                  }
              });
        return true;
    }
}
