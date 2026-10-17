package be.stealingdapenta.coreai.command;

import static be.stealingdapenta.coreai.util.ChatMessages.NO_PERMISSION;
import static be.stealingdapenta.coreai.util.ChatMessages.PLAYERS_ONLY;
import static be.stealingdapenta.coreai.util.ChatMessages.send;

import be.stealingdapenta.coreai.listener.AsyncApiKeyListener;
import be.stealingdapenta.coreai.permission.PermissionNode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class SetApiKeyCommand implements CommandExecutor {

    private final AsyncApiKeyListener keyListener;

    public SetApiKeyCommand(AsyncApiKeyListener keyListener) {
        this.keyListener = keyListener;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            send(sender, PLAYERS_ONLY);
            return true;
        }
        if (!player.hasPermission(PermissionNode.SET_API_KEY.node())) {
            send(player, NO_PERMISSION);
            return true;
        }
        if (args.length != 0) {
            return false;
        }
        keyListener.beginKeyInput(player);
        return true;
    }
}
