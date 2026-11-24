package dev.itsdropper.vantaboost.optimization.occlusion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OcclusionCachePolicyTest {
    @Test
    void visibleResultRemainsReusableAfterWorldChanges() {
        assertTrue(OcclusionCachePolicy.isReusable(false, 10L, 11L));
    }

    @Test
    void occludedResultRequiresUnchangedWorldRevision() {
        assertTrue(OcclusionCachePolicy.isReusable(true, 10L, 10L));
        assertFalse(OcclusionCachePolicy.isReusable(true, 10L, 11L));
    }

    @Test
    void visibleResultDoesNotRequireMatchingCameraSnapshot() {
        assertTrue(OcclusionCachePolicy.isReusable(false, 10L, 11L, false));
        assertTrue(OcclusionCachePolicy.isReusable(false, 10L, 10L, false));
    }

    @Test
    void occludedResultRequiresMatchingCameraSnapshot() {
        assertTrue(OcclusionCachePolicy.isReusable(true, 10L, 10L, true));
        assertFalse(OcclusionCachePolicy.isReusable(true, 10L, 10L, false));
    }

    @Test
    void occludedResultRequiresBothWorldAndCameraSnapshots() {
        assertFalse(OcclusionCachePolicy.isReusable(true, 10L, 11L, true));
        assertFalse(OcclusionCachePolicy.isReusable(true, 10L, 10L, false));
    }
}
