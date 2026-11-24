package dev.itsdropper.vantaboost.optimization.occlusion;

import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RendererOverridePolicyTest {
    @Test
    void unresolvedOptionalRendererSignatureFailsClosed() throws Exception {
        Class<?> rendererType = loadWithoutOptionalSignatureType();

        assertThrows(LinkageError.class, () -> {
            for (var method : rendererType.getDeclaredMethods()) {
                method.getParameterTypes();
            }
        });

        assertTrue(RendererOverridePolicy.declaresMethodWithFirstParameter(
                rendererType, Object.class, "shouldRender", Object.class));
        assertTrue(RendererOverridePolicy.declaresSingleArgumentMethod(
                rendererType, Object.class, "getBoundingBoxForCulling", Object.class));
    }

    private static Class<?> loadWithoutOptionalSignatureType() throws Exception {
        String rendererName = BrokenRendererReflectionFixture.class.getName();
        String missingTypeName = MissingRendererSignatureFixture.class.getName();
        String resourceName = "/" + rendererName.replace('.', '/') + ".class";
        byte[] classBytes;
        try (InputStream input = BrokenRendererReflectionFixture.class.getResourceAsStream(resourceName)) {
            assertNotNull(input, "Compiled renderer fixture class must be available");
            classBytes = input.readAllBytes();
        }

        ClassLoader isolatedLoader = new ClassLoader(ClassLoader.getPlatformClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.equals(missingTypeName)) {
                    throw new ClassNotFoundException(name);
                }
                if (!name.equals(rendererName)) {
                    return super.loadClass(name, resolve);
                }

                synchronized (getClassLoadingLock(name)) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) {
                        loaded = defineClass(name, classBytes, 0, classBytes.length);
                    }
                    if (resolve) {
                        resolveClass(loaded);
                    }
                    return loaded;
                }
            }
        };

        return Class.forName(rendererName, false, isolatedLoader);
    }
}

final class BrokenRendererReflectionFixture {
    public void shouldRender(MissingRendererSignatureFixture ignored) {
    }

    public Object getBoundingBoxForCulling(MissingRendererSignatureFixture ignored) {
        return ignored;
    }
}

final class MissingRendererSignatureFixture {
}
