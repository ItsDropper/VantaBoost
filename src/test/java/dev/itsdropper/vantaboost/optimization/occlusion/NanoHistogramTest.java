package dev.itsdropper.vantaboost.optimization.occlusion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NanoHistogramTest {
    @Test
    void reportsConservativeUpperBoundsForRequestedPercentiles() {
        NanoHistogram histogram = new NanoHistogram();
        histogram.record(1L);
        histogram.record(2L);
        histogram.record(3L);
        histogram.record(4L);
        histogram.record(8L);

        assertEquals(5L, histogram.sampleCount());
        assertEquals(4L, histogram.percentileUpperBoundNanos(0.50));
        assertEquals(8L, histogram.percentileUpperBoundNanos(0.95));
        assertEquals(8L, histogram.percentileUpperBoundNanos(0.99));
        assertEquals(8L, histogram.percentileUpperBoundNanos(1.0));
    }

    @Test
    void emptyHistogramReturnsZeroAndResetClearsSamples() {
        NanoHistogram histogram = new NanoHistogram();
        assertEquals(0L, histogram.percentileUpperBoundNanos(0.95));

        histogram.record(42L);
        histogram.reset();

        assertEquals(0L, histogram.sampleCount());
        assertEquals(0L, histogram.percentileUpperBoundNanos(0.95));
    }

    @Test
    void clampsNegativeDurationsAndRejectsInvalidPercentiles() {
        NanoHistogram histogram = new NanoHistogram();
        histogram.record(-10L);

        assertEquals(1L, histogram.sampleCount());
        assertEquals(1L, histogram.percentileUpperBoundNanos(0.95));
        assertThrows(IllegalArgumentException.class,
                () -> histogram.percentileUpperBoundNanos(0.0));
        assertThrows(IllegalArgumentException.class,
                () -> histogram.percentileUpperBoundNanos(1.01));
    }
}
