package be.stealingdapenta.coreai.command;

import static be.stealingdapenta.coreai.config.Config.API_KEY;
import static be.stealingdapenta.coreai.config.Config.MODEL;
import static be.stealingdapenta.coreai.config.Config.TIMEOUT_MS;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.RED;

import be.stealingdapenta.coreai.CoreAI;
import be.stealingdapenta.coreai.gui.ModelSelectorGUI;
import be.stealingdapenta.coreai.service.ChatAgent;
import be.stealingdapenta.coreai.service.ChatAgentFactory;
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
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can select models.", RED));
            return true;
        }

        player.sendMessage(Component.text("[CoreAI] Fetching available models...", GRAY));
        UUID uuid = player.getUniqueId();

        // Retrieve or create agent
        ChatAgent agent = ChatAgentFactory.getAgent(uuid, API_KEY.get(), MODEL.get(), TIMEOUT_MS.get());

        // Async fetch
        Bukkit.getScheduler()
              .runTaskAsynchronously(CoreAI.getInstance(), () -> {
                  List<String> models = agent.listModels();
                  // Sync open GUI
                  Bukkit.getScheduler()
                        .runTask(CoreAI.getInstance(), () -> gui.openModelGui(player, models));
              });
        return true;
    }
}