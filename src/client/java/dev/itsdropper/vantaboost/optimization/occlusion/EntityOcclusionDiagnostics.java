package dev.itsdropper.vantaboost.optimization.occlusion;

import dev.itsdropper.vantaboost.VantaBoost;

/**
 * Opt-in, allocation-free counters for measuring the entity occlusion prototype.
 * All methods are called on the client render/extraction thread.
 *
 * Enable with -Dvantaboost.profileEntityOcclusion=true. A report is emitted
 * every 300 main entity-extraction frames; profiling is completely dormant
 * when the property is absent.
 */
public final class EntityOcclusionDiagnostics {
    public static final boolean ENABLED =
            Boolean.getBoolean("vantaboost.profileEntityOcclusion");

    private static final int REPORT_INTERVAL_FRAMES = 300;

    private static long frames;
    private static long candidateCalls;
    private static long cacheHits;
    private static long cacheOcclusionHits;
    private static long budgetSkips;
    private static long rayQueries;
    private static long rayOcclusions;
    private static long rayQueryNanos;

    private EntityOcclusionDiagnostics() {
    }

    public static void recordCandidate() {
        if (ENABLED) {
            candidateCalls++;
        }
    }

    public static void recordCacheHit(boolean occluded) {
        if (ENABLED) {
            cacheHits++;
            if (occluded) {
                cacheOcclusionHits++;
            }
        }
    }

    public static void recordBudgetSkip() {
        if (ENABLED) {
            budgetSkips++;
        }
    }

    public static void recordRayQuery(long elapsedNanos, boolean occluded) {
        if (ENABLED) {
            rayQueries++;
            rayQueryNanos += Math.max(0L, elapsedNanos);
            if (occluded) {
                rayOcclusions++;
            }
        }
    }

    public static void onMainEntityExtractionFrame() {
        if (!ENABLED) {
            return;
        }

        frames++;
        if (frames < REPORT_INTERVAL_FRAMES) {
            return;
        }

        long averageQueryMicros = rayQueries == 0
                ? 0L : rayQueryNanos / rayQueries / 1_000L;
        VantaBoost.LOGGER.info(
                "Entity occlusion profile over {} frames: candidates={}, cacheHits={}, "
                        + "cachedOcclusions={}, budgetSkips={}, rayQueries={}, "
                        + "rayOcclusions={}, avgRayQuery={}us",
                frames, candidateCalls, cacheHits, cacheOcclusionHits, budgetSkips,
                rayQueries, rayOcclusions, averageQueryMicros);

        frames = 0L;
        candidateCalls = 0L;
        cacheHits = 0L;
        cacheOcclusionHits = 0L;
        budgetSkips = 0L;
        rayQueries = 0L;
        rayOcclusions = 0L;
        rayQueryNanos = 0L;
    }
}
