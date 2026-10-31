package dev.geo.sockschunkfix.mixin;

import dev.geo.sockschunkfix.RegistryLookupSource;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures the running server so {@link dev.geo.sockschunkfix.RegistryLookupSource} can hand its
 * registry manager to MCPitanLib. Deliberately vanilla-only, so no Fabric API is required.
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void socksChunkFix$captureServer(CallbackInfo ci) {
        RegistryLookupSource.setServer((MinecraftServer) (Object) this);
    }

    @Inject(method = "shutdown", at = @At("HEAD"))
    private void socksChunkFix$releaseServer(CallbackInfo ci) {
        RegistryLookupSource.clearServer();
    }
}
