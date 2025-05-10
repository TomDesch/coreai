package be.stealingdapenta.coreai.command;

import static be.stealingdapenta.coreai.CoreAI.CORE_AI_LOGGER;
import static be.stealingdapenta.coreai.permission.PermissionNode.IMAGE_MAP;
import static be.stealingdapenta.coreai.util.ChatMessages.NO_PERMISSION;
import static be.stealingdapenta.coreai.util.ChatMessages.PLAYERS_ONLY;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.GREEN;
import static net.kyori.adventure.text.format.NamedTextColor.RED;
import static net.kyori.adventure.text.format.NamedTextColor.YELLOW;

import be.stealingdapenta.coreai.CoreAI;
import be.stealingdapenta.coreai.map.MapImageService;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.jetbrains.annotations.NotNull;

public class ImageMapCommand implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PLAYERS_ONLY);
            return true;
        }

        if (!player.hasPermission(IMAGE_MAP.node())) {
            player.sendMessage(NO_PERMISSION);
            return true;
        }

        if (args.length != 1) {
            player.sendMessage(text("Usage: /" + label + " <image-url>", GRAY));
            return true;
        }

        String url = args[0];
        player.sendMessage(text("[CoreAI] Downloading image...", YELLOW));

        Bukkit.getScheduler()
              .runTaskAsynchronously(CoreAI.getInstance(), () -> {
                  try {
                      BufferedImage img = ImageIO.read(java.net.URI.create(url)
                                                                   .toURL());
                      BufferedImage scaled = MapImageService.resizeToMap(img);

                      Bukkit.getScheduler()
                            .runTask(CoreAI.getInstance(), () -> {
                                MapView mapView = Bukkit.createMap(player.getWorld());
                                mapView.getRenderers()
                                       .clear();
                                mapView.addRenderer(MapImageService.rendererFrom(scaled));

                                ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
                                MapMeta meta = (MapMeta) mapItem.getItemMeta();
                                meta.setMapView(mapView);
                                mapItem.setItemMeta(meta);
                                player.getInventory()
                                      .addItem(mapItem);

                                player.sendMessage(text("[CoreAI] Image map created and added to your inventory.", GREEN));
                            });
                  } catch (Exception e) {
                      CORE_AI_LOGGER.warning(e.getMessage());
                      player.sendMessage(text("[CoreAI] Failed to create image map: " + e.getMessage(), RED));
                  }
              });

        return true;
    }
}
