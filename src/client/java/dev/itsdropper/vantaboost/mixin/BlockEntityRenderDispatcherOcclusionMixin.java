package dev.itsdropper.vantaboost.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.itsdropper.vantaboost.optimization.occlusion.BlockEntityOcclusionCuller;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Applies conservative occlusion after vanilla's renderer-specific distance
 * policy succeeds but before the block entity render state is allocated.
 */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderDispatcherOcclusionMixin {
    @WrapOperation(
            method = "tryExtractRenderState(Lnet/minecraft/world/level/block/entity/BlockEntity;FLnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)Lnet/minecraft/client/renderer/blockentity/state/BlockEntityRenderState;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderer;shouldRender(Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/phys/Vec3;)Z"
            )
    )
    private boolean vantaboost$applyBlockEntityOcclusion(
            BlockEntityRenderer renderer,
            BlockEntity blockEntity,
            Vec3 cameraPosition,
            Operation<Boolean> original
    ) {
        boolean vanillaShouldRender = original.call(renderer, blockEntity, cameraPosition);
        return vanillaShouldRender
                && !BlockEntityOcclusionCuller.shouldCull(
                        blockEntity, renderer, cameraPosition);
    }
}
