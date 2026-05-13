package com.leaks.fixes;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class StaticCollectionFix {

    private static final int MAX_ENTRIES = 500;


    private static final Map<String, byte[]> cache = new LinkedHashMap<>(
            MAX_ENTRIES, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    public static void run() throws InterruptedException {
        System.out.println("Running fixed cache — heap should stabilise...");

        int iteration = 0;

        while (iteration < 2000) {
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

        System.out.println("Done — cache never exceeded " + MAX_ENTRIES + " entries");
    }
}
