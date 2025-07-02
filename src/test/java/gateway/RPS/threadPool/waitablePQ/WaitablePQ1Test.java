package gateway.RPS.threadPool.waitablePQ;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class WaitablePQ1Test {
    @Test
    void testEnqueueDequeueSingleThread() throws InterruptedException {
        WaitablePQ1<Integer> pq = new WaitablePQ1<>();
        pq.enqueue(5);
        pq.enqueue(10);
        pq.enqueue(1);

        assertEquals(1, pq.dequeue());
        assertEquals(5, pq.dequeue());
        assertEquals(10, pq.dequeue());
        assertTrue(pq.isEmpty());
    }

    @Test
    void testRemove() throws InterruptedException {
        WaitablePQ1<Integer> pq = new WaitablePQ1<>();
        pq.enqueue(5);
        pq.enqueue(10);
        pq.enqueue(1);

        assertTrue(pq.remove(5));
        assertEquals(1, pq.dequeue());
        assertEquals(10, pq.dequeue());
        assertTrue(pq.isEmpty());
    }

    @Test
    void testPeek() throws InterruptedException {
        WaitablePQ1<Integer> pq = new WaitablePQ1<>();
        pq.enqueue(5);
        pq.enqueue(10);
        pq.enqueue(1);

        assertEquals(1, pq.peek());
        assertEquals(1, pq.dequeue());
    }

    @Test
    void testIsEmptyAndSize() throws InterruptedException {
        WaitablePQ1<Integer> pq = new WaitablePQ1<>();
        assertTrue(pq.isEmpty());
        assertEquals(0, pq.size());

        pq.enqueue(5);
        assertFalse(pq.isEmpty());
        assertEquals(1, pq.size());

        pq.dequeue();
        assertTrue(pq.isEmpty());
        assertEquals(0, pq.size());
    }

    @Test
    void testMultiThreadedEnqueueDequeue() throws InterruptedException {
        WaitablePQ1<Integer> pq = new WaitablePQ1<>();

        Thread t1 = new Thread(() -> {
            try {
                for (int i = 0; i < 50; i++) {
                    pq.enqueue(i);
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        Thread t2 = new Thread(() -> {
            try {
                for (int i = 50; i < 100; i++) {
                    pq.enqueue(i);
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        t1.start();
        t2.start();

        t1.join();
        t2.join();

        assertEquals(100, pq.size());

        for (int i = 0; i < 100; i++) {
            pq.dequeue();
        }

        assertTrue(pq.isEmpty());
    }

    @Test
    void testMultiThreadedRemove() throws InterruptedException {
        WaitablePQ1<Integer> pq = new WaitablePQ1<>();

        for (int i = 0; i < 100; i++) {
            pq.enqueue(i);
        }

        Thread t1 = new Thread(() -> {
            for (int i = 0; i < 25; i++) {
                try {
                    pq.remove(i);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        Thread t2 = new Thread(() -> {
            for (int i = 25; i < 50; i++) {
                try {
                    pq.remove(i);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        t1.start();
        t2.start();

        t1.join();
        t2.join();

        assertEquals(50, pq.size());
    }

    @Test
    void testDequeueBlocking() throws InterruptedException {
        WaitablePQ1<Integer> pq = new WaitablePQ1<>();
        final Integer[] dequeuedValue = {null};

        Thread dequeueThread = new Thread(() -> {
            try {
                dequeuedValue[0] = pq.dequeue();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        dequeueThread.start();
        Thread.sleep(100);

        assertNull(dequeuedValue[0]);

        pq.enqueue(1);
        dequeueThread.join();

        assertEquals(1, dequeuedValue[0]);
    }

    @Test
    void testConcurrentReadWrite() throws InterruptedException {
        WaitablePQ1<Integer> pq = new WaitablePQ1<>();
        pq.enqueue(1);

        Thread readThread = new Thread(() -> {
            try {
                pq.peek();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        Thread writeThread = new Thread(() -> {
            try {
                pq.enqueue(2);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        readThread.start();
        writeThread.start();

        readThread.join();
        writeThread.join();

        assertEquals(2, pq.size());
    }

    @Test
    void testRemoveNonExistent() throws InterruptedException {
        WaitablePQ1<Integer> pq = new WaitablePQ1<>();
        pq.enqueue(1);
        assertFalse(pq.remove(2));
    }

    @Test
    void testDequeueEmpty() throws InterruptedException {
        WaitablePQ1<Integer> pq = new WaitablePQ1<>();
        Thread dequeueThread = new Thread(() -> {
            try {
                pq.dequeue();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        dequeueThread.start();
        Thread.sleep(100);
        pq.enqueue(1);
        dequeueThread.join();
    }

    @Test
    void testRemoveBeforeDequeue() throws InterruptedException {
        WaitablePQ1<Integer> pq = new WaitablePQ1<>();
        pq.enqueue(10);

        Thread remover = new Thread(() -> {
            try {
                Thread.sleep(100); // ensure enqueue happens first
                pq.remove(10);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        Thread dequeuer = new Thread(() -> {
            try {
                pq.dequeue();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        dequeuer.start();
        remover.start();

        remover.join();
        dequeuer.join();

        assertTrue(pq.isEmpty());
    }
}