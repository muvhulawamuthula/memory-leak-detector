package com.leaks.runner;

import com.leaks.leaks.StaticCollectionLeak;
import com.leaks.leaks.ThreadLocalLeak;
import com.leaks.leaks.ListenerLeak;
import com.leaks.leaks.UnclosedResourceLeak;
import com.leaks.leaks.InternLeak;
import com.leaks.fixes.StaticCollectionFix;
import com.leaks.fixes.ThreadLocalFix;
import com.leaks.fixes.ListenerFix;
import com.leaks.fixes.UnclosedResourceFix;
import com.leaks.fixes.InternFix;

public class LeakRunner {

    public static void main(String[] args) throws Exception {
        System.out.println("=== Memory Leak Detector ===");
        System.out.println("PID: " + ProcessHandle.current().pid());
        System.out.println();

        String mode     = args.length > 0 ? args[0] : "leak";
        String scenario = args.length > 1 ? args[1] : "all";

        if (scenario.equals("all")) {
            // Run every leak or every fix in sequence
            if (mode.equals("fix")) {
                runScenario("Static Collection FIX",   StaticCollectionFix::run);
                runScenario("ThreadLocal FIX",         ThreadLocalFix::run);
                runScenario("Listener FIX",            ListenerFix::run);
                runScenario("Unclosed Resource FIX",   UnclosedResourceFix::run);
                runScenario("String Intern FIX",       InternFix::run);
            } else {
                runScenario("Static Collection LEAK",  StaticCollectionLeak::run);
                runScenario("ThreadLocal LEAK",        ThreadLocalLeak::run);
                runScenario("Listener LEAK",           ListenerLeak::run);
                runScenario("Unclosed Resource LEAK",  UnclosedResourceLeak::run);
                runScenario("String Intern LEAK",      InternLeak::run);
            }
            return;
        }

        switch (scenario) {
            case "static" -> {
                if (mode.equals("fix")) runScenario("Static Collection FIX",  StaticCollectionFix::run);
                else                    runScenario("Static Collection LEAK",  StaticCollectionLeak::run);
            }
            case "threadlocal" -> {
                if (mode.equals("fix")) runScenario("ThreadLocal FIX",         ThreadLocalFix::run);
                else                    runScenario("ThreadLocal LEAK",         ThreadLocalLeak::run);
            }
            case "listener" -> {
                if (mode.equals("fix")) runScenario("Listener FIX",            ListenerFix::run);
                else                    runScenario("Listener LEAK",            ListenerLeak::run);
            }
            case "resource" -> {
                if (mode.equals("fix")) runScenario("Unclosed Resource FIX",   UnclosedResourceFix::run);
                else                    runScenario("Unclosed Resource LEAK",   UnclosedResourceLeak::run);
            }
            case "intern" -> {
                if (mode.equals("fix")) runScenario("String Intern FIX",       InternFix::run);
                else                    runScenario("String Intern LEAK",       InternLeak::run);
            }
            default -> {
                System.out.println("Unknown scenario: " + scenario);
                System.out.println("Usage: java -jar app.jar [leak|fix] [static|threadlocal|listener|resource|intern|all]");
            }
        }
    }

    static void runScenario(String name, LeakScenario scenario) throws Exception {
        System.out.println("--- " + name + " ---");
        printHeap("Before");
        scenario.run();
        printHeap("After ");
        System.out.println();
    }

    static void printHeap(String label) {
        Runtime rt = Runtime.getRuntime();
        long used  = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
        long total = rt.totalMemory() / 1024 / 1024;
        long max   = rt.maxMemory()   / 1024 / 1024;
        System.out.printf("%s → used: %dMB  total: %dMB  max: %dMB%n",
                label, used, total, max);
    }

    @FunctionalInterface
    interface LeakScenario {
        void run() throws Exception;
    }
}
