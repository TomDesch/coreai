package be.stealingdapenta.coreai.command;

import static be.stealingdapenta.coreai.util.ChatMessages.FETCHING_MODELS;
import static be.stealingdapenta.coreai.util.ChatMessages.INVALID_API_KEY_WITH_INSTRUCTIONS;
import static be.stealingdapenta.coreai.util.ChatMessages.NO_PERMISSION;
import static be.stealingdapenta.coreai.util.ChatMessages.PLAYERS_ONLY;
import static be.stealingdapenta.coreai.util.ChatMessages.BUSY;

import be.stealingdapenta.coreai.CoreAI;
import be.stealingdapenta.coreai.gui.ModelSelectorGUI;
import be.stealingdapenta.coreai.manager.SessionManager;
import be.stealingdapenta.coreai.permission.PermissionNode;
import be.stealingdapenta.coreai.service.ChatAgent;
import be.stealingdapenta.coreai.service.OpenAiException;
import be.stealingdapenta.coreai.util.ChatMessages;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * /models command: fetches available models and opens the selection GUI.
 */
public class ModelCommand implements CommandExecutor {

    private final ModelSelectorGUI gui;

    public ModelCommand(@NotNull ModelSelectorGUI gui) {
        this.gui = gui;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            ChatMessages.send(sender, PLAYERS_ONLY);
            return true;
        }
        if (!player.hasPermission(PermissionNode.MODELS.node())) {
            ChatMessages.send(player, NO_PERMISSION);
            return true;
        }

        UUID uuid = player.getUniqueId();
        ChatMessages.send(player, FETCHING_MODELS);

        // Get the player's agent (includes key and stored model)
        ChatAgent agent = SessionManager.SESSION_MANAGER.getAgent(uuid);
        UUID requestId = gui.beginRequest(player);
        String key = agent.getApiKey();
        if (key == null || key.isBlank()) {
            gui.cancelRequest(uuid, requestId);
            ChatMessages.send(player, INVALID_API_KEY_WITH_INSTRUCTIONS);
            return true;
        }

        boolean accepted = CoreAI.getInstance().submitAsync(() -> {
                  if (!gui.isCurrentRequest(uuid, requestId)
                          || !SessionManager.SESSION_MANAGER.isCurrentAgent(uuid, agent)) {
                      return;
                  }
                  try {
                      List<String> models = agent.listModels();
                      CoreAI.getInstance().runMain(() -> {
                          if (isCurrent(player, uuid, agent, requestId)) {
                              gui.openModelGui(player, models, agent, requestId);
                          }
                      });
                  } catch (OpenAiException oae) {
                      CoreAI.getInstance().runMain(() -> {
                          if (!isCurrent(player, uuid, agent, requestId)) {
                              return;
                          }
                          gui.cancelRequest(uuid, requestId);
                                if ("invalid_api_key".equals(oae.getCode())) {
                                    ChatMessages.send(player, INVALID_API_KEY_WITH_INSTRUCTIONS);
                                } else {
                                    ChatMessages.send(player, ChatMessages.openAiError(oae));
                                }
                      });
                      return;
                  } catch (IOException ioe) {
                      CoreAI.getInstance().runMain(() -> {
                          if (isCurrent(player, uuid, agent, requestId)) {
                              gui.cancelRequest(uuid, requestId);
                              ChatMessages.send(player, ChatMessages.ioError(ioe));
                          }
                      });
                  }
              });
        if (!accepted) {
            gui.cancelRequest(uuid, requestId);
            ChatMessages.send(player, BUSY);
        }

        return true;
    }

    private boolean isCurrent(Player player, UUID uuid, ChatAgent agent, UUID requestId) {
        return player.isOnline() && Bukkit.getPlayer(uuid) == player
                && gui.isCurrentRequest(uuid, requestId)
                && SessionManager.SESSION_MANAGER.isCurrentAgent(uuid, agent);
    }
}
