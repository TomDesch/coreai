package be.stealingdapenta.coreai.listener;

import static be.stealingdapenta.coreai.map.LastSeenTracker.LAST_SEEN_TRACKER;
import static be.stealingdapenta.coreai.map.MapStorage.MAP_STORAGE;

import be.stealingdapenta.coreai.CoreAI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.scheduler.BukkitTask;

public final class MapUsageTrackerListener implements Listener {

    private static final int WORK_ITEMS_PER_TICK = 8;
    private static final long OBSERVATION_PERIOD_TICKS = 1_200L;

    private final CoreAI plugin;
    private final AtomicBoolean scanning = new AtomicBoolean();
    private BukkitTask periodicTask;
    private BukkitTask batchTask;
    private CompletableFuture<Void> initialScan;

    public MapUsageTrackerListener(CoreAI plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<Void> start() {
        initialScan = startScan();
        periodicTask = Bukkit.getScheduler().runTaskTimer(plugin, this::startScan,
                OBSERVATION_PERIOD_TICKS, OBSERVATION_PERIOD_TICKS);
        return initialScan;
    }

    public CompletableFuture<Void> refreshLoadedUsage() {
        return startScan();
    }

    public void stop() {
        if (periodicTask != null) {
            periodicTask.cancel();
        }
        if (batchTask != null) {
            batchTask.cancel();
        }
        scanning.set(false);
    }

    private CompletableFuture<Void> startScan() {
        if (!Bukkit.isPrimaryThread()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Map usage scans require the server thread."));
        }
        if (!scanning.compareAndSet(false, true)) {
            return initialScan == null ? CompletableFuture.completedFuture(null) : initialScan;
        }
        initialScan = new CompletableFuture<>();
        List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        List<Chunk[]> worldChunks = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            worldChunks.add(world.getLoadedChunks());
        }
        ScanState state = new ScanState(onlinePlayers, worldChunks);
        batchTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> processBatch(state), 1L, 1L);
        return initialScan;
    }

    private void processBatch(ScanState state) {
        int processed = 0;
        while (processed < WORK_ITEMS_PER_TICK && state.hasPlayers()) {
            scanPlayer(state.nextPlayer());
            processed++;
        }
        while (processed < WORK_ITEMS_PER_TICK && state.hasChunks()) {
            scanChunk(state.nextChunk());
            processed++;
        }
        if (!state.hasPlayers() && !state.hasChunks()) {
            batchTask.cancel();
            batchTask = null;
            scanning.set(false);
            LAST_SEEN_TRACKER.saveAsync();
            if (initialScan != null && !initialScan.isDone()) {
                initialScan.complete(null);
            }
        }
    }

    private void scanPlayer(Player player) {
        scanInventory(player.getInventory());
        scanInventory(player.getEnderChest());
    }

    private void scanChunk(Chunk chunk) {
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof ItemFrame frame) {
                trackMapFromItem(frame.getItem());
            } else if (entity instanceof Item item) {
                trackMapFromItem(item.getItemStack());
            }
        }
        for (BlockState blockState : chunk.getTileEntities()) {
            if (blockState instanceof InventoryHolder holder) {
                scanInventory(holder.getInventory());
            }
        }
    }

    private void scanInventory(Inventory inventory) {
        for (ItemStack item : inventory.getContents()) {
            trackMapFromItem(item);
        }
    }

    private void trackMapFromItem(ItemStack item) {
        if (item == null || item.getType() != Material.FILLED_MAP) {
            return;
        }
        if (!(item.getItemMeta() instanceof MapMeta meta)) {
            return;
        }
        MapView view = meta.getMapView();
        if (view != null) {
            MAP_STORAGE.restoreRenderer(view);
            LAST_SEEN_TRACKER.markSeen(view.getId());
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        scanChunk(event.getChunk());
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        startScan();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        scanPlayer(event.getPlayer());
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        scanInventory(event.getInventory());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        scanInventory(event.getView().getTopInventory());
        if (event.getWhoClicked() instanceof Player player) {
            scanPlayer(player);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        scanInventory(event.getView().getTopInventory());
        if (event.getWhoClicked() instanceof Player player) {
            scanPlayer(player);
        }
    }

    private static final class ScanState {

        private final List<Player> players;
        private final List<Chunk[]> worldChunks;
        private int playerIndex;
        private int worldIndex;
        private int chunkIndex;

        private ScanState(List<Player> players, List<Chunk[]> worldChunks) {
            this.players = players;
            this.worldChunks = worldChunks;
        }

        private boolean hasPlayers() {
            return playerIndex < players.size();
        }

        private Player nextPlayer() {
            return players.get(playerIndex++);
        }

        private boolean hasChunks() {
            while (worldIndex < worldChunks.size()
                    && chunkIndex >= worldChunks.get(worldIndex).length) {
                worldIndex++;
                chunkIndex = 0;
            }
            return worldIndex < worldChunks.size();
        }

        private Chunk nextChunk() {
            return worldChunks.get(worldIndex)[chunkIndex++];
        }
    }
}
