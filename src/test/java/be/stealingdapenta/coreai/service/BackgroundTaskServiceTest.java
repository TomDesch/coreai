package be.stealingdapenta.coreai.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class BackgroundTaskServiceTest {

    @Test
    void rejectsWorkWhenItsBoundedQueueIsFull() throws InterruptedException {
        BackgroundTaskService tasks = new BackgroundTaskService(1, 1);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch queuedRan = new CountDownLatch(1);
        AtomicBoolean rejectedTaskRan = new AtomicBoolean();
        try {
            assertTrue(tasks.submit(() -> {
                started.countDown();
                try {
                    release.await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertTrue(started.await(2, TimeUnit.SECONDS));
            assertTrue(tasks.submit(queuedRan::countDown));
            assertFalse(tasks.submit(() -> rejectedTaskRan.set(true)));
        } finally {
            release.countDown();
            tasks.shutdown(2, TimeUnit.SECONDS);
        }

        assertTrue(queuedRan.await(2, TimeUnit.SECONDS));
        assertFalse(rejectedTaskRan.get());
    }
}
