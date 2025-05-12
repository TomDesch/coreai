package be.stealingdapenta.coreai.map;

import static be.stealingdapenta.coreai.map.LastSeenTracker.LAST_SEEN_TRACKER;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapPalette;
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
        int width = cols * MAP_WIDTH;
        int height = rows * MAP_HEIGHT;

        Image scaled = image.getScaledInstance(width, height, Image.SCALE_SMOOTH);
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.drawImage(scaled, 0, 0, null);
        g.dispose();
        return resized;
    }

    /**
     * Splits an image into map-sized 128×128 tiles.
     */
    public BufferedImage[][] splitIntoTiles(BufferedImage largeImage, int columns, int rows) {
        BufferedImage[][] tiles = new BufferedImage[rows][columns];
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < columns; col++) {
                tiles[row][col] = largeImage.getSubimage(col * MAP_WIDTH, row * MAP_HEIGHT, MAP_WIDTH, MAP_HEIGHT);
            }
        }
        return tiles;
    }

    /**
     * Creates a one-time MapRenderer for rendering a static image to a map.
     */
    public MapRenderer rendererFrom(BufferedImage image, MapView mapView) {
        return new MapRenderer() {
            private boolean rendered = false;

            @Override
            public void render(@NotNull MapView map, @NotNull MapCanvas canvas, @NotNull Player player) {
                if (rendered) {
                    return;
                }
                rendered = true;

                BufferedImage finalImage = MapPalette.resizeImage(image);
                canvas.drawImage(0, 0, finalImage);
                MapStorage.MAP_STORAGE.saveNewMap(mapView, finalImage); // Save actual rendered image
            }
        };
    }

    /**
     * Creates a single filled map item from a tile image.
     */
    public ItemStack createMapItem(Player player, BufferedImage tile, int row, int col) {
        MapView mapView = Bukkit.createMap(player.getWorld());
        mapView.getRenderers()
               .clear();
        mapView.addRenderer(rendererFrom(tile, mapView));
        LAST_SEEN_TRACKER.markSeen(mapView.getId());

        ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) mapItem.getItemMeta();
        meta.setMapView(mapView);
        meta.displayName(text("Map col: " + (col + 1) + ", row: " + (row + 1), GRAY));
        mapItem.setItemMeta(meta);
        return mapItem;
    }

    /**
     * Creates and adds a full grid of maps to the player's inventory.
     */
    public void addMapToInventory(Player player, int width, int height, BufferedImage[][] tiles) {
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                ItemStack mapItem = createMapItem(player, tiles[row][col], row, col);
                Map<Integer, ItemStack> leftovers = player.getInventory()
                                                          .addItem(mapItem);
                for (ItemStack leftover : leftovers.values()) {
                    player.getWorld()
                          .dropItemNaturally(player.getLocation(), leftover);
                }
            }
        }
    }
}
