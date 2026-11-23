package dev.itsdropper.vantaboost.mixin;

import dev.itsdropper.vantaboost.optimization.occlusion.EntityOcclusionCuller;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Reuses the exact conservative AABB vanilla already computed for frustum
 * culling. The redirect runs only after vanilla decided the renderer is
 * affected by culling, and preserves the original frustum result.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererOcclusionMixin<T extends Entity> {
    @Shadow
    @Final
    protected EntityRenderDispatcher entityRenderDispatcher;

    @Shadow
    protected float shadowRadius;

    @Redirect(
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
            T entity,
            Frustum originalFrustum,
            double cameraX,
            double cameraY,
            double cameraZ
    ) {
        if (bounds == null) {
            return true;
        }

        boolean vanillaVisible = frustum.isVisible(bounds);
        if (!vanillaVisible
                || !EntityOcclusionCuller.isMainEntityExtraction(frustum)
                || entity == entityRenderDispatcher.crosshairPickEntity
                || !this.getClass().getName().startsWith("net.minecraft.client.renderer.entity.")) {
            return vanillaVisible;
        }

        return !EntityOcclusionCuller.shouldCull(
                entity, bounds,
                frustum.getCamX(), frustum.getCamY(), frustum.getCamZ(),
                shadowRadius, this.getClass());
    }
}
