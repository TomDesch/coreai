package be.stealingdapenta.coreai.command;

import static be.stealingdapenta.coreai.manager.SessionManager.SESSION_MANAGER;
import static be.stealingdapenta.coreai.util.ChatMessages.INVALID_API_KEY_WITH_INSTRUCTIONS;
import static be.stealingdapenta.coreai.util.ChatMessages.MODEL_IS_THINKING;
import static be.stealingdapenta.coreai.util.ChatMessages.MODEL_NOT_FOUND;
import static be.stealingdapenta.coreai.util.ChatMessages.NO_PERMISSION;
import static be.stealingdapenta.coreai.util.ChatMessages.PLAYERS_ONLY;
import static be.stealingdapenta.coreai.util.ChatMessages.chatPrompt;
import static be.stealingdapenta.coreai.util.ChatMessages.chatResponse;

import be.stealingdapenta.coreai.CoreAI;
import be.stealingdapenta.coreai.permission.PermissionNode;
import be.stealingdapenta.coreai.service.ChatAgent;
import be.stealingdapenta.coreai.service.OpenAiException;
import be.stealingdapenta.coreai.util.ChatMessages;
import java.io.IOException;
import java.util.UUID;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class ChatCommand implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PLAYERS_ONLY);
            return true;
        }
        if (!player.hasPermission(PermissionNode.CHAT.node())) {
            player.sendMessage(NO_PERMISSION);
            return true;
        }
        if (args.length == 0) {
            return false;
        }

        String prompt = String.join(" ", args);
        player.sendMessage(chatPrompt(player, prompt));
        player.sendMessage(MODEL_IS_THINKING);
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
                            .runTask(CoreAI.getInstance(), () -> player.sendMessage(chatResponse(response)));
                  } catch (OpenAiException oae) {
                      CoreAI.getInstance()
                            .getServer()
                            .getScheduler()
                            .runTask(CoreAI.getInstance(), () -> {
                                switch (oae.getCode()) {
                                    case "invalid_api_key" -> player.sendMessage(INVALID_API_KEY_WITH_INSTRUCTIONS);
                                    case "model_not_found" -> player.sendMessage(MODEL_NOT_FOUND);

                                    default -> player.sendMessage(ChatMessages.openAiError(oae));
                                }
                            });
                  } catch (IOException ioe) {
                      // generic network/parsing error -> also on the main thread
                      CoreAI.getInstance()
                            .getServer()
                            .getScheduler()
                            .runTask(CoreAI.getInstance(), () -> player.sendMessage(ChatMessages.ioError(ioe)));
                  }
              });

        return true;
    }
}
