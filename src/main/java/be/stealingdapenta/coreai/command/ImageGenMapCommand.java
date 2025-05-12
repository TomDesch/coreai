package be.stealingdapenta.coreai.command;

import static be.stealingdapenta.coreai.CoreAI.CORE_AI_LOGGER;
import static be.stealingdapenta.coreai.map.MapImageService.MAP_IMAGE_SERVICE;
import static be.stealingdapenta.coreai.permission.PermissionNode.IMAGE_MAP;
import static be.stealingdapenta.coreai.util.ChatMessages.GENERATING_AI_IMAGE;
import static be.stealingdapenta.coreai.util.ChatMessages.IMAGE_GENERATION_ERROR;
import static be.stealingdapenta.coreai.util.ChatMessages.INVALID_API_KEY_WITH_INSTRUCTIONS;
import static be.stealingdapenta.coreai.util.ChatMessages.INVALID_DIMENSIONS;
import static be.stealingdapenta.coreai.util.ChatMessages.NO_PERMISSION;
import static be.stealingdapenta.coreai.util.ChatMessages.PLAYERS_ONLY;
import static be.stealingdapenta.coreai.util.ChatMessages.imageMapGeneratedFromAI;
import static be.stealingdapenta.coreai.util.ChatMessages.usageImageMapCommand;

import be.stealingdapenta.coreai.CoreAI;
import be.stealingdapenta.coreai.manager.SessionManager;
import be.stealingdapenta.coreai.service.ChatAgent;
import be.stealingdapenta.coreai.service.OpenAIApi;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import javax.imageio.ImageIO;
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
            sender.sendMessage(PLAYERS_ONLY);
            return true;
        }

        if (!player.hasPermission(IMAGE_MAP.node())) {
            player.sendMessage(NO_PERMISSION);
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(usageImageMapCommand(label, "<prompt>"));
            return true;
        }

        // Get the player's agent (includes key and stored model)
        ChatAgent agent = SessionManager.SESSION_MANAGER.getAgent(player.getUniqueId());
        String key = agent.getApiKey();
        if (key == null || key.isBlank()) {
            player.sendMessage(INVALID_API_KEY_WITH_INSTRUCTIONS);
            return true;
        }

        int width = 1;
        int height = 1;
        String prompt;

        if (args.length >= 2 && args[0].matches("\\d+x\\d+")) {
            String[] dims = args[0].split("x");
            try {
                width = Integer.parseInt(dims[0]);
                height = Integer.parseInt(dims[1]);
            } catch (NumberFormatException e) {
                player.sendMessage(INVALID_DIMENSIONS);
                return true;
            }
            prompt = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        } else {
            prompt = String.join(" ", args);
        }

        final int finalWidth = width;
        final int finalHeight = height;
        final int amount = finalWidth * finalHeight;

        player.sendMessage(GENERATING_AI_IMAGE);

        Bukkit.getScheduler()
              .runTaskAsynchronously(CoreAI.getInstance(), () -> {
                  try {
                      long startTime = System.currentTimeMillis();
                      String imageUrl = OpenAIApi.OPEN_AI_API.generateImage(prompt, finalWidth * 128, finalHeight * 128, key);
                      BufferedImage img = ImageIO.read(java.net.URI.create(imageUrl)
                                                                   .toURL());
                      BufferedImage scaled = MAP_IMAGE_SERVICE.resizeToGrid(img, finalWidth, finalHeight);
                      BufferedImage[][] tiles = MAP_IMAGE_SERVICE.splitIntoTiles(scaled, finalWidth, finalHeight);

                      Bukkit.getScheduler()
                            .runTask(CoreAI.getInstance(), () -> {
                                long endTime = System.currentTimeMillis();
                                long duration = endTime - startTime;
                                MAP_IMAGE_SERVICE.addMapToInventory(player, finalWidth, finalHeight, tiles);
                                player.sendMessage(imageMapGeneratedFromAI(amount, duration));
                            });

                  } catch (Exception e) {
                      CORE_AI_LOGGER.warning("Failed to generate AI image: " + e.getMessage());
                      CORE_AI_LOGGER.warning(Arrays.toString(e.getStackTrace()));
                      player.sendMessage(IMAGE_GENERATION_ERROR);
                  }
              });

        return true;
    }
}
