package com.leaks.fixes;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;

public class UnclosedResourceFix {

    public static void run() throws InterruptedException {
        System.out.println("Opening streams with try-with-resources...");

        for (int i = 0; i < 5000; i++) {
            // FIX: try-with-resources guarantees close() on every path
            // including exceptions — the compiler generates the finally block
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(
                            new ByteArrayInputStream(new byte[1024 * 50])))) {

                // do work with reader
                reader.readLine();

            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
                // close() was already called before we get here
            }

            if (i % 500 == 0 && i > 0) {
                System.gc();
                Runtime rt = Runtime.getRuntime();
                long used = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
                System.out.printf("Streams processed: %d  |  Heap: %dMB%n", i, used);
                Thread.sleep(30);
            }
        }

        System.gc();
        Thread.sleep(500);

        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
        System.out.println("After GC — Heap: " + used + "MB  (resources properly released)");
    }
}
