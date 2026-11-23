package dev.itsdropper.vantaboost.optimization.occlusion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockEntityOcclusionPolicyTest {
    private static final class VanillaChestRenderer {
    }

    private static final class VanillaChestBlockEntity {
    }

    private static final class ModdedChestRenderer extends VanillaChestRenderer {
    }

    private static final class ModdedChestBlockEntity extends VanillaChestBlockEntity {
    }

    @Test
    void acceptsOnlyExactKnownVanillaRendererAndBlockEntityClasses() {
        assertTrue(BlockEntityOcclusionPolicy.isSupported(
                type("net.minecraft.client.renderer.blockentity.ChestRenderer"),
                type("net.minecraft.world.level.block.entity.ChestBlockEntity"),
                false,
                false));
    }

    @Test
    void rejectsUnknownOrModdedRendererAndBlockEntityClasses() {
        assertFalse(BlockEntityOcclusionPolicy.isSupported(
                ModdedChestRenderer.class, VanillaChestBlockEntity.class, false, false));
        assertFalse(BlockEntityOcclusionPolicy.isSupported(
                VanillaChestRenderer.class, ModdedChestBlockEntity.class, false, false));
        assertFalse(BlockEntityOcclusionPolicy.isSupported(
                type("net.minecraft.client.renderer.blockentity.BeaconRenderer"),
                type("net.minecraft.world.level.block.entity.BeaconBlockEntity"),
                false,
                false));
    }

    @Test
    void rejectsOffScreenAndGloballyRenderedBlockEntities() {
        Class<?> renderer = type("net.minecraft.client.renderer.blockentity.ChestRenderer");
        Class<?> blockEntity = type("net.minecraft.world.level.block.entity.ChestBlockEntity");

        assertFalse(BlockEntityOcclusionPolicy.isSupported(renderer, blockEntity, true, false));
        assertFalse(BlockEntityOcclusionPolicy.isSupported(renderer, blockEntity, false, true));
    }

    private static Class<?> type(String name) {
        return new NamedType(name);
    }

    private static final class NamedType extends ClassLoader {
        private final String name;

        private NamedType(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return this.name;
        }
    }
}
