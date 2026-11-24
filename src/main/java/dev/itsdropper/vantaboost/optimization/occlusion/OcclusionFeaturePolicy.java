package dev.itsdropper.vantaboost.optimization.occlusion;

/**
 * Shared opt-in/force-disable policy for experimental occlusion features.
 */
public final class OcclusionFeaturePolicy {
    private OcclusionFeaturePolicy() {
    }

    public static boolean isEnabled(boolean explicitlyEnabled, boolean explicitlyDisabled) {
        return explicitlyEnabled && !explicitlyDisabled;
    }
}
