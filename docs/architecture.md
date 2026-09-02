# Architecture

How this lab is wired: packages, the CLI runner, build, and how scenarios relate to JVM retention.

## Goals

1. **Reproduce** five well-known JVM leak patterns with visible heap growth.
2. **Observe** growth via runner heap prints, optional GC logging, and heap dumps.
3. **Fix** each pattern in a parallel class under `com.leaks.fixes` and re-run under the same CLI.

There is no production service, DI container, or test suite — only deliberate demos and a thin harness.

## Packages

```text
com.leaks
├── App.java                 # Maven archetype stub ("Hello World") — not the jar entry point
├── leaks/
│   ├── EventBus.java        # Singleton bus used by listener leak/fix
│   ├── StaticCollectionLeak.java
│   ├── ThreadLocalLeak.java
│   ├── ListenerLeak.java
│   ├── UnclosedResourceLeak.java
│   └── InternLeak.java
├── fixes/
│   ├── StaticCollectionFix.java
│   ├── ThreadLocalFix.java
│   ├── ListenerFix.java
│   ├── UnclosedResourceFix.java
│   └── InternFix.java
└── runner/
    └── LeakRunner.java      # public static void main — shade plugin Main-Class
```

| Concern | Location |
|---|---|
| Deliberate retention | `com.leaks.leaks.*` |
| Corrected behaviour | `com.leaks.fixes.*` |
| CLI, PID, heap snapshots | `com.leaks.runner.LeakRunner` |
| Shared listener registry | `com.leaks.leaks.EventBus` (used by both `ListenerLeak` and `ListenerFix`) |

## Build

`pom.xml`:

- **Java 21** (`maven.compiler.source` / `target`).
- **`maven-shade-plugin`** packages a fat jar and sets  
  `Main-Class: com.leaks.runner.LeakRunner`.
- **`exec-maven-plugin`** is present but the intended path is `mvn package` then `java -jar …`.

Artifact: `target/memory-leak-detector-1.0-SNAPSHOT.jar`.

## Runner contract

`LeakRunner.main`:

1. Prints a banner and the current **PID** (`ProcessHandle.current().pid()`).
2. Parses args: `[mode] [scenario]`  
   - `mode`: `leak` (default) or `fix`  
   - `scenario`: `static` | `threadlocal` | `listener` | `resource` | `intern` | `all` (default)
3. For each selected scenario, calls `runScenario(name, runnable)` which:
   - prints `Before` heap (`used` / `total` / `max` MB from `Runtime`)
   - invokes `XxxLeak.run()` or `XxxFix.run()`
   - prints `After` heap

`leak all` / `fix all` run the five scenarios **in sequence** (static → threadlocal → listener → resource → intern).  
**Note:** `StaticCollectionLeak.run()` is an **infinite loop** (`while (true)`); in `leak all` it never advances to later scenarios unless you interrupt the process. Prefer single-scenario runs for dumps, or run `static` alone until OOM / Ctrl+C.

Unknown scenario prints usage:

```text
Usage: java -jar app.jar [leak|fix] [static|threadlocal|listener|resource|intern|all]
```

## Data flow (conceptual)

```text
                    ┌─────────────────────┐
  CLI args ───────► │     LeakRunner      │
                    │  PID + heap prints  │
                    └─────────┬───────────┘
                              │ mode + scenario
              ┌───────────────┼───────────────┐
              ▼               ▼               ▼
         *Leak.run()    *Fix.run()     EventBus (listener only)
              │               │               │
              └──────► Runtime heap / optional GC log / OOM dump
```

Retention paths (why GC cannot collect):

| Scenario | Typical root → retained objects |
|---|---|
| Static collection | Class static `cache` → `HashMap` → `byte[100KiB]` entries |
| ThreadLocal | Pool threads → `ThreadLocalMap` → `byte[500KiB]` values |
| Listener | `EventBus.INSTANCE` → `ArrayList` → `OrderService` (+ 200KiB payload) |
| Unclosed resource | Local `List<BufferedReader>` + unclosed readers / buffers |
| Intern | JVM string pool (Metaspace-oriented); heap print alone understates Metaspace pressure |

## Heap dump & MAT artifacts

`.gitignore` excludes `*.hprof`, `gc.log`, `target/`, IDE files.

Committed analysis leftovers from a prior MAT session on a dump named **`heap-leak`** may include:

- MAT index files: `heap-leak.index`, `heap-leak.*.index`, `heap-leak.threads`
- Leak Suspects report archive: `heap-leak_Leak_Suspects.zip`
- Sample GC output: `gc.log.0`

Docs may reference these as evidence that MAT was used; regenerate dumps locally when experimenting. This repository does not ship MAT project files or a scripted MAT walkthrough beyond opening a dump and using standard Leak Suspects / Dominator Tree / Path to GC Roots actions.

## Suggested observation workflow

1. Run one **leak** scenario with `-Xmx256m` and dump-on-OOM (and optionally `-Xlog:gc*`).
2. Note PID from runner output; optional live dump: `jcmd <PID> GC.heap_dump heap-manual.hprof`.
3. Open the dump in Eclipse MAT; inspect retained heap and path to GC roots.
4. Run the matching **fix** scenario under the same `-Xmx256m` and compare runner heap lines / GC behaviour.

Scenario-by-scenario behaviour and fix mapping: [leaks.md](leaks.md).