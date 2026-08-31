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
        assertEquals(2, request.width);
        assertEquals(3, request.height);
        assertEquals("https://example.com/image.png", request.url);
    }

    @Test
    void keepsSingleArgumentAsUrl() {
        ImageMapCommand.ParsedImageRequest request = ImageMapCommand.parseRequest(new String[] {"https://example.com/image.png"});

        assertNotNull(request);
        assertEquals(1, request.width);
        assertEquals(1, request.height);
        assertEquals("https://example.com/image.png", request.url);
    }

    @Test
    void rejectsMalformedDimensionSyntax() {
        assertNull(ImageMapCommand.parseRequest(new String[] {"abc", "https://example.com/image.png"}));
        assertNull(ImageMapCommand.parseRequest(new String[] {"2x", "https://example.com/image.png"}));
    }
}
