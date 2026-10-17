package be.stealingdapenta.coreai.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.junit.jupiter.api.Test;

class MapUsageTrackerListenerTest {

    @Test
    void scansMapsNestedInShulkerBoxes() {
        ItemStack map = item(Material.FILLED_MAP, proxy(MapMeta.class, name -> null));
        Inventory inventory = proxy(Inventory.class,
                name -> name.equals("getContents") ? new ItemStack[] {map} : null);
        BlockState state = (BlockState) proxy(new Class<?>[] {BlockState.class, InventoryHolder.class},
                name -> name.equals("getInventory") ? inventory : null);
        ItemStack shulker = item(Material.SHULKER_BOX,
                proxy(BlockStateMeta.class, name -> name.equals("getBlockState") ? state : null));

        List<ItemStack> scanned = scan(shulker);

        assertTrue(scanned.contains(map));
        assertEquals(2, scanned.size());
    }

    @Test
    void scansMapsNestedInBundlesAndStopsAtRepeatedItems() {
        ItemStack map = item(Material.FILLED_MAP, proxy(MapMeta.class, name -> null));
        ItemStack[] bundleReference = new ItemStack[1];
        ItemStack bundle = item(Material.BUNDLE,
                proxy(BundleMeta.class, name -> name.equals("getItems")
                        ? List.of(bundleReference[0], map) : null));
        bundleReference[0] = bundle;

        List<ItemStack> scanned = scan(bundle);

        assertTrue(scanned.contains(map));
        assertEquals(2, scanned.size());
    }

    private static List<ItemStack> scan(ItemStack root) {
        List<ItemStack> scanned = new ArrayList<>();
        MapUsageTrackerListener.scanNestedItems(root, scanned::add);
        return scanned;
    }

    private static ItemStack item(Material material, ItemMeta meta) {
        return new StubItemStack(material, meta);
    }

    private static <T> T proxy(Class<T> type, InvocationResult result) {
        return type.cast(proxy(new Class<?>[] {type}, result));
    }

    private static Object proxy(Class<?>[] interfaces, InvocationResult result) {
        return Proxy.newProxyInstance(
                interfaces[0].getClassLoader(),
                interfaces,
                (proxy, method, args) -> result.invoke(method.getName()));
    }

    @FunctionalInterface
    private interface InvocationResult {
        Object invoke(String methodName);
    }

    private static final class StubItemStack extends ItemStack {

        private final ItemMeta meta;

        private StubItemStack(Material material, ItemMeta meta) {
            super(material);
            this.meta = meta;
        }

        @Override
        public ItemMeta getItemMeta() {
            return meta;
        }
    }
}
