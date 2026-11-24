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

    private static final int REPORT_INTERVAL_FRAMES = 300;

    private static long frameStartedNanos;
    private static long quadGroupStartedNanos;
    private static long frameQuadGroupNanos;
    private static long frameQuadGroups;
    private static long frameParticleTests;
    private static long frameFrustumVisible;
    private static long frameFrustumRejected;

    private static long frames;
    private static long engineExtractionNanos;
    private static long maximumEngineExtractionNanos;
    private static long quadGroupExtractionNanos;
    private static long quadGroups;
    private static long particleTests;
    private static long frustumVisible;
    private static long frustumRejected;

    private ParticleExtractionDiagnostics() {
    }

    public static void beginFrame() {
        if (!ENABLED) {
            return;
        }
        frameStartedNanos = System.nanoTime();
        frameQuadGroupNanos = 0L;
        frameQuadGroups = 0L;
        frameParticleTests = 0L;
        frameFrustumVisible = 0L;
        frameFrustumRejected = 0L;
    }

    public static void endFrame() {
        if (!ENABLED) {
            return;
        }

        long elapsed = Math.max(0L, System.nanoTime() - frameStartedNanos);
        frames++;
        engineExtractionNanos += elapsed;
        maximumEngineExtractionNanos = Math.max(maximumEngineExtractionNanos, elapsed);
        quadGroupExtractionNanos += frameQuadGroupNanos;
        quadGroups += frameQuadGroups;
        particleTests += frameParticleTests;
        frustumVisible += frameFrustumVisible;
        frustumRejected += frameFrustumRejected;

        if (frames < REPORT_INTERVAL_FRAMES) {
            return;
        }

        VantaBoost.LOGGER.info(
                "Particle extraction profile over {} frames: avgEngine={}us, maxEngine={}us, "
                        + "avgQuadGroups={}/frame, avgQuadExtraction={}us/frame, "
                        + "quadCandidates={}/frame, frustumVisible={}/frame, "
                        + "frustumRejected={}/frame",
                frames,
                engineExtractionNanos / frames / 1_000L,
                maximumEngineExtractionNanos / 1_000L,
                (double) quadGroups / frames,
                quadGroupExtractionNanos / frames / 1_000L,
                (double) particleTests / frames,
                (double) frustumVisible / frames,
                (double) frustumRejected / frames);

        frames = 0L;
        engineExtractionNanos = 0L;
        maximumEngineExtractionNanos = 0L;
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
        frameQuadGroups++;
        quadGroupStartedNanos = System.nanoTime();
    }

    public static void endQuadGroup() {
        if (ENABLED) {
            frameQuadGroupNanos += Math.max(0L, System.nanoTime() - quadGroupStartedNanos);
        }
    }

    /** Records the original vanilla frustum decision and returns it unchanged. */
    public static boolean recordFrustumResult(boolean vanillaVisible) {
        if (ENABLED) {
            frameParticleTests++;
            if (vanillaVisible) {
                frameFrustumVisible++;
            } else {
                frameFrustumRejected++;
            }
        }
        return vanillaVisible;
    }
}
