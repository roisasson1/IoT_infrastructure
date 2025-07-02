/*****************************************
 *   Author:     Roi Sasson              *
 *   Date:       04.04.2025              *
 *   Approver:   Chen Ben-Tolila         *
 *****************************************/
package gateway.RPS.threadPool.waitablePQ;

import java.util.PriorityQueue;
import java.util.concurrent.Semaphore;

public class WaitablePQ1<E extends Comparable<E>> {
    private final PriorityQueue<E> pq = new PriorityQueue<>();
    private final Semaphore elementsAvailable = new Semaphore(0);
    private final Semaphore readLock = new Semaphore(1);
    private final Semaphore writeLock = new Semaphore(1);
    private int readerCount = 0;

    public void enqueue(E element) throws InterruptedException {
        writeLock.acquire();
        try {
            pq.add(element);
            elementsAvailable.release(); // Signal that an element is available
        } finally {
            writeLock.release();
        }
    }

    public E dequeue() throws InterruptedException {
        elementsAvailable.acquire(); // blocking - wait for an element
        writeLock.acquire();
        try {
            return pq.poll();
        } finally {
            writeLock.release();
        }
    }

    public boolean remove(E element) throws InterruptedException {
        writeLock.acquire();
        try {
            boolean removed = pq.remove(element);
            if (removed) {
                elementsAvailable.tryAcquire();
            }
            return removed;
        } finally {
            writeLock.release();
        }
    }

    public E peek() throws InterruptedException {
        startRead();
        try {
            return pq.peek();
        } finally {
            endRead();
        }
    }

    public boolean isEmpty() throws InterruptedException {
        startRead();
        try {
            return pq.isEmpty();
        } finally {
            endRead();
        }
    }

    public int size() throws InterruptedException {
        startRead();
        try {
            return pq.size();
        } finally {
            endRead();
        }
    }

    private void startRead() throws InterruptedException {
        readLock.acquire();
        try {
            if (readerCount == 0) {
                writeLock.acquire(); // if first reader, block writers
            }
            readerCount++;
        } finally {
            readLock.release();
        }
    }

    private void endRead() throws InterruptedException {
        readLock.acquire();
        try {
            --readerCount;
            if (readerCount == 0) {
                writeLock.release(); // If last reader, allow writers
            }
        } finally {
            readLock.release();
        }
    }
}