package dev.itsdropper.vantaboost.optimization.occlusion;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OcclusionSamplingTest {
    @Test
    void requiresAllTwentySevenSamplesToBeOccluded() {
        AtomicInteger calls = new AtomicInteger();

        assertTrue(OcclusionSampling.isFullyOccluded(
                0, 0, 0, 1, 2, 1,
                -2, 1, 0.5,
                (x, y, z) -> {
                    calls.incrementAndGet();
                    return true;
                }));

        assertEquals(27, calls.get());
    }

    @Test
    void anyVisibleSampleKeepsTheWholeObjectVisible() {
        AtomicInteger calls = new AtomicInteger();

        assertFalse(OcclusionSampling.isFullyOccluded(
                0, 0, 0, 1, 2, 1,
                -2, 1, 0.5,
                (x, y, z) -> calls.incrementAndGet() != 14));

        assertEquals(14, calls.get());
    }

    @Test
    void cameraInsideOrOnTheBoundsAlwaysFailsOpen() {
        assertFalse(OcclusionSampling.isFullyOccluded(
                0, 0, 0, 1, 2, 1,
                0.5, 1, 0.5, (x, y, z) -> true));
        assertFalse(OcclusionSampling.isFullyOccluded(
                0, 0, 0, 1, 2, 1,
                0, 1, 0.5, (x, y, z) -> true));
    }

    @Test
    void degenerateAndOversizedBoundsAlwaysFailOpen() {
        assertFalse(OcclusionSampling.isFullyOccluded(
                0, 0, 0, 0.1, 2, 1,
                -2, 1, 0.5, (x, y, z) -> true));
        assertFalse(OcclusionSampling.isFullyOccluded(
                0, 0, 0, 7, 2, 1,
                -2, 1, 0.5, (x, y, z) -> true));
    }

    @Test
    void nonFiniteCoordinatesAlwaysFailOpen() {
        assertFalse(OcclusionSampling.isFullyOccluded(
                Double.NaN, 0, 0, 1, 2, 1,
                -2, 1, 0.5, (x, y, z) -> true));
    }

    @Test
    void rayQueryFailureAlwaysFailsOpen() {
        assertFalse(OcclusionSampling.isFullyOccluded(
                0, 0, 0, 1, 2, 1,
                -2, 1, 0.5,
                (x, y, z) -> {
                    throw new IllegalStateException("test failure");
                }));
    }
}
