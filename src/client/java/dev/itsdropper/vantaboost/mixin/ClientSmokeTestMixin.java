package dev.itsdropper.vantaboost.mixin;

import net.minecraft.client.Minecraft;
import dev.itsdropper.vantaboost.optimization.occlusion.OcclusionFeaturePolicy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * CI-only startup probe. It is inert in normal launches and exits after forcing
 * the optimization targets and renderer name-tag invokers through the Mixin transformer.
 */
@Mixin(Minecraft.class)
public abstract class ClientSmokeTestMixin {
    @Unique
    private boolean vantaboost$smokeChecked;

    @Inject(method = "tick()V", at = @At("TAIL"))
    private void vantaboost$runClientMixinSmokeTest(CallbackInfo ci) {
        if (vantaboost$smokeChecked) {
            return;
        }
        vantaboost$smokeChecked = true;

        if (!Boolean.getBoolean("vantaboost.ciSmoke")) {
            return;
        }

        ClassLoader classLoader = Minecraft.class.getClassLoader();
        try {
            Class.forName("net.minecraft.client.renderer.entity.EntityRenderer", false, classLoader);
            Class.forName("net.minecraft.client.renderer.entity.LivingEntityRenderer", false, classLoader);
            Class.forName("net.minecraft.client.renderer.entity.MobRenderer", false, classLoader);
            Class.forName("net.minecraft.client.multiplayer.ClientLevel", false, classLoader);
            Class.forName("net.minecraft.client.renderer.LevelRenderer", false, classLoader);
            Class.forName("net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher", false, classLoader);
            Class.forName("net.minecraft.client.particle.ParticleEngine", false, classLoader);
            Class.forName("net.minecraft.client.particle.QuadParticleGroup", false, classLoader);

            Class<?> livingRenderer = Class.forName(
                    "net.minecraft.client.renderer.entity.LivingEntityRenderer", false, classLoader);
            Class<?> mobRenderer = Class.forName(
                    "net.minecraft.client.renderer.entity.MobRenderer", false, classLoader);
            Class<?> entityRenderer = Class.forName(
                    "net.minecraft.client.renderer.entity.EntityRenderer", false, classLoader);
            Class<?> clientLevel = Class.forName(
                    "net.minecraft.client.multiplayer.ClientLevel", false, classLoader);
            Class<?> blockEntityDispatcher = Class.forName(
                    "net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher", false, classLoader);
            Class<?> levelRenderer = Class.forName(
                    "net.minecraft.client.renderer.LevelRenderer", false, classLoader);
            Class<?> particleEngine = Class.forName(
                    "net.minecraft.client.particle.ParticleEngine", false, classLoader);
            Class<?> quadParticleGroup = Class.forName(
                    "net.minecraft.client.particle.QuadParticleGroup", false, classLoader);

            boolean entityOcclusionEnabled = OcclusionFeaturePolicy.isEnabled(
                    Boolean.getBoolean("vantaboost.enableEntityOcclusion"),
                    Boolean.getBoolean("vantaboost.disableEntityOcclusion"));
            boolean blockEntityOcclusionEnabled = OcclusionFeaturePolicy.isEnabled(
                    Boolean.getBoolean("vantaboost.enableBlockEntityOcclusion"),
                    Boolean.getBoolean("vantaboost.disableBlockEntityOcclusion"));
            boolean particleProfilerEnabled = Boolean.getBoolean("vantaboost.profileParticleExtraction");
            boolean anyOcclusionEnabled = entityOcclusionEnabled || blockEntityOcclusionEnabled;

            if (LivingEntityRendererNameTagInvoker.class.isAssignableFrom(livingRenderer)
                    != entityOcclusionEnabled
                    || MobRendererNameTagInvoker.class.isAssignableFrom(mobRenderer)
                    != entityOcclusionEnabled) {
                throw new IllegalStateException("Entity name-tag invoker application did not match feature state");
            }

            assertHandler(entityRenderer, "vantaboost$applyEntityOcclusion", entityOcclusionEnabled);
            assertHandler(clientLevel, "vantaboost$invalidateAfterBlockChange", anyOcclusionEnabled);
            assertHandler(clientLevel, "vantaboost$invalidateAfterVerifiedBlockState", anyOcclusionEnabled);
            assertHandler(clientLevel, "vantaboost$invalidateAfterPredictedBlockState", anyOcclusionEnabled);
            assertHandler(clientLevel, "vantaboost$invalidateAfterSectionActivation", anyOcclusionEnabled);
            assertHandler(clientLevel, "vantaboost$invalidateAfterChunkLoad", anyOcclusionEnabled);
            assertHandler(clientLevel, "vantaboost$invalidateAfterChunkUnload", anyOcclusionEnabled);
            assertHandler(blockEntityDispatcher, "vantaboost$applyBlockEntityOcclusion",
                    blockEntityOcclusionEnabled);
            assertHandler(levelRenderer, "vantaboost$beginMainEntityExtraction", entityOcclusionEnabled);
            assertHandler(levelRenderer, "vantaboost$endMainEntityExtraction", entityOcclusionEnabled);
            assertHandler(levelRenderer, "vantaboost$beginBlockEntityExtraction", blockEntityOcclusionEnabled);
            assertHandler(levelRenderer, "vantaboost$endBlockEntityExtraction", blockEntityOcclusionEnabled);
            assertHandler(particleEngine, "vantaboost$beginParticleExtraction", particleProfilerEnabled);
            assertHandler(particleEngine, "vantaboost$endParticleExtraction", particleProfilerEnabled);
            assertHandler(quadParticleGroup, "vantaboost$beginQuadParticleExtraction", particleProfilerEnabled);
            assertHandler(quadParticleGroup, "vantaboost$endQuadParticleExtraction", particleProfilerEnabled);
            assertHandler(quadParticleGroup, "vantaboost$recordParticleFrustumResult", particleProfilerEnabled);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Could not load VantaBoost mixin smoke-test targets", exception);
        }

        ((Minecraft) (Object) this).stop();
    }

    @Unique
    private static void assertHandler(Class<?> target, String handlerName, boolean expected) {
        for (var method : target.getDeclaredMethods()) {
            if (method.getName().contains(handlerName)) {
                if (!expected) {
                    throw new IllegalStateException(
                            "Disabled VantaBoost mixin handler was still merged into "
                                    + target.getName() + ": " + handlerName);
                }
                return;
            }
        }
        if (expected) {
            throw new IllegalStateException(
                    "Expected VantaBoost mixin handler was not merged into " + target.getName()
                            + ": " + handlerName);
        }
    }
}
