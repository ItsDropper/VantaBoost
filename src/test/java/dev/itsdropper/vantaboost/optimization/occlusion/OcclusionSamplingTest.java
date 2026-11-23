package dev.itsdropper.vantaboost.optimization.occlusion;

import org.junit.jupiter.api.Test;

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
    void rejectsInvalidAxisAndFaceEdges() {
        assertFalse(OcclusionSampling.isFullyOccludedByBlockFace(
                2, 0.25, 0.25, 3, 0.75, 0.75,
                -2, 0.5, 0.5, 3, -1, 0, 0, 1, 0, 1));

        assertFalse(OcclusionSampling.isFullyOccludedByBlockFace(
                2, 0.25, 0.25, 3, 0.75, 0.75,
                -2, 0.5, 0.5, 0, -1, 0, 0, 0.0001, 0, 1));
    }
}
