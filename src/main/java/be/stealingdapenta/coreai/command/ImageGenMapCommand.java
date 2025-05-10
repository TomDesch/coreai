package be.stealingdapenta.coreai.command;

import static be.stealingdapenta.coreai.CoreAI.CORE_AI_LOGGER;
import static be.stealingdapenta.coreai.map.MapImageService.addMapToInventory;
import static be.stealingdapenta.coreai.permission.PermissionNode.IMAGE_MAP;
import static be.stealingdapenta.coreai.util.ChatMessages.INVALID_API_KEY_WITH_INSTRUCTIONS;
import static be.stealingdapenta.coreai.util.ChatMessages.NO_PERMISSION;
import static be.stealingdapenta.coreai.util.ChatMessages.PLAYERS_ONLY;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.GREEN;
import static net.kyori.adventure.text.format.NamedTextColor.RED;
import static net.kyori.adventure.text.format.NamedTextColor.YELLOW;

import be.stealingdapenta.coreai.CoreAI;
import be.stealingdapenta.coreai.manager.SessionManager;
import be.stealingdapenta.coreai.map.MapImageService;
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
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PLAYERS_ONLY);
            return true;
        }

        if (!player.hasPermission(IMAGE_MAP.node())) {
            player.sendMessage(NO_PERMISSION);
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(text("Usage: /" + label + " [WxH] <prompt>", GRAY));
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
                player.sendMessage(text("Invalid dimensions. Use format like 2x2.", RED));
                return true;
            }
            prompt = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        } else {
            prompt = String.join(" ", args);
        }

        final int finalWidth = width;
        final int finalHeight = height;
        final int amount = finalWidth * finalHeight;

        player.sendMessage(text("[CoreAI] Generating image with AI...", YELLOW));

        Bukkit.getScheduler()
              .runTaskAsynchronously(CoreAI.getInstance(), () -> {
                  try {
                      String imageUrl = OpenAIApi.OPEN_AI_API.generateImage(prompt, finalWidth * 128, finalHeight * 128, key);
                      BufferedImage img = ImageIO.read(java.net.URI.create(imageUrl)
                                                                   .toURL());
                      BufferedImage scaled = MapImageService.resizeToGrid(img, finalWidth, finalHeight);
                      BufferedImage[][] tiles = MapImageService.splitIntoTiles(scaled, finalWidth, finalHeight);

                      Bukkit.getScheduler()
                            .runTask(CoreAI.getInstance(), () -> {
                                addMapToInventory(player, finalWidth, finalHeight, tiles);
                                player.sendMessage(text("[CoreAI] Generated AI image as " + amount + " connecting map tiles.", GREEN));
                            });

                  } catch (Exception e) {
                      CORE_AI_LOGGER.warning("Failed to generate AI image: " + e.getMessage());
                      CORE_AI_LOGGER.warning(Arrays.toString(e.getStackTrace()));
                      player.sendMessage(text("[CoreAI] Error generating image. Please check your server logs to find out why.", RED));
                  }
              });

        return true;
    }


}
