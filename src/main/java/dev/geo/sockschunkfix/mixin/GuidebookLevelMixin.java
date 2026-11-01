package dev.geo.sockschunkfix.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fixes a crash when opening the AE2 Guidebook while Starlight + Indium are installed.
 *
 * Starlight's ExtendedWorld interface declares getAnyChunkImmediately(int, int), but
 * AE2's GuidebookLevel (a fake level used for rendering guidebook scenes) never
 * implements it. When Indium tries to compute lighting for a block in the guidebook
 * preview, it calls through to Starlight's getAnyChunkNow, which dispatches to the
 * unimplemented interface method and throws AbstractMethodError.
 *
 * This mixin adds a default (null) implementation so the call returns null instead
 * of crashing. Indium/Sodium handle a null chunk gracefully (no lighting data).
 */
@Mixin(targets = "appeng.client.guidebook.scene.level.GuidebookLevel", remap = false)
public class GuidebookLevelMixin {

    /**
     * Starlight's ExtendedWorld#getAnyChunkImmediately(int, int).
     * Return null — there are no real chunks in a guidebook preview level.
     */
    public net.minecraft.world.chunk.Chunk getAnyChunkImmediately(int chunkX, int chunkZ) {
        return null;
    }
}