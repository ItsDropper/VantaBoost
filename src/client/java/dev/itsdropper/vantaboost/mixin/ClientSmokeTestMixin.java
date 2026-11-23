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

            if (!LivingEntityRendererNameTagInvoker.class.isAssignableFrom(
                    Class.forName("net.minecraft.client.renderer.entity.LivingEntityRenderer", false, classLoader))
                    || !MobRendererNameTagInvoker.class.isAssignableFrom(
                    Class.forName("net.minecraft.client.renderer.entity.MobRenderer", false, classLoader))) {
                throw new IllegalStateException("Renderer name-tag invokers were not applied");
            }
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Could not load VantaBoost mixin smoke-test targets", exception);
        }

        ((Minecraft) (Object) this).stop();
    }
}
