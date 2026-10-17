package be.stealingdapenta.coreai.storage;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;

public final class PersistenceService {

    private static final int MAX_PENDING_WRITES = 64;
    private static final int SHUTDOWN_WAIT_SECONDS = 10;
    private final ThreadPoolExecutor writer;

    public PersistenceService() {
        writer = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(MAX_PENDING_WRITES), task -> {
                    Thread thread = new Thread(task, "CoreAI-storage");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    public CompletableFuture<Void> writeString(Path path, String value) {
        return write(path, value.getBytes(StandardCharsets.UTF_8));
    }

    public CompletableFuture<Void> write(Path path, byte[] value) {
        byte[] snapshot = value.clone();
        return submit(() -> AtomicFileWriter.write(path, snapshot));
    }

    public CompletableFuture<Void> writeImage(Path path, BufferedImage image) {
        return submit(() -> {
            ByteArrayOutputStream encoded = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "png", encoded)) {
                throw new IOException("No PNG encoder is available.");
            }
            AtomicFileWriter.write(path, encoded.toByteArray());
        });
    }

    public <T> CompletableFuture<T> submitIo(Callable<T> task) {
        CompletableFuture<T> result = new CompletableFuture<>();
        try {
            writer.execute(() -> {
                try {
                    result.complete(task.call());
                } catch (Exception exception) {
                    result.completeExceptionally(exception);
                }
            });
        } catch (RejectedExecutionException exception) {
            result.completeExceptionally(exception);
        }
        return result;
    }

    public void shutdown() {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
                writer.shutdownNow();
            }
        } catch (InterruptedException exception) {
            writer.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private CompletableFuture<Void> submit(IoTask task) {
        return submitIo(() -> {
            task.run();
            return null;
        });
    }

    @FunctionalInterface
    private interface IoTask {

        void run() throws IOException;
    }
}
