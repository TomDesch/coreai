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
import static be.stealingdapenta.coreai.util.ChatMessages.BUSY;
import static be.stealingdapenta.coreai.util.ChatMessages.imageMapCreated;
import static be.stealingdapenta.coreai.util.ChatMessages.mapCreationFailure;
import static be.stealingdapenta.coreai.util.ChatMessages.usageImageMapCommand;

import be.stealingdapenta.coreai.CoreAI;
import be.stealingdapenta.coreai.map.ImageGrid;
import be.stealingdapenta.coreai.service.SafeImageDownloader;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class ImageMapCommand implements CommandExecutor {

    static final class ParsedImageRequest {
        final String url;
        final ImageGrid grid;

        ParsedImageRequest(String url, ImageGrid grid) {
            this.url = url;
            this.grid = grid;
        }
    }

    static ParsedImageRequest parseRequest(String[] args) {
        if (args.length < 1 || args.length > 2) {
            return null;
        }

        String url;
        ImageGrid grid = new ImageGrid(1, 1);
        if (args.length == 2) {
            try {
                if (args[0].matches("(?i)\\d+x\\d+")) {
                    grid = ImageGrid.parse(args[0]);
                    url = args[1];
                } else {
                    grid = ImageGrid.parse(args[1]);
                    url = args[0];
                }
            } catch (IllegalArgumentException exception) {
                return null;
            }
        } else {
            url = args[0];
        }

        try {
            SafeImageDownloader.validateSource(url);
        } catch (IOException exception) {
            return null;
        }
        return new ParsedImageRequest(url, grid);
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

        final int finalWidth = request.grid.columns();
        final int finalHeight = request.grid.rows();
        final String url = request.url;
        final UUID playerId = player.getUniqueId();

        boolean accepted = CoreAI.getInstance().submitAsync(() -> {
                  try {
                      BufferedImage img = SafeImageDownloader.download(url);

                      BufferedImage gridImage = MAP_IMAGE_SERVICE.resizeToGrid(img, finalWidth, finalHeight);
                      BufferedImage[][] tiles = MAP_IMAGE_SERVICE.splitIntoTiles(gridImage, finalWidth, finalHeight);

                      CoreAI.getInstance().runMain(() -> {
                          if (!player.isOnline() || Bukkit.getPlayer(playerId) != player) {
                              return;
                          }
                                MAP_IMAGE_SERVICE.addMapToInventory(player, finalWidth, finalHeight, tiles);

                                ChatMessages.send(player, imageMapCreated((finalWidth * finalHeight)));
                            });
                  } catch (Exception e) {
                      CORE_AI_LOGGER.warning("Failed to process image map: " + e.getMessage());
                      CoreAI.getInstance().runMain(() -> {
                          if (player.isOnline() && Bukkit.getPlayer(playerId) == player) {
                              ChatMessages.send(player, mapCreationFailure("The image could not be processed."));
                          }
                      });
                  }
              });
        if (!accepted) {
            ChatMessages.send(player, BUSY);
        }

        return true;
    }
}
