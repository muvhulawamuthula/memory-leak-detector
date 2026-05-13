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


        List<BufferedReader> leaked = new ArrayList<>();

        for (int i = 0; i < 5000; i++) {
            try {

                byte[] data = new byte[1024 * 50];
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(new ByteArrayInputStream(data)));


                leaked.add(reader);


                if (i % 500 == 0 && i > 0) {
                    Runtime rt = Runtime.getRuntime();
                    long used = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
                    System.out.printf("Streams opened: %d  |  Heap: %dMB%n", i, used);
                    Thread.sleep(30);
                }

            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());

            }
        }

        System.gc();
        Thread.sleep(500);

        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
        System.out.println("After GC — Heap: " + used + "MB  |  Unclosed streams: " + leaked.size());
    }
}
