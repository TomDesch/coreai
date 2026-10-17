package be.stealingdapenta.coreai.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import okhttp3.Dns;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class SafeImageDownloader {

    public static final int MAX_DOWNLOAD_BYTES = 8 * 1024 * 1024;
    public static final int MAX_IMAGE_WIDTH = 4096;
    public static final int MAX_IMAGE_HEIGHT = 4096;
    public static final long MAX_IMAGE_PIXELS = 4_194_304L;
    private static final int MAX_REDIRECTS = 5;
    private static final int CONNECT_TIMEOUT_SECONDS = 5;
    private static final int READ_TIMEOUT_SECONDS = 10;
    private static final int CALL_TIMEOUT_SECONDS = 20;
    private static final Set<String> BLOCKED_HOST_SUFFIXES = Set.of(".localhost", ".local", ".internal");
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .dns(new PublicAddressDns())
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .followRedirects(false)
            .build();

    private SafeImageDownloader() {
    }

    public static BufferedImage download(String source) throws IOException {
        URI current = validateSource(source);
        for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
            Request request = new Request.Builder().url(current.toURL())
                    .header("Accept", "image/*")
                    .get()
                    .build();
            try (Response response = CLIENT.newCall(request).execute()) {
                if (isRedirect(response.code())) {
                    if (redirects == MAX_REDIRECTS) {
                        throw new IOException("The image source redirected too many times.");
                    }
                    String location = response.header("Location");
                    if (location == null) {
                        throw new IOException("The image source returned an invalid redirect.");
                    }
                    current = resolveRedirect(current, location);
                    continue;
                }
                if (!response.isSuccessful()) {
                    throw new IOException("The image source returned HTTP " + response.code() + ".");
                }
                ResponseBody body = response.body();
                if (body == null) {
                    throw new IOException("The image source returned an empty response.");
                }
                if (body.contentLength() > MAX_DOWNLOAD_BYTES) {
                    throw new IOException("The image exceeds the download size limit.");
                }
                return decode(readLimited(body.byteStream(), MAX_DOWNLOAD_BYTES));
            }
        }
        throw new IOException("The image source redirected too many times.");
    }

    public static URI validateSource(String source) throws IOException {
        try {
            URI uri = new URI(source).normalize();
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null || uri.getRawUserInfo() != null || uri.getFragment() != null
                    || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                    || (uri.getPort() != -1 && uri.getPort() != 80 && uri.getPort() != 443)) {
                throw new IOException("Only public HTTP and HTTPS image URLs are allowed.");
            }
            String normalizedHost = host.toLowerCase(Locale.ROOT);
            if (BLOCKED_HOST_SUFFIXES.stream().anyMatch(normalizedHost::endsWith)
                    || normalizedHost.equals("localhost")) {
                throw new IOException("Only public HTTP and HTTPS image URLs are allowed.");
            }
            if (isAddressLiteral(normalizedHost) && !isPublicAddress(parseAddress(normalizedHost))) {
                throw new IOException("Only public HTTP and HTTPS image URLs are allowed.");
            }
            return uri;
        } catch (URISyntaxException | IllegalArgumentException exception) {
            throw new IOException("The image URL is invalid.", exception);
        }
    }

    public static BufferedImage decode(byte[] bytes) throws IOException {
        try (ImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            java.util.Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IOException("The response is not a supported image.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                long pixels = (long) width * height;
                if (width < 1 || height < 1 || width > MAX_IMAGE_WIDTH || height > MAX_IMAGE_HEIGHT
                        || pixels > MAX_IMAGE_PIXELS) {
                    throw new IOException("The image dimensions exceed the supported limit.");
                }
                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw new IOException("The response is not a supported image.");
                }
                return image;
            } finally {
                reader.dispose();
            }
        }
    }

    static byte[] readLimited(InputStream input, int maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maxBytes, 8192));
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total = Math.addExact(total, read);
            if (total > maxBytes) {
                throw new IOException("The image exceeds the download size limit.");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    static boolean isPublicAddress(InetAddress address) {
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            return isPublicIpv4(bytes);
        }
        if (bytes.length != 16 || address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        if ((bytes[0] & 0xFE) == 0xFC || (bytes[0] & 0xE0) != 0x20) {
            return false;
        }
        boolean documentationAddress = bytes[0] == 0x20 && bytes[1] == 0x01 && bytes[2] == 0x0D
                && bytes[3] == (byte) 0xB8;
        boolean transitionAddress = bytes[0] == 0x20 && bytes[1] == 0x01 && (bytes[2] & 0xFE) == 0x00;
        boolean sixToFourAddress = bytes[0] == 0x20 && bytes[1] == 0x02;
        return !documentationAddress && !transitionAddress && !sixToFourAddress;
    }

    private static boolean isPublicIpv4(byte[] address) {
        int first = address[0] & 0xFF;
        int second = address[1] & 0xFF;
        int third = address[2] & 0xFF;
        return first != 0 && first != 10 && first != 127 && first < 224
                && !(first == 100 && second >= 64 && second <= 127)
                && !(first == 169 && second == 254)
                && !(first == 172 && second >= 16 && second <= 31)
                && !(first == 192 && (second == 168
                        || second == 0
                        || (second == 88 && third == 99)))
                && !(first == 198 && (second == 18 || second == 19
                        || (second == 51 && third == 100)))
                && !(first == 203 && second == 0 && third == 113);
    }

    private static URI resolveRedirect(URI current, String location) throws IOException {
        try {
            return validateSource(current.resolve(new URI(location)).toString());
        } catch (URISyntaxException | IllegalArgumentException exception) {
            throw new IOException("The image source returned an invalid redirect.", exception);
        }
    }

    private static boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    private static boolean isAddressLiteral(String host) {
        return host.indexOf(':') >= 0 || host.matches("[0-9.]+");
    }

    private static InetAddress parseAddress(String host) throws IOException {
        try {
            return InetAddress.getByName(host);
        } catch (UnknownHostException exception) {
            throw new IOException("The image URL address is invalid.", exception);
        }
    }

    private static final class PublicAddressDns implements Dns {

        @Override
        public List<InetAddress> lookup(String hostname) throws UnknownHostException {
            InetAddress[] addresses = InetAddress.getAllByName(hostname);
            if (addresses.length == 0 || Arrays.stream(addresses).anyMatch(address -> !isPublicAddress(address))) {
                throw new UnknownHostException("The image host must resolve only to public addresses.");
            }
            return List.of(addresses);
        }
    }
}
