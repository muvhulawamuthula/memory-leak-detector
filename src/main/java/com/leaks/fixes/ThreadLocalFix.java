package com.leaks.fixes;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ThreadLocalFix {

    private static final ThreadLocal<byte[]> requestContext = new ThreadLocal<>();

    public static void run() throws InterruptedException {
        System.out.println("Submitting tasks — ThreadLocal cleaned up with try/finally...");

        ExecutorService pool = Executors.newFixedThreadPool(10);

        for (int i = 0; i < 1000; i++) {
            final int taskId = i;
            pool.submit(() -> {
                try {
                    requestContext.set(new byte[1024 * 500]); // 500KB
                    doWork(taskId);
                } finally {

                    requestContext.remove();
                }
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

        System.gc();
        Thread.sleep(500);

        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
        System.out.println("After GC — Heap used: " + used + "MB  (low — ThreadLocals cleaned up)");
    }

    static void doWork(int taskId) {
        try { Thread.sleep(1); } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
