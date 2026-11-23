package dev.itsdropper.vantaboost.optimization.occlusion;

/**
 * Geometric proof that one opaque, axis-aligned block face covers the complete
 * perspective projection of an entity's conservative render bounds.
 *
 * <p>Unlike sparse point sampling, this checks all eight AABB corners against
 * the same occluder face. Since the projected AABB is convex, containing every
 * projected corner contains its complete projected silhouette.</p>
 */
public final class OcclusionSampling {
    private static final double MIN_EXTENT = 0.2;
    private static final double MAX_EXTENT = 6.0;
    private static final double PLANE_EPSILON = 1.0E-5;
    private static final double FACE_EDGE_MARGIN = 1.0E-4;

    private OcclusionSampling() {
    }

    /**
     * @param axis 0 for an X-normal face, 1 for Y, 2 for Z
     * @param normalStep -1 when the camera is on the negative side of the
     *                    face plane, +1 when it is on the positive side
     * @param plane coordinate of the block face plane
     * @param minU/maxU and minV/maxV the two-dimensional face rectangle
     */
    public static boolean isFullyOccludedByBlockFace(
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            double cameraX, double cameraY, double cameraZ,
            int axis, int normalStep, double plane,
            double minU, double maxU, double minV, double maxV
    ) {
        if (axis < 0 || axis > 2 || (normalStep != -1 && normalStep != 1)
                || !isFinite(minX) || !isFinite(minY) || !isFinite(minZ)
                || !isFinite(maxX) || !isFinite(maxY) || !isFinite(maxZ)
                || !isFinite(cameraX) || !isFinite(cameraY) || !isFinite(cameraZ)
                || !isFinite(plane) || !isFinite(minU) || !isFinite(maxU)
                || !isFinite(minV) || !isFinite(maxV)) {
            return false;
        }

        double sizeX = maxX - minX;
        double sizeY = maxY - minY;
        double sizeZ = maxZ - minZ;
        if (sizeX < MIN_EXTENT || sizeY < MIN_EXTENT || sizeZ < MIN_EXTENT
                || sizeX > MAX_EXTENT || sizeY > MAX_EXTENT || sizeZ > MAX_EXTENT
                || maxU - minU <= FACE_EDGE_MARGIN * 2
                || maxV - minV <= FACE_EDGE_MARGIN * 2) {
            return false;
        }

        double cameraAxis = coordinate(cameraX, cameraY, cameraZ, axis);
        double targetMin = axis == 0 ? minX : axis == 1 ? minY : minZ;
        double targetMax = axis == 0 ? maxX : axis == 1 ? maxY : maxZ;

        // The entire bounds must lie beyond the face plane, not straddle it.
        if (normalStep < 0) {
            if (cameraAxis >= plane - PLANE_EPSILON
                    || targetMin <= plane + PLANE_EPSILON) {
                return false;
            }
        } else if (cameraAxis <= plane + PLANE_EPSILON
                || targetMax >= plane - PLANE_EPSILON) {
            return false;
        }

        int uAxis = axis == 0 ? 1 : 0;
        int vAxis = axis == 2 ? 1 : 2;

        // Every projected corner must land strictly inside the opaque face.
        for (int corner = 0; corner < 8; corner++) {
            double x = (corner & 1) == 0 ? minX : maxX;
            double y = (corner & 2) == 0 ? minY : maxY;
            double z = (corner & 4) == 0 ? minZ : maxZ;

            double targetAxis = coordinate(x, y, z, axis);
            double t = (plane - cameraAxis) / (targetAxis - cameraAxis);
            if (!(t > 0.0 && t < 1.0)) {
                return false;
            }

            double cameraU = coordinate(cameraX, cameraY, cameraZ, uAxis);
            double cameraV = coordinate(cameraX, cameraY, cameraZ, vAxis);
            double targetU = coordinate(x, y, z, uAxis);
            double targetV = coordinate(x, y, z, vAxis);
            double projectedU = cameraU + (targetU - cameraU) * t;
            double projectedV = cameraV + (targetV - cameraV) * t;

            if (projectedU <= minU + FACE_EDGE_MARGIN
                    || projectedU >= maxU - FACE_EDGE_MARGIN
                    || projectedV <= minV + FACE_EDGE_MARGIN
                    || projectedV >= maxV - FACE_EDGE_MARGIN) {
                return false;
            }
        }

        return true;
    }

    private static double coordinate(double x, double y, double z, int axis) {
        return axis == 0 ? x : axis == 1 ? y : z;
    }

    private static boolean isFinite(double value) {
        return Double.isFinite(value);
    }
}
