package be.stealingdapenta.coreai.gui;

import static be.stealingdapenta.coreai.manager.SessionManager.SESSION_MANAGER;
import static be.stealingdapenta.coreai.util.ChatMessages.MODEL_SAVE_FAILED;
import static be.stealingdapenta.coreai.util.ChatMessages.NO_MODELS_AVAILABLE;
import static be.stealingdapenta.coreai.util.ChatMessages.modelStored;
import static be.stealingdapenta.coreai.util.ChatMessages.send;

import be.stealingdapenta.coreai.CoreAI;
import be.stealingdapenta.coreai.service.ChatAgent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

/**
 * Identity-bound model selection menu with guarded pagination and asynchronous persistence.
 */
public final class ModelSelectorGUI implements Listener {

    private static final String INVENTORY_TITLE = "Select AI Model";
    private static final int INVENTORY_SIZE = 54;
    private static final int PREVIOUS_SLOT = 45;
    private static final int NEXT_SLOT = 53;
    private static final String NEXT_LABEL = "Next Page";
    private static final String PREVIOUS_LABEL = "Previous Page";

    private final CoreAI plugin;
    private final Map<UUID, GuiSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> activeRequests = new ConcurrentHashMap<>();

    public ModelSelectorGUI(CoreAI plugin) {
        this.plugin = plugin;
    }

    public UUID beginRequest(Player player) {
        UUID playerId = player.getUniqueId();
        GuiSession previous = sessions.remove(playerId);
        if (previous != null && player.getOpenInventory().getTopInventory() == previous.inventory()) {
            player.closeInventory();
        }
        UUID requestId = UUID.randomUUID();
        activeRequests.put(playerId, requestId);
        return requestId;
    }

    public boolean isCurrentRequest(UUID playerId, UUID requestId) {
        return requestId.equals(activeRequests.get(playerId));
    }

    public void cancelRequest(UUID playerId, UUID requestId) {
        activeRequests.remove(playerId, requestId);
    }

