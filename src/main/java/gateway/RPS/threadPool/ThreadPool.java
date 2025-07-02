/*****************************************
 *   Author:     Roi Sasson              *
 *   Date:       22.04.2025              *
 *   Approver:   Sefi Hendry             *
 *****************************************/
package gateway.RPS.threadPool;

import gateway.RPS.threadPool.waitablePQ.WaitablePQ1;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;


import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

 /* Manages a pool of threads to execute tasks */
public class ThreadPool implements Executor {
     private final AtomicInteger numOfThreads;
     private final WaitablePQ1<Task<?>> taskQueue = new WaitablePQ1<>();
     private final Semaphore pausedTasks = new Semaphore(0);
     private final Lock lock = new ReentrantLock(false);
     private final Condition condition = lock.newCondition();
     private volatile boolean isShuttingDown = false;

     private static final int MIN_VALUE = 1;
     private static final int MAX_VALUE = Integer.MAX_VALUE;

     public enum TaskPriority {
         HIGH(InternalTaskPriority.HIGH),
         MEDIUM(InternalTaskPriority.MEDIUM),
         LOW(InternalTaskPriority.LOW);

         private final InternalTaskPriority value;

         TaskPriority(InternalTaskPriority value) {
             this.value = value;
         }
     }

     public ThreadPool(@Range(from = MIN_VALUE, to = MAX_VALUE) int numOfThreads) {
         this.numOfThreads = new AtomicInteger(numOfThreads);
         initializeThreads();
     }

     @Override
     public void execute(@NotNull Runnable command) {
         checkShutdown();
         try {
             taskQueue.enqueue(new Task<>(Executors.callable(command)));
         } catch (InterruptedException e) {
             // IGNORE CATCH
         }
     }

     public <T> Future<T> submit(Runnable runnable, TaskPriority TaskPriority) {
         return submit(Executors.callable(runnable, null), TaskPriority);
     }

     public <T> Future<T> submit(Runnable runnable, TaskPriority TaskPriority, T value) {
         return submit(Executors.callable(runnable, value), TaskPriority);
     }

     public <T> Future<T> submit(@NotNull Callable<T> callable, TaskPriority TaskPriority) {
         checkShutdown();
         Task<T> task = new Task<>(callable, TaskPriority.value);
         try {
             taskQueue.enqueue(task);
         } catch (InterruptedException e) {
             // IGNORE CATCH
         }
         return task.future;
     }

     public <T> Future<T> submit(Callable<T> callable) {
         return submit(callable, TaskPriority.MEDIUM);
     }

     public synchronized void setNumOfThreads(@Range(from = MIN_VALUE, to = MAX_VALUE) int numOfThreads) {
         checkShutdown();

         int difference = this.numOfThreads.get() - numOfThreads;
         this.numOfThreads.set(numOfThreads);
         if (difference > 0) {
             for (int i = 0; i < difference; ++i) {
                 try {
                     taskQueue.enqueue(new PoisonPillTask(InternalTaskPriority.KILL_THREAD));
                 } catch (InterruptedException e) {
                     // IGNORE CATCH
                 }
             }
         } else {
             for (int i = 0; i < Math.abs(difference); ++i) {
                 startNewWorkerThread();
             }
         }
     }

     /* Pauses the execution of new tasks by blocking worker threads */
     public void pause() {
         for (int i = 0; i < numOfThreads.get(); i++) {
             try {
                 taskQueue.enqueue(new PauseTask());
             } catch (InterruptedException e) {
                 // IGNORE CATCH
             }
         }
     }

     /* Resumes the execution of paused tasks by releasing blocked worker threads */
     public void resume() {
         pausedTasks.release(numOfThreads.get());
     }

     /* Initiates shutdown of thread pool - prevents new tasks from being submitted
      * and lets existing threads to terminate after finishing their current tasks */
     public void shutDown() {
         isShuttingDown = true;
         resume(); // ensure paused threads receive the shutdown signal

         for (int i = 0; i < numOfThreads.get(); i++) {
             try {
                 taskQueue.enqueue(new PoisonPillTask(InternalTaskPriority.SHUTDOWN));
             } catch (InterruptedException e) {
                 // IGNORE CATCH
             }
         }
     }

     /* Blocks the calling thread until all worker threads have finished execution
      * after a shutdown request, or the timeout occurs
      * throws InterruptedException if the calling thread is interrupted while waiting */
     public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
         long deadline = System.currentTimeMillis() + unit.toMillis(timeout);

