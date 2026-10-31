# MCPitanLib issue draft — void chunks (filed upstream)

Title: `CompatBlockEntity#toInitialChunkDataNbt` rebuilds all builtin registries per call, throws on packs with dangling registry refs, and voids chunks

---

## Summary

On Fabric 1.20.1 with MCPitanLib 4.0.9, any mod that extends `CompatBlockEntity` (e.g. Item Alchemy
1.4.1's condensers / alchemy chests / collectors) can make chunks render as void for players.

`CompatBlockEntity#toInitialChunkDataNbt()` creates `new CompatRegistryLookup()`, whose no-arg
constructor calls `BuiltinRegistries.createWrapperLookup()`. That rebuilds **every builtin registry
from scratch**, and it throws `IllegalStateException: Errors during registry creation` on any pack
that has a dangling registry reference — which is common in modpacks (with Yttr installed,
`worldgen/biome / yttr:wasteland` always trips it, even though the biome exists and generates).

The call happens inside `ChunkData.BlockEntityData.of(...)`, i.e. while a `ChunkDataS2CPacket` is
being constructed. Vanilla catches and suppresses the failure in `ThreadedAnvilChunkStorage`, so
nothing crashes — the **whole chunk packet is simply never sent**, and the client renders an empty
chunk. From the player's side that looks like "void/lod-only chunks around my base", and it repeats
every time the chunk is re-sent (rejoin, walking away and back).

## Environment

- Minecraft 1.20.1, Fabric Loader 0.19.x, Java 17
- MCPitanLib 4.0.9-1.20.1-fabric
- Item Alchemy 1.4.1 (extends `CompatBlockEntity` for `emc_condenser`, `emc_condenser_mk2`,
  alchemy chest, collector)
- Dedicated server; also reproducible in singleplayer (integrated server)

## Stack trace (server log)

```
[Server thread/ERROR]: Failed to handle packet ..., suppressing error
java.lang.IllegalStateException: Errors during registry creation
    at RegistryBuilder$Registries.throwErrors(RegistryBuilder.java:137)
    at RegistryBuilder.createWrapperLookup(RegistryBuilder.java:269)
    at BuiltinRegistries.createWrapperLookup(BuiltinRegistries.java:96)
    at net.pitan76.mcpitanlib.api.registry.CompatRegistryLookup.<init>(CompatRegistryLookup.java:20)
    at net.pitan76.mcpitanlib.api.tile.CompatBlockEntity.method_16887(CompatBlockEntity.java:70)
    at ChunkData$BlockEntityData.of(ChunkData.java:152)
    at ChunkData.<init>(ChunkData.java:44)
    at ChunkDataS2CPacket.<init>(ChunkDataS2CPacket.java:24)
    at ThreadedAnvilChunkStorage.sendChunkDataPackets(ThreadedAnvilChunkStorage.java:1304)
    ...
    Suppressed: java.lang.IllegalStateException: Unreferenced key: ResourceKey[minecraft:worldgen/biome / yttr:wasteland]
```

## Steps to reproduce

1. Fabric 1.20.1 server with MCPitanLib 4.0.9, Item Alchemy 1.4.1 and Yttr 9.20.1138 (any mod that
   leaves a dangling registry key works).
2. Place an EMC condenser, walk ~200 blocks away and come back (or reconnect).
3. The chunk(s) containing the condenser arrive empty on the client; the log shows the trace above.

## Performance problem, separate from the exception

Even without any registry error the rebuild is expensive: measured **~140–205 ms for the first call**
on a large modpack, and it runs once per block entity per chunk send. Caching the result dropped a
repeat call to **17 µs** in the same JVM.

## Suggested fix

`DynamicRegistryManager` implements `RegistryWrapper.WrapperLookup` on 1.20.1 and
`World#getRegistryManager()` is already available on a loaded block entity, so the rebuild is
unnecessary:

```java
public NbtCompound toInitialChunkDataNbt(CompatRegistryLookup lookup) {
    World world = this.getWorld();
    if (world != null) {
        lookup = new CompatRegistryLookup(world.getRegistryManager()); // no rebuild, never throws
    }
    return super.toInitialChunkDataNbt(lookup.getRegistryLookup());
}
```

If the transient (unloaded) case must keep the builtin fallback, caching
`BuiltinRegistries.createWrapperLookup()` in a static field would at least remove the per-call cost —
but it does not avoid the `throwErrors()` failure, so the world lookup is preferable.

## Interim workaround used in the affected pack

A small mixin mod that (a) logs instead of throwing in
`RegistryBuilder$Registries#throwErrors` and (b) caches
`BuiltinRegistries#createWrapperLookup`. Chunks load correctly again; happy to share the source if
useful.
