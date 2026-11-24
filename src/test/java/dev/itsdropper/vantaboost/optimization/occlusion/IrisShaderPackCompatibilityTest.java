package dev.itsdropper.vantaboost.optimization.occlusion;

import net.irisshaders.iris.api.v0.IrisApi;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IrisShaderPackCompatibilityTest {
    @Test
    void resolvesTheApiOnceAndReadsCurrentShaderPackStateOnEachFrame() {
        IrisApi.reset();
        ClassLoader classLoader = IrisShaderPackCompatibilityTest.class.getClassLoader();

        assertFalse(IrisShaderPackCompatibility.isShaderPackActive(false, classLoader));
        assertEquals(0, IrisApi.instanceRequests());

        assertFalse(IrisShaderPackCompatibility.isShaderPackActive(true, classLoader));
        assertEquals(1, IrisApi.instanceRequests());
        assertEquals(1, IrisApi.shaderPackQueries());

        IrisApi.setShaderPackInUse(true);
        assertTrue(IrisShaderPackCompatibility.isShaderPackActive(true, classLoader));
        assertEquals(1, IrisApi.instanceRequests());
        assertEquals(2, IrisApi.shaderPackQueries());
    }
}
