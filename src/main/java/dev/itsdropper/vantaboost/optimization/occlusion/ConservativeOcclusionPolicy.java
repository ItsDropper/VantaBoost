package dev.itsdropper.vantaboost.optimization.occlusion;

/**
 * Final safety gate for entity and block-entity occlusion decisions.
 *
 * <p>This class deliberately does not perform visibility queries. It only allows a
 * renderer to skip an object after an upstream occlusion system has produced a
 * confirmed result and every correctness precondition is satisfied. Unknown,
 * stale, unsupported, or ambiguous states always render.</p>
 *
 * <p>Keep this method allocation-free: it is intended for a per-object render path.</p>
 */
public final class ConservativeOcclusionPolicy {
    /**
     * Asynchronous visibility results older than this are not trusted.
     *
     * <p>One frame is tolerated only when both the camera and object snapshots are
     * unchanged. Any movement invalidates the result regardless of its age.</p>
     */
    public static final long MAX_RESULT_AGE_FRAMES = 1L;

    private ConservativeOcclusionPolicy() {
    }

    public enum Result {
        UNKNOWN,
        VISIBLE,
        OCCLUDED
    }

    /**
     * Returns true only when skipping this object's render is provably safe.
     *
     * <p>The caller must pass positive proof for every safety condition. This
     * intentionally makes integration fail open when a condition is unavailable.</p>
     */
    public static boolean shouldSkipRender(
            Result result,
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
        if (result != Result.OCCLUDED) {
            return false;
        }

        if (sampledFrame < 0L || currentFrame < sampledFrame
                || currentFrame - sampledFrame > MAX_RESULT_AGE_FRAMES) {
            return false;
        }

        return boundsAreConservative
                && cameraOutsideBounds
                && cameraSnapshotUnchanged
                && objectSnapshotUnchanged
                && !forcedVisible
                && !glowing
                && !targeted
                && standardRenderer
                && mainRenderPass;
    }
}
