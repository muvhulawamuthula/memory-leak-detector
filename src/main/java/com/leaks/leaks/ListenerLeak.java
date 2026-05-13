package com.leaks.leaks;

public class ListenerLeak {

    // Simulates a service that handles orders
    // Each instance carries a large payload (like a real service would)
    static class OrderService implements EventBus.EventListener {

        private final String serviceId;
        private final byte[] payload = new byte[1024 * 200]; // 200KB per service

        OrderService(String serviceId) {
            this.serviceId = serviceId;
            // Registers itself on the shared bus — but never unregisters
            EventBus.getInstance().register(this);
        }

        @Override
        public void onEvent(String event) {
            // handle event
        }
    }

    public static void run() throws InterruptedException {
        System.out.println("Creating OrderServices that never unregister...");

        for (int i = 0; i < 1000; i++) {
            // OrderService goes out of scope immediately after creation
            // App thinks it's done with it — but EventBus still holds it
            new OrderService("service-" + i);

            if (i % 100 == 0) {
                // Suggest GC — won't help because EventBus holds all references
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
