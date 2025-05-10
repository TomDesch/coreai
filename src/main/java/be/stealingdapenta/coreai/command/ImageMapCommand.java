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

        if (args.length < 1 || args.length > 2) {
            player.sendMessage(text("Usage: /" + label + " [WxH] <image-url>", GRAY));
            return true;
        }

        int width = 1;
        int height = 1;
        String url;

        if (args.length == 2) {
            String[] dims = args[0].toLowerCase()
                                   .split("x");
            if (dims.length != 2) {
                player.sendMessage(text("Invalid size format. Use format WxH (e.g., 2x3)", RED));
                return true;
            }

            try {
                width = Integer.parseInt(dims[0]);
                height = Integer.parseInt(dims[1]);
            } catch (NumberFormatException e) {
                player.sendMessage(text("Invalid numbers in size. Use integers like 2x2.", RED));
                return true;
            }

            url = args[1];
        } else {
            url = args[0];
        }

        player.sendMessage(text("[CoreAI] Downloading and processing image...", YELLOW));

        final int finalWidth = width;
        final int finalHeight = height;

        Bukkit.getScheduler()
              .runTaskAsynchronously(CoreAI.getInstance(), () -> {
                  try {
                      BufferedImage img = ImageIO.read(java.net.URI.create(url)
                                                                   .toURL());
                      if (img == null) {
                          throw new IllegalArgumentException("Invalid image format or unreachable URL.");
                      }

                      BufferedImage gridImage = MapImageService.resizeToGrid(img, finalWidth, finalHeight);
                      BufferedImage[][] tiles = MapImageService.splitIntoTiles(gridImage, finalWidth, finalHeight);

                      Bukkit.getScheduler()
                            .runTask(CoreAI.getInstance(), () -> {
                                for (int row = 0; row < finalHeight; row++) {
                                    for (int col = 0; col < finalWidth; col++) {
                                        BufferedImage tile = tiles[row][col];
                                        MapView mapView = Bukkit.createMap(player.getWorld());
                                        mapView.getRenderers()
                                               .clear();
                                        mapView.addRenderer(MapImageService.rendererFrom(tile));

                                        ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
                                        MapMeta meta = (MapMeta) mapItem.getItemMeta();
                                        meta.setMapView(mapView);
                                        mapItem.setItemMeta(meta);

                                        player.getInventory()
                                              .addItem(mapItem);
                                    }
                                }

                                player.sendMessage(text("[CoreAI] Generated " + (finalWidth * finalHeight) + " map tile(s) and added to your inventory.", GREEN));
                            });
                  } catch (Exception e) {
                      CORE_AI_LOGGER.warning("Failed to process image map: " + e.getMessage());
                      player.sendMessage(text("[CoreAI] Failed to create image map: " + e.getMessage(), RED));
                  }
              });

        return true;
    }
}
