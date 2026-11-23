package dev.itsdropper.vantaboost.optimization.occlusion;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OcclusionSamplingTest {
    private static class TestRenderState {
    }
    private static class TestEntity {
    }

    private static class VanillaBoundsRenderer {
        protected Object getBoundingBoxForCulling(TestEntity entity) {
            return entity;
        }
    }

    private static class InheritedBoundsRenderer extends VanillaBoundsRenderer {
    }

    private static class CustomBoundsRenderer extends VanillaBoundsRenderer {
        @Override
        protected Object getBoundingBoxForCulling(TestEntity entity) {
            return entity;
        }
    }


    private static class TestLivingRenderState extends TestRenderState {
    }

    private static class TestEntityRenderer {
    }

    private static class TestMobRenderer extends TestEntityRenderer {
        protected float getShadowRadius(TestLivingRenderState state) {
            return 0.5F;
        }
    }

    private static class InheritedMobRenderer extends TestMobRenderer {
    }

    private static class CustomMobRenderer extends TestMobRenderer {
        @Override
        protected float getShadowRadius(TestLivingRenderState state) {
            return 1.0F;
        }
    }

    private static class CustomDisplayRenderer extends TestEntityRenderer {
        protected float getShadowStrength(TestLivingRenderState state) {
            return 0.5F;
        }
    }

    private static class CustomOffsetRenderer extends TestEntityRenderer {
        protected Object getRenderOffset(TestLivingRenderState state) {
            return null;
        }
    }

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
    void projectsBoundsOntoPlaneAndFindsAllRequiredAdjacentFaceCells() {
        double[] projected = new double[4];
        int[] cells = new int[4];

        assertTrue(OcclusionSampling.projectAabbOntoPlane(
                2.0, 0.25, 0.25, 3.0, 2.75, 0.75,
                -2.0, 1.5, 0.5, 0, -1, 0.0, projected));
        assertTrue(OcclusionSampling.getFaceCellRange(projected, cells, 64));

        assertEquals(0, cells[0]);
        assertEquals(2, cells[1]);
        assertEquals(0, cells[2]);
        assertEquals(0, cells[3]);
        assertTrue(projected[0] > 0.0 && projected[1] < 3.0);
    }

    @Test
    void projectsAdjacentFaceCoverageAcrossEveryAxisAndCameraSide() {
        double[] projected = new double[4];
        int[] cells = new int[4];

        assertTrue(OcclusionSampling.projectAabbOntoPlane(
                0.25, 2.0, 0.25, 2.75, 3.0, 0.75,
                1.5, -2.0, 0.5, 1, -1, 0.0, projected));
        assertTrue(OcclusionSampling.getFaceCellRange(projected, cells, 64));
        assertEquals(0, cells[0]);
        assertEquals(2, cells[1]);
        assertEquals(0, cells[2]);
        assertEquals(0, cells[3]);

        assertTrue(OcclusionSampling.projectAabbOntoPlane(
                0.25, 0.25, 2.0, 2.75, 0.75, 3.0,
                1.5, 0.5, -2.0, 2, -1, 0.0, projected));
        assertTrue(OcclusionSampling.getFaceCellRange(projected, cells, 64));
        assertEquals(0, cells[0]);
        assertEquals(2, cells[1]);
        assertEquals(0, cells[2]);
        assertEquals(0, cells[3]);

        assertTrue(OcclusionSampling.projectAabbOntoPlane(
                2.0, 0.25, 0.25, 3.0, 2.75, 0.75,
                5.0, 1.5, 0.5, 0, 1, 4.0, projected));
        assertTrue(OcclusionSampling.getFaceCellRange(projected, cells, 64));
        assertEquals(0, cells[0]);
        assertEquals(2, cells[1]);
        assertEquals(0, cells[2]);
        assertEquals(0, cells[3]);
    }

    @Test
    void rejectsProjectedFaceCoverageWithHolesOrExcessiveWorkBounds() {
        int[] cells = new int[4];
        assertFalse(OcclusionSampling.getFaceCellRange(
                new double[]{0.00001, 0.5, 0.2, 0.8}, cells, 64));
        assertFalse(OcclusionSampling.getFaceCellRange(
                new double[]{0.25, 9.75, 0.25, 9.75}, cells, 64));
        assertFalse(OcclusionSampling.getFaceCellRange(
                new double[]{Double.NaN, 1.0, 0.0, 1.0}, cells, 64));
    }

    @Test
    void faceCellRangesUseFloorCoordinatesForNegativeWorldPositions() {
        int[] cells = new int[4];
        assertTrue(OcclusionSampling.getFaceCellRange(
                new double[]{-2.75, -1.25, -3.75, -2.25}, cells, 64));
        assertEquals(-3, cells[0]);
        assertEquals(-2, cells[1]);
        assertEquals(-4, cells[2]);
        assertEquals(-3, cells[3]);

        assertFalse(OcclusionSampling.getFaceCellRange(
                new double[]{-2.75, 2_000_000_000.0, -3.75, 2_000_000_000.0},
                cells, 64));
    }

    @Test
    void projectedBoundsRejectPlaneOutsideAnyAabbCorner() {
        double[] projected = new double[4];
        assertFalse(OcclusionSampling.projectAabbOntoPlane(
                -0.5, 0.25, 0.25, 3.0, 0.75, 0.75,
                -2.0, 0.5, 0.5, 0, -1, 0.0, projected));
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
        assertTrue(OcclusionCachePolicy.isReusable(false, 4L, 5L));
        assertFalse(OcclusionCachePolicy.isReusable(true, 4L, 5L));
        assertTrue(OcclusionCachePolicy.isReusable(true, 4L, 4L));
    }

    @Test
    void interpolationPaddingCoversEveryPositionBetweenPreviousAndCurrent() {
        double previous = -0.75;
        double current = 0.5;
        double padding = OcclusionMotion.interpolationPadding(previous, current);

        for (int step = 0; step <= 100; step++) {
            double partialTick = step / 100.0;
            double interpolated = previous + (current - previous) * partialTick;
            assertTrue(Math.abs(interpolated - current) <= padding + 1.0E-12);
        }

        assertTrue(OcclusionMotion.isWithinLimit(padding, 1.5));
        assertFalse(OcclusionMotion.isWithinLimit(1.5001, 1.5));
        assertFalse(OcclusionMotion.isWithinLimit(Double.NaN, 1.5));
    }

    @Test
    void shadowEnvelopeContainsFlooredBlocksAcrossFractionalEntityPositions() {
        double[] radii = {0.25, 0.5, 1.0, 2.0};
        double[] strengths = {0.25, 0.5, 1.0, 1.5, 2.0};
        double epsilon = 1.0E-12;

        for (int step = 0; step <= 100; step++) {
            double position = step / 100.0;
            for (double radius : radii) {
                double minHorizontalBlock = Math.floor(position - radius);
                double maxHorizontalBlock = Math.floor(position + radius) + 1.0;
                double horizontalPadding = OcclusionShadowBounds.horizontalPadding(radius);
                assertTrue(position - minHorizontalBlock <= horizontalPadding + epsilon);
                assertTrue(maxHorizontalBlock - position <= horizontalPadding + epsilon);

                for (double strength : strengths) {
                    double maximumDepth = Math.min(
                            radius, Math.max(0.0, 2.0 * strength - 1.0));
                    double minShadowBlock = Math.floor(position - maximumDepth);
                    double maxShadowBlock = Math.floor(position) + 1.0;
                    assertTrue(position - minShadowBlock
                            <= OcclusionShadowBounds.verticalDropPadding(radius, strength) + epsilon);
                    assertTrue(maxShadowBlock - position
                            <= OcclusionShadowBounds.verticalRisePadding(radius) + epsilon);
                }
            }
        }
    }

    @Test
    void rendererOverrideDetectionRecognizesSpecializedRenderStateParameters() {
        assertFalse(RendererOverridePolicy.declaresStateMethod(
                InheritedMobRenderer.class, TestMobRenderer.class,
                "getShadowRadius", TestRenderState.class));
        assertTrue(RendererOverridePolicy.declaresStateMethod(
                CustomMobRenderer.class, TestMobRenderer.class,
                "getShadowRadius", TestRenderState.class));
        assertTrue(RendererOverridePolicy.declaresStateMethod(
                CustomDisplayRenderer.class, TestEntityRenderer.class,
                "getShadowStrength", TestRenderState.class));
        assertTrue(RendererOverridePolicy.declaresStateMethod(
                CustomOffsetRenderer.class, TestEntityRenderer.class,
                "getRenderOffset", TestRenderState.class));
    }

    @Test
    void rendererSpecificCullingBoundsOverridesFailClosed() {
        assertFalse(RendererOverridePolicy.declaresSingleArgumentMethod(
                InheritedBoundsRenderer.class, VanillaBoundsRenderer.class,
                "getBoundingBoxForCulling", TestEntity.class));
        assertTrue(RendererOverridePolicy.declaresSingleArgumentMethod(
                CustomBoundsRenderer.class, VanillaBoundsRenderer.class,
                "getBoundingBoxForCulling", TestEntity.class));
    }

    @Test
    void livingRendererShadowRadiusUsesEntityAndMobAgeScale() {
        assertEquals(0.5, OcclusionShadowBounds.scaledRadius(0.5, 1.0, 1.0, false), 0.0);
        assertEquals(1.0, OcclusionShadowBounds.scaledRadius(0.5, 2.0, 1.0, false), 0.0);
        assertEquals(0.25, OcclusionShadowBounds.scaledRadius(0.5, 1.0, 0.5, true), 0.0);
    }

    @Test
    void shadowBoundsIncludeBlockFlooringAndStrengthDependentDepth() {
        assertEquals(1.5, OcclusionShadowBounds.horizontalPadding(0.5), 0.0);
        assertEquals(0.0, OcclusionShadowBounds.horizontalPadding(0.0), 0.0);
        assertEquals(1.5, OcclusionShadowBounds.verticalDropPadding(0.5, 1.0), 0.0);
        assertEquals(2.0, OcclusionShadowBounds.verticalDropPadding(2.0, 1.0), 0.0);
        assertEquals(3.0, OcclusionShadowBounds.verticalDropPadding(2.0, 2.0), 0.0);
        assertEquals(1.0, OcclusionShadowBounds.verticalRisePadding(0.5), 0.0);
        assertEquals(0.0, OcclusionShadowBounds.verticalRisePadding(0.0), 0.0);
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
