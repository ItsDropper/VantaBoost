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
                0.25, 2, 0.25, 0.75, 3, 0.75,
                0.5, -2, 0.5,
                1, -1, 0,
                0, 1, 0, 1));

        assertTrue(OcclusionSampling.isFullyOccludedByBlockFace(
                0.25, 0.25, 2, 0.75, 0.75, 3,
                0.5, 0.5, -2,
                2, -1, 0,
                0, 1, 0, 1));

        assertTrue(OcclusionSampling.isFullyOccludedByBlockFace(
                -3, 0.25, 0.25, -2, 0.75, 0.75,
                5, 0.5, 0.5,
                0, 1, 1,
                0, 1, 0, 1));
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
