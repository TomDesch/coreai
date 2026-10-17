package be.stealingdapenta.coreai.command;

import static be.stealingdapenta.coreai.CoreAI.CORE_AI_LOGGER;
import static be.stealingdapenta.coreai.map.MapImageService.MAP_IMAGE_SERVICE;
import static be.stealingdapenta.coreai.permission.PermissionNode.IMAGE_GEN_MAP;
import static be.stealingdapenta.coreai.util.ChatMessages.GENERATING_AI_IMAGE;
import static be.stealingdapenta.coreai.util.ChatMessages.IMAGE_GENERATION_ERROR;
import static be.stealingdapenta.coreai.util.ChatMessages.INVALID_API_KEY_WITH_INSTRUCTIONS;
import static be.stealingdapenta.coreai.util.ChatMessages.INVALID_DIMENSIONS;
import static be.stealingdapenta.coreai.util.ChatMessages.NO_PERMISSION;
import static be.stealingdapenta.coreai.util.ChatMessages.PLAYERS_ONLY;
import static be.stealingdapenta.coreai.util.ChatMessages.BUSY;
import static be.stealingdapenta.coreai.util.ChatMessages.imageMapGeneratedFromAI;
import static be.stealingdapenta.coreai.util.ChatMessages.usageImageMapCommand;

import be.stealingdapenta.coreai.CoreAI;
import be.stealingdapenta.coreai.manager.SessionManager;
import be.stealingdapenta.coreai.map.ImageGrid;
import be.stealingdapenta.coreai.service.SafeImageDownloader;
import be.stealingdapenta.coreai.util.ChatMessages;
import be.stealingdapenta.coreai.service.ChatAgent;
import be.stealingdapenta.coreai.service.OpenAIApi;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class ImageGenMapCommand implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            ChatMessages.send(sender, PLAYERS_ONLY);
            return true;
        }

        if (!player.hasPermission(IMAGE_GEN_MAP.node())) {
            ChatMessages.send(player, NO_PERMISSION);
            return true;
        }

        if (args.length < 1) {
            ChatMessages.send(player, usageImageMapCommand(label, "<prompt>"));
            return true;
        }

        ImageGrid grid = new ImageGrid(1, 1);
        String prompt;
        if (args.length >= 2 && args[0].matches("(?i)\\d+x\\d+")) {
            try {
                grid = ImageGrid.parse(args[0]);
            } catch (IllegalArgumentException exception) {
                ChatMessages.send(player, INVALID_DIMENSIONS);
                return true;
            }
            prompt = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        } else {
            if (args.length >= 2 && args[0].matches("(?i)\\d.*x.*")) {
                ChatMessages.send(player, INVALID_DIMENSIONS);
                return true;
            }
            prompt = String.join(" ", args);
        }

        ChatAgent agent = SessionManager.SESSION_MANAGER.getAgent(player.getUniqueId());
        String key = agent.getApiKey();
        if (key == null || key.isBlank()) {
            ChatMessages.send(player, INVALID_API_KEY_WITH_INSTRUCTIONS);
            return true;
        }

        final int finalWidth = grid.columns();
        final int finalHeight = grid.rows();
        final int amount = grid.tileCount();
        final int timeoutMs = CoreAI.getInstance().getConfigSnapshot().imageTimeoutMs();
        final UUID playerId = player.getUniqueId();
        ChatMessages.send(player, GENERATING_AI_IMAGE);

        boolean accepted = CoreAI.getInstance().submitAsync(() -> {
                  if (!SessionManager.SESSION_MANAGER.isCurrentAgent(playerId, agent)) {
                      return;
                  }
                  try {
                      long startTime = System.currentTimeMillis();
                      byte[] imageBytes = OpenAIApi.OPEN_AI_API.generateImage(prompt, key, timeoutMs);
                      BufferedImage img = SafeImageDownloader.decode(imageBytes);
                      BufferedImage scaled = MAP_IMAGE_SERVICE.resizeToGrid(img, finalWidth, finalHeight);
                      BufferedImage[][] tiles = MAP_IMAGE_SERVICE.splitIntoTiles(scaled, finalWidth, finalHeight);

                      CoreAI.getInstance().runMain(() -> {
                          if (!player.isOnline() || Bukkit.getPlayer(playerId) != player
                                  || !SessionManager.SESSION_MANAGER.isCurrentAgent(playerId, agent)) {
                              return;
                          }
                          long duration = System.currentTimeMillis() - startTime;
                          MAP_IMAGE_SERVICE.createMapItems(player, finalWidth, finalHeight, tiles)
                                  .whenComplete((batch, storageError) -> CoreAI.getInstance().runMain(() -> {
                                      if (storageError != null) {
                                          if (!player.isOnline() || Bukkit.getPlayer(playerId) != player
                                                  || !SessionManager.SESSION_MANAGER.isCurrentAgent(playerId, agent)) {
                                              return;
                                          }
                                          CORE_AI_LOGGER.log(Level.WARNING,
                                                  "Could not persist AI-generated map tiles.", storageError);
                                          ChatMessages.send(player, ChatMessages.mapCreationFailure("The map data could not be saved."));
                                          return;
                                      }
                                      if (!MAP_IMAGE_SERVICE.deliverIfCurrent(batch,
                                              () -> player.isOnline() && Bukkit.getPlayer(playerId) == player
                                                      && SessionManager.SESSION_MANAGER.isCurrentAgent(playerId, agent),
                                              items -> MAP_IMAGE_SERVICE.giveMapItems(player, items))) {
                                          return;
                                      }
                                      ChatMessages.send(player, imageMapGeneratedFromAI(amount, duration));
                                  }));
                      });

                  } catch (Exception e) {
                      CORE_AI_LOGGER.warning("Failed to generate AI image: " + e.getMessage());
                      CoreAI.getInstance().runMain(() -> {
                          if (player.isOnline() && Bukkit.getPlayer(playerId) == player
                                  && SessionManager.SESSION_MANAGER.isCurrentAgent(playerId, agent)) {
                              ChatMessages.send(player, IMAGE_GENERATION_ERROR);
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
