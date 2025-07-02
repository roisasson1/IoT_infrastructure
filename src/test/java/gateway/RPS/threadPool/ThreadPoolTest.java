package gateway.RPS.threadPool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ThreadPoolTest {
    private ThreadPool pool;

    @BeforeEach
    void setUp() {
        pool = new ThreadPool(2);
    }

    @Test
    void testExecute() throws InterruptedException {
        ThreadPool pool = new ThreadPool(2);
        AtomicInteger counter = new AtomicInteger();
        pool.execute(counter::incrementAndGet);
        Thread.sleep(100);
        assertEquals(1, counter.get());
    }

    @Test
    void testSubmitRunnableTaskPriority() throws Exception {
        AtomicBoolean ran = new AtomicBoolean(false);
        Future<?> future = pool.submit(() -> ran.set(true), ThreadPool.TaskPriority.LOW);
        future.get(1, TimeUnit.SECONDS);
        assertTrue(ran.get());
    }

    @Test
    void testSubmitRunnableValueAndTaskPriority() throws Exception {
        Future<String> future = pool.submit(() -> {}, ThreadPool.TaskPriority.MEDIUM, "hello");
        assertEquals("hello", future.get(1, TimeUnit.SECONDS));
    }

    @Test
    void testSubmitCallableTaskPriority() throws Exception {
        Future<Integer> future = pool.submit(() -> 42, ThreadPool.TaskPriority.HIGH);
        assertEquals(42, future.get(1, TimeUnit.SECONDS));
    }

    @Test
    void testSubmitCallable() throws Exception {
        Future<String> future = pool.submit(() -> "callable");
        assertEquals("callable", future.get(1, TimeUnit.SECONDS));
    }

    @Test
    void testTaskTaskPriority() throws InterruptedException, ExecutionException {
        ThreadPool singleThreadPool = new ThreadPool(1);
        List<String> order = new ArrayList<>();

        Callable<String> high = () -> { order.add("High"); return "High"; };
        Callable<String> medium = () -> { order.add("Medium"); return "Medium"; };
        Callable<String> low = () -> { order.add("Low"); return "Low"; };
        Future<String> lowFuture = singleThreadPool.submit(low, ThreadPool.TaskPriority.LOW);
        Future<String> highFuture = singleThreadPool.submit(high, ThreadPool.TaskPriority.HIGH);
        Future<String> mediumFuture = singleThreadPool.submit(medium, ThreadPool.TaskPriority.MEDIUM);

        highFuture.get();
        mediumFuture.get();
        lowFuture.get();

        assertEquals(List.of("High", "Medium", "Low"), order);
    }

    @Test
    void testCannotCancelRunningTask() throws InterruptedException {
        CountDownLatch taskStarted = new CountDownLatch(1); // add small pause

        Future<String> future = pool.submit(() -> {
            taskStarted.countDown();
            Thread.sleep(500);
            return "data";
        });

        taskStarted.await();

        // should fail because the task is already executing
        boolean cancelled = future.cancel(false);
        assertFalse(cancelled);
        assertFalse(future.isCancelled());
    }

    @Test
    void testCancelInterruptRunningTask() throws Exception {
        Future<String> future = pool.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                // long task
            }
            return "interrupted";
        });
        Thread.sleep(100);
        assertFalse(future.cancel(true));
        assertFalse(future.isCancelled());
    }

    @Test
    void testCancelCompletedTask() throws Exception {
        Future<String> future = pool.submit(() -> "completed");
        String result = future.get(1, TimeUnit.SECONDS);
        assertEquals("completed", result);
        assertTrue(future.isDone());

        // Should not be able to cancel completed task
        assertFalse(future.cancel(false));
        assertFalse(future.isCancelled());
    }

    @Test
    void testGetNoTimeout() throws Exception {
        String expectedResult = "Test Result Without Timeout";
        Future<String> future = pool.submit(() -> expectedResult);
        String actualResult = future.get();
        assertEquals(expectedResult, actualResult);
    }

    @Test
    void testGetTimeoutNoOccurs() throws Exception {
        String expectedResult = "Test Result With Timeout";
        long timeoutMillis = 500;
        Future<String> future = pool.submit(() -> {
            Thread.sleep(timeoutMillis - 100); // completes within timeout
            return expectedResult;
        });
        String actualResult = future.get(timeoutMillis * 2, TimeUnit.MILLISECONDS);
        assertEquals(expectedResult, actualResult);
    }

    @Test
    void testGetTimeoutOccurs() {
        long timeoutMillis = 100;
        Future<String> future = pool.submit(() -> {
            Thread.sleep(timeoutMillis * 2); // longer than timeout
            return "Should not reach here";
        });
        assertThrows(TimeoutException.class, () -> future.get(timeoutMillis, TimeUnit.MILLISECONDS));
    }

    @Test
    void testGetTimeoutInterruptedException() {
        Thread mainThread = Thread.currentThread();
        Future<String> future = pool.submit(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                return "Interrupted";
            }
            return "Should not reach here";
        });

        // interrupt main thread while waiting on get()
        new Thread(() -> {
            try {
                Thread.sleep(200);
                mainThread.interrupt();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();

        assertThrows(InterruptedException.class, () -> future.get(500, TimeUnit.MILLISECONDS));
    }

    @Test
    void testGetSleepTask() throws InterruptedException, ExecutionException, TimeoutException {
        Future<String> future = new ThreadPool(1).submit(() -> {
            Thread.sleep(1000); return "Sleep Task Result";
        });
        assertEquals("Sleep Task Result", future.get(2, TimeUnit.SECONDS));
    }

    @Test
    void testGetTimeoutException() {
        Future<String> future = new ThreadPool(1).submit(() -> {
            Thread.sleep(2000); return "Test Result";
        });
        assertThrows(TimeoutException.class, () -> future.get(1, TimeUnit.SECONDS));
    }

    @Test
    void testIncreaseNumOfThreads() throws InterruptedException {
        ThreadPool pool = new ThreadPool(1);

        int targetSize = 3;
        pool.setNumOfThreads(targetSize);
        Thread.sleep(100);
        assertEquals(targetSize, pool.getNumOfThreads(), "Number of threads should increase");
    }

    @Test
    void testDecreaseNumOfThreads() throws InterruptedException {
        int initialSize = 3;
        ThreadPool pool = new ThreadPool(initialSize);
        CountDownLatch latch = new CountDownLatch(initialSize);

        for (int i = 0; i < initialSize; i++) {
            pool.execute(() -> {
                latch.countDown();
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        assertTrue(latch.await(2, TimeUnit.SECONDS), "Not all tasks started");

        Thread.sleep(500);
        pool.setNumOfThreads(1);
        Thread.sleep(500);

        assertEquals(1, pool.getNumOfThreads());
    }


    @Test
    void testPausePreventsTasks() throws InterruptedException {
        ThreadPool pool = new ThreadPool(2);
        AtomicInteger counter = new AtomicInteger();

        pool.pause();
        pool.execute(counter::incrementAndGet);
        pool.execute(counter::incrementAndGet);
        Thread.sleep(200);
        assertEquals(0, counter.get(), "Tasks should not execute while paused");
        pool.resume();
        Thread.sleep(200);
        assertEquals(2, counter.get(), "Tasks should execute after resume");
    }

    @Test
    void testResumeAllowsTasks() throws InterruptedException {
        ThreadPool pool = new ThreadPool(2);
        AtomicInteger counter = new AtomicInteger();

        pool.pause();
        pool.execute(counter::incrementAndGet);
        Thread.sleep(100);
        assertEquals(0, counter.get(), "Task should not run while paused");
        pool.resume();
        Thread.sleep(200);
        assertEquals(1, counter.get(), "Task should run after resume");
    }

    @Test
    void testPauseResumeMultipleTimes() throws InterruptedException {
        ThreadPool pool = new ThreadPool(2);
        AtomicInteger counter = new AtomicInteger();

        pool.pause();
        pool.execute(counter::incrementAndGet);
        Thread.sleep(100);
        assertEquals(0, counter.get());
        pool.resume();
        Thread.sleep(100);
        assertEquals(1, counter.get());
        pool.pause();
        pool.execute(counter::incrementAndGet);
        Thread.sleep(100);
        assertEquals(1, counter.get());
        pool.resume();
        Thread.sleep(100);
        assertEquals(2, counter.get());
    }

    @Test
    void testShutdownNoTasks() throws InterruptedException {
        ThreadPool pool = new ThreadPool(2);
        pool.shutDown();
        assertTrue(pool.awaitTermination(100, TimeUnit.MILLISECONDS));
    }

    @Test
    void testShutdownPreventsNewTasks() throws InterruptedException {
        ThreadPool pool = new ThreadPool(2);
        pool.shutDown();
        assertThrows(RejectedExecutionException.class, () -> pool.execute(() -> {}));
    }

    @Test
    void testShutdownCalledTwice() throws InterruptedException {
        ThreadPool pool = new ThreadPool(1);

        pool.shutDown();
        assertTrue(pool.awaitTermination(100, TimeUnit.MILLISECONDS));
        pool.shutDown();
        assertTrue(pool.awaitTermination(100, TimeUnit.MILLISECONDS));
        assertThrows(RejectedExecutionException.class, () -> pool.execute(() -> {}));
    }

    @Test
    void testShutdownStopsThreads() throws InterruptedException {
        ThreadPool pool = new ThreadPool(2);
        pool.shutDown();
        boolean done = pool.awaitTermination(2, TimeUnit.SECONDS);
        assertTrue(done);
    }

    @Test
    void testAwaitShortTimeout() throws InterruptedException {
        ThreadPool pool = new ThreadPool(1);
        CountDownLatch start = new CountDownLatch(1);

        pool.execute(() -> { start.countDown(); try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } });
        assertTrue(start.await(2, TimeUnit.SECONDS));
        pool.shutDown();
        assertFalse(pool.awaitTermination(100, TimeUnit.MILLISECONDS));
    }
}