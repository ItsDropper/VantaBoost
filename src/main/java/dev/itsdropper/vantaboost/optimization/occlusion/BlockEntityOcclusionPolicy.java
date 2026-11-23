package dev.itsdropper.vantaboost.optimization.occlusion;

import java.util.Set;

/**
 * Strict allowlist for block-entity renderers whose vanilla geometry is known
 * to remain inside a conservative three-block envelope around the block.
 *
 * <p>Renderer and block-entity classes must match exactly. Modded subclasses,
 * globally rendered objects, and off-screen renderers always fail open.</p>
 */
public final class BlockEntityOcclusionPolicy {
    private static final Set<String> SUPPORTED_RENDERERS = Set.of(
            "net.minecraft.client.renderer.blockentity.ChestRenderer",
            "net.minecraft.client.renderer.blockentity.BedRenderer",
            "net.minecraft.client.renderer.blockentity.ShulkerBoxRenderer",
            "net.minecraft.client.renderer.blockentity.DecoratedPotRenderer",
            "net.minecraft.client.renderer.blockentity.BellRenderer",
            "net.minecraft.client.renderer.blockentity.EnchantTableRenderer",
            "net.minecraft.client.renderer.blockentity.LecternRenderer",
            "net.minecraft.client.renderer.blockentity.CopperGolemStatueBlockRenderer"
    );

    private static final Set<String> SUPPORTED_BLOCK_ENTITIES = Set.of(
            "net.minecraft.world.level.block.entity.ChestBlockEntity",
            "net.minecraft.world.level.block.entity.EnderChestBlockEntity",
            "net.minecraft.world.level.block.entity.TrappedChestBlockEntity",
            "net.minecraft.world.level.block.entity.BedBlockEntity",
            "net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity",
            "net.minecraft.world.level.block.entity.DecoratedPotBlockEntity",
            "net.minecraft.world.level.block.entity.BellBlockEntity",
            "net.minecraft.world.level.block.entity.EnchantingTableBlockEntity",
            "net.minecraft.world.level.block.entity.LecternBlockEntity",
            "net.minecraft.world.level.block.entity.CopperGolemStatueBlockEntity"
    );

    private BlockEntityOcclusionPolicy() {
    }

    public static boolean isSupported(
            String rendererClassName,
            String blockEntityClassName,
            boolean rendersOffScreen,
            boolean globallyRendered
    ) {
        return !rendersOffScreen && !globallyRendered
                && SUPPORTED_RENDERERS.contains(rendererClassName)
                && SUPPORTED_BLOCK_ENTITIES.contains(blockEntityClassName);
    }
}
