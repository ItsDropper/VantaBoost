package dev.itsdropper.vantaboost.optimization.occlusion;

/**
 * Defines which cached visibility decisions remain safe after world topology
 * changes. A visible result only causes extra rendering; an occluded result
 * can become incorrect when an occluding block is changed.
 */
public final class OcclusionCachePolicy {
    private OcclusionCachePolicy() {
    }

    public static boolean isReusable(
            boolean occluded, long entryRevision, long currentRevision
    ) {
        return !occluded || entryRevision == currentRevision;
    }
}
