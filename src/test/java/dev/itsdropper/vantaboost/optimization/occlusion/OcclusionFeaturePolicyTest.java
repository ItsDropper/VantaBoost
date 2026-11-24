package dev.itsdropper.vantaboost.optimization.occlusion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OcclusionFeaturePolicyTest {
    @Test
    void experimentalFeatureIsDisabledWithoutExplicitOptIn() {
        assertFalse(OcclusionFeaturePolicy.isEnabled(false, false));
    }

    @Test
    void explicitOptInEnablesFeature() {
        assertTrue(OcclusionFeaturePolicy.isEnabled(true, false));
    }

    @Test
    void explicitDisableOverridesOptIn() {
        assertFalse(OcclusionFeaturePolicy.isEnabled(true, true));
    }

    @Test
    void disableWithoutOptInRemainsDisabled() {
        assertFalse(OcclusionFeaturePolicy.isEnabled(false, true));
    }
}