         lock.lock();
         try {
             while (numOfThreads.get() > 0 && System.currentTimeMillis() < deadline) {
                 long remainingTime = deadline - System.currentTimeMillis();
                 if (remainingTime > 0) {
                     condition.await(remainingTime, TimeUnit.MILLISECONDS);
                 }
             }
             return numOfThreads.get() == 0;
         } finally {
             lock.unlock();
         }
     }

     public int getNumOfThreads() {
         return numOfThreads.get();
     }

     private void checkShutdown() {
         if (isShuttingDown) {
             throw new RejectedExecutionException("ThreadPool is shutting down and cannot accept new tasks.");
         }
     }

     private void initializeThreads() {
         for (int i = 0; i < numOfThreads.get(); ++i) {
             startNewWorkerThread();
         }
     }

     private void startNewWorkerThread() {
         if (isShuttingDown) {
             return;
         }
         try {
             new WorkerThread().start();
         } catch (Exception e) {
             System.out.println("Failed to start worker thread: " + e.getMessage());
         }
     }

     /* Represents a task to be executed by thread pool */
     private class Task<T> implements Comparable<Task<?>> {
         private final FutureIMP<T> future = new FutureIMP<>();
         private final Callable<T> callable;
         private InternalTaskPriority TaskPriority = InternalTaskPriority.MEDIUM; // default TaskPriority

         public Task(Callable<T> callable, InternalTaskPriority TaskPriority) {
             this(callable);
             this.TaskPriority = TaskPriority;
         }

         public Task(Callable<T> callable) {
             this.callable = callable;
         }

         @Override
         public int compareTo(Task<?> o) {
             return Integer.compare(o.TaskPriority.ordinal(), this.TaskPriority.ordinal());
         }

         /* throws InterruptedException if the thread executing this task is interrupted */
         public void start() throws InterruptedException {
             if (future.isCancelled) {
                 return;
             }
             try {
                 T value = this.callable.call();
                 this.future.setResult(value);
             } catch (Exception e) {
                 throw new RuntimeException(e);
             }
         }

         /* Represents Future object for a submitted task */
         private class FutureIMP<V> implements Future<V> {
             private V result = null;
             private volatile boolean isCancelled = false;
             private volatile boolean isDone = false;

             public synchronized void setResult(V result) {
                 this.result = result;
                 this.isDone = true;
                 notifyAll();
             }

             /* throws InterruptedException if the calling thread is interrupted while waiting */
             @Override
             public synchronized V get() throws InterruptedException {
                 while (!isDone && !isCancelled) {
                     wait();
                 }
                 if (isCancelled) {
                     throw new CancellationException("Task was cancelled.");
                 }

                 return result;
             }

             /* throws InterruptedException if the calling thread is interrupted while waiting */
             /* throws TimeoutException if timeout occurs before the result is available */
             @Override
             public synchronized V get(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
                 long startTime = System.currentTimeMillis();
                 long timeOutMillis = unit.toMillis(timeout);
                 long remainingTime = timeOutMillis - (System.currentTimeMillis() - startTime);

                 while (!isDone && !isCancelled && remainingTime > 0) {
                     wait(remainingTime);
                     remainingTime = timeOutMillis - (System.currentTimeMillis() - startTime);
                 }

                 if (isCancelled) {
                     throw new CancellationException("Task was cancelled.");
                 }
                 if (!isDone) {
                     throw new TimeoutException("Timeout while waiting for result.");
                 }
                 return result;
             }

             @Override
             public synchronized boolean cancel(boolean mayInterruptIfRunning) {
                 if (isDone || isCancelled) {
                     return false;
                 }

                 try {
                     boolean removedFromQueue = taskQueue.remove(Task.this);
                     if (removedFromQueue) {
                         isCancelled = true;
                         notifyAll();
                     }
                     return removedFromQueue;
                 } catch (InterruptedException e) {
                     Thread.currentThread().interrupt();
                     return false;
                 }
             }

             @Override
             public boolean isCancelled() {
                 return isCancelled;
             }

             @Override
             public boolean isDone() {
                 return isDone;
             }
         }
     }

     /* Represents the internal priority levels for tasks */
     private enum InternalTaskPriority {
         SHUTDOWN, LOW, MEDIUM, HIGH, KILL_THREAD, IMMEDIATELY
     }

     private abstract class SystemTask extends Task<Object> {
         SystemTask(InternalTaskPriority priority) {
             super(() -> null, priority);
         }
     }

     private class PoisonPillTask extends SystemTask {
         PoisonPillTask(InternalTaskPriority priority) {
             super(priority);
         }
     }

     private class PauseTask extends SystemTask {
         PauseTask() {
             super(InternalTaskPriority.IMMEDIATELY);
         }

         @Override
         public void start() {
             try {
                 pausedTasks.acquire();
             } catch (InterruptedException e) {
                 throw new RuntimeException(e);
             }
         }
     }

     /* Represents a worker thread that executes tasks from the task queue */
     private class WorkerThread extends Thread {
         @Override
         public void run() {
             while (!isShuttingDown) {
                 Task<?> task = getNextTask();
                 if (task == null) {
                     if (isShuttingDown) {
                         break; // exit loop - no more tasks are expected
                     }
                     continue;
                 }

                 if (task instanceof PoisonPillTask) {
                     break;
                 }
                 executeTask(task);
             }
             decrementThreadCount();
         }

         private Task<?> getNextTask() {
             lock.lock();
             try {
                 if (!taskQueue.isEmpty()) {
                     return taskQueue.dequeue();
                 }
                 return null;
             } catch (InterruptedException e) {
                 Thread.currentThread().interrupt();
                 return null;
             } finally {
                 lock.unlock();
             }
         }

         private void executeTask(Task<?> task) {
             try {
                 task.start();
             } catch (RuntimeException e) {
                 System.out.println("Error executing task: " + e.getMessage());
             } catch (InterruptedException e) {
                 throw new RuntimeException(e);
             }
         }

         private void decrementThreadCount() {
             lock.lock();
             try {
                 if (numOfThreads.get() > 0) {
                     numOfThreads.decrementAndGet();
                     condition.signalAll(); // Signal to awaitTermination
                 }
             } finally {
                 lock.unlock();
             }
         }
     }
 }