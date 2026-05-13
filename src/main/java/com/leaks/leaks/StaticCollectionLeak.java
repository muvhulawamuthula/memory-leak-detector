package com.leaks.leaks;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class StaticCollectionLeak {


    private static final Map<String, byte[]> cache = new HashMap<>();

    public static void run() throws InterruptedException {
        System.out.println("Filling static cache — press Ctrl+C when heap spikes...");

        int iteration = 0;

        while (true) {
            String key = UUID.randomUUID().toString();
            byte[] value = new byte[1024 * 100];

            cache.put(key, value);
            iteration++;

            if (iteration % 100 == 0) {
                Runtime rt = Runtime.getRuntime();
                long used = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
                System.out.printf("Entries: %d  |  Heap used: %dMB  |  Cache size: %d%n",
                        iteration, used, cache.size());
            }

            Thread.sleep(10);
        }
    }
}
