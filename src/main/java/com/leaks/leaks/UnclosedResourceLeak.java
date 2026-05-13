package com.leaks.leaks;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class UnclosedResourceLeak {

    public static void run() throws InterruptedException {
        System.out.println("Opening streams without closing them...");

        // Hold references so streams can't be GC'd even if JVM tries
        List<BufferedReader> leaked = new ArrayList<>();

        for (int i = 0; i < 5000; i++) {
            try {
                // Simulate reading data — stream opened but never closed
                byte[] data = new byte[1024 * 50]; // 50KB per stream
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(new ByteArrayInputStream(data)));

                // BUG: reader is never closed — resources accumulate
                leaked.add(reader);

                // Simulate an error path where close() is forgotten
                if (i % 500 == 0 && i > 0) {
                    Runtime rt = Runtime.getRuntime();
                    long used = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
                    System.out.printf("Streams opened: %d  |  Heap: %dMB%n", i, used);
                    Thread.sleep(30);
                }

            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
                // BUG: close() not called in error path either
            }
        }

        System.gc();
        Thread.sleep(500);

        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
        System.out.println("After GC — Heap: " + used + "MB  |  Unclosed streams: " + leaked.size());
    }
}
