/*****************************************
 *   Author:     Roi Sasson              *
 *   Date:       04.04.2025              *
 *   Approver:   Chen Ben-Tolila         *
 *****************************************/
package gateway.RPS.threadPool.waitablePQ;

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class WaitablePQ2 <E> {
    private final PriorityQueue<E> pq;
    private final int capacity;
    private final Lock lockObj = new ReentrantLock();
    private final Condition condition = lockObj.newCondition();
    private int readersCount = 0;

    public WaitablePQ2(int capacity, Comparator<E> comparator) {
        this.capacity = capacity;
        pq = new PriorityQueue<>(capacity, comparator);
    }

    public void enqueue(E element) throws InterruptedException {
        lockObj.lock();
        while (pq.size() >= capacity || readersCount > 0) {
            condition.await(); // blocking - wait for available space
        }

        pq.add(element);
        condition.signalAll();
        lockObj.unlock();
    }

    public E dequeue() throws InterruptedException {
        lockObj.lock();
        while (pq.isEmpty() || readersCount > 0) {
            condition.await(); // blocking - wait until queue has elements
        }
        E result = pq.poll();
        condition.signalAll();
        lockObj.unlock();

        return result;
    }

    public boolean remove(E element) throws InterruptedException {
        lockObj.lock();
        while (readersCount > 0) {
            condition.await();
        }

        boolean isRemoved = pq.remove(element);
        if (isRemoved) {
            condition.signalAll();
        }

        lockObj.unlock();

        return isRemoved;
    }

    public E peek() {
        startRead();
        E result = pq.peek();
        endRead();

        return result;
    }

    public boolean isEmpty() {
        startRead();
        boolean result = pq.isEmpty();
        endRead();

        return result;
    }

    public int size() {
        startRead();
        int size = pq.size();
        endRead();

        return size;
    }

    private void startRead() {
        lockObj.lock();
        ++readersCount;
        lockObj.unlock();
    }

    private void endRead() {
        lockObj.lock();
        --readersCount;
        if (readersCount == 0) {
            condition.signalAll();
        }
        lockObj.unlock();
    }
}
