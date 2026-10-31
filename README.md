# Socks Chunk Fix

A tiny Fabric 1.20.1 mixin mod that stops chunks from rendering as **void** when a block entity from
an MCPitanLib-based mod (Item Alchemy's condensers, alchemy chests, collectors, …) is in that chunk.

It does **not** suppress any errors and does not touch world data: it hands MCPitanLib the running
server's registry manager instead of letting it rebuild every builtin registry.

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
`RegistryKey` that gets a stand-in entry before the value lands. A datapack defining the "missing"
biome was tested on a fresh world and changed nothing.

## What the mod does

| Mixin | Target | What it changes |
|---|---|---|
| `CompatRegistryLookupMixin` | `CompatRegistryLookup()` (MCPitanLib) | Redirects the `BuiltinRegistries.createWrapperLookup()` call to the live server's `DynamicRegistryManager` — already a `RegistryWrapper.WrapperLookup` on 1.20.1. No rebuild, nothing thrown, no error swallowed. |
| `MinecraftServerMixin` | `MinecraftServer#<init>` / `#shutdown` | Captures (and releases) the running server so the mixin above can ask for its registry manager. Vanilla-only, no Fabric API needed. |

If no server is running (pure client, world closing) the original call is made and upstream behaviour
applies unchanged — the mod never invents a lookup and never hides a failure.

Measured on a ~110-mod 1.20.1 pack (Item Alchemy 1.4.1, MCPitanLib 4.0.9, Yttr 9.20.1138), first
serialization of a condenser after server start:

```
CompatRegistryLookup now uses the live server registry manager (no builtin registry rebuild, no registry errors on this path).
OK: EMCCondenserTile serialized for chunk data in 145 us (0 NBT keys) - no registry exception
OK: EMCCondenserTile serialized for chunk data in 6 us       ← repeat call
```

Versus **204 298 µs** for the same call on the 1.0.0 build (which relaxed the throw and cached the
builtin lookup): the registry is not built at all any more.

`0 NBT keys` is normal here — Item Alchemy syncs condenser contents through block-entity update
packets (`BLOCK_ENTITY_UPDATE_S2C`), not through initial chunk data.

## Install

1. Drop `socks-chunkfix-1.1.0.jar` in the **server's** `mods/` folder — the failure happens in chunk
   packet generation, so the server needs it. Restart the server.
2. Install it **client-side too** (same jar, `mods/` in your instance) if you play singleplayer or
   host a LAN world: there the client *is* the server.
3. Nothing to configure, no commands required to play.

Requires: Minecraft 1.20.1, Fabric Loader ≥ 0.15, Java 17. Fabric API is only needed for the optional
diagnostic command below — both mixins are pure Fabric Loader.

## Diagnostic command

```
/sockschunkfix selftest <x> <y> <z>      (permission level 2)
```

Runs the exact call that used to throw — `toInitialChunkDataNbt()` on the block entity at that
position — and reports the result plus how long it took:

```
OK: EMCCondenserTile serialized for chunk data in 6 us (0 NBT keys) - no registry exception
FAILED: IllegalStateException: Errors during registry creation
```

`FAILED` with that message means the mod is not loaded on the side that owns the world.

## Build from source

```bash
git clone https://github.com/Klannato/socks-chunkfix.git
cd socks-chunkfix
./gradlew build            # JDK 17 required; artifact in build/libs/
```

No local jars and no proprietary dependencies — Loom downloads Minecraft 1.20.1 and Yarn mappings on
the first run. Verified by cloning this repo fresh and running the command above.

The build disables the legacy Mixin annotation processor (`loom.mixin.useLegacyMixinAp = false`)
because `CompatRegistryLookupMixin` targets a mod class that is deliberately **not** on the compile
classpath; Loom remaps the `@At` targets itself, so the jar carries no refmap and no MCPitanLib
dependency.

## Verification, and what it does **not** cover

What was actually run on a dedicated 1.20.1 server with the full mod list:

- Fresh world, `itemalchemy:emc_condenser` placed with `setblock`, then `/sockschunkfix selftest` →
  serialization succeeds in 145 µs, repeat 6 µs, log shows the live-lookup line once and **zero**
  "Suppressing" warnings (nothing is swallowed).
- Without the mod the same call throws `IllegalStateException: Errors during registry creation` and
  the chunk packet is dropped — that is the bug being fixed (stack trace in
  `docs/upstream/mcpitanlib-issue.md`).
- Mixins apply cleanly: no "Mixin apply failed" in the log; mod list shows `socks_chunkfix 1.1.0`.
- 1.0.0 (the earlier relax-and-cache build) was verified the same way; 1.1.0 removes the error
  suppression entirely.

Not covered: no automated test suite, no in-game screenshot diff of a formerly-void chunk, and the
fix is only exercised on 1.20.1 Fabric. CI does not run.

## Limitations (honest ones)

- Only MCPitanLib's `CompatRegistryLookup` is fixed. Another mod that calls
  `BuiltinRegistries.createWrapperLookup()` at runtime with a dangling-key pack will still throw —
  this mod no longer hides that, deliberately.
- It needs a running server at the moment of the call. On a pure client there is none, and upstream
  behaviour applies (which is fine: initial chunk data is only built server-side).
- It does not fix the underlying library bug. That is filed upstream:
  [Pitan76/MCPitanLib#245](https://github.com/Pitan76/MCPitanLib/issues/245) — when MCPitanLib passes
  `world.getRegistryManager()` itself, this mod becomes unnecessary.
- Related upstream report: [Pitan76/item-alchemy#135](https://github.com/Pitan76/item-alchemy/issues/135)
  (comment with this analysis). Yttr's side is drafted in `docs/upstream/yttr-issue.md`; Yttr's tracker
  is GitLab (`git.sleeping.town/exa.mods/Yttr`), which needs its own account.

## Changelog

- **1.1.0** — proper fix: registry lookup comes from the live server; the 1.0.0 error suppression and
  builtin-lookup cache are gone.
- **1.0.0** — first working build: logged instead of throwing in
  `RegistryBuilder$Registries#throwErrors` and cached `BuiltinRegistries#createWrapperLookup`.

## Uninstall

Delete the jar and restart. Nothing persists — no config, no world data, no state files.

## Layout

```
src/main/java/dev/geo/sockschunkfix/
  SocksChunkFix.java                          mod initializer, logger
  RegistryLookupSource.java                   holds the live server's registry manager
  SelfTestCommand.java                        /sockschunkfix selftest diagnostic
  mixin/CompatRegistryLookupMixin.java        redirect the rebuild to the live registry manager
  mixin/MinecraftServerMixin.java             capture/release the running server
src/main/resources/
  fabric.mod.json, socks-chunkfix.mixins.json
docs/upstream/                                the issue reports filed/drafted for the real fix
```

## License

MIT — see [LICENSE](LICENSE).
