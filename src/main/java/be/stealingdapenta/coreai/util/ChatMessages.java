package be.stealingdapenta.coreai.util;

import static net.kyori.adventure.text.format.NamedTextColor.AQUA;
import static net.kyori.adventure.text.format.NamedTextColor.DARK_AQUA;
import static net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.GOLD;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.GREEN;
import static net.kyori.adventure.text.format.NamedTextColor.RED;

import be.stealingdapenta.coreai.service.OpenAiException;
import java.io.IOException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class ChatMessages {

    public static final Component INVALID_API_KEY_WITH_INSTRUCTIONS = TextBuilder.TEXT_BUILDER.reset()
                                                                                              .coreAIPrefix()
                                                                                              .append("Error: Your API key is invalid.", RED)
                                                                                              .append(" Use ", GRAY)
                                                                                              .append("/setapikey", GOLD)
                                                                                              .append(" to set a new one.", GRAY)
                                                                                              .build();
    public static final Component MODEL_NOT_FOUND = TextBuilder.TEXT_BUILDER.reset()
                                                                            .coreAIPrefix()
                                                                            .append("Error: That model doesn’t exist. Select one with /models.", RED)
                                                                            .build();
    public static final Component MODEL_IS_THINKING = TextBuilder.TEXT_BUILDER.reset()
                                                                              .coreAIPrefix()
                                                                              .append("Thinking...", DARK_GRAY)
                                                                              .build();
    public static final Component NO_PERMISSION = TextBuilder.TEXT_BUILDER.reset()
                                                                          .coreAIPrefix()
                                                                          .append("Error: You don't have permission to do that.", RED)
                                                                          .build();
    public static final Component PLAYERS_ONLY = TextBuilder.TEXT_BUILDER.reset()
                                                                         .coreAIPrefix()
                                                                         .append("Error: This command can only be used by players.", RED)
                                                                         .build();
    public static final Component BUSY = TextBuilder.TEXT_BUILDER.reset()
                                                                 .coreAIPrefix()
                                                                 .append("The service is busy. Please try again shortly.", RED)
                                                                 .build();
    public static final Component API_KEY_STORED = TextBuilder.TEXT_BUILDER.reset()
                                                                           .coreAIPrefix()
                                                                           .append("Your API key has been securely stored!", AQUA)
                                                                           .build();
    public static final Component API_KEY_SAVE_FAILED = TextBuilder.TEXT_BUILDER.reset()
                                                                                .coreAIPrefix()
                                                                                .append("The API key is active for this session but could not be saved. Ask an administrator to check plugin storage.", RED)
                                                                                .build();
    public static final Component API_KEY_ENTRY_CANCELLED = TextBuilder.TEXT_BUILDER.reset()
                                                                                    .coreAIPrefix()
                                                                                    .append("API key entry cancelled.", AQUA)
                                                                                    .build();
    public static final Component API_KEY_ENTRY_PROMPT = TextBuilder.TEXT_BUILDER.reset()
                                                                                 .coreAIPrefix()
                                                                                 .append("Please enter your OpenAI API key in chat:", AQUA)
                                                                                 .append("\n(You can find it at https://platform.openai.com/account/api-keys)", GRAY)
                                                                                 .append("\nType cancel to stop. Chat logging plugins may still observe cancelled chat events.", GRAY)
                                                                                 .append("\nThe key is encrypted before storage.", GRAY)
                                                                                 .build();
    public static final Component API_KEY_ENTRY_EXPIRED = TextBuilder.TEXT_BUILDER.reset()
                                                                                 .coreAIPrefix()
                                                                                 .append("API key entry expired. Run /setapikey to try again.", RED)
                                                                                 .build();
    public static final Component API_KEY_TOO_LONG = TextBuilder.TEXT_BUILDER.reset()
                                                                            .coreAIPrefix()
                                                                            .append("API key input exceeds the maximum allowed length.", RED)
                                                                            .build();
    public static final Component MODEL_SAVE_FAILED = TextBuilder.TEXT_BUILDER.reset()
                                                                               .coreAIPrefix()
                                                                               .append("The model is active for this session but could not be saved.", RED)
                                                                               .build();
    public static final Component NO_MODELS_AVAILABLE = TextBuilder.TEXT_BUILDER.reset()
                                                                                 .coreAIPrefix()
                                                                                 .append("No models are available to select.", RED)
                                                                                 .build();
    public static final Component FETCHING_MODELS = TextBuilder.TEXT_BUILDER.reset()
                                                                            .coreAIPrefix()
                                                                            .append("Fetching available models...", AQUA)
                                                                            .build();

    public static final Component INVALID_SIZE_NUMBER = TextBuilder.TEXT_BUILDER.reset()
                                                                                .coreAIPrefix()
                                                                                .append("Invalid numbers in size. Use integers like 2x2.", RED)
                                                                                .build();

    public static final Component INVALID_DIMENSIONS = TextBuilder.TEXT_BUILDER.reset()
                                                                               .coreAIPrefix()
                                                                               .append("Invalid dimensions. Use format like 2x2.", RED)
                                                                               .build();

    public static final Component DOWNLOADING_IMAGE = TextBuilder.TEXT_BUILDER.reset()
                                                                              .coreAIPrefix()
                                                                              .append("Downloading and processing image...", AQUA)
                                                                              .build();

    public static final Component GENERATING_AI_IMAGE = TextBuilder.TEXT_BUILDER.reset()
                                                                                .coreAIPrefix()
                                                                                .append("Generating image with AI...", AQUA)
                                                                                .build();

    public static final Component IMAGE_GENERATION_ERROR = TextBuilder.TEXT_BUILDER.reset()
                                                                                   .coreAIPrefix()
                                                                                   .append("Error generating image. Please check your server logs to find out why.", RED)
                                                                                   .build();




    private ChatMessages() {
        // Utility class
    }

    public static String plain(Component component) {
        return PlainTextComponentSerializer.plainText()
                                          .serialize(component);
    }

    public static void send(CommandSender sender, Component component) {
        sender.sendMessage(LegacyComponentSerializer.legacySection()
                                                      .serialize(component));
    }

    /**
     * Builds a dynamic error message for unexpected OpenAI exceptions.
     *
     * @param oae The OpenAI exception
     * @return A formatted Component containing the exception message
     */
    public static Component openAiError(OpenAiException oae) {
        return TextBuilder.TEXT_BUILDER.reset()
                                       .coreAIPrefix()
                                       .append("Error: ", RED)
                                       .append(oae.getMessage(), GRAY)
                                       .build();
    }

    /**
     * Builds a dynamic generic error message for other IOExceptions.
     *
     * @param ioe The exception to format
     * @return A formatted Component
     */
    public static Component ioError(IOException ioe) {
        return TextBuilder.TEXT_BUILDER.reset()
                                       .coreAIPrefix()
                                       .append("Error: ", RED)
                                       .append(ioe.getMessage(), GRAY)
                                       .build();
    }

    /**
     * @param response The response from the AI model
     * @return A formatted Component for the AI response
     */
    public static Component chatResponse(String response) {
        return TextBuilder.TEXT_BUILDER.reset()
                                       .coreAIPrefix()
                                       .append(response, DARK_AQUA)
                                       .build();
    }

    /**
     * @param player The player who sent the prompt
     * @param prompt The prompt sent by the player
     * @return A formatted Component with the chat prompt
     */
    public static Component chatPrompt(Player player, String prompt) {
        return TextBuilder.TEXT_BUILDER.reset()
                                       .append("[%s] " .formatted(player.getName()), GRAY)
                                       .append(prompt, GRAY)
                                       .build();
    }

    /**
     * @param model the model name
     * @return A formatted Component indicating the model was stored
     */
    public static Component modelStored(String model) {
        return TextBuilder.TEXT_BUILDER.reset()
                                       .coreAIPrefix()
                                       .append("Model stored successfully: ", AQUA)
                                       .append(model, DARK_AQUA)
                                       .build();
    }

    /**
     * @param model the model name
     * @return A formatted Component for the model info
     */
    public static Component modelInfo(String model) {
        return TextBuilder.TEXT_BUILDER.reset()
                                       .coreAIPrefix()
                                       .append("Model info: ", AQUA)
                                       .append(model, DARK_AQUA)
                                       .build();
    }

    /**
     * @param model the model name
     * @return A formatted Component indicating the model is being fetched
     */
    public static Component fetchingModelInfo(String model) {
        return TextBuilder.TEXT_BUILDER.reset()
                                       .coreAIPrefix()
                                       .append("Fetching info for model: ", AQUA)
                                       .append(model, DARK_AQUA)
                                       .build();
    }

    public static Component imageMapCreated(int count) {
        return TextBuilder.TEXT_BUILDER.reset()
                                       .coreAIPrefix()
                                       .append("Generated ", GREEN)
                                       .append(count + "", AQUA)
                                       .append(" map tile(s) and added to your inventory.", GREEN)
                                       .build();
    }

    public static Component imageMapGeneratedFromAI(int count, long durationMs) {
        String formattedDuration = String.format("%.2f", durationMs / 1000.0);
        return TextBuilder.TEXT_BUILDER.reset()
                                       .coreAIPrefix()
                                       .append("Generated AI image as ", GREEN)
                                       .append(String.valueOf(count), AQUA)
                                       .append(" connecting map tiles, in ", GREEN)
                                       .append(formattedDuration, AQUA)
                                       .append(" seconds.", GREEN)
                                       .build();
    }

    public static Component mapCreationFailure(String message) {
        return TextBuilder.TEXT_BUILDER.reset()
                                       .coreAIPrefix()
                                       .append("Failed to create image map: ", RED)
                                       .append(message, GRAY)
                                       .build();
    }

    public static Component usageImageMapCommand(String label, String followUp) {
        return TextBuilder.TEXT_BUILDER.reset()
                                       .coreAIPrefix()
                                       .append("Usage: /" + label + " [WxH] " + followUp, GRAY)
                                       .build();
    }

}
