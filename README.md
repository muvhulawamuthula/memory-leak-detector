# Memory Leak Detector

A hands-on Java project that deliberately reproduces five classic JVM memory leak patterns, then detects and fixes each one using real profiling tools — Eclipse MAT, JFR, and heap dumps.

Built to develop the diagnostic instincts that separate senior engineers from developers who guess at performance problems.

---

## What this project covers

| # | Leak pattern | Root cause | Fix strategy |
|---|---|---|---|
| 1 | Static collection | Unbounded `HashMap` as static field | Bounded `LinkedHashMap` with LRU eviction |
| 2 | ThreadLocal | `set()` without `remove()` on pooled thread | `try/finally { remove() }` |
| 3 | Listener / callback | Register without unregister on shared event bus | `AutoCloseable` + `try-with-resources` |
| 4 | Unclosed resource | Stream opened, never closed on error path | `try-with-resources` on every resource |
| 5 | String intern | Dynamic strings interned into permanent JVM pool | Explicit bounded pool with eviction |

---

## Project structure

```
src/main/java/com/leaks/
├── leaks/
│   ├── StaticCollectionLeak.java   # Unbounded static HashMap grows forever
│   ├── ThreadLocalLeak.java        # ThreadLocal never removed from pooled threads
│   ├── ListenerLeak.java           # EventBus holds references to discarded services
│   ├── EventBus.java               # Shared singleton event bus
│   ├── UnclosedResourceLeak.java   # Streams opened but never closed
│   └── InternLeak.java             # Unique strings interned into JVM string pool
├── fixes/
│   ├── StaticCollectionFix.java    # LinkedHashMap with removeEldestEntry
│   ├── ThreadLocalFix.java         # try/finally with ThreadLocal.remove()
│   ├── ListenerFix.java            # AutoCloseable unregisters on close
│   ├── UnclosedResourceFix.java    # try-with-resources on every stream
│   └── InternFix.java              # Explicit bounded string pool
└── runner/
    └── LeakRunner.java             # Harness: runs any leak or fix with heap monitoring
```

---

## Prerequisites

