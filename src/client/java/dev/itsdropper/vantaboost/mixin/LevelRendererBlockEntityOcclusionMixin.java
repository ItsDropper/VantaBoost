package dev.itsdropper.vantaboost.mixin;

import dev.itsdropper.vantaboost.optimization.occlusion.BlockEntityOcclusionCuller;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Limits block-entity occlusion checks to the main visible-block-entity
 * extraction pass. Shadow and auxiliary renderer passes remain untouched.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererBlockEntityOcclusionMixin {
    @Inject(
            method = "extractVisibleBlockEntities(Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/state/LevelRenderState;)V",
            at = @At("HEAD")
    )
    private void vantaboost$beginBlockEntityExtraction(
            Camera camera, float partialTick, LevelRenderState renderState, CallbackInfo ci
    ) {
        BlockEntityOcclusionCuller.beginExtraction(camera.position());
    }

    @Inject(
            method = "extractVisibleBlockEntities(Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/state/LevelRenderState;)V",
            at = @At("RETURN")
    )
    private void vantaboost$endBlockEntityExtraction(
            Camera camera, float partialTick, LevelRenderState renderState, CallbackInfo ci
    ) {
        BlockEntityOcclusionCuller.endExtraction();
    }
}
