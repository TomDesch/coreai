package be.stealingdapenta.coreai.command;

import static net.kyori.adventure.text.format.NamedTextColor.GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.RED;

import be.stealingdapenta.coreai.CoreAI;
import be.stealingdapenta.coreai.gui.ModelSelectorGUI;
import be.stealingdapenta.coreai.manager.SessionManager;
import be.stealingdapenta.coreai.permission.PermissionNode;
import be.stealingdapenta.coreai.service.ChatAgent;
import be.stealingdapenta.coreai.service.OpenAiException;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
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
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can select models.", RED));
            return true;
        }
        if (!player.hasPermission(PermissionNode.MODELS.node())) {
            player.sendMessage(Component.text("You don't have permission.", RED));
            return true;
        }

        UUID uuid = player.getUniqueId();
        player.sendMessage(Component.text("[CoreAI] Fetching available models...", GRAY));

        // Get the player's agent (includes key and stored model)
        ChatAgent agent = SessionManager.SESSION_MANAGER.getAgent(uuid);
        String key = agent.getApiKey();
        if (key == null || key.isBlank()) {
            player.sendMessage(Component.text("[CoreAI] You have not set an API key. Use /setapikey <key> to configure.", RED));
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
                                if ("invalid_api_key".equals(oae.getCode())) {
                                    player.sendMessage(Component.text("[CoreAI] Your API key is invalid—please set it with /setapikey <key>", RED));
                                } else {
                                    player.sendMessage(Component.text("[CoreAI] Error validating key: " + oae.getMessage(), RED));
                                }
                            });
                      return;
                  } catch (IOException ioe) {
                      Bukkit.getScheduler()
                            .runTask(CoreAI.getInstance(), () -> player.sendMessage(Component.text("[CoreAI] Error validating API key: " + ioe.getMessage(), RED)));
                      return;
                  }

                  // 2) Fetch model list
                  List<String> models;
                  try {
                      models = agent.listModels();
                  } catch (OpenAiException | RuntimeException oae) {
                      Bukkit.getScheduler()
                            .runTask(CoreAI.getInstance(), () -> player.sendMessage(Component.text("[CoreAI] Error fetching models: " + oae.getMessage(), RED)));
                      return;
                  }

                  // 3) Open GUI on the main thread
                  Bukkit.getScheduler()
                        .runTask(CoreAI.getInstance(), () -> gui.openModelGui(player, models));
              });

        return true;
    }
}
