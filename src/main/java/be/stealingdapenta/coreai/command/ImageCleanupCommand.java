package be.stealingdapenta.coreai.command;

import static be.stealingdapenta.coreai.map.MapStorage.MAP_STORAGE;
import static be.stealingdapenta.coreai.permission.PermissionNode.IMAGE_CLEANUP;
import static be.stealingdapenta.coreai.util.ChatMessages.NO_PERMISSION;
import static be.stealingdapenta.coreai.util.ChatMessages.send;

import be.stealingdapenta.coreai.CoreAI;
import be.stealingdapenta.coreai.listener.MapUsageTrackerListener;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class ImageCleanupCommand implements CommandExecutor {

    private final MapUsageTrackerListener mapUsageTracker;

    public ImageCleanupCommand(MapUsageTrackerListener mapUsageTracker) {
        this.mapUsageTracker = mapUsageTracker;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission(IMAGE_CLEANUP.node())) {
            send(sender, NO_PERMISSION);
            return true;
        }
        if (args.length != 1 || !"confirm".equalsIgnoreCase(args[0])) {
            sender.sendMessage("[CoreAI] Cleanup is age-based and may include maps in offline inventories or unloaded containers. Use /cleanup confirm to proceed.");
            return true;
        }

        sender.sendMessage("[CoreAI] Refreshing map usage before cleanup. This may take a moment.");
        mapUsageTracker.refreshLoadedUsage().thenCompose(ignored -> MAP_STORAGE.cleanUpUnusedImages())
                .whenComplete((count, error) ->
                CoreAI.getInstance().runMain(() -> {
                    if (sender instanceof Player player && !player.isOnline()) {
                        return;
                    }
                    if (error != null) {
                        sender.sendMessage("[CoreAI] Cleanup could not finish. Check the server log.");
                    } else {
                        sender.sendMessage("[CoreAI] Removed " + count + " stale map image file(s).");
                    }
                }));
        return true;
    }
}
