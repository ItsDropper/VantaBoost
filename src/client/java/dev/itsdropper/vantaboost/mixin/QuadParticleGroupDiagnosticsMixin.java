package dev.itsdropper.vantaboost.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.itsdropper.vantaboost.optimization.occlusion.ParticleExtractionDiagnostics;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.QuadParticleGroup;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.ParticleGroupRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Counts vanilla quad-particle frustum decisions without changing their result.
 */
@Mixin(QuadParticleGroup.class)
public abstract class QuadParticleGroupDiagnosticsMixin {
    @Inject(
            method = "extractRenderState(Lnet/minecraft/client/renderer/culling/Frustum;Lnet/minecraft/client/Camera;F)Lnet/minecraft/client/renderer/state/ParticleGroupRenderState;",
            at = @At("HEAD")
    )
    private void vantaboost$beginQuadParticleExtraction(
            Frustum frustum, Camera camera, float partialTickTime,
            CallbackInfoReturnable<ParticleGroupRenderState> cir
    ) {
        ParticleExtractionDiagnostics.beginQuadGroup();
    }

    @Inject(
            method = "extractRenderState(Lnet/minecraft/client/renderer/culling/Frustum;Lnet/minecraft/client/Camera;F)Lnet/minecraft/client/renderer/state/ParticleGroupRenderState;",
            at = @At("RETURN")
    )
    private void vantaboost$endQuadParticleExtraction(
            Frustum frustum, Camera camera, float partialTickTime,
            CallbackInfoReturnable<ParticleGroupRenderState> cir
    ) {
        ParticleExtractionDiagnostics.endQuadGroup();
    }

    @ModifyExpressionValue(
            method = "extractRenderState(Lnet/minecraft/client/renderer/culling/Frustum;Lnet/minecraft/client/Camera;F)Lnet/minecraft/client/renderer/state/ParticleGroupRenderState;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/culling/Frustum;pointInFrustum(DDD)Z"
            )
    )
    private boolean vantaboost$recordParticleFrustumResult(boolean vanillaVisible) {
        return ParticleExtractionDiagnostics.recordFrustumResult(vanillaVisible);
    }
}
