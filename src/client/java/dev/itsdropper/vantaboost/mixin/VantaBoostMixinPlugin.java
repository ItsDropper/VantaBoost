package dev.itsdropper.vantaboost.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Removes opt-in diagnostics mixins entirely when their profiler is disabled.
 * This keeps instrumentation out of hot paths instead of adding a branch to
 * every particle's frustum test during ordinary gameplay.
 */
public final class VantaBoostMixinPlugin implements IMixinConfigPlugin {
    private static final String PARTICLE_PROFILER_PROPERTY =
            "vantaboost.profileParticleExtraction";
    private static final String PARTICLE_ENGINE_MIXIN =
            "dev.itsdropper.vantaboost.mixin.ParticleEngineDiagnosticsMixin";
    private static final String QUAD_PARTICLE_GROUP_MIXIN =
            "dev.itsdropper.vantaboost.mixin.QuadParticleGroupDiagnosticsMixin";

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (PARTICLE_ENGINE_MIXIN.equals(mixinClassName)
                || QUAD_PARTICLE_GROUP_MIXIN.equals(mixinClassName)) {
            return Boolean.getBoolean(PARTICLE_PROFILER_PROPERTY);
        }
        return true;
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
