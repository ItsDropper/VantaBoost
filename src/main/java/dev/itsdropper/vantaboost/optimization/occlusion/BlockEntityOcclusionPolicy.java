package dev.itsdropper.vantaboost.optimization.occlusion;

import java.util.Map;
import java.util.Set;

/**
 * Strict allowlist of exact vanilla renderer/block-entity pairs whose geometry
 * is bounded by the culler's conservative three-block envelope.
 */
public final class BlockEntityOcclusionPolicy {
    private static final Map<String, Set<String>> SUPPORTED_PAIRS = Map.of(
            "net.minecraft.client.renderer.blockentity.ChestRenderer", Set.of(
                    "net.minecraft.world.level.block.entity.ChestBlockEntity",
                    "net.minecraft.world.level.block.entity.EnderChestBlockEntity",
                    "net.minecraft.world.level.block.entity.TrappedChestBlockEntity"),
            "net.minecraft.client.renderer.blockentity.BedRenderer", Set.of(
                    "net.minecraft.world.level.block.entity.BedBlockEntity"),
            "net.minecraft.client.renderer.blockentity.ShulkerBoxRenderer", Set.of(
                    "net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity"),
            "net.minecraft.client.renderer.blockentity.DecoratedPotRenderer", Set.of(
                    "net.minecraft.world.level.block.entity.DecoratedPotBlockEntity"),
            "net.minecraft.client.renderer.blockentity.BellRenderer", Set.of(
                    "net.minecraft.world.level.block.entity.BellBlockEntity"),
            "net.minecraft.client.renderer.blockentity.EnchantTableRenderer", Set.of(
                    "net.minecraft.world.level.block.entity.EnchantingTableBlockEntity"),
            "net.minecraft.client.renderer.blockentity.LecternRenderer", Set.of(
                    "net.minecraft.world.level.block.entity.LecternBlockEntity"),
            "net.minecraft.client.renderer.blockentity.CopperGolemStatueBlockRenderer", Set.of(
                    "net.minecraft.world.level.block.entity.CopperGolemStatueBlockEntity")
    );

    private BlockEntityOcclusionPolicy() {
    }

    public static boolean isSupported(String rendererClassName, String blockEntityClassName) {
        return SUPPORTED_PAIRS.getOrDefault(rendererClassName, Set.of())
                .contains(blockEntityClassName);
    }

    public static boolean isEligible(
            String rendererClassName,
            String blockEntityClassName,
            boolean rendersOffScreen,
            boolean globallyRendered
    ) {
        return !rendersOffScreen && !globallyRendered
                && isSupported(rendererClassName, blockEntityClassName);
    }
}
