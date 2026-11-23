package dev.itsdropper.vantaboost.optimization.occlusion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockEntityOcclusionPolicyTest {
    private static final String RENDERER_PREFIX =
            "net.minecraft.client.renderer.blockentity.";
    private static final String ENTITY_PREFIX =
            "net.minecraft.world.level.block.entity.";

    @Test
    void acceptsEveryExplicitVanillaRendererEntityPair() {
        assertSupported("ChestRenderer", "ChestBlockEntity");
        assertSupported("ChestRenderer", "EnderChestBlockEntity");
        assertSupported("ChestRenderer", "TrappedChestBlockEntity");
        assertSupported("BedRenderer", "BedBlockEntity");
        assertSupported("ShulkerBoxRenderer", "ShulkerBoxBlockEntity");
        assertSupported("DecoratedPotRenderer", "DecoratedPotBlockEntity");
        assertSupported("BellRenderer", "BellBlockEntity");
        assertSupported("EnchantTableRenderer", "EnchantingTableBlockEntity");
        assertSupported("LecternRenderer", "LecternBlockEntity");
        assertSupported("CopperGolemStatueBlockRenderer", "CopperGolemStatueBlockEntity");
    }

    @Test
    void rejectsUnknownOrModdedRendererAndBlockEntityClasses() {
        assertRejected("example.mod.CustomChestRenderer", ENTITY_PREFIX + "ChestBlockEntity");
        assertRejected(RENDERER_PREFIX + "ChestRenderer",
                "example.mod.CustomChestBlockEntity");
        assertRejected(RENDERER_PREFIX + "BeaconRenderer",
                ENTITY_PREFIX + "BeaconBlockEntity");
    }

    @Test
    void rejectsMismatchedVanillaRendererAndBlockEntityPairs() {
        assertRejected(RENDERER_PREFIX + "ChestRenderer",
                ENTITY_PREFIX + "BellBlockEntity");
        assertRejected(RENDERER_PREFIX + "BellRenderer",
                ENTITY_PREFIX + "ChestBlockEntity");
        assertRejected(RENDERER_PREFIX + "BedRenderer",
                ENTITY_PREFIX + "ShulkerBoxBlockEntity");
    }

    @Test
    void rejectsOffScreenAndGloballyRenderedBlockEntities() {
        String renderer = RENDERER_PREFIX + "ChestRenderer";
        String blockEntity = ENTITY_PREFIX + "ChestBlockEntity";

        assertFalse(BlockEntityOcclusionPolicy.isEligible(renderer, blockEntity, true, false));
        assertFalse(BlockEntityOcclusionPolicy.isEligible(renderer, blockEntity, false, true));
    }

    private static void assertSupported(String renderer, String blockEntity) {
        assertTrue(BlockEntityOcclusionPolicy.isEligible(
                RENDERER_PREFIX + renderer, ENTITY_PREFIX + blockEntity, false, false));
    }

    private static void assertRejected(String renderer, String blockEntity) {
        assertFalse(BlockEntityOcclusionPolicy.isEligible(
                renderer, blockEntity, false, false));
    }
}
