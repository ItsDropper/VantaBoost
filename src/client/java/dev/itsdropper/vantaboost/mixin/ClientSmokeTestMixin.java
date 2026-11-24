package dev.itsdropper.vantaboost.mixin;

import net.minecraft.client.Minecraft;
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

            if (!LivingEntityRendererNameTagInvoker.class.isAssignableFrom(livingRenderer)
                    || !MobRendererNameTagInvoker.class.isAssignableFrom(mobRenderer)) {
                throw new IllegalStateException("Renderer name-tag invokers were not applied");
            }

            assertMergedHandler(entityRenderer, "vantaboost$applyEntityOcclusion");
            assertMergedHandler(clientLevel, "vantaboost$invalidateAfterBlockChange");
            assertMergedHandler(clientLevel, "vantaboost$invalidateAfterVerifiedBlockState");
            assertMergedHandler(clientLevel, "vantaboost$invalidateAfterPredictedBlockState");
            assertMergedHandler(clientLevel, "vantaboost$invalidateAfterSectionActivation");
            assertMergedHandler(clientLevel, "vantaboost$invalidateAfterChunkLoad");
            assertMergedHandler(clientLevel, "vantaboost$invalidateAfterChunkUnload");
            assertMergedHandler(blockEntityDispatcher, "vantaboost$applyBlockEntityOcclusion");
            assertMergedHandler(levelRenderer, "vantaboost$beginMainEntityExtraction");
            assertMergedHandler(levelRenderer, "vantaboost$endMainEntityExtraction");
            assertMergedHandler(levelRenderer, "vantaboost$beginBlockEntityExtraction");
            assertMergedHandler(levelRenderer, "vantaboost$endBlockEntityExtraction");
            assertMergedHandler(particleEngine, "vantaboost$beginParticleExtraction");
            assertMergedHandler(particleEngine, "vantaboost$endParticleExtraction");
            assertMergedHandler(quadParticleGroup, "vantaboost$beginQuadParticleExtraction");
            assertMergedHandler(quadParticleGroup, "vantaboost$endQuadParticleExtraction");
            assertMergedHandler(quadParticleGroup, "vantaboost$recordParticleFrustumResult");
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Could not load VantaBoost mixin smoke-test targets", exception);
        }

        ((Minecraft) (Object) this).stop();
    }

    @Unique
    private static void assertMergedHandler(Class<?> target, String handlerName) {
        for (var method : target.getDeclaredMethods()) {
            if (method.getName().contains(handlerName)) {
                return;
            }
        }
        throw new IllegalStateException(
                "Expected VantaBoost mixin handler was not merged into " + target.getName()
                        + ": " + handlerName);
    }
}
