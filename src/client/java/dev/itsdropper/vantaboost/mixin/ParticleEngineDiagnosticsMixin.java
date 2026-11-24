package dev.itsdropper.vantaboost.mixin;

import dev.itsdropper.vantaboost.optimization.occlusion.ParticleExtractionDiagnostics;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.ParticlesRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Measures the complete vanilla particle extraction interval when diagnostics
 * are explicitly enabled. The disabled path only performs a constant branch.
 */
@Mixin(ParticleEngine.class)
public abstract class ParticleEngineDiagnosticsMixin {
    @Inject(
            method = "extract(Lnet/minecraft/client/renderer/state/ParticlesRenderState;Lnet/minecraft/client/renderer/culling/Frustum;Lnet/minecraft/client/Camera;F)V",
            at = @At("HEAD")
    )
    private void vantaboost$beginParticleExtraction(
            ParticlesRenderState renderState, Frustum frustum,
            Camera camera, float partialTickTime, CallbackInfo ci
    ) {
        ParticleExtractionDiagnostics.beginExtraction();
    }

    @Inject(
            method = "extract(Lnet/minecraft/client/renderer/state/ParticlesRenderState;Lnet/minecraft/client/renderer/culling/Frustum;Lnet/minecraft/client/Camera;F)V",
            at = @At("RETURN")
    )
    private void vantaboost$endParticleExtraction(
            ParticlesRenderState renderState, Frustum frustum,
            Camera camera, float partialTickTime, CallbackInfo ci
    ) {
        ParticleExtractionDiagnostics.endExtraction();
    }
}
