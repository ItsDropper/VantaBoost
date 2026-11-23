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
    private static final double MAX_EXTENT = 4.5;
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

    /**
     * Projects all eight corners of an AABB onto a plane strictly between the
     * camera and the complete AABB. The output is [minU, maxU, minV, maxV].
     * The projected silhouette is convex and therefore contained by this
     * projected-corner bounding rectangle.
     */
    public static boolean projectAabbOntoPlane(
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            double cameraX, double cameraY, double cameraZ,
            int axis, int normalStep, double plane,
            double[] projectedBounds
    ) {
        if (projectedBounds == null || projectedBounds.length < 4
                || axis < 0 || axis > 2 || (normalStep != -1 && normalStep != 1)
                || !isFinite(minX) || !isFinite(minY) || !isFinite(minZ)
                || !isFinite(maxX) || !isFinite(maxY) || !isFinite(maxZ)
                || !isFinite(cameraX) || !isFinite(cameraY) || !isFinite(cameraZ)
                || !isFinite(plane)) {
            return false;
        }

        double sizeX = maxX - minX;
        double sizeY = maxY - minY;
        double sizeZ = maxZ - minZ;
        if (sizeX < MIN_EXTENT || sizeY < MIN_EXTENT || sizeZ < MIN_EXTENT
                || sizeX > MAX_EXTENT || sizeY > MAX_EXTENT || sizeZ > MAX_EXTENT) {
            return false;
        }

        double cameraAxis = coordinate(cameraX, cameraY, cameraZ, axis);
        double targetMin = coordinate(minX, minY, minZ, axis);
        double targetMax = coordinate(maxX, maxY, maxZ, axis);
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
        double cameraU = coordinate(cameraX, cameraY, cameraZ, uAxis);
        double cameraV = coordinate(cameraX, cameraY, cameraZ, vAxis);
        double minU = Double.POSITIVE_INFINITY;
        double maxU = Double.NEGATIVE_INFINITY;
        double minV = Double.POSITIVE_INFINITY;
        double maxV = Double.NEGATIVE_INFINITY;

        for (int corner = 0; corner < 8; corner++) {
            double x = (corner & 1) == 0 ? minX : maxX;
            double y = (corner & 2) == 0 ? minY : maxY;
            double z = (corner & 4) == 0 ? minZ : maxZ;
            double targetAxis = coordinate(x, y, z, axis);
            double t = (plane - cameraAxis) / (targetAxis - cameraAxis);
            if (!(t > 0.0 && t < 1.0) || !isFinite(t)) {
                return false;
            }

            double projectedU = cameraU
                    + (coordinate(x, y, z, uAxis) - cameraU) * t;
            double projectedV = cameraV
                    + (coordinate(x, y, z, vAxis) - cameraV) * t;
            if (!isFinite(projectedU) || !isFinite(projectedV)) {
                return false;
            }

            minU = Math.min(minU, projectedU);
            maxU = Math.max(maxU, projectedU);
            minV = Math.min(minV, projectedV);
            maxV = Math.max(maxV, projectedV);
        }

        if (maxU - minU <= FACE_EDGE_MARGIN * 2
                || maxV - minV <= FACE_EDGE_MARGIN * 2) {
            return false;
        }

        projectedBounds[0] = minU;
        projectedBounds[1] = maxU;
        projectedBounds[2] = minV;
        projectedBounds[3] = maxV;
        return true;
    }

    /**
     * Finds the complete integer face-cell rectangle containing a projected
     * AABB. Requiring every cell in this rectangle to be opaque is conservative
     * because the projected silhouette is a subset of the rectangle.
     *
     * @param projectedBounds [minU, maxU, minV, maxV]
     * @param cellRange output [minUCell, maxUCell, minVCell, maxVCell]
     */
    public static boolean getFaceCellRange(
            double[] projectedBounds, int[] cellRange, int maximumCells
    ) {
        if (projectedBounds == null || projectedBounds.length < 4
                || cellRange == null || cellRange.length < 4 || maximumCells < 1) {
            return false;
        }

        double minU = projectedBounds[0];
        double maxU = projectedBounds[1];
        double minV = projectedBounds[2];
        double maxV = projectedBounds[3];
        if (!isFinite(minU) || !isFinite(maxU) || !isFinite(minV) || !isFinite(maxV)
                || maxU <= minU || maxV <= minV) {
            return false;
        }

        double minUCellValue = Math.floor(minU);
        double maxUCellValue = Math.ceil(maxU) - 1.0;
        double minVCellValue = Math.floor(minV);
        double maxVCellValue = Math.ceil(maxV) - 1.0;
        if (minUCellValue < Integer.MIN_VALUE || maxUCellValue > Integer.MAX_VALUE
                || minVCellValue < Integer.MIN_VALUE || maxVCellValue > Integer.MAX_VALUE) {
            return false;
        }

        int minUCell = (int) minUCellValue;
        int maxUCell = (int) maxUCellValue;
        int minVCell = (int) minVCellValue;
        int maxVCell = (int) maxVCellValue;
        if (maxUCell < minUCell || maxVCell < minVCell
                || minU <= minUCell + FACE_EDGE_MARGIN
                || maxU >= (double) maxUCell + 1.0 - FACE_EDGE_MARGIN
                || minV <= minVCell + FACE_EDGE_MARGIN
                || maxV >= (double) maxVCell + 1.0 - FACE_EDGE_MARGIN) {
            return false;
        }

        long width = (long) maxUCell - minUCell + 1L;
        long height = (long) maxVCell - minVCell + 1L;
        if (width * height > maximumCells) {
            return false;
        }

        cellRange[0] = minUCell;
        cellRange[1] = maxUCell;
        cellRange[2] = minVCell;
        cellRange[3] = maxVCell;
        return true;
    }

    private static double coordinate(double x, double y, double z, int axis) {
        return axis == 0 ? x : axis == 1 ? y : z;
    }

    private static boolean isFinite(double value) {
        return Double.isFinite(value);
    }
}
