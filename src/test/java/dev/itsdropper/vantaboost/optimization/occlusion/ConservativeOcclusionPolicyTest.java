package dev.itsdropper.vantaboost.optimization.occlusion;

import org.junit.jupiter.api.Test;

import static dev.itsdropper.vantaboost.optimization.occlusion.ConservativeOcclusionPolicy.Result.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConservativeOcclusionPolicyTest {
    private static final long FRAME = 100L;

    private static boolean safeDecision(
            ConservativeOcclusionPolicy.Result result,
            long sampledFrame,
            long currentFrame,
            boolean boundsAreConservative,
            boolean cameraOutsideBounds,
            boolean cameraSnapshotUnchanged,
            boolean objectSnapshotUnchanged,
            boolean forcedVisible,
            boolean glowing,
            boolean targeted,
            boolean standardRenderer,
            boolean mainRenderPass
    ) {
        return ConservativeOcclusionPolicy.shouldSkipRender(
                result,
                sampledFrame,
                currentFrame,
                boundsAreConservative,
                cameraOutsideBounds,
                cameraSnapshotUnchanged,
                objectSnapshotUnchanged,
                forcedVisible,
                glowing,
                targeted,
                standardRenderer,
                mainRenderPass
        );
    }

    private static boolean safeDecision() {
        return safeDecision(OCCLUDED, FRAME, FRAME,
                true, true, true, true,
                false, false, false, true, true);
    }

    @Test
    void skipsOnlyConfirmedOccludedObjectsWhenEverySafetyConditionHolds() {
        assertTrue(safeDecision());
    }

    @Test
    void unknownAndVisibleResultsAlwaysFailOpen() {
        assertFalse(safeDecision(UNKNOWN, FRAME, FRAME,
                true, true, true, true, false, false, false, true, true));
        assertFalse(safeDecision(VISIBLE, FRAME, FRAME,
                true, true, true, true, false, false, false, true, true));
    }

    @Test
    void acceptsOneFrameOldResultOnlyWhenSnapshotsAreUnchanged() {
        assertTrue(safeDecision(OCCLUDED, FRAME - 1, FRAME,
                true, true, true, true, false, false, false, true, true));

        assertFalse(safeDecision(OCCLUDED, FRAME - 1, FRAME,
                true, true, false, true, false, false, false, true, true));
        assertFalse(safeDecision(OCCLUDED, FRAME - 1, FRAME,
                true, true, true, false, false, false, false, true, true));
    }

    @Test
    void staleOrFutureResultsAlwaysFailOpen() {
        assertFalse(safeDecision(OCCLUDED, FRAME - 2, FRAME,
                true, true, true, true, false, false, false, true, true));
        assertFalse(safeDecision(OCCLUDED, FRAME + 1, FRAME,
                true, true, true, true, false, false, false, true, true));
        assertFalse(safeDecision(OCCLUDED, -1, FRAME,
                true, true, true, true, false, false, false, true, true));
    }

    @Test
    void invalidBoundsOrCameraIntersectionAlwaysFailOpen() {
        assertFalse(safeDecision(OCCLUDED, FRAME, FRAME,
                false, true, true, true, false, false, false, true, true));
        assertFalse(safeDecision(OCCLUDED, FRAME, FRAME,
                true, false, true, true, false, false, false, true, true));
    }

    @Test
    void specialVisibilityCasesAlwaysRender() {
        assertFalse(safeDecision(OCCLUDED, FRAME, FRAME,
                true, true, true, true, true, false, false, true, true));
        assertFalse(safeDecision(OCCLUDED, FRAME, FRAME,
                true, true, true, true, false, true, false, true, true));
        assertFalse(safeDecision(OCCLUDED, FRAME, FRAME,
                true, true, true, true, false, false, true, true, true));
    }

    @Test
    void unsupportedRendererAndNonMainPassAlwaysFailOpen() {
        assertFalse(safeDecision(OCCLUDED, FRAME, FRAME,
                true, true, true, true, false, false, false, false, true));
        assertFalse(safeDecision(OCCLUDED, FRAME, FRAME,
                true, true, true, true, false, false, false, true, false));
    }

    @Test
    void eachMissingSafetyPreconditionIndependentlyPreventsCulling() {
        boolean[][] cases = {
                {false, true, true, true},
                {true, false, true, true},
                {true, true, false, true},
                {true, true, true, false}
        };

        for (boolean[] conditions : cases) {
            assertFalse(safeDecision(OCCLUDED, FRAME, FRAME,
                    conditions[0], conditions[1], conditions[2], conditions[3],
                    false, false, false, true, true));
        }
    }
}
