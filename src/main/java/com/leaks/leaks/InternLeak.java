package com.leaks.leaks;

public class InternLeak {

    public static void run() throws InterruptedException {
        System.out.println("Interning unique strings into the string pool...");

        // String.intern() stores strings in the JVM string pool (Metaspace)
        // The pool is never GC'd during normal operation
        // Interning unique dynamic strings fills it permanently
        for (int i = 0; i < 1_000_000; i++) {
            // BUG: interning dynamically generated unique strings
            // Each one is added to the string pool and never removed
            String s = ("order-id-" + i).intern();

            if (i % 100_000 == 0 && i > 0) {
                Runtime rt = Runtime.getRuntime();
                long used = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
                System.out.printf("Strings interned: %d  |  Heap: %dMB%n", i, used);
                Thread.sleep(100);
            }
        }

        // Suggest GC — interned strings in the pool won't be collected
        System.gc();
        Thread.sleep(500);

        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
        System.out.println("After GC — Heap: " + used + "MB  (interned strings survive GC)");
    }
}
