package dev.itsdropper.vantaboost.optimization.occlusion;

/**
 * Conservative per-axis padding for Minecraft's previous-to-current entity
 * position interpolation.
 */
public final class OcclusionMotion {
    private OcclusionMotion() {
    }

    public static double interpolationPadding(double previous, double current) {
        return Math.abs(previous - current);
    }

    public static boolean isWithinLimit(double padding, double maximum) {
        return Double.isFinite(padding) && padding <= maximum;
    }
}
