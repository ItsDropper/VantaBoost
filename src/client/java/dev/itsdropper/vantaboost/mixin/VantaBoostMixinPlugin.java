package dev.itsdropper.vantaboost.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Applies optional optimization, diagnostic, and CI-only mixins only when
 * their corresponding startup flags are enabled. Disabled features therefore
 * add no wrappers or instrumentation to normal rendering and tick paths.
 */
public final class VantaBoostMixinPlugin implements IMixinConfigPlugin {
    private static final String PARTICLE_PROFILER_PROPERTY =
            "vantaboost.profileParticleExtraction";
    private static final String CI_SMOKE_PROPERTY = "vantaboost.ciSmoke";
    private static final String CLIENT_SMOKE_TEST_MIXIN =
            "dev.itsdropper.vantaboost.mixin.ClientSmokeTestMixin";
    private static final String PARTICLE_ENGINE_MIXIN =
            "dev.itsdropper.vantaboost.mixin.ParticleEngineDiagnosticsMixin";
    private static final String QUAD_PARTICLE_GROUP_MIXIN =
            "dev.itsdropper.vantaboost.mixin.QuadParticleGroupDiagnosticsMixin";
    private static final String ENTITY_OCCLUSION_PROPERTY =
            "vantaboost.enableEntityOcclusion";
    private static final String BLOCK_ENTITY_OCCLUSION_PROPERTY =
            "vantaboost.enableBlockEntityOcclusion";
    private static final String DISABLE_ENTITY_OCCLUSION_PROPERTY =
            "vantaboost.disableEntityOcclusion";
    private static final String DISABLE_BLOCK_ENTITY_OCCLUSION_PROPERTY =
            "vantaboost.disableBlockEntityOcclusion";
    private static final Set<String> ENTITY_OCCLUSION_MIXINS = Set.of(
            "dev.itsdropper.vantaboost.mixin.EntityRendererOcclusionMixin",
            "dev.itsdropper.vantaboost.mixin.LivingEntityRendererNameTagInvoker",
            "dev.itsdropper.vantaboost.mixin.MobRendererNameTagInvoker",
            "dev.itsdropper.vantaboost.mixin.LevelRendererEntityOcclusionMixin"
    );
    private static final Set<String> BLOCK_ENTITY_OCCLUSION_MIXINS = Set.of(
            "dev.itsdropper.vantaboost.mixin.LevelRendererBlockEntityOcclusionMixin",
            "dev.itsdropper.vantaboost.mixin.BlockEntityRenderDispatcherOcclusionMixin"
    );
    private static final String CLIENT_LEVEL_OCCLUSION_MIXIN =
            "dev.itsdropper.vantaboost.mixin.ClientLevelOcclusionMixin";

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (CLIENT_SMOKE_TEST_MIXIN.equals(mixinClassName)) {
            return Boolean.getBoolean(CI_SMOKE_PROPERTY);
        }
        if (PARTICLE_ENGINE_MIXIN.equals(mixinClassName)
                || QUAD_PARTICLE_GROUP_MIXIN.equals(mixinClassName)) {
            return Boolean.getBoolean(PARTICLE_PROFILER_PROPERTY);
        }
        if (ENTITY_OCCLUSION_MIXINS.contains(mixinClassName)) {
            return isEnabled(ENTITY_OCCLUSION_PROPERTY, DISABLE_ENTITY_OCCLUSION_PROPERTY);
        }
        if (BLOCK_ENTITY_OCCLUSION_MIXINS.contains(mixinClassName)) {
            return isEnabled(BLOCK_ENTITY_OCCLUSION_PROPERTY, DISABLE_BLOCK_ENTITY_OCCLUSION_PROPERTY);
        }
        if (CLIENT_LEVEL_OCCLUSION_MIXIN.equals(mixinClassName)) {
            return isEnabled(ENTITY_OCCLUSION_PROPERTY, DISABLE_ENTITY_OCCLUSION_PROPERTY)
                    || isEnabled(BLOCK_ENTITY_OCCLUSION_PROPERTY, DISABLE_BLOCK_ENTITY_OCCLUSION_PROPERTY);
        }
        return true;
    }

    private static boolean isEnabled(String enabledProperty, String disabledProperty) {
        return Boolean.getBoolean(enabledProperty) && !Boolean.getBoolean(disabledProperty);
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(
            String targetClassName, ClassNode targetClass,
            String mixinClassName, IMixinInfo mixinInfo
    ) {
    }

    @Override
    public void postApply(
            String targetClassName, ClassNode targetClass,
            String mixinClassName, IMixinInfo mixinInfo
    ) {
    }
}
