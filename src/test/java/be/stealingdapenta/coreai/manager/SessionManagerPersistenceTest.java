package be.stealingdapenta.coreai.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import be.stealingdapenta.coreai.service.ChatAgent;
import be.stealingdapenta.coreai.storage.PersistenceService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class SessionManagerPersistenceTest {

    @Test
    void storesPlayerKeyEncryptedAndLoadsItAfterReinitialization() throws Exception {
        Path directory = createDataDirectory();
        PersistenceService persistence = new PersistenceService();
        UUID playerId = UUID.randomUUID();
        String fakeApiKey = "test-only-key-not-a-credential";
        try {
            SessionManager manager = SessionManager.SESSION_MANAGER;
            manager.initialize(directory, persistence, Logger.getAnonymousLogger(), "", "gpt-test", 10_000).join();
            manager.setPlayerAPIKey(playerId, fakeApiKey).join();
            String persistedKeys = Files.readString(directory.resolve("playerkeys.yml"));
            assertFalse(persistedKeys.contains(fakeApiKey));

            manager.clearAgents();
            manager.initialize(directory, persistence, Logger.getAnonymousLogger(), "", "gpt-test", 10_000).join();
            ChatAgent agent = manager.getAgent(playerId);

            assertEquals(fakeApiKey, agent.getApiKey());
        } finally {
            SessionManager.SESSION_MANAGER.clearAgents();
            persistence.shutdown();
            deleteTree(directory);
        }
    }

    @Test
    void refusesToInitializeWithAnUnreadableEncryptionKeyFile() throws Exception {
        Path directory = createDataDirectory();
        Files.writeString(directory.resolve("secret.key"), "not-valid-base64!!!");
        PersistenceService persistence = new PersistenceService();
        try {
            CompletionException exception = assertThrows(CompletionException.class,
                    () -> SessionManager.SESSION_MANAGER.initialize(directory, persistence,
                            Logger.getAnonymousLogger(), "", "gpt-test", 10_000).join());

            assertTrue(exception.getCause() instanceof IOException);
            assertFalse(SessionManager.SESSION_MANAGER.isInitialized());
        } finally {
            SessionManager.SESSION_MANAGER.clearAgents();
            persistence.shutdown();
            deleteTree(directory);
        }
    }

    private static Path createDataDirectory() throws IOException {
        Path parent = Path.of("target", "test-data", "session-manager");
        Files.createDirectories(parent);
        Path directory = parent.resolve(UUID.randomUUID().toString());
        Files.createDirectory(directory);
        return directory;
    }

    private static void deleteTree(Path directory) throws IOException {
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
