package dev.itsdropper.vantaboost.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import dev.itsdropper.vantaboost.optimization.occlusion.BlockEntityOcclusionCuller;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Brackets the main visible-block-entity extraction pass, including exceptional
 * exits. Shadow and auxiliary renderer passes remain untouched.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererBlockEntityOcclusionMixin {
    @WrapMethod(
            method = "extractVisibleBlockEntities(Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/state/LevelRenderState;)V"
    )
    private void vantaboost$trackBlockEntityExtraction(
            Camera camera, float partialTick, LevelRenderState renderState,
            Operation<Void> original
    ) {
        boolean completed = false;
        try {
            BlockEntityOcclusionCuller.beginExtraction(camera.position());
            original.call(camera, partialTick, renderState);
            completed = true;
        } finally {
            BlockEntityOcclusionCuller.endExtraction(completed);
        }
    }
}
