package dev.itsdropper.vantaboost.optimization.occlusion;

import java.lang.reflect.Method;

/**
 * Optional Iris integration without a hard dependency on Iris classes.
 *
 * <p>Entity occlusion relies on vanilla entity bounds. An active shader pack
 * may deform rendered entity geometry, so the optimization must fail open
 * while a pack is active. Iris itself remains supported when no pack is in use.</p>
 */
final class IrisShaderPackCompatibility {
    private static final String IRIS_API_CLASS = "net.irisshaders.iris.api.v0.IrisApi";
    private static final IrisApiAccess UNAVAILABLE = new IrisApiAccess(null, null);

    // Iris is queried once per main entity-extraction frame. Resolve its API
    // only once so reflection lookup and singleton discovery stay out of the
    // per-frame path.
    private static volatile IrisApiAccess cachedAccess;

    private IrisShaderPackCompatibility() {
    }

    static boolean isShaderPackActive(boolean irisLoaded, ClassLoader classLoader) {
        if (!irisLoaded) {
            return false;
        }

        IrisApiAccess access = cachedAccess;
        if (access == null) {
            synchronized (IrisShaderPackCompatibility.class) {
                access = cachedAccess;
                if (access == null) {
                    access = resolveAccess(classLoader);
                    cachedAccess = access;
                }
            }
        }

        if (access == UNAVAILABLE) {
            return true;
        }

        try {
            return access.isShaderPackInUse();
        } catch (ReflectiveOperationException | LinkageError | SecurityException | RuntimeException exception) {
            // Unknown Iris state is not safe to treat as a vanilla renderer.
            return true;
        }
    }

    private static IrisApiAccess resolveAccess(ClassLoader classLoader) {
        try {
            Class<?> irisApi = Class.forName(IRIS_API_CLASS, false, classLoader);
            Object api = irisApi.getMethod("getInstance").invoke(null);
            Method isShaderPackInUse = irisApi.getMethod("isShaderPackInUse");
            return new IrisApiAccess(api, isShaderPackInUse);
        } catch (ReflectiveOperationException | LinkageError | SecurityException | RuntimeException exception) {
            // Cache an unavailable result: repeatedly resolving a broken API
            // every frame would add overhead without making culling safer.
            return UNAVAILABLE;
        }
    }

    private record IrisApiAccess(Object instance, Method isShaderPackInUse) {
        private boolean isShaderPackInUse() throws ReflectiveOperationException {
            return Boolean.TRUE.equals(isShaderPackInUse.invoke(instance));
        }
    }
}
