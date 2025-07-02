package gateway.RPS.threadPool.waitablePQ;

import org.testng.annotations.Test;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

public class WaitablePQ2Test {
    private final int capacity = 15;

    @Test
    public void enqueue() throws InterruptedException {
        WaitablePQ2<Integer> queue = new WaitablePQ2<>(capacity, Integer::compare);
        queue.enqueue(3);
        queue.enqueue(1);
        queue.enqueue(2);
        assertEquals(3, queue.size());
        assertEquals(Integer.valueOf(1), queue.dequeue());
        assertEquals(Integer.valueOf(2), queue.dequeue());
        assertEquals(Integer.valueOf(3), queue.dequeue());
    }

    @Test
    public void dequeue() throws InterruptedException {
        WaitablePQ2<Integer> queue = new WaitablePQ2<>(capacity, Integer::compare);
        queue.enqueue(5);
        queue.enqueue(3);
        assertEquals(Integer.valueOf(3), queue.dequeue());
        assertEquals(Integer.valueOf(5), queue.dequeue());
    }

    @Test
    public void remove() throws InterruptedException {
        WaitablePQ2<String> queue = new WaitablePQ2<>(capacity, String::compareTo);
        queue.enqueue("dog");
        queue.enqueue("cat");
        queue.enqueue("bird");
        queue.enqueue("fish");
        assertEquals(4, queue.size());
        assertTrue(queue.remove("cat"));
        assertEquals(3, queue.size());
        assertFalse(queue.remove("elephant"));
        assertEquals(3, queue.size());
        assertEquals("bird", queue.dequeue());
        assertEquals("dog", queue.dequeue());
        assertEquals("fish", queue.dequeue());
    }

    @Test
    public void peek() throws InterruptedException {
        WaitablePQ2<String> queue = new WaitablePQ2<>(capacity, String::compareTo);
        assertNull(queue.peek());
        queue.enqueue("banana");
        queue.enqueue("apple");
        queue.enqueue("cherry");
        assertEquals("apple", queue.peek());
        assertEquals(3, queue.size());
        assertEquals("apple", queue.peek());
    }

    @Test
    public void isEmpty() throws InterruptedException {
        WaitablePQ2<Integer> queue = new WaitablePQ2<>(capacity, Integer::compare);
        assertTrue(queue.isEmpty());
        queue.enqueue(5);
        assertFalse(queue.isEmpty());
        queue.dequeue();
        assertTrue(queue.isEmpty());
    }

    @Test
    public void size() throws InterruptedException {
        WaitablePQ2<Double> queue = new WaitablePQ2<>(capacity, Double::compare);
        assertEquals(0, queue.size());
        queue.enqueue(1.1);
        assertEquals(1, queue.size());
        queue.enqueue(2.2);
        queue.enqueue(3.3);
        assertEquals(3, queue.size());
        queue.dequeue();
        assertEquals(2, queue.size());
    }

    @Test
    public void testConcurrentOperations() throws InterruptedException {
        final WaitablePQ2<Integer> queue = new WaitablePQ2<>(capacity, Integer::compare);
        final int COUNT = 1000;
        Thread producer = new Thread(() -> {
            try {
                for (int i = 0; i < COUNT; i++) {
                    queue.enqueue(i);
                }
            } catch (InterruptedException e) {
                fail("Producer thread interrupted");
            }
        });

        Thread consumer = new Thread(() -> {
            try {
                for (int i = 0; i < COUNT; i++) {
                    queue.dequeue();
                }
            } catch (InterruptedException e) {
                fail("Consumer thread interrupted");
            }
        });

        producer.start();
        consumer.start();
        producer.join(5000);
        consumer.join(5000);
        assertEquals(0, queue.size());
    }

    @Test
    public void testDequeueBlocking() throws InterruptedException {
        WaitablePQ2<Integer> queue = new WaitablePQ2<>(capacity, Integer::compare);
        AtomicReference<Integer> result = new AtomicReference<>();
        Thread consumer = new Thread(() -> {
            try {
                result.set(queue.dequeue());
            } catch (InterruptedException e) {
                fail("Consumer thread was interrupted");
            }
        });
        consumer.start();
        Thread.sleep(2000);
        assertNull(result.get(), "Consumer should be blocked");
        queue.enqueue(42);
        consumer.join(1000);
        assertEquals(Integer.valueOf(42), result.get());
    }

    @Test
    public void testEnqueueBlocking() throws InterruptedException {
        WaitablePQ2<Integer> queue = new WaitablePQ2<>(3, Integer::compare);
        queue.enqueue(1);
        queue.enqueue(2);
        queue.enqueue(3);
        Thread producer = new Thread(() -> {
            try {
                queue.enqueue(0);
            } catch (InterruptedException e) {
                fail("Producer thread was interrupted");
            }
        });
        producer.start();
        Thread.sleep(2000);
        assertEquals(3, queue.size());
        queue.dequeue();
        producer.join(1000);
        assertEquals(3, queue.size());
        assertEquals(0, queue.peek());
    }
}
