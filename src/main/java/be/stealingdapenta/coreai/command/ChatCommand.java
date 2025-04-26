package be.stealingdapenta.coreai.command;

import be.stealingdapenta.coreai.service.ChatAgent;
import be.stealingdapenta.coreai.service.ChatAgentFactory;
import java.util.UUID;
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
 * /chat command: delegates to per-player ChatAgent for context-aware conversation.
 */
public class ChatCommand implements CommandExecutor {

    private final Plugin plugin;
    private final Logger logger;

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
            player.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(Component.text("Usage: /" + label + " <message>", NamedTextColor.RED));
            return true;
        }

        String prompt = String.join(" ", args);
        player.sendMessage(Component.text("[CoreAI] Thinking...", NamedTextColor.GRAY));

        UUID uuid = player.getUniqueId();

        plugin.getServer()
              .getScheduler()
              .runTaskAsynchronously(plugin, () -> {
                  try {
                      // Determine API key: plugin config only
                      String defaultKey = plugin.getConfig()
                                                .getString("openai.api-key", "")
                                                .trim();

                      // Build or fetch agent
                      ChatAgent agent = ChatAgentFactory.getAgent(uuid, defaultKey, plugin.getConfig()
                                                                                          .getString("openai.model", "gpt-3.5-turbo"), plugin.getConfig()
                                                                                                                                             .getInt("openai.timeout-ms", 60000), logger);

                      // Apply player override key if set
                      String overrideKey = SetApiKeyCommand.getKey(uuid);
                      if (overrideKey != null && !overrideKey.isBlank()) {
                          agent.setApiKey(overrideKey);
                      }

                      // TODO: apply model selector override: agent.setModel(...)

                      // Perform chat
                      String response = agent.chat(prompt);

                      // Send back on the main thread
                      plugin.getServer()
                            .getScheduler()
                            .runTask(plugin, () -> player.sendMessage(Component.text("[CoreAI] " + response, NamedTextColor.GREEN)));
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
