package be.stealingdapenta.coreai.command;

import static be.stealingdapenta.coreai.CoreAI.CORE_AI_LOGGER;
import static be.stealingdapenta.coreai.manager.SessionManager.SESSION_MANAGER;
import static net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.GREEN;
import static net.kyori.adventure.text.format.NamedTextColor.RED;

import be.stealingdapenta.coreai.CoreAI;
import be.stealingdapenta.coreai.permission.PermissionNode;
import be.stealingdapenta.coreai.service.ChatAgent;
import be.stealingdapenta.coreai.service.OpenAiException;
import java.io.IOException;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class ChatCommand implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", RED));
            return true;
        }
        if (!player.hasPermission(PermissionNode.CHAT.node())) {
            player.sendMessage(Component.text("You don't have permission.", RED));
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(Component.text("Usage: /" + label + " <message>", RED));
            return true;
        }

        String prompt = String.join(" ", args);
        player.sendMessage(Component.text("[%s] %s".formatted(player.getName(), prompt), GRAY));
        player.sendMessage(Component.text("[CoreAI] Thinking...", DARK_GRAY));
        UUID uuid = player.getUniqueId();

        // Run the chat in an async task
        CoreAI.getInstance()
              .getServer()
              .getScheduler()
              .runTaskAsynchronously(CoreAI.getInstance(), () -> {
                  ChatAgent agent = SESSION_MANAGER.getAgent(uuid);
                  try {
                      String response = agent.chat(prompt);
                      // deliver response on the main thread
                      CoreAI.getInstance()
                            .getServer()
                            .getScheduler()
                            .runTask(CoreAI.getInstance(), () -> player.sendMessage(Component.text("[CoreAI] " + response, GREEN)));
                  } catch (OpenAiException oae) {
                      CoreAI.getInstance()
                            .getServer()
                            .getScheduler()
                            .runTask(CoreAI.getInstance(), () -> {
                                switch (oae.getCode()) {
                                    case "invalid_api_key" -> player.sendMessage(Component.text("[CoreAI] Error: Your API key is invalid. Use /setapikey <key>", RED));
                                    case "model_not_found" -> player.sendMessage(Component.text("[CoreAI] Error: That model doesn’t exist. Select one with /models", RED));
                                    default -> player.sendMessage(Component.text("[CoreAI] Error: " + oae.getMessage(), RED));
                                }
                            });

                      CORE_AI_LOGGER.warning("======================================");
                      CORE_AI_LOGGER.warning("The agent: " + agent);
                      CORE_AI_LOGGER.warning("The prompt: " + prompt);
                      CORE_AI_LOGGER.warning("The error code: " + oae.getCode());
                      CORE_AI_LOGGER.warning("The error: " + oae.getMessage());
                      CORE_AI_LOGGER.warning("======================================");
                  } catch (IOException ioe) {
                      // generic network/parsing error -> also on the main thread
                      CoreAI.getInstance()
                            .getServer()
                            .getScheduler()
                            .runTask(CoreAI.getInstance(), () -> player.sendMessage(Component.text("[CoreAI] Error: " + ioe.getMessage(), RED)));
                  }
              });

        return true;
    }
}
