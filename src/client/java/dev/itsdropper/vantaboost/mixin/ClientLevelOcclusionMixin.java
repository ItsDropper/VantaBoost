package dev.itsdropper.vantaboost.mixin;

import dev.itsdropper.vantaboost.optimization.occlusion.EntityOcclusionCuller;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Invalidates cached visibility whenever the client world topology changes.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelOcclusionMixin {
    @Inject(
            method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("TAIL")
    )
    private void vantaboost$invalidateAfterBlockChange(
            BlockPos pos, BlockState state, int flags, int recursionLeft,
            CallbackInfoReturnable<Boolean> cir
    ) {
        EntityOcclusionCuller.invalidateWorld();
    }

    @Inject(
            method = "setServerVerifiedBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)V",
            at = @At("TAIL")
    )
    private void vantaboost$invalidateAfterVerifiedBlockState(
            BlockPos pos, BlockState state, int flags, CallbackInfo ci
    ) {
        EntityOcclusionCuller.invalidateWorld();
    }

    @Inject(
            method = "syncBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/phys/Vec3;)V",
            at = @At("TAIL")
    )
    private void vantaboost$invalidateAfterPredictedBlockState(
            BlockPos pos, BlockState state, net.minecraft.world.phys.Vec3 playerPosition,
            CallbackInfo ci
    ) {
        EntityOcclusionCuller.invalidateWorld();
    }

    @Inject(
            method = "onSectionBecomingNonEmpty(J)V",
            at = @At("TAIL")
    )
    private void vantaboost$invalidateAfterSectionActivation(long sectionPos, CallbackInfo ci) {
        EntityOcclusionCuller.invalidateWorld();
    }

    @Inject(
            method = "onChunkLoaded(Lnet/minecraft/world/level/ChunkPos;)V",
            at = @At("TAIL")
    )
    private void vantaboost$invalidateAfterChunkLoad(ChunkPos pos, CallbackInfo ci) {
        EntityOcclusionCuller.invalidateWorld();
    }

    @Inject(
            method = "unload(Lnet/minecraft/world/level/chunk/LevelChunk;)V",
            at = @At("TAIL")
    )
    private void vantaboost$invalidateAfterChunkUnload(LevelChunk chunk, CallbackInfo ci) {
        EntityOcclusionCuller.invalidateWorld();
    }
}
