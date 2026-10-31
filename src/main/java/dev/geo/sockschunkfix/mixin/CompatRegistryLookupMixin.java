package dev.geo.sockschunkfix.mixin;

import dev.geo.sockschunkfix.RegistryLookupSource;
import dev.geo.sockschunkfix.SocksChunkFix;
import net.minecraft.registry.BuiltinRegistries;
import net.minecraft.registry.RegistryWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Replaces MCPitanLib's registry rebuild with the running server's registry manager.
 *
 * <p>Upstream, {@code CompatRegistryLookup()} calls {@code BuiltinRegistries.createWrapperLookup()},
 * which rebuilds every builtin registry (≈140–205 ms measured) and throws whenever the pack leaves a
 * dangling registry key. When that happens inside {@code ChunkData.BlockEntityData.of}, vanilla
 * suppresses the error and the whole chunk packet is dropped, so the chunk arrives empty on the
 * client.
 *
 * <p>Nothing is suppressed here: with a live server we hand over its {@code DynamicRegistryManager},
 * which is already a {@link RegistryWrapper.WrapperLookup}. With no server running (pure client,
 * world closing) the original call is made and upstream behaviour applies unchanged.
 */
@Mixin(targets = "net.pitan76.mcpitanlib.api.registry.CompatRegistryLookup")
public abstract class CompatRegistryLookupMixin {
    private static final AtomicBoolean SOCKS_CHUNKFIX_LOGGED = new AtomicBoolean();

    @Redirect(
            method = "<init>()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/registry/BuiltinRegistries;createWrapperLookup()Lnet/minecraft/registry/RegistryWrapper$WrapperLookup;"))
    private static RegistryWrapper.WrapperLookup socksChunkFix$useLiveRegistryManager() {
        RegistryWrapper.WrapperLookup live = RegistryLookupSource.current();
        if (live == null) {
            return BuiltinRegistries.createWrapperLookup();
        }

        if (SOCKS_CHUNKFIX_LOGGED.compareAndSet(false, true)) {
            SocksChunkFix.LOGGER.info(
                    "CompatRegistryLookup now uses the live server registry manager "
                            + "(no builtin registry rebuild, no registry errors on this path).");
        }
        return live;
    }
}
