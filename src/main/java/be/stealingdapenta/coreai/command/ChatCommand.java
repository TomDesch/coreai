package be.stealingdapenta.coreai.command;

import static be.stealingdapenta.coreai.manager.SessionManager.SESSION_MANAGER;
import static be.stealingdapenta.coreai.util.ChatMessages.INVALID_API_KEY_WITH_INSTRUCTIONS;
import static be.stealingdapenta.coreai.util.ChatMessages.MODEL_IS_THINKING;
import static be.stealingdapenta.coreai.util.ChatMessages.MODEL_NOT_FOUND;
import static be.stealingdapenta.coreai.util.ChatMessages.NO_PERMISSION;
import static be.stealingdapenta.coreai.util.ChatMessages.PLAYERS_ONLY;
import static be.stealingdapenta.coreai.util.ChatMessages.chatPrompt;
import static be.stealingdapenta.coreai.util.ChatMessages.chatResponse;
import static be.stealingdapenta.coreai.util.ChatMessages.BUSY;

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
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class ChatCommand implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            ChatMessages.send(sender, PLAYERS_ONLY);
            return true;
        }
        if (!player.hasPermission(PermissionNode.CHAT.node())) {
            ChatMessages.send(player, NO_PERMISSION);
            return true;
        }
        if (args.length == 0) {
            return false;
        }

        String prompt = String.join(" ", args);
        ChatMessages.send(player, chatPrompt(player, prompt));
        ChatMessages.send(player, MODEL_IS_THINKING);
        UUID uuid = player.getUniqueId();
        ChatAgent agent = SESSION_MANAGER.getAgent(uuid);

        boolean accepted = CoreAI.getInstance().submitAsync(() -> {
                  if (!SESSION_MANAGER.isCurrentAgent(uuid, agent)) {
                      return;
                  }
                  try {
                      String response = agent.chat(prompt);
                      CoreAI.getInstance().runMain(() -> {
                          if (isCurrent(player, uuid, agent)) {
                              ChatMessages.send(player, chatResponse(response));
                          }
                      });
                  } catch (OpenAiException oae) {
                      CoreAI.getInstance().runMain(() -> {
                          if (!isCurrent(player, uuid, agent)) {
                              return;
                          }
                                switch (oae.getCode()) {
                                    case "invalid_api_key" -> ChatMessages.send(player, INVALID_API_KEY_WITH_INSTRUCTIONS);
                                    case "model_not_found" -> ChatMessages.send(player, MODEL_NOT_FOUND);

                                    default -> ChatMessages.send(player, ChatMessages.openAiError(oae));
                                }
                      });
                  } catch (IOException ioe) {
                      CoreAI.getInstance().runMain(() -> {
                          if (isCurrent(player, uuid, agent)) {
                              ChatMessages.send(player, ChatMessages.ioError(ioe));
                          }
                      });
                  }
              });
        if (!accepted) {
            ChatMessages.send(player, BUSY);
        }

        return true;
    }

    private boolean isCurrent(Player player, UUID uuid, ChatAgent agent) {
        return player.isOnline() && Bukkit.getPlayer(uuid) == player
                && SESSION_MANAGER.isCurrentAgent(uuid, agent);
    }
}
