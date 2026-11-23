package dev.itsdropper.vantaboost.mixin;

import dev.itsdropper.vantaboost.optimization.occlusion.EntityOcclusionCuller;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Applies occlusion only after vanilla has accepted the entity's normal
 * distance/frustum checks. Custom renderers and renderers opting out of culling
 * are left untouched.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererOcclusionMixin<T extends Entity> {
    @Shadow
    @Final
    protected EntityRenderDispatcher entityRenderDispatcher;

    @Shadow
    protected float shadowRadius;

    @Shadow
    protected abstract boolean affectedByCulling(T entity);

    @Shadow
    protected abstract AABB getBoundingBoxForCulling(T entity);

    @Inject(
            method = "shouldRender(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/culling/Frustum;DDD)Z",
            at = @At("RETURN"),
            cancellable = true
    )
    private void vantaboost$applyEntityOcclusion(
            T entity,
            net.minecraft.client.renderer.culling.Frustum frustum,
            double cameraX,
            double cameraY,
            double cameraZ,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!cir.getReturnValueZ()
                || !affectedByCulling(entity)
                || !this.getClass().getName().startsWith("net.minecraft.client.renderer.entity.")) {
            return;
        }

        if (entity == entityRenderDispatcher.crosshairPickEntity) {
            return;
        }

        AABB bounds = getBoundingBoxForCulling(entity);
        if (shadowRadius > 0.0F) {
            // Include the vanilla ground-shadow footprint in the occlusion proof.
            bounds = bounds.inflate(shadowRadius, 0.0, shadowRadius);
        }
        if (EntityOcclusionCuller.shouldCull(
                entity, bounds, entityRenderDispatcher.camera.getPosition())) {
            cir.setReturnValue(false);
        }
    }
}
