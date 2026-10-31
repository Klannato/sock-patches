package dev.geo.sockschunkfix.mixin;

import dev.geo.sockschunkfix.SocksChunkFix;
import net.minecraft.registry.RegistryBuilder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stops registry-building from hard-failing the whole operation when a datapack/pack leaves
 * dangling keys behind (e.g. unreferenced biome keys such as {@code yttr:wasteland}).
 *
 * <p>Vanilla {@code RegistryBuilder.Registries#throwErrors} is the single choke point: any
 * consumer that rebuilds a wrapper lookup at runtime - notably MCPitanLib's
 * {@code CompatRegistryLookup} - dies here, and if that happens while a chunk packet is being
 * built the packet is silently dropped and the client renders void chunks.
 */
@Mixin(targets = "net.minecraft.registry.RegistryBuilder$Registries")
public abstract class RegistryBuilderRegistriesMixin {
    @Shadow
    @Final
    private List<?> errors;

    private static final AtomicInteger SOCKS_CHUNKFIX_REPORTED = new AtomicInteger();

    @Inject(method = "throwErrors", at = @At("HEAD"), cancellable = true)
    private void socksChunkFix$relaxRegistryErrors(CallbackInfo ci) {
        if (errors == null || errors.isEmpty()) {
            return;
        }

        int seen = SOCKS_CHUNKFIX_REPORTED.incrementAndGet();
        if (seen <= 3) {
            SocksChunkFix.LOGGER.warn(
                    "Suppressing {} registry build error(s) instead of aborting the whole registry "
                            + "(first one: {}). Dangling registry keys should be fixed in the datapack/pack; "
                            + "this mod only keeps chunk data flowing until then.",
                    errors.size(), String.valueOf(errors.get(0)));
        } else if (seen == 4) {
            SocksChunkFix.LOGGER.warn("Further registry build errors suppressed silently.");
        }

        ci.cancel();
    }
}
