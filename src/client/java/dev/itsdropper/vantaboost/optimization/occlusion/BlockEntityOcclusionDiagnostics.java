package dev.itsdropper.vantaboost.optimization.occlusion;

import dev.itsdropper.vantaboost.VantaBoost;

/**
 * Opt-in counters for the block-entity occlusion prototype.
 *
 * Enable with -Dvantaboost.profileBlockEntityOcclusion=true. Timing is disabled
 * completely during ordinary gameplay.
 */
public final class BlockEntityOcclusionDiagnostics {
    public static final boolean ENABLED =
            Boolean.getBoolean("vantaboost.profileBlockEntityOcclusion");

    private static final int REPORT_INTERVAL_FRAMES = 300;

    private static long frames;
    private static long candidateCalls;
    private static long culls;
    private static long cullerNanos;
    private static long maximumCullerNanos;
    private static long cacheHits;
    private static long cachedCulls;
    private static long budgetSkips;
    private static long faceCellsPlanned;
    private static long queries;
    private static long queryCulls;
    private static long queryNanos;

    private BlockEntityOcclusionDiagnostics() {
    }

    public static void recordCandidate(long elapsedNanos, boolean occluded) {
        if (!ENABLED) {
            return;
        }
        candidateCalls++;
        cullerNanos += Math.max(0L, elapsedNanos);
        maximumCullerNanos = Math.max(maximumCullerNanos, elapsedNanos);
        if (occluded) {
            culls++;
        }
    }

    public static void recordCacheHit(boolean occluded) {
        if (!ENABLED) {
            return;
        }
        cacheHits++;
        if (occluded) {
            cachedCulls++;
        }
    }

    public static void recordBudgetSkip() {
        if (ENABLED) {
            budgetSkips++;
        }
    }

    public static void recordFaceCellsPlanned(int cells) {
        if (ENABLED) {
            faceCellsPlanned += Math.max(0, cells);
        }
    }

    public static void recordOcclusionQuery(long elapsedNanos, boolean occluded) {
        if (!ENABLED) {
            return;
        }
        queries++;
        queryNanos += Math.max(0L, elapsedNanos);
        if (occluded) {
            queryCulls++;
        }
    }

    public static void onExtractionFrame() {
        if (!ENABLED || ++frames < REPORT_INTERVAL_FRAMES) {
            return;
        }

        long averageCullerMicros = candidateCalls == 0
                ? 0L : cullerNanos / candidateCalls / 1_000L;
        long averageQueryMicros = queries == 0
                ? 0L : queryNanos / queries / 1_000L;
        VantaBoost.LOGGER.info(
                "Block-entity occlusion profile over {} frames: candidates={}, culls={}, "
                        + "avgCuller={}us, maxCuller={}us, cacheHits={}, cachedCulls={}, "
                        + "budgetSkips={}, faceCellsPlanned={}, queries={}, queryCulls={}, "
                        + "avgQuery={}us",
                frames, candidateCalls, culls, averageCullerMicros,
                maximumCullerNanos / 1_000L, cacheHits, cachedCulls, budgetSkips,
                faceCellsPlanned, queries, queryCulls, averageQueryMicros);

        frames = 0L;
        candidateCalls = 0L;
        culls = 0L;
        cullerNanos = 0L;
        maximumCullerNanos = 0L;
        cacheHits = 0L;
        cachedCulls = 0L;
        budgetSkips = 0L;
        faceCellsPlanned = 0L;
        queries = 0L;
        queryCulls = 0L;
        queryNanos = 0L;
    }
}
