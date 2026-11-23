package dev.itsdropper.vantaboost.optimization.occlusion;

/**
 * Conservative, allocation-free sampling for axis-aligned render bounds.
 *
 * <p>A bound is considered hidden only when every point in a 3x3x3 interior
 * sample is independently confirmed occluded. Invalid, degenerate, oversized,
 * or camera-intersecting bounds always remain visible.</p>
 */
public final class OcclusionSampling {
    private static final double[] SAMPLE_FACTORS = {0.1, 0.5, 0.9};
    private static final double MIN_EXTENT = 0.2;
    private static final double MAX_EXTENT = 6.0;

    private OcclusionSampling() {
    }

    @FunctionalInterface
    public interface RayTester {
        boolean isOccluded(double x, double y, double z);
    }

    public static boolean isFullyOccluded(
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            double cameraX, double cameraY, double cameraZ,
            RayTester rayTester
    ) {
        if (rayTester == null
                || !isFinite(minX) || !isFinite(minY) || !isFinite(minZ)
                || !isFinite(maxX) || !isFinite(maxY) || !isFinite(maxZ)
                || !isFinite(cameraX) || !isFinite(cameraY) || !isFinite(cameraZ)) {
            return false;
        }

        double sizeX = maxX - minX;
        double sizeY = maxY - minY;
        double sizeZ = maxZ - minZ;
        if (sizeX < MIN_EXTENT || sizeY < MIN_EXTENT || sizeZ < MIN_EXTENT
                || sizeX > MAX_EXTENT || sizeY > MAX_EXTENT || sizeZ > MAX_EXTENT) {
            return false;
        }

        if (cameraX >= minX && cameraX <= maxX
                && cameraY >= minY && cameraY <= maxY
                && cameraZ >= minZ && cameraZ <= maxZ) {
            return false;
        }

        try {
            for (double xFactor : SAMPLE_FACTORS) {
                double x = minX + sizeX * xFactor;
                for (double yFactor : SAMPLE_FACTORS) {
                    double y = minY + sizeY * yFactor;
                    for (double zFactor : SAMPLE_FACTORS) {
                        double z = minZ + sizeZ * zFactor;
                        if (!rayTester.isOccluded(x, y, z)) {
                            return false;
                        }
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // A failed or unavailable ray query is uncertainty, not occlusion.
            return false;
        }

        return true;
    }

    private static boolean isFinite(double value) {
        return Double.isFinite(value);
    }
}
