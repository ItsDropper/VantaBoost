package dev.itsdropper.vantaboost.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import dev.itsdropper.vantaboost.optimization.occlusion.EntityOcclusionCuller;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Brackets the exact main-world entity extraction interval, including exceptional
 * exits. Other frustum uses (shadow, portal, or auxiliary passes) remain untouched.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererEntityOcclusionMixin {
    @WrapMethod(
            method = "extractVisibleEntities(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;Lnet/minecraft/client/DeltaTracker;Lnet/minecraft/client/renderer/state/LevelRenderState;)V"
    )
    private void vantaboost$trackMainEntityExtraction(
            Camera camera, Frustum frustum, DeltaTracker deltaTracker,
            LevelRenderState output, Operation<Void> original
    ) {
        boolean completed = false;
        try {
            EntityOcclusionCuller.beginMainEntityExtraction(frustum);
            original.call(camera, frustum, deltaTracker, output);
            completed = true;
        } finally {
            EntityOcclusionCuller.endMainEntityExtraction(frustum, completed);
        }
    }
}