    public void openModelGui(
            @NotNull Player player,
            @NotNull List<String> models,
            @NotNull ChatAgent agent,
            @NotNull UUID requestId) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Model menus must be opened from the server thread.");
        }
        if (!isCurrentRequest(player.getUniqueId(), requestId)
                || !SESSION_MANAGER.isCurrentAgent(player.getUniqueId(), agent)) {
            return;
        }
        if (models.isEmpty()) {
            cancelRequest(player.getUniqueId(), requestId);
            send(player, NO_MODELS_AVAILABLE);
            return;
        }
        List<String> sortedModels = new ArrayList<>(models);
        sortedModels.sort(String::compareToIgnoreCase);
        openModelGui(player, List.copyOf(sortedModels), agent, requestId, 0);
    }

    private void openModelGui(
            Player player,
            List<String> models,
            ChatAgent agent,
            UUID sessionId,
            int requestedPage) {
        int totalPages = ModelPage.count(models.size());
        int page = Math.max(0, Math.min(requestedPage, totalPages - 1));
        ModelMenuHolder holder = new ModelMenuHolder(player.getUniqueId(), sessionId);
        Inventory inventory = Bukkit.createInventory(holder, INVENTORY_SIZE, INVENTORY_TITLE);
        holder.setInventory(inventory);

        int startIndex = page * ModelPage.MODELS_PER_PAGE;
        int endIndex = Math.min(startIndex + ModelPage.MODELS_PER_PAGE, models.size());
        for (int modelIndex = startIndex; modelIndex < endIndex; modelIndex++) {
            inventory.setItem(modelIndex - startIndex, namedItem(Material.PAPER, models.get(modelIndex)));
        }
        if (page > 0) {
            inventory.setItem(PREVIOUS_SLOT, namedItem(Material.ARROW, PREVIOUS_LABEL));
        }
        if (page < totalPages - 1) {
            inventory.setItem(NEXT_SLOT, namedItem(Material.ARROW, NEXT_LABEL));
        }

        sessions.put(player.getUniqueId(), new GuiSession(sessionId, models, agent, page, inventory));
        player.openInventory(inventory);
    }

    private ItemStack namedItem(Material material, String displayName) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName);
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || !(event.getView().getTopInventory().getHolder() instanceof ModelMenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        GuiSession session = currentSession(player, holder, event.getView().getTopInventory());
        if (session == null) {
            return;
        }

        int rawSlot = event.getRawSlot();
        if (rawSlot == PREVIOUS_SLOT && session.page > 0) {
            schedulePageChange(player, session, session.page - 1);
            return;
        }
        if (rawSlot == NEXT_SLOT && session.page < ModelPage.count(session.models.size()) - 1) {
            schedulePageChange(player, session, session.page + 1);
            return;
        }
        int modelIndex = ModelPage.modelIndex(session.page, rawSlot, session.models.size());
        if (modelIndex >= 0) {
            selectModel(player, session, session.models.get(modelIndex));
        }
    }

    @EventHandler
    public void onInventoryDrag(@NotNull InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ModelMenuHolder)) {
            return;
        }
        if (event.getRawSlots().stream().anyMatch(rawSlot -> rawSlot < event.getView().getTopInventory().getSize())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(@NotNull InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof ModelMenuHolder holder)) {
            return;
        }
        GuiSession session = sessions.get(holder.playerId());
        if (session != null && session.sessionId().equals(holder.sessionId())
                && session.inventory() == event.getInventory()) {
            sessions.remove(holder.playerId());
            activeRequests.remove(holder.playerId(), holder.sessionId());
        }
    }

    @EventHandler
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        sessions.remove(playerId);
        activeRequests.remove(playerId);
    }

    public void clearSessions() {
        sessions.clear();
        activeRequests.clear();
    }

    private GuiSession currentSession(Player player, ModelMenuHolder holder, Inventory inventory) {
        GuiSession session = sessions.get(player.getUniqueId());
        if (session == null || !holder.playerId().equals(player.getUniqueId())
                || !session.sessionId().equals(holder.sessionId()) || session.inventory() != inventory
        || !isCurrentRequest(player.getUniqueId(), session.sessionId())
        || !SESSION_MANAGER.isCurrentAgent(player.getUniqueId(), session.agent())) {
            return null;
        }
        return session;
    }

    private void schedulePageChange(Player player, GuiSession session, int newPage) {
        plugin.runMain(() -> {
            UUID playerId = player.getUniqueId();
            GuiSession current = sessions.get(playerId);
            if (Bukkit.getPlayer(playerId) == player && current == session
                    && isCurrentRequest(playerId, session.sessionId())
                    && SESSION_MANAGER.isCurrentAgent(playerId, session.agent())) {
                openModelGui(player, session.models(), session.agent(), session.sessionId(), newPage);
            }
        });
    }

    private void selectModel(Player player, GuiSession session, String model) {
        UUID playerId = player.getUniqueId();
        if (sessions.get(playerId) != session) {
            return;
        }
        sessions.remove(playerId);
        activeRequests.remove(playerId, session.sessionId());
        plugin.runMain(() -> {
            if (Bukkit.getPlayer(playerId) == player
                    && player.getOpenInventory().getTopInventory() == session.inventory()) {
                player.closeInventory();
            }
        });
        SESSION_MANAGER.setPlayerModel(playerId, model).whenComplete((ignored, error) ->
                plugin.runMain(() -> {
                    if (Bukkit.getPlayer(playerId) != player
                            || !SESSION_MANAGER.isCurrentAgent(playerId, session.agent())) {
                        return;
                    }
                    send(player, error == null ? modelStored(model) : MODEL_SAVE_FAILED);
                }));
    }

    private record GuiSession(
            UUID sessionId,
            List<String> models,
            ChatAgent agent,
            int page,
            Inventory inventory) {
    }

    private static final class ModelMenuHolder implements InventoryHolder {

        private final UUID playerId;
        private final UUID sessionId;
        private Inventory inventory;

        private ModelMenuHolder(UUID playerId, UUID sessionId) {
            this.playerId = playerId;
            this.sessionId = sessionId;
        }

        private UUID playerId() {
            return playerId;
        }

        private UUID sessionId() {
            return sessionId;
        }

        private void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
