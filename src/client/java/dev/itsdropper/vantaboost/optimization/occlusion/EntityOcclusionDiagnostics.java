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
    private static long candidateOcclusions;
    private static long cullerNanos;
    private static long maximumCullerNanos;
    private static final NanoHistogram CULLER_LATENCY = ENABLED ? new NanoHistogram() : null;
    private static final NanoHistogram QUERY_LATENCY = ENABLED ? new NanoHistogram() : null;
    private static long cacheHits;
    private static long cacheOcclusionHits;
    private static long budgetSkips;
    private static long faceCellsPlanned;
    private static long occlusionQueries;
    private static long occlusionResults;
    private static long occlusionQueryNanos;

    private EntityOcclusionDiagnostics() {
    }

    public static void recordCandidate(long elapsedNanos, boolean occluded) {
        if (ENABLED) {
            candidateCalls++;
            cullerNanos += Math.max(0L, elapsedNanos);
            maximumCullerNanos = Math.max(maximumCullerNanos, elapsedNanos);
            CULLER_LATENCY.record(elapsedNanos);
            if (occluded) {
                candidateOcclusions++;
            }
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

    public static void recordFaceCellsPlanned(int cells) {
        if (ENABLED) {
            faceCellsPlanned += Math.max(0, cells);
        }
    }

    public static void recordOcclusionQuery(long elapsedNanos, boolean occluded) {
        if (ENABLED) {
            occlusionQueries++;
            occlusionQueryNanos += Math.max(0L, elapsedNanos);
            QUERY_LATENCY.record(elapsedNanos);
            if (occluded) {
                occlusionResults++;
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

        long averageCullerNanos = candidateCalls == 0
                ? 0L : cullerNanos / candidateCalls;
        long averageQueryNanos = occlusionQueries == 0
                ? 0L : occlusionQueryNanos / occlusionQueries;
        VantaBoost.LOGGER.info(
                "Entity occlusion profile over {} frames: candidates={}, cullerOcclusions={}, "
                        + "avgCuller={}ns, p50Culler<={}ns, p95Culler<={}ns, p99Culler<={}ns, maxCuller={}ns, "
                        + "cacheHits={}, cachedOcclusions={}, budgetSkips={}, faceCellsPlanned={}, "
                        + "occlusionQueries={}, occlusionResults={}, avgQuery={}ns, "
                        + "p50Query<={}ns, p95Query<={}ns, p99Query<={}ns",
                frames, candidateCalls, candidateOcclusions, averageCullerNanos,
                CULLER_LATENCY.percentileUpperBoundNanos(0.50) / 1_000L,
                CULLER_LATENCY.percentileUpperBoundNanos(0.95) / 1_000L,
                CULLER_LATENCY.percentileUpperBoundNanos(0.99) / 1_000L,
                maximumCullerNanos, cacheHits, cacheOcclusionHits, budgetSkips,
                faceCellsPlanned, occlusionQueries, occlusionResults, averageQueryNanos,
                QUERY_LATENCY.percentileUpperBoundNanos(0.50) / 1_000L,
                QUERY_LATENCY.percentileUpperBoundNanos(0.95) / 1_000L,
                QUERY_LATENCY.percentileUpperBoundNanos(0.99) / 1_000L);

        frames = 0L;
        candidateCalls = 0L;
        candidateOcclusions = 0L;
        cullerNanos = 0L;
        maximumCullerNanos = 0L;
        CULLER_LATENCY.reset();
        cacheHits = 0L;
        cacheOcclusionHits = 0L;
        budgetSkips = 0L;
        faceCellsPlanned = 0L;
        occlusionQueries = 0L;
        occlusionResults = 0L;
        occlusionQueryNanos = 0L;
        QUERY_LATENCY.reset();
    }
}
