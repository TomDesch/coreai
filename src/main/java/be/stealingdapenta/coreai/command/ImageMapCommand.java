package be.stealingdapenta.coreai.command;

import static be.stealingdapenta.coreai.CoreAI.CORE_AI_LOGGER;
import static be.stealingdapenta.coreai.map.MapImageService.MAP_IMAGE_SERVICE;
import static be.stealingdapenta.coreai.permission.PermissionNode.IMAGE_MAP;
import static be.stealingdapenta.coreai.util.ChatMessages.DOWNLOADING_IMAGE;

import be.stealingdapenta.coreai.util.ChatMessages;
import static be.stealingdapenta.coreai.util.ChatMessages.INVALID_DIMENSIONS;
import static be.stealingdapenta.coreai.util.ChatMessages.INVALID_SIZE_NUMBER;
import static be.stealingdapenta.coreai.util.ChatMessages.NO_PERMISSION;
import static be.stealingdapenta.coreai.util.ChatMessages.PLAYERS_ONLY;
import static be.stealingdapenta.coreai.util.ChatMessages.imageMapCreated;
import static be.stealingdapenta.coreai.util.ChatMessages.mapCreationFailure;
import static be.stealingdapenta.coreai.util.ChatMessages.usageImageMapCommand;

import be.stealingdapenta.coreai.CoreAI;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import javax.imageio.ImageIO;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class ImageMapCommand implements CommandExecutor {

    static final class ParsedImageRequest {
        final String url;
        final int width;
        final int height;

        ParsedImageRequest(String url, int width, int height) {
            this.url = url;
            this.width = width;
            this.height = height;
        }
    }

    static ParsedImageRequest parseRequest(String[] args) {
        if (args.length < 1 || args.length > 2) {
            return null;
        }

        String url;
        int width = 1;
        int height = 1;

        if (args.length == 2) {
            String[] dims = args[0].toLowerCase()
                                   .split("x");
            if (dims.length != 2) {
               return null;
            }

            try {
               width = Integer.parseInt(dims[0]);
               height = Integer.parseInt(dims[1]);
            } catch (NumberFormatException e) {
               return null;
            }

            url = args[1];
        } else {
            url = args[0];
        }

        return new ParsedImageRequest(url, width, height);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            ChatMessages.send(sender, PLAYERS_ONLY);
            return true;
        }

        if (!player.hasPermission(IMAGE_MAP.node())) {
            ChatMessages.send(player, NO_PERMISSION);
            return true;
        }

        if (args.length < 1 || args.length > 2) {
            ChatMessages.send(player, usageImageMapCommand(label, "<image-url>"));
            return true;
        }

        ParsedImageRequest request = parseRequest(args);
        if (request == null) {
            if (args.length == 2 && args[0].toLowerCase()
                                        .contains("x")) {
               ChatMessages.send(player, INVALID_DIMENSIONS);
            } else {
               ChatMessages.send(player, INVALID_SIZE_NUMBER);
            }
            return true;
        }

        ChatMessages.send(player, DOWNLOADING_IMAGE);

        final int finalWidth = request.width;
        final int finalHeight = request.height;
        final String url = request.url;

        Bukkit.getScheduler()
              .runTaskAsynchronously(CoreAI.getInstance(), () -> {
                  try {
                      BufferedImage img = ImageIO.read(java.net.URI.create(url)
                                                                   .toURL());
                      if (img == null) {
                          throw new IllegalArgumentException("Invalid image format or unreachable URL.");
                      }

                      BufferedImage gridImage = MAP_IMAGE_SERVICE.resizeToGrid(img, finalWidth, finalHeight);
                      BufferedImage[][] tiles = MAP_IMAGE_SERVICE.splitIntoTiles(gridImage, finalWidth, finalHeight);

                      Bukkit.getScheduler()
                            .runTask(CoreAI.getInstance(), () -> {
                                MAP_IMAGE_SERVICE.addMapToInventory(player, finalWidth, finalHeight, tiles);

                                ChatMessages.send(player, imageMapCreated((finalWidth * finalHeight)));
                            });
                  } catch (Exception e) {
                      CORE_AI_LOGGER.warning("Failed to process image map: " + e.getMessage());
                      CORE_AI_LOGGER.warning(Arrays.toString(e.getStackTrace()));
                      ChatMessages.send(player, mapCreationFailure(e.getMessage()));
                  }
              });

        return true;
    }
}
