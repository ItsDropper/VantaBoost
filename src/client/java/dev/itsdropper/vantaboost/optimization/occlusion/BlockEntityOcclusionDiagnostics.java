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
    private static final NanoHistogram CULLER_LATENCY = ENABLED ? new NanoHistogram() : null;
    private static final NanoHistogram QUERY_LATENCY = ENABLED ? new NanoHistogram() : null;
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
        CULLER_LATENCY.record(elapsedNanos);
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
        QUERY_LATENCY.record(elapsedNanos);
        if (occluded) {
            queryCulls++;
        }
    }

    public static void onExtractionFrame() {
        if (!ENABLED || ++frames < REPORT_INTERVAL_FRAMES) {
            return;
        }

        long averageCullerNanos = candidateCalls == 0
                ? 0L : cullerNanos / candidateCalls;
        long averageQueryNanos = queries == 0
                ? 0L : queryNanos / queries;
        VantaBoost.LOGGER.info(
                "Block-entity occlusion profile over {} frames: candidates={}, culls={}, "
                        + "avgCuller={}ns, p50Culler<={}ns, p95Culler<={}ns, p99Culler<={}ns, maxCuller={}ns, "
                        + "cacheHits={}, cachedCulls={}, budgetSkips={}, faceCellsPlanned={}, "
                        + "queries={}, queryCulls={}, avgQuery={}ns, p50Query<={}ns, p95Query<={}ns, p99Query<={}ns",
                frames, candidateCalls, culls, averageCullerNanos,
                CULLER_LATENCY.percentileUpperBoundNanos(0.50),
                CULLER_LATENCY.percentileUpperBoundNanos(0.95),
                CULLER_LATENCY.percentileUpperBoundNanos(0.99),
                maximumCullerNanos, cacheHits, cachedCulls, budgetSkips,
                faceCellsPlanned, queries, queryCulls, averageQueryNanos,
                QUERY_LATENCY.percentileUpperBoundNanos(0.50),
                QUERY_LATENCY.percentileUpperBoundNanos(0.95),
                QUERY_LATENCY.percentileUpperBoundNanos(0.99));

        frames = 0L;
        candidateCalls = 0L;
        culls = 0L;
        cullerNanos = 0L;
        maximumCullerNanos = 0L;
        CULLER_LATENCY.reset();
        cacheHits = 0L;
        cachedCulls = 0L;
        budgetSkips = 0L;
        faceCellsPlanned = 0L;
        queries = 0L;
        queryCulls = 0L;
        queryNanos = 0L;
        QUERY_LATENCY.reset();
    }
}
