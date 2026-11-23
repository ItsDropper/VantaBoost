package dev.itsdropper.vantaboost.optimization.occlusion;

/**
 * Defines which cached visibility decisions remain safe after world topology
 * or camera changes. A visible result only causes extra rendering; an occluded
 * result can become incorrect when its occluder or camera changes.
 */
public final class OcclusionCachePolicy {
    private OcclusionCachePolicy() {
    }

    public static boolean isReusable(
            boolean occluded, long entryRevision, long currentRevision
    ) {
        return !occluded || entryRevision == currentRevision;
    }

    /**
     * Visible results remain conservative across camera movement because they
     * only cause extra rendering. Positive occlusion results require the exact
     * camera snapshot used to prove that the complete bounds were hidden.
     */
    public static boolean isReusable(
            boolean occluded,
            long entryRevision,
            long currentRevision,
            boolean cameraSnapshotMatches
    ) {
        return isReusable(occluded, entryRevision, currentRevision)
                && (!occluded || cameraSnapshotMatches);
    }
}
