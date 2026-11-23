package dev.itsdropper.vantaboost.optimization.occlusion;

/**
 * Conservative bounds for the vanilla entity-shadow extraction grid.
 *
 * <p>Vanilla floors the horizontal radius endpoints to block coordinates and
 * the vertical depth endpoint to a block coordinate. The extra block covers
 * that rounding; depth is bounded by the maximum possible camera-distance
 * factor and the configured shadow radius.</p>
 */
public final class OcclusionShadowBounds {
    private OcclusionShadowBounds() {
    }

    public static double scaledRadius(
            double baseRadius, double entityScale, double ageScale, boolean mobRenderer
    ) {
        return baseRadius * entityScale * (mobRenderer ? ageScale : 1.0);
    }

    public static double horizontalPadding(double shadowRadius) {
        return shadowRadius > 0.0 ? shadowRadius + 1.0 : 0.0;
    }

    public static double verticalDropPadding(double shadowRadius, double shadowStrength) {
        if (shadowRadius <= 0.0) {
            return 0.0;
        }

        double maximumDepth = Math.min(shadowRadius, Math.max(0.0, 2.0 * shadowStrength - 1.0));
        return maximumDepth + 1.0;
    }

    public static double verticalRisePadding(double shadowRadius) {
        return shadowRadius > 0.0 ? 1.0 : 0.0;
    }
}
