package be.stealingdapenta.coreai.map;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapPalette;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.jetbrains.annotations.NotNull;

public final class MapImageService {

    private static final int MAP_WIDTH = 128;
    private static final int MAP_HEIGHT = 128;

    private MapImageService() {
    }

    /**
     * Resizes an image to exactly 128x128 pixels.
     *
     * @param image The input image.
     * @return A new resized BufferedImage.
     */
    public static BufferedImage resizeToMap(BufferedImage image) {
        return resizeToGrid(image, 1, 1);
    }

    /**
     * Resizes an image to fit a specific grid of maps (columns × rows).
     *
     * @param image The input image.
     * @param cols  Number of horizontal maps.
     * @param rows  Number of vertical maps.
     * @return A new resized BufferedImage.
     */
    public static BufferedImage resizeToGrid(BufferedImage image, int cols, int rows) {
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
     *
     * @param largeImage The image sized to the full grid.
     * @param columns    Number of columns.
     * @param rows       Number of rows.
     * @return A 2D array of BufferedImages [row][column].
     */
    public static BufferedImage[][] splitIntoTiles(BufferedImage largeImage, int columns, int rows) {
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
     *
     * @param image The image to render.
     * @return A custom MapRenderer.
     */
    public static MapRenderer rendererFrom(BufferedImage image) {
        return new MapRenderer() {
            private boolean rendered = false;

            @Override
            public void render(@NotNull MapView map, @NotNull MapCanvas canvas, @NotNull Player player) {
                if (rendered) {
                    return;
                }
                BufferedImage mapImage = MapPalette.resizeImage(image);
                canvas.drawImage(0, 0, mapImage);
                rendered = true;
            }
        };
    }
}
