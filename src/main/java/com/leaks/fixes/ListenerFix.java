package com.leaks.fixes;

import com.leaks.leaks.EventBus;

public class ListenerFix {

    static class OrderService implements EventBus.EventListener, AutoCloseable {

        private final String serviceId;
        private final byte[] payload = new byte[1024 * 200]; // 200KB

        OrderService(String serviceId) {
            this.serviceId = serviceId;
            EventBus.getInstance().register(this);
        }

        @Override
        public void onEvent(String event) {
            // handle event
        }

        // FIX: implement AutoCloseable and unregister on close
        @Override
        public void close() {
            EventBus.getInstance().unregister(this);
        }
    }

    public static void run() throws InterruptedException {
        System.out.println("Creating OrderServices that properly unregister on close...");

        for (int i = 0; i < 1000; i++) {
            // try-with-resources guarantees unregister is called
            try (OrderService service = new OrderService("service-" + i)) {
                // do work with service
                EventBus.getInstance().publish("order.created");
            } // close() called here — unregisters from EventBus

            if (i % 100 == 0) {
                System.gc();
                Thread.sleep(50);

                Runtime rt = Runtime.getRuntime();
                long used = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
                System.out.printf("Services created: %d  |  Listeners on bus: %d  |  Heap: %dMB%n",
                        i,
                        EventBus.getInstance().listenerCount(),
                        used);
            }
        }

        System.gc();
        Thread.sleep(500);

        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
        System.out.println("After GC — Heap: " + used + "MB  |  Listeners still on bus: "
                + EventBus.getInstance().listenerCount());
    }
}
