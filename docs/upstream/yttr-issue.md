# Yttr issue draft — dangling `yttr:wasteland` registry key (GitLab, not filed)

Yttr's live tracker is https://git.sleeping.town/exa.mods/Yttr/-/issues (the GitHub repo
`exaskye/Yttr` is archived since 2022). Posting needs a login on that GitLab instance, so this one is
drafted only.

Title: `RegistryBuilder.validateReferences always reports "Unreferenced key: worldgen/biome / yttr:wasteland"`

---

## Summary

In Yttr 9.20.1138 on 1.20.1, every registry rebuild that goes through
`RegistryBuilder.Registries.validateReferences()` reports:

```
java.lang.IllegalStateException: Unreferenced key: ResourceKey[minecraft:worldgen/biome / yttr:wasteland]
```

The biome itself is fine: `data/yttr/worldgen/biome/wasteland.json` ships in the jar, it registers,
and `/locate biome yttr:wasteland` finds one (`The nearest yttr:wasteland is at [672, 136, 640]` in a
freshly generated world). Adding a datapack that defines the biome changes nothing.

## Likely cause

`validateReferences()` lists every key that has a stand-alone placeholder entry in
`StandAloneEntryCreatingLookup.keysToEntries`. Yttr references the biome key from code and data before
the value registers in the same build:

- `diy.y2k.yttr.init.content.YBiomes#WASTELAND` (`RegistryKey<Biome>`) is used by
  `YBiomes.WASTELAND_RULE` (a surface rule)
- `data/c/tags/worldgen/biome/wasteland.json`, `climate_dry.json`, `dead.json` all list
  `yttr:wasteland`

So a placeholder is created for the key and the later value registration does not clear it.

## Why it matters

The error itself is harmless for vanilla, but any third-party mod that calls
`BuiltinRegistries.createWrapperLookup()` at runtime now throws and aborts whatever it was doing. In
our pack it hit MCPitanLib's `CompatRegistryLookup`, which runs while chunk packets are being built —
the exception dropped the whole `ChunkDataS2CPacket`, so every chunk containing an Item Alchemy block
entity rendered as void for players until we patched the registry path.

The clean fix on Yttr's side would be to resolve the biome entry through the registry lookup at use
time instead of holding a raw `RegistryKey` that gets looked up during registry construction (or to
register the biome before its consumers reference it).
