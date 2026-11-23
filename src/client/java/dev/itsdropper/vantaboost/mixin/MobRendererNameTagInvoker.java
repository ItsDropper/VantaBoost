package dev.itsdropper.vantaboost.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.client.renderer.entity.MobRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Calls the Mob-typed name-tag overload used by MobRenderer during extraction.
 */
@Mixin(MobRenderer.class)
public interface MobRendererNameTagInvoker {
    @Invoker("shouldShowName")
    boolean vantaboost$invokeShouldShowName(Mob entity, double distanceToCameraSq);
}
