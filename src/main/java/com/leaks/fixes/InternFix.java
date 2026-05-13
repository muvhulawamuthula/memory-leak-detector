package com.leaks.fixes;

import java.util.HashMap;
import java.util.Map;

public class InternFix {



    private static final Map<String, String> stringPool = new HashMap<>();
    private static final int MAX_POOL_SIZE = 1000;

    public static String deduplicate(String s) {
        if (stringPool.size() >= MAX_POOL_SIZE) {
            stringPool.clear();
        }
        return stringPool.computeIfAbsent(s, k -> k);
    }

    public static void run() throws InterruptedException {
        System.out.println("Using explicit bounded pool instead of String.intern()...");

        for (int i = 0; i < 1_000_000; i++) {

            String s = deduplicate("order-id-" + i);

            if (i % 100_000 == 0 && i > 0) {
                Runtime rt = Runtime.getRuntime();
                long used = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
                System.out.printf("Strings processed: %d  |  Pool size: %d  |  Heap: %dMB%n",
                        i, stringPool.size(), used);
                Thread.sleep(100);
            }
        }

        System.gc();
        Thread.sleep(500);

        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
        System.out.println("After GC — Heap: " + used + "MB  (bounded pool, no permanent retention)");
    }
}
