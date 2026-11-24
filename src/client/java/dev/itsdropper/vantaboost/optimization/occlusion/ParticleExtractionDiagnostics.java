package dev.itsdropper.vantaboost.optimization.occlusion;

import dev.itsdropper.vantaboost.VantaBoost;

/**
 * Opt-in measurements for vanilla quad-particle extraction.
 *
 * Enable with -Dvantaboost.profileParticleExtraction=true. The profiler counts
 * vanilla frustum candidates and survivors, and times the full particle-engine
 * extraction plus quad-group extraction. It never changes visibility results.
 */
public final class ParticleExtractionDiagnostics {
    public static final boolean ENABLED =
            Boolean.getBoolean("vantaboost.profileParticleExtraction");

    private static final int REPORT_INTERVAL_EXTRACTIONS = 300;

    private static long extractionStartedNanos;
    private static long quadGroupStartedNanos;
    private static long extractionQuadGroupNanos;
    private static long extractionQuadGroups;
    private static long extractionParticleTests;
    private static long extractionFrustumVisible;
    private static long extractionFrustumRejected;

    private static long extractionCalls;
    private static long engineExtractionNanos;
    private static long maximumEngineExtractionNanos;
    private static final NanoHistogram ENGINE_LATENCY = ENABLED ? new NanoHistogram() : null;
    private static final NanoHistogram QUAD_GROUP_LATENCY = ENABLED ? new NanoHistogram() : null;
    private static long quadGroupExtractionNanos;
    private static long quadGroups;
    private static long particleTests;
    private static long frustumVisible;
    private static long frustumRejected;

    private ParticleExtractionDiagnostics() {
    }

    public static void beginExtraction() {
        if (!ENABLED) {
            return;
        }
        extractionStartedNanos = System.nanoTime();
        extractionQuadGroupNanos = 0L;
        extractionQuadGroups = 0L;
        extractionParticleTests = 0L;
        extractionFrustumVisible = 0L;
        extractionFrustumRejected = 0L;
    }

    public static void endExtraction() {
        if (!ENABLED) {
            return;
        }

        long elapsed = Math.max(0L, System.nanoTime() - extractionStartedNanos);
        extractionCalls++;
        engineExtractionNanos += elapsed;
        maximumEngineExtractionNanos = Math.max(maximumEngineExtractionNanos, elapsed);
        ENGINE_LATENCY.record(elapsed);
        quadGroupExtractionNanos += extractionQuadGroupNanos;
        quadGroups += extractionQuadGroups;
        particleTests += extractionParticleTests;
        frustumVisible += extractionFrustumVisible;
        frustumRejected += extractionFrustumRejected;

        if (extractionCalls < REPORT_INTERVAL_EXTRACTIONS) {
            return;
        }

        VantaBoost.LOGGER.info(
                "Particle extraction profile over {} extraction calls: avgEngine={}ns, "
                        + "p50Engine<={}ns, p95Engine<={}ns, p99Engine<={}ns, maxEngine={}ns, "
                        + "avgQuadGroups={}/call, avgQuadExtraction={}ns/call, "
                        + "p50QuadGroup<={}ns, p95QuadGroup<={}ns, p99QuadGroup<={}ns, "
                        + "quadCandidates={}/call, frustumVisible={}/call, "
                        + "frustumRejected={}/call",
                extractionCalls,
                engineExtractionNanos / extractionCalls,
                ENGINE_LATENCY.percentileUpperBoundNanos(0.50),
                ENGINE_LATENCY.percentileUpperBoundNanos(0.95),
                ENGINE_LATENCY.percentileUpperBoundNanos(0.99),
                maximumEngineExtractionNanos,
                (double) quadGroups / extractionCalls,
                quadGroupExtractionNanos / extractionCalls,
                QUAD_GROUP_LATENCY.percentileUpperBoundNanos(0.50),
                QUAD_GROUP_LATENCY.percentileUpperBoundNanos(0.95),
                QUAD_GROUP_LATENCY.percentileUpperBoundNanos(0.99),
                (double) particleTests / extractionCalls,
                (double) frustumVisible / extractionCalls,
                (double) frustumRejected / extractionCalls);

        extractionCalls = 0L;
        engineExtractionNanos = 0L;
        maximumEngineExtractionNanos = 0L;
        ENGINE_LATENCY.reset();
        QUAD_GROUP_LATENCY.reset();
        quadGroupExtractionNanos = 0L;
        quadGroups = 0L;
        particleTests = 0L;
        frustumVisible = 0L;
        frustumRejected = 0L;
    }

    public static void beginQuadGroup() {
        if (!ENABLED) {
            return;
        }
        extractionQuadGroups++;
        quadGroupStartedNanos = System.nanoTime();
    }

    public static void endQuadGroup() {
        if (ENABLED) {
            long elapsed = Math.max(0L, System.nanoTime() - quadGroupStartedNanos);
            extractionQuadGroupNanos += elapsed;
            QUAD_GROUP_LATENCY.record(elapsed);
        }
    }

    /** Records the original vanilla frustum decision and returns it unchanged. */
    public static boolean recordFrustumResult(boolean vanillaVisible) {
        if (ENABLED) {
            extractionParticleTests++;
            if (vanillaVisible) {
                extractionFrustumVisible++;
            } else {
                extractionFrustumRejected++;
            }
        }
        return vanillaVisible;
    }
}
