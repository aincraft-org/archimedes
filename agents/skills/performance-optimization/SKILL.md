---
name: performance-optimization
description: Use when diagnosing server lag or tick loss, profiling a Paper server or plugin, optimizing main-thread work, managing async chunk loading, fixing listener leaks, or tuning Paper configuration. Triggers include Spark profiling, TPS drops, MSPT, flame graphs, async scheduler, chunk loading, and tick lag.
---

# Performance Optimization (Paper Server)

Diagnose and fix Paper server lag with profiling, main-thread discipline, and safe async patterns.

Core principle: **measure before optimizing; the main thread is the bottleneck — never make it wait.**

Pins verified 2026-08-21 against: Paper global configuration (https://docs.papermc.io/paper/reference/global-configuration/#chunk_system), Paper getting-started requirements (https://docs.papermc.io/paper/getting-started/#requirements), and the Spark installation docs (https://spark.lucko.me/docs/Installation).

## Profiling with Spark

Spark is bundled with Paper 1.21+ and is the standard profiler. Profile during peak load, not on an empty server, for 60–180 seconds:

```text
/spark health          # quick TPS, MSPT, memory snapshot
/spark profiler start  # begin sampling
/spark profiler stop   # end sampling, prints a shareable URL
```

Read the flame graph: the widest bars at the top of the call stack are the methods consuming the most CPU. If the main-thread stack is dominated by scheduler execution, batch the work; if it is chunk generation/loading, reduce request rate and avoid urgent loads; if it is event dispatch or listener collections, inspect listener registration and unregistering.

## Main-thread discipline

Time-consuming work — database queries, file I/O, complex calculations — must run off the main thread. Use the Paper async scheduler:

```java
Bukkit.getAsyncScheduler().runNow(plugin, task -> {
    // heavy work
});
```

Bring results back to the main thread only when a Bukkit API call requires it, and never call Bukkit APIs from an async task:

```java
Bukkit.getAsyncScheduler().runNow(plugin, task -> {
    String result = expensiveWork();
    Bukkit.getScheduler().runTask(plugin, () -> {
        // main-thread work with result
    });
});
```

## Async chunk loading

Load chunks asynchronously and never retain live `Chunk` objects. Deduplicate in-flight requests so the same chunk is not loaded repeatedly:

```java
private final ConcurrentHashMap<ChunkKey, CompletableFuture<Chunk>> inFlight =
        new ConcurrentHashMap<>();

private record ChunkKey(UUID worldId, int x, int z) {}

public CompletableFuture<Chunk> loadChunk(World world, int x, int z) {
    ChunkKey key = new ChunkKey(world.getUID(), x, z);
    return inFlight.computeIfAbsent(key, ignored -> {
        CompletableFuture<Chunk> future = world.getChunkAtAsync(x, z, true, false);
        future.whenComplete((chunk, error) -> inFlight.remove(key, future));
        return future;
    });
}
```

Process only a bounded number of completed chunks per tick to avoid a completion burst turning into one massive tick:

```java
private final Queue<Chunk> completedChunks = new ConcurrentLinkedQueue<>();

public void startProcessor() {
    Bukkit.getScheduler().runTaskTimer(plugin, task -> {
        int budget = 2; // tune experimentally
        while (budget-- > 0) {
            Chunk chunk = completedChunks.poll();
            if (chunk == null) break;
            processChunk(chunk);
        }
    }, 1L, 1L);
}
```

Do not use per-request `ChunkLoadEvent` listeners — they leak unless unregistered on every path. Prefer the returned future, or one global listener routing through a map. Add timeouts and clear all maps/queues on plugin disable and world unload.

## Listener and cache hygiene

- Unregister listeners on plugin disable; never register a listener per request.
- Do not store live `Chunk` or `Player` objects in long-lived caches; store immutable summaries instead.
- If you must keep a chunk loaded, use a deliberate plugin chunk ticket and remove it later.
- Clear caches, in-flight maps, and queues on disable and world unload.

## Paper configuration

- Set `chunk-system.worker-threads: -1` for auto-detected worker threads. Note that `chunk-system.io-threads: -1` means one I/O thread, not auto-detection.
- Use `alternate-current` for redstone and adjust entity/mob spawn limits only when entity-related lag is confirmed.
- Run the recommended Java version: **25 for Paper 26.1+**, **21 for Paper 1.20 through 1.21.11**.

## Verify

```bash
./gradlew build spotlessCheck
./gradlew runServer
```

Load the server with players or a load generator, run `/spark profiler start`, wait 60–180 seconds, stop it, and confirm the flame graph shows your plugin's hot paths rather than main-thread blocking or unbounded listener growth. Confirm `inFlight` and `completedChunks` counts stay bounded under sustained load.

## Common mistakes

| Wrong | Right | Why |
|---|---|---|
| Optimizing without profiling | Spark profile first | Guessing wastes effort and misses the real bottleneck |
| Blocking the main thread | Async scheduler + sync hop | Main-thread blocking drops TPS |
| Calling Bukkit APIs from async tasks | Hop to main thread first | Unsafe cross-thread access corrupts world state |
| One chunk load per request, no dedup | Deduplicating in-flight map | Duplicate loads and callback bursts spike the tick |
| Per-request `ChunkLoadEvent` listener | Future-based load or one global listener | Listeners leak unless unregistered on every path |
| Storing live `Chunk`/`Player` in caches | Store immutable summaries | Retains chunks/entities and leaks memory |
| Processing all completed chunks in one tick | Bounded per-tick budget | A completion burst becomes one massive tick |
| Ignoring chunk tickets | Remove tickets deliberately | Accidental tickets keep chunks loaded forever |
| Profiling an empty server | Profile during peak load | Empty-server data misses real bottlenecks |
