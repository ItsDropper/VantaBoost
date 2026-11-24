package dev.itsdropper.vantaboost.optimization.occlusion;

/**
 * Fixed-memory logarithmic histogram for opt-in nanosecond diagnostics.
 *
 * <p>Percentiles are reported as the upper bound of the bucket containing the
 * requested rank. Recording performs no allocation.</p>
 */
final class NanoHistogram {
    private static final int BUCKET_COUNT = Long.SIZE;
    private final long[] buckets = new long[BUCKET_COUNT];
    private long sampleCount;

    void record(long elapsedNanos) {
        long value = Math.max(0L, elapsedNanos);
        int bucket = value <= 1L
                ? 0
                : Long.SIZE - Long.numberOfLeadingZeros(value - 1L);
        buckets[bucket]++;
        sampleCount++;
    }

    long percentileUpperBoundNanos(double percentile) {
        if (!(percentile > 0.0 && percentile <= 1.0)) {
            throw new IllegalArgumentException("percentile must be in (0, 1]");
        }
        if (sampleCount == 0L) {
            return 0L;
        }

        long targetRank = Math.max(1L, (long) Math.ceil(sampleCount * percentile));
        long cumulative = 0L;
        for (int bucket = 0; bucket < BUCKET_COUNT; bucket++) {
            cumulative += buckets[bucket];
            if (cumulative >= targetRank) {
                return bucket == Long.SIZE - 1
                        ? Long.MAX_VALUE
                        : 1L << bucket;
            }
        }
        return Long.MAX_VALUE;
    }

    long sampleCount() {
        return sampleCount;
    }

    void reset() {
        java.util.Arrays.fill(buckets, 0L);
        sampleCount = 0L;
    }
}
