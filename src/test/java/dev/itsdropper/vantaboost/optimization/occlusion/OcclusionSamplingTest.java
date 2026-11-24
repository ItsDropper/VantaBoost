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

    private static class TestNameTagRenderer {
        protected boolean shouldShowName(TestEntity entity, double distanceToCameraSq) {
            return false;
        }
    }

    private static class TestRenderStateExtractionRenderer {
        protected void extractRenderState(TestEntity entity, TestRenderState state, float partialTicks) {
        }

        protected void finalizeRenderState(TestEntity entity, TestRenderState state) {
        }
    }

    private static class InheritedRenderStateExtractionRenderer extends TestRenderStateExtractionRenderer {
    }

    private static class CustomRenderStateExtractionRenderer extends TestRenderStateExtractionRenderer {
        @Override
        protected void extractRenderState(TestEntity entity, TestRenderState state, float partialTicks) {
        }
    }

    private static class IntermediateRenderStateExtractionRenderer extends TestRenderStateExtractionRenderer {
        @Override
        protected void extractRenderState(TestEntity entity, TestRenderState state, float partialTicks) {
        }
    }

    private static class InheritedIntermediateRenderStateExtractionRenderer extends IntermediateRenderStateExtractionRenderer {
    }

    private static class CustomRenderStateFinalizationRenderer extends TestRenderStateExtractionRenderer {
        @Override
        protected void finalizeRenderState(TestEntity entity, TestRenderState state) {
        }
    }

    private static class TestShouldRenderRenderer {
        protected boolean shouldRender(TestEntity entity, Object frustum,
                                       double cameraX, double cameraY, double cameraZ) {
            return true;
        }
    }

    private static class InheritedShouldRenderRenderer extends TestShouldRenderRenderer {
    }

    private static class CustomShouldRenderRenderer extends TestShouldRenderRenderer {
        @Override
        protected boolean shouldRender(TestEntity entity, Object frustum,
                                       double cameraX, double cameraY, double cameraZ) {
            return false;
        }
    }

    private static class TestCullingPolicyRenderer {
        protected boolean affectedByCulling(TestEntity entity) {
            return true;
        }
    }

    private static class InheritedCullingPolicyRenderer extends TestCullingPolicyRenderer {
    }

    private static class CustomCullingPolicyRenderer extends TestCullingPolicyRenderer {
        @Override
        protected boolean affectedByCulling(TestEntity entity) {
            return false;
        }
    }

    private static class TestNameTagSubmissionRenderer {
        protected void submitNameTag(TestRenderState state, Object pose, Object collector, Object camera) {
        }
    }

    private static class InheritedNameTagSubmissionRenderer extends TestNameTagSubmissionRenderer {
    }

    private static class CustomNameTagSubmissionRenderer extends TestNameTagSubmissionRenderer {
        @Override
        protected void submitNameTag(TestRenderState state, Object pose, Object collector, Object camera) {
        }
    }

    private static class InheritedNameTagRenderer extends TestNameTagRenderer {
    }

    private static class CustomNameTagRenderer extends TestNameTagRenderer {
        @Override
        protected boolean shouldShowName(TestEntity entity, double distanceToCameraSq) {
            return true;
        }
    }

    private static class TestLivingRendererBase {
        protected void scale(TestRenderState state, Object pose) {
        }

        protected void setupRotations(TestRenderState state, Object pose) {
        }
    }

    private static class InheritedLivingRenderer extends TestLivingRendererBase {
    }

    private static class CustomScaleRenderer extends TestLivingRendererBase {
        @Override
        protected void scale(TestRenderState state, Object pose) {
        }
    }

    private static class CustomRotationRenderer extends TestLivingRendererBase {
        @Override
        protected void setupRotations(TestRenderState state, Object pose) {
        }
    }

    private static class TestSubmitRenderer {
        protected void submit(TestRenderState state, Object pose, Object collector, Object camera) {
        }
    }

    private static class InheritedSubmitRenderer extends TestSubmitRenderer {
    }

    private static class CustomSubmitRenderer extends TestSubmitRenderer {
        @Override
        protected void submit(TestRenderState state, Object pose, Object collector, Object camera) {
        }
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
    void rayHitMustLieOnTheActualOuterBlockFacePlane() {
        assertTrue(OcclusionSampling.isHitOnBlockFace(4.0, 4, -1));
        assertTrue(OcclusionSampling.isHitOnBlockFace(5.0, 4, 1));
        assertTrue(OcclusionSampling.isHitOnBlockFace(4.0 + 5.0E-6, 4, -1));
        assertFalse(OcclusionSampling.isHitOnBlockFace(4.0 + 2.0E-5, 4, -1));
        assertFalse(OcclusionSampling.isHitOnBlockFace(4.5, 4, -1));
        assertFalse(OcclusionSampling.isHitOnBlockFace(Double.NaN, 4, -1));
        assertFalse(OcclusionSampling.isHitOnBlockFace(4.0, 4, 0));
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
    void projectedCornerRectangleContainsRandomInteriorProjections() {
        Random random = new Random(0x51A7E);
        double[][] cases = {
                {2, 0.25, 0.25, 3, 2.75, 0.75, -2, 1.5, 0.5, 0, -1, 0},
                {2, 0.25, 0.25, 3, 2.75, 0.75, 5, 1.5, 0.5, 0, 1, 4},
                {0.25, 2, 0.25, 2.75, 3, 0.75, 1.5, -2, 0.5, 1, -1, 0},
                {0.25, 0.25, 2, 2.75, 0.75, 3, 1.5, 0.5, -2, 2, -1, 0}
        };

        for (double[] testCase : cases) {
            double[] projectedBounds = new double[4];
            int axis = (int) testCase[9];
            assertTrue(OcclusionSampling.projectAabbOntoPlane(
                    testCase[0], testCase[1], testCase[2],
                    testCase[3], testCase[4], testCase[5],
                    testCase[6], testCase[7], testCase[8],
                    axis, (int) testCase[10], testCase[11], projectedBounds));
            int[] cellRange = new int[4];
            assertTrue(OcclusionSampling.getFaceCellRange(projectedBounds, cellRange, 64));

            int uAxis = axis == 0 ? 1 : 0;
            int vAxis = axis == 2 ? 1 : 2;
            for (int sample = 0; sample < 2_000; sample++) {
                double x = between(random, testCase[0], testCase[3]);
                double y = between(random, testCase[1], testCase[4]);
                double z = between(random, testCase[2], testCase[5]);
                double cameraAxis = coordinate(testCase[6], testCase[7], testCase[8], axis);
                double targetAxis = coordinate(x, y, z, axis);
                double t = (testCase[11] - cameraAxis) / (targetAxis - cameraAxis);
                double projectedU = coordinate(testCase[6], testCase[7], testCase[8], uAxis)
                        + (coordinate(x, y, z, uAxis)
                        - coordinate(testCase[6], testCase[7], testCase[8], uAxis)) * t;
                double projectedV = coordinate(testCase[6], testCase[7], testCase[8], vAxis)
                        + (coordinate(x, y, z, vAxis)
                        - coordinate(testCase[6], testCase[7], testCase[8], vAxis)) * t;

                assertTrue(projectedU >= projectedBounds[0] - 1.0E-10);
                assertTrue(projectedU <= projectedBounds[1] + 1.0E-10);
                assertTrue(projectedV >= projectedBounds[2] - 1.0E-10);
                assertTrue(projectedV <= projectedBounds[3] + 1.0E-10);
                assertTrue(projectedU > cellRange[0] + 1.0E-4);
                assertTrue(projectedU < cellRange[1] + 1.0 - 1.0E-4);
                assertTrue(projectedV > cellRange[2] + 1.0E-4);
                assertTrue(projectedV < cellRange[3] + 1.0 - 1.0E-4);
            }
        }
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
        assertFalse(OcclusionSampling.getFaceCellRange(
                new double[]{3_000_000_000.0, 3_000_000_001.0, 0.25, 0.75},
                cells, 64));
        assertFalse(OcclusionSampling.getFaceCellRange(
                new double[]{-3_000_000_001.0, -3_000_000_000.0, 0.25, 0.75},
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
    void visibleCacheEntriesSurviveCameraMovementButOccludedEntriesDoNot() {
        assertTrue(OcclusionCachePolicy.isReusable(false, 4L, 5L, false));
        assertTrue(OcclusionCachePolicy.isReusable(false, 4L, 4L, true));
        assertFalse(OcclusionCachePolicy.isReusable(true, 4L, 4L, false));
        assertTrue(OcclusionCachePolicy.isReusable(true, 4L, 4L, true));
        assertFalse(OcclusionCachePolicy.isReusable(true, 4L, 5L, true));
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
    void rendererSpecificRenderStateHooksAreDetected() {
        assertFalse(RendererOverridePolicy.declaresMethodWithFirstParameter(
                InheritedRenderStateExtractionRenderer.class, TestRenderStateExtractionRenderer.class,
                "extractRenderState", TestEntity.class));
        assertTrue(RendererOverridePolicy.declaresMethodWithFirstParameter(
                CustomRenderStateExtractionRenderer.class, TestRenderStateExtractionRenderer.class,
                "extractRenderState", TestEntity.class));
        assertTrue(RendererOverridePolicy.declaresMethodWithFirstParameter(
                InheritedIntermediateRenderStateExtractionRenderer.class, TestRenderStateExtractionRenderer.class,
                "extractRenderState", TestEntity.class));
        assertTrue(RendererOverridePolicy.declaresMethodWithFirstParameter(
                CustomRenderStateFinalizationRenderer.class, TestRenderStateExtractionRenderer.class,
                "finalizeRenderState", TestEntity.class));
    }

    @Test
    void rendererSpecificShouldRenderOverridesAreDetected() {
        assertFalse(RendererOverridePolicy.declaresMethodWithFirstParameter(
                InheritedShouldRenderRenderer.class, TestShouldRenderRenderer.class,
                "shouldRender", TestEntity.class));
        assertTrue(RendererOverridePolicy.declaresMethodWithFirstParameter(
                CustomShouldRenderRenderer.class, TestShouldRenderRenderer.class,
                "shouldRender", TestEntity.class));
    }

    @Test
    void rendererSpecificCullingPoliciesAreDetected() {
        assertFalse(RendererOverridePolicy.declaresSingleArgumentMethod(
                InheritedCullingPolicyRenderer.class, TestCullingPolicyRenderer.class,
                "affectedByCulling", TestEntity.class));
        assertTrue(RendererOverridePolicy.declaresSingleArgumentMethod(
                CustomCullingPolicyRenderer.class, TestCullingPolicyRenderer.class,
                "affectedByCulling", TestEntity.class));
    }

    @Test
    void rendererSpecificNameTagSubmissionOverridesAreDetected() {
        assertFalse(RendererOverridePolicy.declaresMethodWithFirstParameter(
                InheritedNameTagSubmissionRenderer.class, TestNameTagSubmissionRenderer.class,
                "submitNameTag", TestRenderState.class));
        assertTrue(RendererOverridePolicy.declaresMethodWithFirstParameter(
                CustomNameTagSubmissionRenderer.class, TestNameTagSubmissionRenderer.class,
                "submitNameTag", TestRenderState.class));
    }

    @Test
    void rendererSpecificNameTagOverloadsAreDetected() {
        assertFalse(RendererOverridePolicy.declaresMethodWithFirstParameter(
                InheritedNameTagRenderer.class, TestNameTagRenderer.class,
                "shouldShowName", TestEntity.class));
        assertTrue(RendererOverridePolicy.declaresMethodWithFirstParameter(
                CustomNameTagRenderer.class, TestNameTagRenderer.class,
                "shouldShowName", TestEntity.class));
    }

    @Test
    void livingRendererScaleAndRotationOverridesAreDetected() {
        assertFalse(RendererOverridePolicy.declaresMethodWithFirstParameter(
                InheritedLivingRenderer.class, TestLivingRendererBase.class,
                "scale", TestRenderState.class));
        assertTrue(RendererOverridePolicy.declaresMethodWithFirstParameter(
                CustomScaleRenderer.class, TestLivingRendererBase.class,
                "scale", TestRenderState.class));
        assertTrue(RendererOverridePolicy.declaresMethodWithFirstParameter(
                CustomRotationRenderer.class, TestLivingRendererBase.class,
                "setupRotations", TestRenderState.class));
    }

    @Test
    void rendererSpecificSubmitOverridesFailClosed() {
        assertFalse(RendererOverridePolicy.declaresMethodWithFirstParameter(
                InheritedSubmitRenderer.class, TestSubmitRenderer.class,
                "submit", TestRenderState.class));
        assertTrue(RendererOverridePolicy.declaresMethodWithFirstParameter(
                CustomSubmitRenderer.class, TestSubmitRenderer.class,
                "submit", TestRenderState.class));
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
