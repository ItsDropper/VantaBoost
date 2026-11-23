package dev.itsdropper.vantaboost.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;

/**
 * Calls the LivingEntity-typed name-tag overload used during render-state extraction.
 */
@Mixin(LivingEntityRenderer.class)
public interface LivingEntityRendererNameTagInvoker {
    @Invoker("shouldShowName")
    boolean vantaboost$invokeShouldShowName(LivingEntity entity, double distanceToCameraSq);
}
