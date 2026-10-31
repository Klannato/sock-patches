package dev.geo.sockschunkfix.mixin;

import dev.geo.sockschunkfix.SocksChunkFix;
import net.minecraft.registry.BuiltinRegistries;
import net.minecraft.registry.RegistryWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@code BuiltinRegistries.createWrapperLookup()} rebuilds every builtin registry from scratch on
 * each call. MCPitanLib calls it once per block entity while building chunk data, so a 16x16 chunk
 * with a few condensers rebuilt the entire registry dozens of times per tick.
 *
 * <p>This caches the lookup: it is built from static builtin registries, so the instance is
 * interchangeable between calls and safe to reuse across worlds.
 */
@Mixin(BuiltinRegistries.class)
public class BuiltinRegistriesMixin {
    @Unique
    private static volatile RegistryWrapper.WrapperLookup socksChunkFix$cachedLookup;
    @Unique
    private static boolean socksChunkFix$loggedCacheHit;

    @Inject(method = "createWrapperLookup", at = @At("HEAD"), cancellable = true)
    private static void socksChunkFix$reuseLookup(CallbackInfoReturnable<RegistryWrapper.WrapperLookup> cir) {
        RegistryWrapper.WrapperLookup cached = socksChunkFix$cachedLookup;
        if (cached != null) {
            if (!socksChunkFix$loggedCacheHit) {
                socksChunkFix$loggedCacheHit = true;
                SocksChunkFix.LOGGER.info("Reusing cached builtin registry lookup instead of rebuilding it.");
            }
            cir.setReturnValue(cached);
        }
    }

    @Inject(method = "createWrapperLookup", at = @At("RETURN"))
    private static void socksChunkFix$storeLookup(CallbackInfoReturnable<RegistryWrapper.WrapperLookup> cir) {
        RegistryWrapper.WrapperLookup built = cir.getReturnValue();
        if (built != null) {
            socksChunkFix$cachedLookup = built;
        }
    }
}
