# Memory Leak Detector

Five classic JVM memory leak patterns — **reproduced**, inspected with **Eclipse MAT** / heap dumps, and **fixed**. Java 21.

Built as a hands-on lab for the diagnostic habits that matter in production: reading GC pressure, capturing a dump, finding retained heap, and proving a fix with a before/after heap comparison.

| | |
|---|---|
| **Language** | Java 21 |
| **Build** | Maven (`maven-shade-plugin` → runnable jar) |
| **Entry point** | `com.leaks.runner.LeakRunner` |
| **Scenarios** | `static` · `threadlocal` · `listener` · `resource` · `intern` · `all` |

---

## The five leaks

| # | Scenario arg | Pattern | Retention root | Fix in this repo |
|---|---|---|---|---|
| 1 | `static` | Unbounded static `HashMap` | Static field (GC root) | Bounded LRU `LinkedHashMap` (`removeEldestEntry`, max 500) |
| 2 | `threadlocal` | `ThreadLocal.set` without `remove` on pooled threads | Live pool threads → `ThreadLocalMap` | `try/finally { requestContext.remove() }` |
| 3 | `listener` | Register on singleton bus, never unregister | `EventBus.INSTANCE` → listener list | `AutoCloseable` + `try-with-resources` |
| 4 | `resource` | Streams opened and retained without `close` | Strong refs in a collection + unclosed readers | `try-with-resources` (no retained list) |
| 5 | `intern` | Unique dynamic strings via `String.intern()` | JVM string pool / Metaspace | Explicit bounded `HashMap` pool (clear at 1000) |

Deep dives with code-accurate behaviour: **[docs/leaks.md](docs/leaks.md)**  
Package layout, runner, and JVM flags: **[docs/architecture.md](docs/architecture.md)**

---

## Prerequisites

- **Java 21+**
- **Maven 3.8+**
- **Eclipse MAT** (optional, for heap analysis) — [download](https://eclipse.dev/mat/downloads.php)

---

## Quick start

```bash
git clone https://github.com/muvhulawamuthula/memory-leak-detector.git
cd memory-leak-detector
mvn package -q
```

Main class is wired by the shade plugin to `com.leaks.runner.LeakRunner` (ignore the stub `com.leaks.App`).

### Run a leak (small heap + auto dump on OOM)

```bash
java -Xmx256m \
     -XX:+HeapDumpOnOutOfMemoryError \
     -XX:HeapDumpPath=./heap.hprof \
     -jar target/memory-leak-detector-1.0-SNAPSHOT.jar leak static
```

### Run the matching fix

```bash
java -Xmx256m \
     -jar target/memory-leak-detector-1.0-SNAPSHOT.jar fix static
```

### All scenarios

```bash
# Leaks in sequence — static collection runs forever until OOM / Ctrl+C
java -Xmx256m \
     -XX:+HeapDumpOnOutOfMemoryError \
     -XX:HeapDumpPath=./heap-all.hprof \
     -Xlog:gc*:file=gc.log \
     -jar target/memory-leak-detector-1.0-SNAPSHOT.jar leak all

# Fixes — bounded / cleaned; heap should stay controlled
java -Xmx256m \
     -jar target/memory-leak-detector-1.0-SNAPSHOT.jar fix all
```

**CLI:** `java -jar … [leak|fix] [static|threadlocal|listener|resource|intern|all]`  
Defaults: mode `leak`, scenario `all`. The runner prints the process **PID** on startup (useful for `jcmd`).

---

## JVM flags used here

| Flag | Why |
|---|---|
| `-Xmx256m` | Small heap so growth / OOM shows up quickly |
| `-XX:+HeapDumpOnOutOfMemoryError` | Write a dump when the heap dies |
| `-XX:HeapDumpPath=./heap.hprof` | Dump path (gitignored: `*.hprof`) |
| `-Xlog:gc*:file=gc.log` | GC log for the “reclaim nothing” signature |

---

## Heap dumps and MAT artifacts in this repo

Raw `*.hprof` dumps are **gitignored**. What you may see in the repo root are **Eclipse MAT index / report artifacts** from a prior analysis of a dump named `heap-leak` (e.g. `heap-leak.index`, `heap-leak.*.index`, `heap-leak.threads`, `heap-leak_Leak_Suspects.zip`) plus a sample GC log (`gc.log.0`).

Those files document that MAT was run against a captured dump; regenerate your own dump with the flags above (or `jcmd <PID> GC.heap_dump heap-manual.hprof`), then open it in MAT.

High-level MAT flow (same as typical Eclipse MAT usage):

1. Open the `.hprof` in MAT (`File → Open Heap Dump`).
2. Run **Leak Suspects** from the Overview actions (a Suspects zip may already exist under the `heap-leak_*` name for a prior run).
3. Use the **Dominator Tree**, sorted by **Retained Heap**.
4. From a suspect: **Path to GC Roots → exclude weak references** to see why the object stays alive.

Do not treat index files as a substitute for opening a fresh dump when you change scenarios.

---

## What a leak looks like in the GC log

Healthy (young GC reclaims most of the nursery):

```text
Pause Young  80M->12M
```

Leaking (frequent GC, almost no reclaim → Full GC → OOM):

```text
Pause Young  240M->238M
Pause Full   254M->254M
OutOfMemoryError: Java heap space
```

The runner also prints before/after used/total/max heap per scenario via `Runtime`.

---

## Project layout (summary)

```text
src/main/java/com/leaks/
├── leaks/     # deliberate leak demos + EventBus singleton
├── fixes/     # matching fixes
└── runner/    # LeakRunner CLI + heap snapshots
docs/
├── architecture.md
└── leaks.md
```

Full structure and data-flow notes: [docs/architecture.md](docs/architecture.md).

---

## Key ideas (short)

- **GC roots** that show up here: static fields, live threads (and their `ThreadLocalMap`s), the singleton `EventBus`.
- **Retained heap** (what would free if this object died) matters more than shallow size when hunting leaks.
- Every demo is an unintended path from a root to objects the app thinks it is “done with.”

---

## Stretch goals

- [ ] `WeakHashMap` variant of the static cache — watch weak keys vs strong values
- [ ] Classloader leak via custom `URLClassLoader` (classic app-server redeploy leak)
- [ ] JFR continuous recording — compare allocation rates leak vs fix
- [ ] WeakReference-based regression test that fails if retained size stays high
- [ ] `-XX:NativeMemoryTracking=summary` during the intern scenario (Metaspace / NMT)

---

## References

- [Java SE 21 GC Tuning Guide](https://docs.oracle.com/en/java/javase/21/gctuning/)
- [Eclipse MAT documentation](https://help.eclipse.org/latest/index.jsp?topic=/org.eclipse.mat.ui.help/welcome.html)
- [JEP 328 — Java Flight Recorder](https://openjdk.org/jeps/328)
- [Aleksey Shipilëv — JVM internals](https://shipilev.net)

---

## License

MIT