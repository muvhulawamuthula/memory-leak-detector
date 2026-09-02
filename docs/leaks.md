# Leak walkthrough

Code-accurate notes for each scenario under `com.leaks.leaks` and its pair under `com.leaks.fixes`.  
Run via: `java -jar target/memory-leak-detector-1.0-SNAPSHOT.jar [leak|fix] <scenario>`.

---

## 1. Static collection — `static`

**Leak:** `StaticCollectionLeak`  
**Fix:** `StaticCollectionFix`

### What the leak does

- Static field: `Map<String, byte[]> cache = new HashMap<>()`.
- Infinite loop: each iteration puts a new UUID key and `new byte[1024 * 100]` (100 KiB).
- Every 100 puts, prints entry count, heap used, and `cache.size()`.
- `Thread.sleep(10)` between puts.

Static fields are GC roots. Nothing ever removes entries, so retained heap grows until OOM (especially under `-Xmx256m`).

### What the fix does

- Same put pattern, but **bounded** to 2000 iterations (demo completes).
- Cache is an access-order `LinkedHashMap` (capacity 500, load 0.75, `accessOrder=true`) overriding `removeEldestEntry` to evict when `size() > MAX_ENTRIES` (500).
- Heap / cache size should stabilise around the cap.

### Observe

```bash
java -Xmx256m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=./heap-static.hprof \
  -jar target/memory-leak-detector-1.0-SNAPSHOT.jar leak static
# Ctrl+C or wait for OOM

java -Xmx256m -jar target/memory-leak-detector-1.0-SNAPSHOT.jar fix static
```

In MAT, expect a large retained set under the static `cache` / `HashMap` for the leak run.

---

## 2. ThreadLocal — `threadlocal`

**Leak:** `ThreadLocalLeak`  
**Fix:** `ThreadLocalFix`

### What the leak does

- `ThreadLocal<byte[]> requestContext`.
- Fixed pool of **10** threads; submits **1000** tasks.
- Each task: `requestContext.set(new byte[1024 * 500])` (500 KiB), then `doWork` (1 ms sleep). **No `remove()`.**
- After shutdown + `System.gc()`, prints heap — values remain reachable from live pool threads’ `ThreadLocalMap` entries until the threads die (they do not, while the pool lives).

### What the fix does

- Same pool / task volume / payload size.
- Each task wraps work in `try/finally { requestContext.remove(); }`.
- After GC, heap should drop; message notes ThreadLocals cleaned up.

### Observe

```bash
java -Xmx256m -jar target/memory-leak-detector-1.0-SNAPSHOT.jar leak threadlocal
java -Xmx256m -jar target/memory-leak-detector-1.0-SNAPSHOT.jar fix threadlocal
```

MAT angle: live `Thread` instances → `threadLocals` (`ThreadLocalMap`) → `Entry` values holding `byte[]`.

---

## 3. Listener / callback — `listener`

**Leak:** `ListenerLeak` (+ `EventBus`)  
**Fix:** `ListenerFix` (same `EventBus`)

### Shared bus

`EventBus` is a singleton (`INSTANCE`) with an `ArrayList<EventListener>`, `register` / `unregister` / `publish` / `listenerCount`.

### What the leak does

- Inner `OrderService` implements `EventListener`, holds `byte[1024 * 200]` (200 KiB), registers on construction.
- Creates **1000** services with `new OrderService(...)` and **never unregisters**.
- Periodically calls `System.gc()` and prints services created, `listenerCount()`, heap.
- After final GC: listeners remain on the bus ≈ 1000; heap stays high.

The bus singleton is a GC root; registered services stay reachable even after the creating loop drops local references.

### What the fix does

- `OrderService` also implements `AutoCloseable`; `close()` calls `unregister(this)`.
- Each iteration: `try (OrderService service = new OrderService(...)) { EventBus.getInstance().publish("order.created"); }`.
- After the loop, `listenerCount()` should be ~0 and heap should fall after GC.

### Observe

```bash
java -Xmx256m -jar target/memory-leak-detector-1.0-SNAPSHOT.jar leak listener
java -Xmx256m -jar target/memory-leak-detector-1.0-SNAPSHOT.jar fix listener
```

**Caveat:** leak and fix share one JVM-wide `EventBus`. Prefer separate processes (as above), not mixing leak then fix in one long-lived JVM without clearing listeners.

MAT angle: `EventBus.INSTANCE` → `listeners` → `OrderService` array → payloads.

---

## 4. Unclosed resource — `resource`

**Leak:** `UnclosedResourceLeak`  
**Fix:** `UnclosedResourceFix`

### What the leak does

- Opens **5000** `BufferedReader`s over `ByteArrayInputStream` of `byte[1024 * 50]`.
- **Never closes** readers; stores each in `List<BufferedReader> leaked`.
- After GC, prints heap and `leaked.size()` (still 5000).

This lab retains the readers in a strong collection so the leak is obvious on the heap. It is not primarily an “exception skipped the close()” demo; the fix shows the idiomatic close-on-all-paths pattern.

### What the fix does

- Same iteration count and buffer size.
- `try (BufferedReader reader = …) { reader.readLine(); }` — no list of live readers.
- Periodic `System.gc()`; final heap should be low with resources released.

### Observe

```bash
java -Xmx256m -jar target/memory-leak-detector-1.0-SNAPSHOT.jar leak resource
java -Xmx256m -jar target/memory-leak-detector-1.0-SNAPSHOT.jar fix resource
```

---

## 5. String intern — `intern`

**Leak:** `InternLeak`  
**Fix:** `InternFix`

### What the leak does

- Loop **1_000_000** times: `("order-id-" + i).intern()`.
- Comments in source: interned strings live in the JVM string pool (Metaspace-oriented); unique dynamic strings accumulate.
- Heap `Runtime` prints may stay modest while Metaspace / native string-table pressure grows — use NMT / MAT / process metrics if you extend the lab (see README stretch goals).

### What the fix does

- Explicit pool: `HashMap<String, String> stringPool` with `MAX_POOL_SIZE = 1000`.
- `deduplicate(s)`: if `stringPool.size() >= MAX_POOL_SIZE`, **`clear()`** the map, then `computeIfAbsent(s, k -> k)`.
- Processes 1_000_000 strings without calling `String.intern()`.

This is **not** a `LinkedHashMap` LRU in the current code; eviction is bulk `clear()` when the cap is hit.

### Observe

```bash
java -Xmx256m -jar target/memory-leak-detector-1.0-SNAPSHOT.jar leak intern
java -Xmx256m -jar target/memory-leak-detector-1.0-SNAPSHOT.jar fix intern
```

---

## Scenario cheat sheet

| Arg | Leak class | Fix class | Completes on its own? |
|---|---|---|---|
| `static` | `StaticCollectionLeak` | `StaticCollectionFix` | Leak: no (infinite). Fix: yes (2000 iters) |
| `threadlocal` | `ThreadLocalLeak` | `ThreadLocalFix` | Yes (1000 tasks) |
| `listener` | `ListenerLeak` | `ListenerFix` | Yes (1000 services) |
| `resource` | `UnclosedResourceLeak` | `UnclosedResourceFix` | Yes (5000 streams) |
| `intern` | `InternLeak` | `InternFix` | Yes (1e6 strings) |

Related: [architecture.md](architecture.md) · [README.md](../README.md)