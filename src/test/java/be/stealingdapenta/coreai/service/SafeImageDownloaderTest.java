package be.stealingdapenta.coreai.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.awt.image.BufferedImage;
import java.net.InetAddress;
import java.net.URI;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class SafeImageDownloaderTest {

    @Test
    void rejectsLocalAndNonHttpImageAddresses() {
        assertThrows(IOException.class, () -> SafeImageDownloader.validateSource("file:///etc/passwd"));
        assertThrows(IOException.class, () -> SafeImageDownloader.validateSource("http://127.0.0.1/image.png"));
        assertThrows(IOException.class, () -> SafeImageDownloader.validateSource("http://169.254.169.254/latest"));
        assertThrows(IOException.class, () -> SafeImageDownloader.validateSource("http://localhost/image.png"));
        assertThrows(IOException.class, () -> SafeImageDownloader.validateSource("http://user@example.com/image.png"));
        assertThrows(IOException.class, () -> SafeImageDownloader.validateSource("https://example.com:8443/image.png"));
    }

    @Test
    void allowsPublicHttpAddresses() throws Exception {
        URI uri = SafeImageDownloader.validateSource("https://example.com/image.png");

        assertTrue(uri.getHost().equals("example.com"));
        assertTrue(SafeImageDownloader.isPublicAddress(InetAddress.getByName("1.1.1.1")));
        assertFalse(SafeImageDownloader.isPublicAddress(InetAddress.getByName("10.0.0.1")));
        assertFalse(SafeImageDownloader.isPublicAddress(InetAddress.getByName("192.0.2.1")));
        assertFalse(SafeImageDownloader.isPublicAddress(InetAddress.getByName("192.0.0.8")));
        assertFalse(SafeImageDownloader.isPublicAddress(InetAddress.getByName("::1")));
        assertFalse(SafeImageDownloader.isPublicAddress(InetAddress.getByName("fc00::1")));
    }

    @Test
    void rejectsOversizedPixelImagesBeforeDecoding() throws IOException {
        BufferedImage oversized = new BufferedImage(2048, 2049, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        ImageIO.write(oversized, "png", encoded);

        assertThrows(IOException.class, () -> SafeImageDownloader.decode(encoded.toByteArray()));
    }

    @Test
    void rejectsDownloadsThatExceedByteLimit() {
        byte[] tooLarge = new byte[SafeImageDownloader.MAX_DOWNLOAD_BYTES + 1];

        assertThrows(IOException.class, () -> SafeImageDownloader.readLimited(
                new ByteArrayInputStream(tooLarge), SafeImageDownloader.MAX_DOWNLOAD_BYTES));
    }
}
