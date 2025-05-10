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
     * Resizes an input image to the Minecraft map resolution (128x128).
     *
     * @param image the original image
     * @return a resized version for map rendering
     */
    public static BufferedImage resizeToMap(BufferedImage image) {
        Image scaled = image.getScaledInstance(MAP_WIDTH, MAP_HEIGHT, Image.SCALE_SMOOTH);
        BufferedImage resized = new BufferedImage(MAP_WIDTH, MAP_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.drawImage(scaled, 0, 0, null);
        g.dispose();
        return resized;
    }

    /**
     * Creates a MapRenderer that draws the provided image once.
     *
     * @param image the image to render
     * @return a one-time MapRenderer
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
