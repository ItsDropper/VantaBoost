package dev.itsdropper.vantaboost.optimization.occlusion;

/**
 * Optional Iris integration without a hard dependency on Iris classes.
 *
 * <p>Entity occlusion relies on vanilla entity bounds. An active shader pack
 * may deform rendered entity geometry, so the optimization must fail open
 * while a pack is active. Iris itself remains supported when no pack is in use.</p>
 */
final class IrisShaderPackCompatibility {
    private static final String IRIS_API_CLASS = "net.irisshaders.iris.api.v0.IrisApi";

    private IrisShaderPackCompatibility() {
    }

    static boolean isShaderPackActive(boolean irisLoaded, ClassLoader classLoader) {
        if (!irisLoaded) {
            return false;
        }

        try {
            Class<?> irisApi = Class.forName(IRIS_API_CLASS, false, classLoader);
            Object api = irisApi.getMethod("getInstance").invoke(null);
            return Boolean.TRUE.equals(irisApi.getMethod("isShaderPackInUse").invoke(api));
        } catch (ReflectiveOperationException | LinkageError | SecurityException exception) {
            // Unknown Iris state is not safe to treat as a vanilla renderer.
            return true;
        }
    }
}
