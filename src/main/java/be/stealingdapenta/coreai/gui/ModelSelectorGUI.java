package be.stealingdapenta.coreai.gui;

import static be.stealingdapenta.coreai.manager.SessionManager.SESSION_MANAGER;
import static be.stealingdapenta.coreai.util.ChatMessages.modelStored;
import static net.kyori.adventure.text.format.NamedTextColor.AQUA;
import static net.kyori.adventure.text.format.NamedTextColor.GREEN;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
 * Inventory GUI listener for model selection with pagination and alphabetical sorting.
 */
public class ModelSelectorGUI implements Listener {

    private static final String INVENTORY_TITLE = "          Select AI Model";
    private static final Component TITLE_COMPONENT = Component.text(INVENTORY_TITLE, AQUA);
    private static final int SLOTS_PER_ROW = 9;
    private static final int MAX_SLOTS = 54;
    private static final Component NEXT_LABEL = Component.text("Next Page", AQUA);
    private static final Component PREV_LABEL = Component.text("Previous Page", AQUA);

    private final Map<UUID, List<String>> playerModels = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerPage = new ConcurrentHashMap<>();

    /**
     * Opens the model-selection inventory for a player at page 0.
     */
    public void openModelGui(@NotNull Player player, @NotNull List<String> models) {
        // Sort models alphabetically (case-insensitive)
        List<String> sorted = new ArrayList<>(models);
        sorted.sort(String::compareToIgnoreCase);
        openModelGui(player, sorted, 0);
    }

    /**
     * Opens the model-selection inventory for a player at a given page.
     */
    private void openModelGui(@NotNull Player player, @NotNull List<String> sortedModels, int page) {
        int totalModels = sortedModels.size();
        int maxRows = MAX_SLOTS / SLOTS_PER_ROW;
        int maxModelsPerPage = maxRows * SLOTS_PER_ROW;
        int totalPages = (totalModels + maxModelsPerPage - 1) / maxModelsPerPage;
        int currentPage = Math.min(Math.max(page, 0), totalPages - 1);

        playerModels.put(player.getUniqueId(), sortedModels);
        playerPage.put(player.getUniqueId(), currentPage);

        int startIndex = currentPage * maxModelsPerPage;
        int endIndex = Math.min(startIndex + maxModelsPerPage, totalModels);
        int displayCount = endIndex - startIndex;

        int rowsNeeded = (displayCount + SLOTS_PER_ROW - 1) / SLOTS_PER_ROW;
        int rows = Math.min(rowsNeeded, maxRows);
        int size = rows * SLOTS_PER_ROW;

        Inventory inv = Bukkit.createInventory(null, size, TITLE_COMPONENT);

        // Populate model items
        for (int i = 0; i < displayCount; i++) {
            String modelId = sortedModels.get(startIndex + i);
            ItemStack item = new ItemStack(Material.PAPER);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(modelId, GREEN));
            item.setItemMeta(meta);
            inv.setItem(i, item);
        }

        // Add navigation arrows
        if (currentPage < totalPages - 1) {
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta m = next.getItemMeta();
            m.displayName(NEXT_LABEL);
            next.setItemMeta(m);
            inv.setItem(size - 1, next);
        }
        if (currentPage > 0) {
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta m = prev.getItemMeta();
            m.displayName(PREV_LABEL);
            prev.setItemMeta(m);
            inv.setItem(size - SLOTS_PER_ROW, prev);
        }

        player.openInventory(inv);
    }

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
        String chosenModel = PlainTextComponentSerializer.plainText()
                                                         .serialize(nameComp);

        UUID uuid = player.getUniqueId();
        List<String> models = playerModels.get(uuid);
        int page = playerPage.getOrDefault(uuid, 0);

        if (NEXT_LABEL.equals(nameComp)) {
            openModelGui(player, models, page + 1);
            return;
        }
        if (PREV_LABEL.equals(nameComp)) {
            openModelGui(player, models, page - 1);
            return;
        }

        // Model selection
        SESSION_MANAGER.setPlayerModel(uuid, chosenModel);

        player.closeInventory();
        player.sendMessage(modelStored(chosenModel));
    }
}
