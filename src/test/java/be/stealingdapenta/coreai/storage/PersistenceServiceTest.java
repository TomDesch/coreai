package be.stealingdapenta.coreai.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

class PersistenceServiceTest {

    @Test
    void atomicallyReplacesAFileWithNewContent() throws Exception {
        Path directory = Path.of("target", "test-data", "persistence");
        Path file = directory.resolve(UUID.randomUUID() + ".yml");
        PersistenceService persistence = new PersistenceService();
        try {
            persistence.writeString(file, "first: value\n").join();
            persistence.writeString(file, "second: value\n").join();

            assertEquals("second: value\n", Files.readString(file));
        } finally {
            persistence.shutdown();
            Files.deleteIfExists(file);
        }
    }

    @Test
    void leavesExistingFileUntouchedWhenItsParentCannotBeCreated() throws IOException {
        Path directory = Path.of("target", "test-data", "persistence");
        Files.createDirectories(directory);
        Path blocker = directory.resolve(UUID.randomUUID().toString());
        Files.writeString(blocker, "existing");
        Path destination = blocker.resolve("data.yml");
        PersistenceService persistence = new PersistenceService();
        try {
            CompletionException exception = assertThrows(CompletionException.class,
                    () -> persistence.write(destination, "replacement".getBytes(StandardCharsets.UTF_8)).join());

            assertTrue(exception.getCause() instanceof IOException);
            assertEquals("existing", Files.readString(blocker));
        } finally {
            persistence.shutdown();
            Files.deleteIfExists(blocker);
        }
    }
}
