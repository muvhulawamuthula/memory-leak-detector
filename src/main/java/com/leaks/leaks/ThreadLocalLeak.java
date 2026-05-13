package com.leaks.leaks;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ThreadLocalLeak {

    // ThreadLocal holding a large payload
    private static final ThreadLocal<byte[]> requestContext = new ThreadLocal<>();

    public static void run() throws InterruptedException {
        System.out.println("Submitting tasks to thread pool — ThreadLocal never cleaned up...");

        // Fixed thread pool — threads are reused, never die
        ExecutorService pool = Executors.newFixedThreadPool(10);

        for (int i = 0; i < 1000; i++) {
            final int taskId = i;
            pool.submit(() -> {
                // Simulate storing request context (common in web frameworks)
                requestContext.set(new byte[1024 * 500]); // 500KB per thread

                // Simulate doing some work
                doWork(taskId);

                // BUG: no requestContext.remove() here
                // Thread goes back to pool with 500KB still attached
            });

            if (i % 100 == 0) {
                Runtime rt = Runtime.getRuntime();
                long used = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
                System.out.printf("Tasks submitted: %d  |  Heap used: %dMB%n", i, used);
                Thread.sleep(50);
            }
        }

        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);

        // Force GC — if ThreadLocals were cleaned up, heap would drop
        // Since they're not, heap stays high
        System.gc();
        Thread.sleep(500);

        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
        System.out.println("After GC — Heap used: " + used + "MB  (should be low, but isn't)");
    }

    static void doWork(int taskId) {
        // Simulate real work
        try { Thread.sleep(1); } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
