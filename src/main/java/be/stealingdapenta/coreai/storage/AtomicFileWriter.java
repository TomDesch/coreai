package be.stealingdapenta.coreai.storage;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

public final class AtomicFileWriter {

    private AtomicFileWriter() {
    }

    public static void write(Path destination, byte[] data) throws IOException {
        Path target = destination.toAbsolutePath();
        Path parent = target.getParent();
        if (parent == null) {
            throw new IOException("Storage path has no parent directory.");
        }
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, ".coreai-", ".tmp");
        try {
            Files.write(temporary, data, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
