package be.stealingdapenta.coreai.map;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.jetbrains.annotations.NotNull;

/**
 * Singleton service for creating Minecraft map images from BufferedImages.
 */
public enum MapImageService {
    /**
     * The singleton instance.
     */
    MAP_IMAGE_SERVICE;

    private static final int MAP_WIDTH = 128;
    private static final int MAP_HEIGHT = 128;

    /**
     * Resizes an image to fit a specific grid of maps (columns × rows).
     */
    public BufferedImage resizeToGrid(BufferedImage image, int cols, int rows) {
        ImageGrid grid = new ImageGrid(cols, rows);
        int width = Math.multiplyExact(grid.columns(), MAP_WIDTH);
        int height = Math.multiplyExact(grid.rows(), MAP_HEIGHT);

        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        try {
            g.drawImage(image, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return resized;
    }

    /**
     * Splits an image into map-sized 128×128 tiles.
     */
    public BufferedImage[][] splitIntoTiles(BufferedImage largeImage, int columns, int rows) {
        ImageGrid grid = new ImageGrid(columns, rows);
        BufferedImage[][] tiles = new BufferedImage[grid.rows()][grid.columns()];
        for (int row = 0; row < grid.rows(); row++) {
            for (int col = 0; col < grid.columns(); col++) {
                tiles[row][col] = largeImage.getSubimage(col * MAP_WIDTH, row * MAP_HEIGHT, MAP_WIDTH, MAP_HEIGHT);
            }
        }
        return tiles;
    }

    /**
     * Creates a one-time MapRenderer for rendering a static image to a map.
     */
    public MapRenderer rendererFrom(BufferedImage image) {
        return new MapRenderer() {
            private boolean rendered = false;

            @Override
            public void render(@NotNull MapView map, @NotNull MapCanvas canvas, @NotNull Player player) {
                if (rendered) {
                    return;
                }
                rendered = true;

                canvas.drawImage(0, 0, image);
            }
        };
    }

    public CompletableFuture<List<ItemStack>> createMapItems(Player player, int width, int height, BufferedImage[][] tiles) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Map items must be allocated from the server thread.");
        }
        ImageGrid grid = new ImageGrid(width, height);
        if (tiles.length != grid.rows()) {
            throw new IllegalArgumentException("Tile rows do not match the requested image grid.");
        }
        List<ItemStack> items = new ArrayList<>(grid.tileCount());
        List<CompletableFuture<Void>> writes = new ArrayList<>(grid.tileCount());
        for (int row = 0; row < grid.rows(); row++) {
            if (tiles[row].length != grid.columns()) {
                throw new IllegalArgumentException("Tile columns do not match the requested image grid.");
            }
            for (int col = 0; col < grid.columns(); col++) {
                BufferedImage tile = tiles[row][col];
                MapView view = Bukkit.createMap(player.getWorld());
                view.getRenderers().clear();
                view.addRenderer(rendererFrom(tile));
                writes.add(MapStorage.MAP_STORAGE.saveNewMap(view, tile));
                items.add(createMapItem(view, row, col));
            }
        }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> List.copyOf(items));
    }

    public void giveMapItems(Player player, List<ItemStack> items) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Map items must be given from the server thread.");
        }
        for (ItemStack mapItem : items) {
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(mapItem);
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
    }

    private ItemStack createMapItem(MapView view, int row, int column) {
        ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) mapItem.getItemMeta();
        if (meta != null) {
            meta.setMapView(view);
            meta.setDisplayName("Map col: " + (column + 1) + ", row: " + (row + 1));
            mapItem.setItemMeta(meta);
        }
        return mapItem;
    }
}
