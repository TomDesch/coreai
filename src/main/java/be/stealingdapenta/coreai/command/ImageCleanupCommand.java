package be.stealingdapenta.coreai.command;

import static be.stealingdapenta.coreai.map.MapStorage.MAP_STORAGE;
import static be.stealingdapenta.coreai.permission.PermissionNode.IMAGE_CLEANUP;
import static be.stealingdapenta.coreai.util.ChatMessages.NO_PERMISSION;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.GREEN;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class ImageCleanupCommand implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission(IMAGE_CLEANUP.node())) {
            sender.sendMessage(NO_PERMISSION);
            return true;
        }

        sender.sendMessage("[CoreAI] Cleaning up unused images...");

        MAP_STORAGE.cleanUpUnusedImages();
        sender.sendMessage(text("[CoreAI] Finished cleaning up unused images. Check the logs for any potential issues.", GREEN));
        return true;
    }
}
