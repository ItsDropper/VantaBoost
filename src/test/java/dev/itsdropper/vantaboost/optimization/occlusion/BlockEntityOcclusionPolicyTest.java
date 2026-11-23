package dev.itsdropper.vantaboost.optimization.occlusion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockEntityOcclusionPolicyTest {
    private static final String CHEST_RENDERER =
            "net.minecraft.client.renderer.blockentity.ChestRenderer";
    private static final String CHEST_ENTITY =
            "net.minecraft.world.level.block.entity.ChestBlockEntity";

    @Test
    void acceptsOnlyExactKnownVanillaRendererAndBlockEntityClasses() {
        assertTrue(BlockEntityOcclusionPolicy.isSupported(
                CHEST_RENDERER, CHEST_ENTITY, false, false));
    }

    @Test
    void rejectsUnknownOrModdedRendererAndBlockEntityClasses() {
        assertFalse(BlockEntityOcclusionPolicy.isSupported(
                "example.mod.CustomChestRenderer", CHEST_ENTITY, false, false));
        assertFalse(BlockEntityOcclusionPolicy.isSupported(
                CHEST_RENDERER, "example.mod.CustomChestBlockEntity", false, false));
        assertFalse(BlockEntityOcclusionPolicy.isSupported(
                "net.minecraft.client.renderer.blockentity.BeaconRenderer",
                "net.minecraft.world.level.block.entity.BeaconBlockEntity",
                false,
                false));
    }

    @Test
    void rejectsOffScreenAndGloballyRenderedBlockEntities() {
        assertFalse(BlockEntityOcclusionPolicy.isSupported(
                CHEST_RENDERER, CHEST_ENTITY, true, false));
        assertFalse(BlockEntityOcclusionPolicy.isSupported(
                CHEST_RENDERER, CHEST_ENTITY, false, true));
    }
}
