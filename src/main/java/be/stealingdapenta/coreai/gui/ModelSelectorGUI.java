package be.stealingdapenta.coreai.gui;

import static be.stealingdapenta.coreai.manager.SessionManager.SESSION_MANAGER;
import static net.kyori.adventure.text.format.NamedTextColor.AQUA;
import static net.kyori.adventure.text.format.NamedTextColor.GOLD;
import static net.kyori.adventure.text.format.NamedTextColor.GREEN;

import java.util.List;
import net.kyori.adventure.text.Component;
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
 * Inventory GUI listener for model selection, with size capped at 54 slots.
 */
public class ModelSelectorGUI implements Listener {

    private static final String INVENTORY_TITLE = "Select AI Model";
    private static final Component TITLE_COMPONENT = Component.text(INVENTORY_TITLE, AQUA);
    private static final int SLOTS_PER_ROW = 9;
    private static final int MAX_SLOTS = 54;

    /**
     * Opens the model-selection inventory for a player.
     * Caps size to maximum allowed slots (54).
     */
    public void openModelGui(@NotNull Player player, @NotNull List<String> models) {
        // Calculate the necessary rows, cap at 6 (54 slots)
        int rowsNeeded = (models.size() + SLOTS_PER_ROW - 1) / SLOTS_PER_ROW;
        int rows = Math.min(rowsNeeded, MAX_SLOTS / SLOTS_PER_ROW);
        int size = rows * SLOTS_PER_ROW;
        Inventory inv = Bukkit.createInventory(null, size, TITLE_COMPONENT);

        // Only display up to 'size' models
        int displayCount = Math.min(models.size(), size);
        for (int i = 0; i < displayCount; i++) {
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
                 .equals(TITLE_COMPONENT)) {
            return;
        }
        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) {
            return;
        }

        Component nameComp = clicked.getItemMeta()
                                    .displayName();
        if (nameComp == null) {
            return;
        }
        String selectedModel = PlainTextComponentSerializer.plainText()
                                                           .serialize(nameComp);

        // Persist the selection
        SESSION_MANAGER.setPlayerModel(player.getUniqueId(), selectedModel);

        player.closeInventory();
        player.sendMessage(Component.text("[CoreAI] Selected model stored: " + selectedModel, GOLD));
    }
}
