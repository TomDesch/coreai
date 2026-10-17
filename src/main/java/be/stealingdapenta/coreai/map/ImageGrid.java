package be.stealingdapenta.coreai.map;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record ImageGrid(int columns, int rows) {

    public static final int MAX_DIMENSION = 4;
    public static final int MAX_TILES = 16;
    private static final Pattern DIMENSIONS = Pattern.compile("(?i)(\\d+)x(\\d+)");

    public ImageGrid {
        if (columns < 1 || rows < 1 || columns > MAX_DIMENSION || rows > MAX_DIMENSION
                || Math.multiplyExact(columns, rows) > MAX_TILES) {
            throw new IllegalArgumentException("Image dimensions must be between 1x1 and 4x4.");
        }
    }

    public static ImageGrid parse(String value) {
        Matcher matcher = DIMENSIONS.matcher(value);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Image dimensions must use the format columnsxrows.");
        }
        try {
            return new ImageGrid(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Image dimensions are outside the supported range.", exception);
        }
    }

    public int tileCount() {
        return columns * rows;
    }
}
