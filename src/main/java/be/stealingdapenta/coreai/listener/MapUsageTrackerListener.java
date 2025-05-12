package be.stealingdapenta.coreai.listener;

import static be.stealingdapenta.coreai.map.LastSeenTracker.LAST_SEEN_TRACKER;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;

public class MapUsageTrackerListener implements Listener {

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();

        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof ItemFrame frame) {
                trackMapFromItem(frame.getItem());
            } else if (entity instanceof Item item) {
                trackMapFromItem(item.getItemStack());
            }
        }
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        for (Entity entity : event.getWorld()
                                  .getEntities()) {
            if (entity instanceof ItemFrame frame) {
                trackMapFromItem(frame.getItem());
            } else if (entity instanceof Item item) {
                trackMapFromItem(item.getItemStack());
            }
        }
    }

    private void trackMapFromItem(ItemStack item) {
        if (item.getType() != Material.FILLED_MAP) {
            return;
        }
        if (!(item.getItemMeta() instanceof MapMeta meta)) {
            return;
        }
        MapView view = meta.getMapView();
        if (view != null) {
            LAST_SEEN_TRACKER.markSeen(view.getId());
        }
    }
}
