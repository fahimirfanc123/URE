package core.threading;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * ThreadManager manages background worker threads for the
 * Universal Recovery Engine.
 *
 * Heavy operations such as disk scanning and file recovery
 * can be submitted as tasks instead of blocking the main thread.
 */
public class ThreadManager implements AutoCloseable {

    private final ExecutorService executorService;

    /**
     * Creates a ThreadManager with the given number of worker threads.
     *
     * @param threadCount number of worker threads
     */
    public ThreadManager(int threadCount) {

        if (threadCount <= 0) {
            throw new IllegalArgumentException(
                    "Thread count must be greater than zero"
            );
        }

        executorService = Executors.newFixedThreadPool(threadCount);
    }

    /**
     * Submit a task for execution.
     *
     * @param task task to execute
     */
    public void submitTask(Runnable task) {

        if (task == null) {
            throw new IllegalArgumentException(
                    "Task cannot be null"
            );
        }

        executorService.submit(task);
    }

    /**
     * Stops accepting new tasks and allows submitted
     * tasks to finish.
     */
    public void shutdown() {
        executorService.shutdown();
    }

    /**
     * Waits for all submitted tasks to complete.
     *
     * @param timeoutSeconds maximum time to wait
     * @return true if all tasks completed, false if timeout occurred
     */
    public boolean awaitCompletion(long timeoutSeconds)
            throws InterruptedException {

        if (timeoutSeconds < 0) {
            throw new IllegalArgumentException(
                    "Timeout cannot be negative"
            );
        }

        return executorService.awaitTermination(
                timeoutSeconds,
                TimeUnit.SECONDS
        );
    }

    /**
     * Returns true if shutdown has been requested.
     */
    public boolean isShutdown() {
        return executorService.isShutdown();
    }

    /**
     * Automatically shuts down the ThreadManager.
     */
    @Override
    public void close() {
        shutdown();
    }
}