- Java 21+
- Maven 3.8+
- Eclipse MAT (for heap dump analysis) — [download here](https://eclipse.dev/mat/downloads.php)

---

## Getting started

```bash
git clone https://github.com/yourusername/memory-leak-detector.git
cd memory-leak-detector
mvn package -q
```

---

## Running scenarios

### Run a single leak

```bash
java -Xmx256m \
     -XX:+HeapDumpOnOutOfMemoryError \
     -XX:HeapDumpPath=./heap.hprof \
     -jar target/memory-leak-detector-1.0-SNAPSHOT.jar leak static
```

Available scenarios: `static` · `threadlocal` · `listener` · `resource` · `intern` · `all`

### Run the corresponding fix

```bash
java -Xmx256m \
     -jar target/memory-leak-detector-1.0-SNAPSHOT.jar fix static
```

### Run all leaks then all fixes

```bash
# All leaks in sequence — will OOM on static collection
java -Xmx256m \
     -XX:+HeapDumpOnOutOfMemoryError \
     -XX:HeapDumpPath=./heap-all.hprof \
     -Xlog:gc*:file=gc.log \
     -jar target/memory-leak-detector-1.0-SNAPSHOT.jar leak all

# All fixes — heap stays flat throughout
java -Xmx256m \
     -jar target/memory-leak-detector-1.0-SNAPSHOT.jar fix all
```

---

## JVM flags explained

| Flag | Purpose |
|---|---|
| `-Xmx256m` | Small heap so leaks hit OOM faster |
| `-XX:+HeapDumpOnOutOfMemoryError` | Auto-captures heap dump on OOM |
| `-XX:HeapDumpPath=./heap.hprof` | Where to write the dump |
| `-Xlog:gc*:file=gc.log` | GC log — reveals leak signature (GC running constantly, reclaiming nothing) |

---

## Detecting leaks with Eclipse MAT

### 1. Trigger the heap dump

Either let the app OOM (dump is written automatically), or trigger manually:

```bash
# Get the PID from the runner output
jcmd <PID> GC.heap_dump heap-manual.hprof
```

### 2. Open MAT

```
File → Open Heap Dump → select heap.hprof
```

### 3. Run Leak Suspects report

```
Overview → Actions → Leak Suspects
```

MAT automatically identifies the biggest retained heap holders and shows the reference chain to the GC root.

### 4. Inspect the Dominator Tree

```
Overview → Actions → Dominator Tree
```

Sort by **Retained Heap** descending. The leak will be at the top.

### 5. Trace the GC root path

```
Right-click the suspect object → Path to GC Roots → Exclude weak references
```

This shows exactly why GC cannot collect the object — the chain always ends at a static field, a live thread, or a JNI reference.

---

## What the GC log reveals

A healthy app shows GC running occasionally and reclaiming most of the heap:

```
GC(4)  Pause Young  80M->12M   ← collected 68MB, healthy
GC(5)  Pause Young  80M->11M   ← same
```

A leaking app shows GC running constantly and reclaiming nothing:

```
GC(41)  Pause Young  240M->238M  ← collected 2MB, struggling
GC(42)  Pause Full   254M->254M  ← full GC, still can't free anything
GC(43)  java.lang.OutOfMemoryError: Java heap space
```

That pattern — increasing GC frequency with decreasing reclaim — is the signature of a memory leak before it hits OOM.

---

## Leak deep dives

### 1. Static collection leak

**The leak:**
```java
private static final Map<String, byte[]> cache = new HashMap<>();

public static void store(String key) {
    cache.put(key, new byte[1024 * 100]); // never removed
}
```

Static fields are GC roots. Anything reachable from a static field can never be collected. An unbounded static map grows until OOM.

**MAT finding:** `StaticCollectionLeak.cache` → `HashMap` → `byte[]` × N, retaining the entire heap.

**The fix:**
```java
private static final Map<String, byte[]> cache = new LinkedHashMap<>(500, 0.75f, true) {
    protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
        return size() > 500;
    }
};
```

---

### 2. ThreadLocal leak

**The leak:**
```java
private static final ThreadLocal<byte[]> context = new ThreadLocal<>();

pool.submit(() -> {
    context.set(new byte[1024 * 500]);
    doWork();
    // BUG: no context.remove() — thread returns to pool with value attached
});
```

Thread pool threads never die. `ThreadLocal` values attached to them are reachable via the live thread — GC cannot collect them. Every task that forgets `remove()` permanently retains another 500KB.

**MAT finding:** Live pool threads → `ThreadLocalMap` → `ThreadLocalMap$Entry[]` → `byte[]` × 10.

**The fix:**
```java
pool.submit(() -> {
    try {
        context.set(new byte[1024 * 500]);
        doWork();
    } finally {
        context.remove(); // always runs, even if doWork() throws
    }
});
```

---

### 3. Listener leak

**The leak:**
```java
class OrderService implements EventBus.EventListener {
    OrderService(String id) {
        EventBus.getInstance().register(this); // never unregisters
    }
}

new OrderService("svc-1"); // goes out of scope, but NOT collected
```

The singleton `EventBus` is a GC root. Every registered listener is reachable from it. Services that go out of scope in application code are still strongly referenced by the bus.

**MAT finding:** `EventBus.INSTANCE` → `ArrayList` → `OrderService[]` → `byte[]` × 1000.

**The fix:**
```java
class OrderService implements EventBus.EventListener, AutoCloseable {
    @Override
    public void close() {
        EventBus.getInstance().unregister(this);
    }
}

try (OrderService svc = new OrderService("svc-1")) {
    EventBus.getInstance().publish("order.created");
} // close() called — unregistered, now eligible for GC
```

---

### 4. Unclosed resource leak

**The leak:**
```java
BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
// process data...
// BUG: no reader.close() on exception paths
```

Resources hold native handles. Without `close()`, they accumulate. The problem is worst on error paths where `close()` is written for the happy path but skipped when an exception is thrown.

**The fix:**
```java
try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
    reader.readLine();
} // compiler generates finally { reader.close() } — runs on every path
```

---

### 5. String intern leak

**The leak:**
```java
for (int i = 0; i < 1_000_000; i++) {
    String s = ("order-id-" + i).intern(); // unique string added to JVM pool permanently
}
```

`String.intern()` stores strings in the JVM string pool which lives in Metaspace. The pool is not garbage collected during normal JVM operation. Interning dynamically generated unique strings fills Metaspace permanently.

**The fix:**
```java
private static final Map<String, String> pool = new LinkedHashMap<>(1000, 0.75f, true) {
    protected boolean removeEldestEntry(Map.Entry<String, String> e) {
        return size() > 1000;
    }
};

public static String deduplicate(String s) {
    return pool.computeIfAbsent(s, k -> k);
}
```

Only use `String.intern()` for a small, finite, known-at-compile-time set of strings. Never intern dynamically generated strings.

---

## Key concepts

### GC roots

An object is reachable — and therefore not collectible — if any path exists from a GC root to it. GC roots in the JVM are:

- Static fields
- Local variables on live thread stacks
- Active threads themselves
- JNI references

Every memory leak in this project is an unintended path from one of these roots to an object the application considers done.

### Retained vs shallow heap

- **Shallow heap** — memory the object itself occupies
- **Retained heap** — memory that would be freed if this object were collected

A `HashMap` might have 48 bytes shallow heap but 200MB retained heap. Always sort the Dominator Tree by retained heap.

### The leak signature in GC logs

```
Healthy:  GC running every few seconds, reclaiming 60-80% of heap
Leaking:  GC running constantly, reclaiming < 5%, heap floor rising each cycle
OOM:      Full GC fires repeatedly, reclaims nothing, process dies
```

---

## Why this matters

Most developers encounter memory leaks by waiting for a production alert. This project builds the diagnostic muscle before that happens:

- You know what a leak looks like in a GC log before the OOM
- You can open a heap dump and find the culprit in under 5 minutes
- You can trace a retention path to its GC root and explain exactly why GC cannot collect it
- You can write the fix and prove it with a before/after heap comparison

These are skills most senior candidates claim but few can demonstrate.

---

## Stretch goals

- [ ] Add a `WeakHashMap` variant of the static collection leak — observe how weak references interact with GC
- [ ] Reproduce a classloader leak using a custom `URLClassLoader` — root cause of most app server leaks on redeploy
- [ ] Add JFR continuous recording and compare allocation rate profiles between leak and fix runs
- [ ] Write a `WeakReference` + `GC.runFinalization()` unit test that asserts no leak — automated regression testing for memory
- [ ] Add `-XX:NativeMemoryTracking=summary` and observe Metaspace growth during the intern leak

---

## References

- [Java Garbage Collection Tuning Guide](https://docs.oracle.com/en/java/javase/21/gctuning/)
- [Eclipse MAT Documentation](https://help.eclipse.org/latest/index.jsp?topic=/org.eclipse.mat.ui.help/welcome.html)
- [JEP 328 — Java Flight Recorder](https://openjdk.org/jeps/328)
- [Aleksey Shipilëv — JVM Internals](https://shipilev.net)

---

## License

MIT
