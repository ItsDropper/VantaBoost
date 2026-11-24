package net.irisshaders.iris.api.v0;

/**
 * Minimal test fixture for VantaBoost's optional Iris integration.
 * This class is compiled only into the test source set.
 */
public final class IrisApi {
    private static final IrisApi INSTANCE = new IrisApi();

    private static boolean shaderPackInUse;
    private static int instanceRequests;
    private static int shaderPackQueries;
    private static boolean failOnQuery;

    private IrisApi() {
    }

    public static IrisApi getInstance() {
        instanceRequests++;
        return INSTANCE;
    }

    public boolean isShaderPackInUse() {
        shaderPackQueries++;
        if (failOnQuery) {
            throw new IllegalStateException("Simulated Iris API failure");
        }
        return shaderPackInUse;
    }

    public static void reset() {
        shaderPackInUse = false;
        instanceRequests = 0;
        shaderPackQueries = 0;
        failOnQuery = false;
    }

    public static void setShaderPackInUse(boolean active) {
        shaderPackInUse = active;
    }

    public static void setFailOnQuery(boolean fail) {
        failOnQuery = fail;
    }

    public static int instanceRequests() {
        return instanceRequests;
    }

    public static int shaderPackQueries() {
        return shaderPackQueries;
    }
}
