package be.stealingdapenta.coreai.command;

import static be.stealingdapenta.coreai.util.ChatMessages.FETCHING_MODELS;
import static be.stealingdapenta.coreai.util.ChatMessages.INVALID_API_KEY_WITH_INSTRUCTIONS;
import static be.stealingdapenta.coreai.util.ChatMessages.NO_PERMISSION;
import static be.stealingdapenta.coreai.util.ChatMessages.PLAYERS_ONLY;

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
            sender.sendMessage(PLAYERS_ONLY);
            return true;
        }
        if (!player.hasPermission(PermissionNode.MODELS.node())) {
            player.sendMessage(NO_PERMISSION);
            return true;
        }

        UUID uuid = player.getUniqueId();
        player.sendMessage(FETCHING_MODELS);

        // Get the player's agent (includes key and stored model)
        ChatAgent agent = SessionManager.SESSION_MANAGER.getAgent(uuid);
        String key = agent.getApiKey();
        if (key == null || key.isBlank()) {
            player.sendMessage(INVALID_API_KEY_WITH_INSTRUCTIONS);
            return true;
        }

        // Async: test API key then list models
        Bukkit.getScheduler()
              .runTaskAsynchronously(CoreAI.getInstance(), () -> {
                  // 1) Validate key
                  try {
                      agent.testKey();
                  } catch (OpenAiException oae) {
                      Bukkit.getScheduler()
                            .runTask(CoreAI.getInstance(), () -> {
                                if ("invalid_api_key" .equals(oae.getCode())) {
                                    player.sendMessage(INVALID_API_KEY_WITH_INSTRUCTIONS);
                                } else {
                                    player.sendMessage(ChatMessages.openAiError(oae));
                                }
                            });
                      return;
                  } catch (IOException ioe) {
                      Bukkit.getScheduler()
                            .runTask(CoreAI.getInstance(), () -> player.sendMessage(ChatMessages.ioError(ioe)));
                      return;
                  }

                  // 2) Fetch model list
                  List<String> models;
                  try {
                      models = agent.listModels();
                  } catch (OpenAiException oae) {
                      Bukkit.getScheduler()
                            .runTask(CoreAI.getInstance(), () -> player.sendMessage(ChatMessages.openAiError(oae)));
                      return;
                  }

                  // 3) Open GUI on the main thread
                  Bukkit.getScheduler()
                        .runTask(CoreAI.getInstance(), () -> gui.openModelGui(player, models));
              });

        return true;
    }
}
