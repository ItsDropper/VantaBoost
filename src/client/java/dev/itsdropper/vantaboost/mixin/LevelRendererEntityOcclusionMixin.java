package dev.itsdropper.vantaboost.mixin;

import dev.itsdropper.vantaboost.optimization.occlusion.EntityOcclusionCuller;
import dev.itsdropper.vantaboost.optimization.occlusion.EntityOcclusionDiagnostics;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Marks the exact main-world entity extraction interval. Other frustum uses
 * (shadow, portal, or auxiliary passes) retain vanilla visibility behavior.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererEntityOcclusionMixin {
    @Inject(
            method = "extractVisibleEntities(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;Lnet/minecraft/client/DeltaTracker;Lnet/minecraft/client/renderer/state/LevelRenderState;)V",
            at = @At("HEAD")
    )
    private void vantaboost$beginMainEntityExtraction(
            Camera camera, Frustum frustum, DeltaTracker deltaTracker,
            LevelRenderState output, CallbackInfo ci
    ) {
        EntityOcclusionCuller.beginMainEntityExtraction(frustum);
    }

    @Inject(
            method = "extractVisibleEntities(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;Lnet/minecraft/client/DeltaTracker;Lnet/minecraft/client/renderer/state/LevelRenderState;)V",
            at = @At("RETURN")
    )
    private void vantaboost$endMainEntityExtraction(
            Camera camera, Frustum frustum, DeltaTracker deltaTracker,
            LevelRenderState output, CallbackInfo ci
    ) {
        EntityOcclusionCuller.endMainEntityExtraction(frustum);
        EntityOcclusionDiagnostics.onMainEntityExtractionFrame();
    }
}
