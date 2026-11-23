package dev.itsdropper.vantaboost.optimization.occlusion;

import java.lang.reflect.Method;

/**
 * Reflection policy for renderer methods with one typed parameter. Called through
 * ClassValue so method inspection happens once per renderer class.
 */
final class RendererOverridePolicy {
    private RendererOverridePolicy() {
    }

    static boolean declaresStateMethod(
            Class<?> rendererType, Class<?> stopBefore,
            String methodName, Class<?> renderStateBase
    ) {
        return declaresSingleArgumentMethod(rendererType, stopBefore, methodName, renderStateBase);
    }

    static boolean declaresMethodWithFirstParameter(
            Class<?> rendererType, Class<?> stopBefore,
            String methodName, Class<?> parameterBase
    ) {
        for (Class<?> type = rendererType;
             type != null && type != stopBefore;
             type = type.getSuperclass()) {
            try {
                for (Method method : type.getDeclaredMethods()) {
                    Class<?>[] parameters = method.getParameterTypes();
                    if (method.getName().equals(methodName)
                            && parameters.length > 0
                            && parameterBase.isAssignableFrom(parameters[0])) {
                        return true;
                    }
                }
            } catch (SecurityException ignored) {
                // Inability to inspect a renderer must fail closed.
                return true;
            }
        }
        return false;
    }

    static boolean declaresSingleArgumentMethod(
            Class<?> rendererType, Class<?> stopBefore,
            String methodName, Class<?> parameterBase
    ) {
        for (Class<?> type = rendererType;
             type != null && type != stopBefore;
             type = type.getSuperclass()) {
            try {
                for (Method method : type.getDeclaredMethods()) {
                    Class<?>[] parameters = method.getParameterTypes();
                    if (method.getName().equals(methodName)
                            && parameters.length == 1
                            && parameterBase.isAssignableFrom(parameters[0])) {
                        return true;
                    }
                }
            } catch (SecurityException ignored) {
                // Inability to inspect a renderer must fail closed.
                return true;
            }
        }
        return false;
    }
}
