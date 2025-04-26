package be.stealingdapenta.coreai.gui;

import static be.stealingdapenta.coreai.config.Config.API_KEY;
import static be.stealingdapenta.coreai.config.Config.MODEL;
import static be.stealingdapenta.coreai.config.Config.TIMEOUT_MS;
import static net.kyori.adventure.text.format.NamedTextColor.GOLD;
import static net.kyori.adventure.text.format.NamedTextColor.GREEN;

import be.stealingdapenta.coreai.service.ChatAgent;
import be.stealingdapenta.coreai.service.ChatAgentFactory;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

/**
 * Inventory GUI listener for model selection.
 */
public class ModelSelectorGUI implements Listener {

    private static final Component TITLE = Component.text("Select AI Model", NamedTextColor.AQUA);
    private static final int SLOTS_PER_ROW = 9;

    /**
     * Opens the model-selection inventory for a player.
     */
    public void openModelGui(@NotNull Player player, @NotNull List<String> models) {
        int rows = (models.size() + SLOTS_PER_ROW - 1) / SLOTS_PER_ROW;
        int size = rows * SLOTS_PER_ROW;
        Inventory inv = Bukkit.createInventory(null, size, TITLE);

        for (int i = 0; i < models.size(); i++) {
            String modelId = models.get(i);
            ItemStack item = new ItemStack(Material.PAPER);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(modelId, GREEN));
            item.setItemMeta(meta);
            inv.setItem(i, item);
        }

        player.openInventory(inv);
    }

    /**
     * Handles clicks in the model selection inventory.
     */
    @EventHandler
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        InventoryView view = event.getView();
        if (!view.title()
                 .equals(TITLE)) {
            return;
        }
        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) {
            return;
        }

        // Extract plain text from the Component display name
        Component nameComp = clicked.getItemMeta()
                                    .displayName();
        String selectedModel = PlainTextComponentSerializer.plainText()
                                                           .serialize(nameComp);

        // Retrieve or create the agent with server defaults
        ChatAgent agent = ChatAgentFactory.getAgent(player.getUniqueId(), API_KEY.get(), MODEL.get(), TIMEOUT_MS.get());
        // Apply the player's selection
        agent.setModel(selectedModel);

        player.closeInventory();
        player.sendMessage(Component.text("[CoreAI] Selected model: " + selectedModel, GOLD));
    }
}
