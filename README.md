# Socks Chunk Fix

A tiny Fabric 1.20.1 mixin mod that stops chunks from rendering as **void** when a block entity from
an MCPitanLib-based mod (Item Alchemy's condensers, alchemy chests, collectors, …) is in that chunk.

No world data is changed, nothing is added to the game; it only makes the chunk-data path not throw
and not rebuild the entire registry for every block entity.

## The symptom

Players stand near a base containing an Item Alchemy condenser and everything around it vanishes —
only Distant Horizons LODs or empty sky where the chunks should be. Removing the block, or walking far
enough that the chunk unloads, "fixes" it until the chunk is re-sent. It reproduces in singleplayer
too. Client-side shaders/Iris/DH are irrelevant; older notes blaming a condenser renderer are wrong —
`emc_condenser` has no block entity renderer at all.

## Root cause

```text
ChunkData.BlockEntityData.of                      ← assembling a chunk packet on the server
 └─ CompatBlockEntity#toInitialChunkDataNbt()     ← mcpitanlib override, runs per block entity
     └─ new CompatRegistryLookup()                ← no-arg ctor: builds a brand-new lookup
         └─ BuiltinRegistries.createWrapperLookup()   ← rebuilds EVERY builtin registry
             └─ RegistryBuilder.Registries.throwErrors()
                 java.lang.IllegalStateException: Errors during registry creation
                   Suppressed: Unreferenced key: [...] yttr:wasteland
```

The exception is thrown while `ChunkDataS2CPacket` is being constructed. Vanilla catches it in
`ThreadedAnvilChunkStorage.sendChunkDataPackets` ("Failed to handle packet …, suppressing error"), so
nothing crashes — the **whole chunk packet is simply never sent**, and the client renders an empty
chunk.

The dangling registry key is not a pack defect you can patch with a datapack: with Yttr installed,
`yttr:wasteland` registers and generates normally (`/locate biome yttr:wasteland` finds one), yet
`validateReferences()` still reports it because Yttr's surface rule and `c:` biome tags hold a raw
`RegistryKey` that gets a stand-in entry before the value lands.

A datapack defining the "missing" biome was tested on a fresh world and changed nothing.

## What the mod does

| Mixin | Target | Why |
|---|---|---|
| `RegistryBuilderRegistriesMixin` | `RegistryBuilder$Registries#throwErrors` | Logs the dangling keys instead of aborting registry creation, so the chunk packet can be built. Throttled to 3 warnings per JVM. |
| `BuiltinRegistriesMixin` | `BuiltinRegistries#createWrapperLookup` | Caches the result. Without it every block entity of every chunk rebuilt all builtin registries. |

Measured on a ~110-mod 1.20.1 pack (Item Alchemy 1.4.1, MCPitanLib 4.0.9, Yttr 9.20.1138):

```
Suppressing 1 registry build error(s) … Unreferenced key: … yttr:wasteland
OK: EMCCondenserTile serialized for chunk data in 204298 us (0 NBT keys) - no registry exception
OK: EMCCondenserTile serialized for chunk data in 17 us      ← second call, cache hit
```

## Install

1. Drop `socks-chunkfix-1.0.0.jar` in the **server's** `mods/` folder — the failure happens in chunk
   packet generation, so the server needs it. Restart the server.
2. Install it **client-side too** (same jar, `mods/` in your instance) if you play singleplayer or
   host a LAN world: there the client *is* the server.
3. Nothing to configure, no commands required to play.

Requires: Minecraft 1.20.1, Fabric Loader ≥ 0.15, Java 17. Fabric API is only needed for the optional
diagnostic command below — the two mixins are pure Fabric Loader.

## Diagnostic command

```
/sockschunkfix selftest <x> <y> <z>      (permission level 2)
```

Runs the exact call that used to throw — `toInitialChunkDataNbt()` on the block entity at that
position — and reports the result plus how long it took:

```
OK: EMCCondenserTile serialized for chunk data in 17 us (0 NBT keys) - no registry exception
FAILED: IllegalStateException: Errors during registry creation
```

`FAILED` with that message means the mod is not loaded on the side that owns the world.

## Build from source

```bash
git clone https://github.com/Klannato/socks-chunkfix.git
cd socks-chunkfix
./gradlew build            # JDK 17 required; artifact in build/libs/
```

No local jars or proprietary dependencies — Loom downloads Minecraft 1.20.1 and Yarn mappings on the
first run. Verified by cloning this repo fresh and running the command above.

## Verification, and what it does **not** cover

What was actually run (see `docs/upstream/mcpitanlib-issue.md` for the full log excerpt):

- A dedicated 1.20.1 server started with the full mod list, a fresh world, an
  `itemalchemy:emc_condenser` placed with `setblock`, then `/sockschunkfix selftest` → serialization
  succeeds, 204 ms first call, 17 µs cached.
- Without the mod, the same call throws `IllegalStateException: Errors during registry creation` and
  the chunk packet is dropped — that is the bug being fixed.
- Mixins apply cleanly: no "Mixin apply failed" in the server log; mod list shows `socks_chunkfix`.

Not covered: no automated test suite, no in-game screenshot diff of a formerly-void chunk, and the
fix is only exercised on 1.20.1 Fabric. CI does not run.

## Limitations (honest ones)

- This **suppresses** registry-build errors pack-wide, not just for block entities. If a pack has a
  genuinely broken registry entry, you lose the loud crash that would have told you so — the warning
  in the log is all you get. Prefer fixing the pack when you can.
- The cached lookup comes from `BuiltinRegistries`, which is static, so reuse is safe across worlds,
  but a mod that mutates builtin registries at runtime would not be respected.
- It does not fix the underlying library bug. That is filed upstream:
  [Pitan76/MCPitanLib#245](https://github.com/Pitan76/MCPitanLib/issues/245) — `DynamicRegistryManager`
  already implements `RegistryWrapper.WrapperLookup` on 1.20.1, so MCPitanLib can pass
  `world.getRegistryManager()` instead of rebuilding the registry. When that ships, this mod becomes
  unnecessary.
- Related upstream report: [Pitan76/item-alchemy#135](https://github.com/Pitan76/item-alchemy/issues/135)
  (comment with this analysis). Yttr's side is drafted in `docs/upstream/yttr-issue.md`; Yttr's tracker
  is GitLab (`git.sleeping.town/exa.mods/Yttr`), which needs its own account.

## Uninstall

Delete the jar and restart. Nothing persists — no config, no world data, no state files.

## Layout

```
src/main/java/dev/geo/sockschunkfix/
  SocksChunkFix.java              mod initializer, logger
  SelfTestCommand.java            /sockschunkfix selftest diagnostic
  mixin/RegistryBuilderRegistriesMixin.java   relax throwErrors
  mixin/BuiltinRegistriesMixin.java           cache createWrapperLookup
src/main/resources/
  fabric.mod.json, socks-chunkfix.mixins.json
docs/upstream/                    the issue reports filed/drafted for the real fix
```

## License

MIT — see [LICENSE](LICENSE).
