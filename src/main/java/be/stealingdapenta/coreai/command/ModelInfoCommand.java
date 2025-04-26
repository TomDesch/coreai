package be.stealingdapenta.coreai.command;

import static net.kyori.adventure.text.format.NamedTextColor.AQUA;
import static net.kyori.adventure.text.format.NamedTextColor.GOLD;
import static net.kyori.adventure.text.format.NamedTextColor.RED;

import be.stealingdapenta.coreai.manager.SessionManager;
import be.stealingdapenta.coreai.permission.PermissionNode;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * /modelinfo command: displays the player's current AI model and configuration.
 */
public class ModelInfoCommand implements CommandExecutor {

    /**
     * Executes the /modelinfo command.
     *
     * @param sender  the command sender
     * @param command the command executed
     * @param label   the alias used
     * @param args    command arguments
     * @return true if handled
     */
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", RED));
            return true;
        }
        if (!player.hasPermission(PermissionNode.MODELS.node())) {
            player.sendMessage(Component.text("You don't have permission to view your model info.", RED));
            return true;
        }

        UUID uuid = player.getUniqueId();
        var agent = SessionManager.SESSION_MANAGER.getAgent(uuid);
        String model = agent.getModel();
        String apiKey = agent.getApiKey();
        String maskedKey = apiKey == null || apiKey.isBlank() ? "<none>" : "****" + apiKey.substring(Math.max(0, apiKey.length() - 4));

        player.sendMessage(Component.text("[CoreAI] Current Model: " + model, AQUA));
        player.sendMessage(Component.text("[CoreAI] API Key: " + maskedKey, AQUA));
        player.sendMessage(Component.text("Use /models to change model or /setapikey to update your key.", GOLD));
        return true;
    }
}
