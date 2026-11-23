package dev.itsdropper.vantaboost.optimization.occlusion;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OcclusionSamplingTest {
    @Test
    void oneOpaqueFaceCanFullyCoverACompactEntityBehindIt() {
        assertTrue(OcclusionSampling.isFullyOccludedByBlockFace(
                2, 0.25, 0.25, 3, 0.75, 0.75,
                -2, 0.5, 0.5,
                0, -1, 0,
                0, 1, 0, 1));
    }

    @Test
    void projectedBoundsOutsideTheFaceRemainVisible() {
        assertFalse(OcclusionSampling.isFullyOccludedByBlockFace(
                2, -1, 0.25, 3, 1.5, 0.75,
                -2, 0.5, 0.5,
                0, -1, 0,
                0, 1, 0, 1));
    }

    @Test
    void boundsStraddlingTheOccluderPlaneRemainVisible() {
        assertFalse(OcclusionSampling.isFullyOccludedByBlockFace(
                -0.5, 0.25, 0.25, 3, 0.75, 0.75,
                -2, 0.5, 0.5,
                0, -1, 0,
                0, 1, 0, 1));
    }

    @Test
    void occluderMustBeBetweenCameraAndEveryBoundsCorner() {
        assertFalse(OcclusionSampling.isFullyOccludedByBlockFace(
                2, 0.25, 0.25, 3, 0.75, 0.75,
                2.5, 0.5, 0.5,
                0, -1, 0,
                0, 1, 0, 1));
    }

    @Test
    void supportsAllThreeFaceAxesAndBothCameraSides() {
        assertTrue(OcclusionSampling.isFullyOccludedByBlockFace(
                4.25, 2, -1.75, 4.75, 3, -1.25,
                4.5, -2, -1.5,
                1, -1, 0,
                4, 5, -2, -1));

        assertTrue(OcclusionSampling.isFullyOccludedByBlockFace(
                4.25, -1.75, 2, 4.75, -1.25, 3,
                4.5, -1.5, -2,
                2, -1, 0,
                4, 5, -2, -1));

        assertTrue(OcclusionSampling.isFullyOccludedByBlockFace(
                -3, 0.25, 0.25, -2, 0.75, 0.75,
                5, 0.5, 0.5,
                0, 1, 1,
                0, 1, 0, 1));

        assertTrue(OcclusionSampling.isFullyOccludedByBlockFace(
                4.25, -3, -1.75, 4.75, -2, -1.25,
                4.5, 5, -1.5,
                1, 1, 1,
                4, 5, -2, -1));

        assertTrue(OcclusionSampling.isFullyOccludedByBlockFace(
                4.25, -1.75, -3, 4.75, -1.25, -2,
                4.5, -1.5, 5,
                2, 1, 1,
                4, 5, -2, -1));
    }

    @Test
    void allInteriorPointsRemainInsideAProvenOccluderFace() {
        Random random = new Random(0xC0FFEE);
        ProjectionCase[] cases = {
                new ProjectionCase(
                        2, 0.25, 0.25, 3, 0.75, 0.75,
                        -2, 0.5, 0.5, 0, -1, 0, 0, 1, 0, 1),
                new ProjectionCase(
                        4.25, 2, -1.75, 4.75, 3, -1.25,
                        4.5, -2, -1.5, 1, -1, 0, 4, 5, -2, -1),
                new ProjectionCase(
                        4.25, -1.75, 2, 4.75, -1.25, 3,
                        4.5, -1.5, -2, 2, -1, 0, 4, 5, -2, -1),
                new ProjectionCase(
                        -3, 0.25, 0.25, -2, 0.75, 0.75,
                        5, 0.5, 0.5, 0, 1, 1, 0, 1, 0, 1),
                new ProjectionCase(
                        4.25, -3, -1.75, 4.75, -2, -1.25,
                        4.5, 5, -1.5, 1, 1, 1, 4, 5, -2, -1),
                new ProjectionCase(
                        4.25, -1.75, -3, 4.75, -1.25, -2,
                        4.5, -1.5, 5, 2, 1, 1, 4, 5, -2, -1)
        };

        for (ProjectionCase testCase : cases) {
            assertTrue(testCase.isOccluded());
            for (int sample = 0; sample < 1_000; sample++) {
                double x = between(random, testCase.minX, testCase.maxX);
                double y = between(random, testCase.minY, testCase.maxY);
                double z = between(random, testCase.minZ, testCase.maxZ);
                assertTrue(testCase.projectsInsideFace(x, y, z));
            }
        }
    }

    private static double between(Random random, double min, double max) {
        return min + random.nextDouble() * (max - min);
    }

    private static double coordinate(double x, double y, double z, int axis) {
        return axis == 0 ? x : axis == 1 ? y : z;
    }

    private record ProjectionCase(
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            double cameraX, double cameraY, double cameraZ,
            int axis, int normalStep, double plane,
            double minU, double maxU, double minV, double maxV
    ) {
        private boolean isOccluded() {
            return OcclusionSampling.isFullyOccludedByBlockFace(
                    minX, minY, minZ, maxX, maxY, maxZ,
                    cameraX, cameraY, cameraZ,
                    axis, normalStep, plane, minU, maxU, minV, maxV);
        }

        private boolean projectsInsideFace(double x, double y, double z) {
            int uAxis = axis == 0 ? 1 : 0;
            int vAxis = axis == 2 ? 1 : 2;
            double cameraAxis = coordinate(cameraX, cameraY, cameraZ, axis);
            double targetAxis = coordinate(x, y, z, axis);
            double t = (plane - cameraAxis) / (targetAxis - cameraAxis);
            double cameraU = coordinate(cameraX, cameraY, cameraZ, uAxis);
            double cameraV = coordinate(cameraX, cameraY, cameraZ, vAxis);
            double projectedU = cameraU
                    + (coordinate(x, y, z, uAxis) - cameraU) * t;
            double projectedV = cameraV
                    + (coordinate(x, y, z, vAxis) - cameraV) * t;
            return projectedU > minU && projectedU < maxU
                    && projectedV > minV && projectedV < maxV;
        }
    }

    @Test
    void rejectsDegenerateOversizedAndNonFiniteBounds() {
        assertFalse(OcclusionSampling.isFullyOccludedByBlockFace(
                2, 0, 0.25, 2.1, 0.75, 0.75,
                -2, 0.5, 0.5, 0, -1, 0, 0, 1, 0, 1));

        assertFalse(OcclusionSampling.isFullyOccludedByBlockFace(
                2, -4, 0.25, 3, 4, 0.75,
                -2, 0.5, 0.5, 0, -1, 0, 0, 1, 0, 1));

        assertFalse(OcclusionSampling.isFullyOccludedByBlockFace(
                Double.NaN, 0, 0, 1, 1, 1,
                -2, 0.5, 0.5, 0, -1, 0, 0, 1, 0, 1));
    }

    @Test
    void visibleCacheEntriesSurviveWorldRevisionsButOccludedEntriesDoNot() {
        var visible = new EntityOcclusionCuller.CacheEntry(
                4L, 1, 2, 3, 4, 5, 6, 7, 8, 9, false);
        var occluded = new EntityOcclusionCuller.CacheEntry(
                4L, 1, 2, 3, 4, 5, 6, 7, 8, 9, true);

        assertTrue(visible.matches(5L, 1, 2, 3, 4, 5, 6, 7, 8, 9));
        assertFalse(occluded.matches(5L, 1, 2, 3, 4, 5, 6, 7, 8, 9));
        assertTrue(occluded.matches(4L, 1, 2, 3, 4, 5, 6, 7, 8, 9));
        assertFalse(visible.matches(5L, 1, 2, 3, 4, 5, 6, 7, 8, 10));
    }

    @Test
    void rejectsInvalidAxisAndFaceEdges() {
        assertFalse(OcclusionSampling.isFullyOccludedByBlockFace(
                2, 0.25, 0.25, 3, 0.75, 0.75,
                -2, 0.5, 0.5, 3, -1, 0, 0, 1, 0, 1));

        assertFalse(OcclusionSampling.isFullyOccludedByBlockFace(
                2, 0.25, 0.25, 3, 0.75, 0.75,
                -2, 0.5, 0.5, 0, -1, 0, 0, 0.0001, 0, 1));
    }
}
