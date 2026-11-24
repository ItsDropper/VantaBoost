package dev.itsdropper.vantaboost.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.itsdropper.vantaboost.optimization.occlusion.EntityOcclusionCuller;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Wraps the first entity-bounds frustum test without replacing it. MixinExtras
 * allows this optimization to compose with renderer mods such as VulkanMod
 * that also wrap or redirect the same vanilla call.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererOcclusionMixin {
    @Shadow
    @Final
    protected EntityRenderDispatcher entityRenderDispatcher;

    @Shadow
    protected float shadowRadius;

    @Shadow
    protected float shadowStrength;

    @Shadow
    protected abstract boolean shouldShowName(Entity entity, double distanceToCameraSq);

    @WrapOperation(
            method = "shouldRender(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/culling/Frustum;DDD)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/culling/Frustum;isVisible(Lnet/minecraft/world/phys/AABB;)Z",
                    ordinal = 0
            )
    )
    private boolean vantaboost$applyEntityOcclusion(
            Frustum frustum,
            AABB bounds,
            Operation<Boolean> original,
            Entity entity,
            Frustum originalFrustum,
            double cameraX,
            double cameraY,
            double cameraZ
    ) {
        if (bounds == null) {
            return true;
        }

        // Always preserve the wrapped operation. It may include another
        // renderer's visibility logic (for example VulkanMod's section test).
        boolean vanillaVisible = original.call(frustum, bounds);
        if (!vanillaVisible
                || !EntityOcclusionCuller.isEnabled()
                || frustum != originalFrustum
                || !Double.isFinite(cameraX) || !Double.isFinite(cameraY)
                || !Double.isFinite(cameraZ)
                || !EntityOcclusionCuller.isMainEntityExtraction(frustum)
                || entity == entityRenderDispatcher.crosshairPickEntity
                || !this.getClass().getName().startsWith("net.minecraft.client.renderer.entity.")) {
            return vanillaVisible;
        }

        // shouldShowName is overloaded by LivingEntityRenderer and MobRenderer;
        // calling the Entity-typed base overload would miss those name-tag rules.
        if (entityRenderDispatcher.camera == null
                || entity instanceof LivingEntity && Minecraft.getInstance().player == null) {
            return vanillaVisible;
        }

        double distanceToCameraSq = entityRenderDispatcher.distanceToSqr(entity);
        boolean rendererShowsName;
        if (entity instanceof Mob mob
                && (Object) this instanceof MobRendererNameTagInvoker mobInvoker) {
            rendererShowsName = mobInvoker.vantaboost$invokeShouldShowName(
                    mob, distanceToCameraSq);
        } else if (entity instanceof LivingEntity livingEntity
                && (Object) this instanceof LivingEntityRendererNameTagInvoker livingInvoker) {
            rendererShowsName = livingInvoker.vantaboost$invokeShouldShowName(
                    livingEntity, distanceToCameraSq);
        } else {
            rendererShowsName = this.shouldShowName(entity, distanceToCameraSq);
        }

        if (rendererShowsName) {
            return vanillaVisible;
        }

        return !EntityOcclusionCuller.shouldCull(
                entity, bounds,
                frustum.getCamX(), frustum.getCamY(), frustum.getCamZ(),
                shadowRadius, shadowStrength, this.getClass());
    }
}
