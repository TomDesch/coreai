package be.stealingdapenta.coreai.command;

import static be.stealingdapenta.coreai.manager.SessionManager.SESSION_MANAGER;
import static be.stealingdapenta.coreai.service.OpenAIApi.OPEN_AI_API;
import static be.stealingdapenta.coreai.util.ChatMessages.INVALID_API_KEY_WITH_INSTRUCTIONS;
import static be.stealingdapenta.coreai.util.ChatMessages.NO_PERMISSION;
import static be.stealingdapenta.coreai.util.ChatMessages.PLAYERS_ONLY;
import static be.stealingdapenta.coreai.util.ChatMessages.BUSY;
import static be.stealingdapenta.coreai.util.ChatMessages.fetchingModelInfo;
import static be.stealingdapenta.coreai.util.ChatMessages.ioError;
import static be.stealingdapenta.coreai.util.ChatMessages.modelInfo;
import static be.stealingdapenta.coreai.util.ChatMessages.openAiError;
import static net.kyori.adventure.text.format.NamedTextColor.AQUA;

import be.stealingdapenta.coreai.CoreAI;
import be.stealingdapenta.coreai.util.ChatMessages;
import be.stealingdapenta.coreai.permission.PermissionNode;
import be.stealingdapenta.coreai.service.ChatAgent;
import be.stealingdapenta.coreai.service.OpenAiException;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * /modelinfo command: fetches and displays detailed info for the player's current AI model.
 */
public class ModelInfoCommand implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            ChatMessages.send(sender, PLAYERS_ONLY);
            return true;
        }
        if (!player.hasPermission(PermissionNode.MODEL_INFO.node())) {
            ChatMessages.send(player, NO_PERMISSION);
            return true;
        }

        UUID uuid = player.getUniqueId();
        ChatAgent agent = SESSION_MANAGER.getAgent(uuid);
        String apiKey = agent.getApiKey();
        String modelId = agent.getModel();

        if (apiKey == null || apiKey.isBlank()) {
            ChatMessages.send(player, INVALID_API_KEY_WITH_INSTRUCTIONS);
            return true;
        }

        ChatMessages.send(player, fetchingModelInfo(modelId));

        // Async fetch model info
        boolean accepted = CoreAI.getInstance().submitAsync(() -> {
                  if (!SESSION_MANAGER.isCurrentAgent(uuid, agent)) {
                      return;
                  }
                  try {
                      Map<String, Object> info = OPEN_AI_API.getModelInfo(apiKey, modelId);
                      CoreAI.getInstance().runMain(() -> {
                          if (!isCurrent(player, uuid, agent)) {
                              return;
                          }
                                ChatMessages.send(player, modelInfo(modelId));
                                info.forEach((key, value) -> {
                                    ChatMessages.send(player, Component.text(key + ": " + value, AQUA));
                                });
                            });
                  } catch (OpenAiException oae) {
                      CoreAI.getInstance().runMain(() -> {
                          if (!isCurrent(player, uuid, agent)) {
                              return;
                          }
                                ChatMessages.send(player, openAiError(oae));
                            });
                  } catch (IOException ioe) {
                      CoreAI.getInstance().runMain(() -> {
                          if (!isCurrent(player, uuid, agent)) {
                              return;
                          }
                                ChatMessages.send(player, ioError(ioe));
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
