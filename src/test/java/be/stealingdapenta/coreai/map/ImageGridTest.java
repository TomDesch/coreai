package be.stealingdapenta.coreai.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ImageGridTest {

    @Test
    void parsesSupportedDimensions() {
        ImageGrid grid = ImageGrid.parse("4x3");

        assertEquals(4, grid.columns());
        assertEquals(3, grid.rows());
        assertEquals(12, grid.tileCount());
    }

    @Test
    void rejectsZeroOversizedAndOverflowDimensions() {
        assertThrows(IllegalArgumentException.class, () -> ImageGrid.parse("0x1"));
        assertThrows(IllegalArgumentException.class, () -> ImageGrid.parse("5x1"));
        assertThrows(IllegalArgumentException.class, () -> ImageGrid.parse("999999999999x1"));
        assertThrows(IllegalArgumentException.class, () -> new ImageGrid(Integer.MAX_VALUE, Integer.MAX_VALUE));
    }
}
