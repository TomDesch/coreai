package be.stealingdapenta.coreai.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class ImageMapCommandTest {

    @Test
    void parsesSizeBeforeUrl() {
        ImageMapCommand.ParsedImageRequest request = ImageMapCommand.parseRequest(new String[] {"2x3", "https://example.com/image.png"});

        assertNotNull(request);
        assertEquals(2, request.grid.columns());
        assertEquals(3, request.grid.rows());
        assertEquals("https://example.com/image.png", request.url);
    }

    @Test
    void parsesUrlBeforeSize() {
        ImageMapCommand.ParsedImageRequest request = ImageMapCommand.parseRequest(
                new String[] {"https://example.com/image.png", "3x2"});

        assertNotNull(request);
        assertEquals(3, request.grid.columns());
        assertEquals(2, request.grid.rows());
    }

    @Test
    void keepsSingleArgumentAsUrl() {
        ImageMapCommand.ParsedImageRequest request = ImageMapCommand.parseRequest(new String[] {"https://example.com/image.png"});

        assertNotNull(request);
        assertEquals(1, request.grid.columns());
        assertEquals(1, request.grid.rows());
        assertEquals("https://example.com/image.png", request.url);
    }

    @Test
    void rejectsMalformedDimensionSyntax() {
        assertNull(ImageMapCommand.parseRequest(new String[] {"abc", "https://example.com/image.png"}));
        assertNull(ImageMapCommand.parseRequest(new String[] {"2x", "https://example.com/image.png"}));
        assertNull(ImageMapCommand.parseRequest(new String[] {"5x1", "https://example.com/image.png"}));
        assertNull(ImageMapCommand.parseRequest(new String[] {"file:///etc/passwd"}));
        assertNull(ImageMapCommand.parseRequest(new String[] {"http://127.0.0.1/image.png"}));
    }
}
