package dev.itsdropper.vantaboost.optimization.occlusion;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Client-thread-only entity occlusion cache used during entity extraction.
 *
 * <p>World access stays on the client thread. Vanilla entities with vanilla
 * renderers are considered only when previous-to-current interpolation can be
 * enclosed by a bounded expansion of the vanilla culling AABB, and renderers
 * using custom culling bounds, submit geometry, living-model transforms, shadow radii,
 * shadow strengths, or render offsets are excluded unless the vanilla living-renderer
 * bounds and submit path are used.
 * A cached result is reused only while the Frustum origin and conservative bounds
 * are unchanged; block and chunk
 * mutations advance a world revision. Query work is bounded per client tick,
 * and every uncertain state fails open.</p>
 */
public final class EntityOcclusionCuller {
    private static final int MAX_QUERIES_PER_TICK = 64;
    private static final int QUERY_ID_BUCKETS = 1024;
    private static final double MAX_DISTANCE_SQUARED = 48.0 * 48.0;
    private static final double MAX_INTERPOLATION_PADDING = 1.5;
    private static final int MAX_CACHE_ENTRIES = 16384;
    private static final int MAX_FACE_CELLS = 16;
    // The culler is client-thread-only; reusable scratch arrays avoid hot-path
    // allocations while projecting and enumerating the candidate wall plane.
    private static final double[] PROJECTED_FACE_BOUNDS = new double[4];
    private static final int[] FACE_CELL_RANGE = new int[4];
    private static final boolean ENABLED =
            !Boolean.getBoolean("vantaboost.disableEntityOcclusion");

    private static final Map<Entity, CacheEntry> CACHE = new WeakHashMap<>();
    private static final boolean UNSUPPORTED_RENDERER_LOADED =
            FabricLoader.getInstance().isModLoaded("iris")
                    || FabricLoader.getInstance().isModLoaded("canvas")
                    || FabricLoader.getInstance().isModLoaded("vulkanmod")
                    || FabricLoader.getInstance().isModLoaded("entityculling")
                    || FabricLoader.getInstance().isModLoaded("entity_model_features");
    private record RendererEligibility(
            boolean customNameTagPolicy,
            boolean customCullingBounds,
            boolean customSubmit,
            boolean customLivingScale,
            boolean customLivingRotations,
            boolean customRenderOffset,
            boolean customShadowStrength,
            boolean customShadowRadius
    ) {
        private boolean isSafe() {
            return !customNameTagPolicy && !customCullingBounds && !customSubmit
                    && !customLivingScale && !customLivingRotations
                    && !customRenderOffset && !customShadowStrength && !customShadowRadius;
        }
    }

    // One cached lookup per renderer class keeps repeated eligibility checks
    // out of the per-entity hot path. Reflection is performed only once/class.
    private static final ClassValue<RendererEligibility> RENDERER_ELIGIBILITY = new ClassValue<>() {
        @Override
        protected RendererEligibility computeValue(Class<?> rendererType) {
            boolean livingRenderer = LivingEntityRenderer.class.isAssignableFrom(rendererType);
            boolean mobRenderer = MobRenderer.class.isAssignableFrom(rendererType);
            Class<?> livingBase = livingRenderer ? LivingEntityRenderer.class : EntityRenderer.class;
            Class<?> nameTagBase = mobRenderer ? MobRenderer.class
                    : livingRenderer ? LivingEntityRenderer.class : EntityRenderer.class;
            Class<?> shadowRadiusBase = mobRenderer ? MobRenderer.class
                    : livingRenderer ? LivingEntityRenderer.class : EntityRenderer.class;

            return new RendererEligibility(
                    RendererOverridePolicy.declaresMethodWithFirstParameter(
                            rendererType, nameTagBase, "shouldShowName", Entity.class),
                    RendererOverridePolicy.declaresSingleArgumentMethod(
                            rendererType, livingBase, "getBoundingBoxForCulling", Entity.class),
                    RendererOverridePolicy.declaresMethodWithFirstParameter(
                            rendererType, livingBase, "submit", EntityRenderState.class),
                    livingRenderer && RendererOverridePolicy.declaresMethodWithFirstParameter(
                            rendererType, LivingEntityRenderer.class, "scale", EntityRenderState.class),
                    livingRenderer && RendererOverridePolicy.declaresMethodWithFirstParameter(
                            rendererType, LivingEntityRenderer.class, "setupRotations", EntityRenderState.class),
                    RendererOverridePolicy.declaresStateMethod(
                            rendererType, EntityRenderer.class, "getRenderOffset", EntityRenderState.class),
                    RendererOverridePolicy.declaresStateMethod(
                            rendererType, EntityRenderer.class, "getShadowStrength", EntityRenderState.class),
                    RendererOverridePolicy.declaresStateMethod(
                            rendererType, shadowRadiusBase, "getShadowRadius", EntityRenderState.class)
            );
        }
    };

    private static WeakReference<ClientLevel> activeLevel = new WeakReference<>(null);
    private static net.minecraft.client.renderer.culling.Frustum mainEntityFrustum;
    private static long worldRevision;
    private static long budgetTick = Long.MIN_VALUE;
    private static int queriesThisTick;

    private EntityOcclusionCuller() {
    }

    public static boolean shouldCull(
            Entity entity, AABB renderBounds,
            double cameraX, double cameraY, double cameraZ,
            double shadowRadius, double shadowStrength, Class<?> rendererType
    ) {
        if (!EntityOcclusionDiagnostics.ENABLED) {
            return shouldCullInternal(
                    entity, renderBounds, cameraX, cameraY, cameraZ,
                    shadowRadius, shadowStrength, rendererType);
        }

        long started = System.nanoTime();
        boolean occluded = shouldCullInternal(
                entity, renderBounds, cameraX, cameraY, cameraZ,
                shadowRadius, shadowStrength, rendererType);
        EntityOcclusionDiagnostics.recordCandidate(System.nanoTime() - started, occluded);
        return occluded;
    }

    private static boolean shouldCullInternal(
            Entity entity, AABB renderBounds,
            double cameraX, double cameraY, double cameraZ,
            double shadowRadius, double shadowStrength, Class<?> rendererType
    ) {
        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;

        if (!ENABLED || UNSUPPORTED_RENDERER_LOADED
                || level == null || entity.level() != level || entity.isRemoved()
                || client.shouldEntityAppearGlowing(entity) || entity.displayFireAnimation()
                || entity.isInvisible() || (client.player != null && entity.isInvisibleTo(client.player))
                || entity.hasCustomName() || entity.shouldShowName()
                || (entity instanceof LivingEntity livingEntity
                && (livingEntity.getPose() == net.minecraft.world.entity.Pose.SLEEPING
                || livingEntity.deathTime > 0 || livingEntity.isAutoSpinAttack()))
                || entity.isPassenger() || entity.isVehicle()
                || (entity instanceof Leashable leashable && leashable.getLeashHolder() != null)
                || entity instanceof net.minecraft.world.entity.boss.enderdragon.EndCrystal
                || entity instanceof net.minecraft.world.entity.monster.Guardian
                || entity instanceof net.minecraft.world.entity.monster.warden.Warden
                || entity instanceof net.minecraft.world.entity.monster.EnderMan
                || entity instanceof net.minecraft.world.entity.monster.Witch
                || entity instanceof net.minecraft.world.entity.player.Player
                || !entity.getClass().getName().startsWith("net.minecraft.world.entity.")
                || entity == client.player || entity == client.getCameraEntity()
                || renderBounds == null || rendererType == null) {
            return false;
        }

        // EntityRenderer interpolates positions between xOld/yOld/zOld and
        // current coordinates. Expand the vanilla culling AABB by the complete
        // per-axis displacement so every interpolated position is enclosed.
        // Large teleports/knockback fail open rather than creating huge bounds.
        double motionX = OcclusionMotion.interpolationPadding(entity.xOld, entity.getX());
        double motionY = OcclusionMotion.interpolationPadding(entity.yOld, entity.getY());
        double motionZ = OcclusionMotion.interpolationPadding(entity.zOld, entity.getZ());
        if (!OcclusionMotion.isWithinLimit(motionX, MAX_INTERPOLATION_PADDING)
                || !OcclusionMotion.isWithinLimit(motionY, MAX_INTERPOLATION_PADDING)
                || !OcclusionMotion.isWithinLimit(motionZ, MAX_INTERPOLATION_PADDING)) {
            return false;
        }

        if (!Double.isFinite(shadowRadius) || shadowRadius < 0.0
                || !Double.isFinite(shadowStrength)
                || !RENDERER_ELIGIBILITY.get(rendererType).isSafe()
                // Dispatcher applies this offset after entity extraction; vanilla's
                // culling AABB does not include arbitrary renderer-specific offsets.
                || !Double.isFinite(renderBounds.minX) || !Double.isFinite(renderBounds.minY)
                || !Double.isFinite(renderBounds.minZ) || !Double.isFinite(renderBounds.maxX)
                || !Double.isFinite(renderBounds.maxY) || !Double.isFinite(renderBounds.maxZ)
                || !Double.isFinite(cameraX) || !Double.isFinite(cameraY)
                || !Double.isFinite(cameraZ)) {
            return false;
        }

        double effectiveShadowRadius = shadowRadius;
        if (LivingEntityRenderer.class.isAssignableFrom(rendererType)) {
            if (!(entity instanceof LivingEntity livingEntity)) {
                return false;
            }

            effectiveShadowRadius = OcclusionShadowBounds.scaledRadius(
                    shadowRadius,
                    livingEntity.getScale(),
                    livingEntity.getAgeScale(),
                    MobRenderer.class.isAssignableFrom(rendererType));
            if (!Double.isFinite(effectiveShadowRadius) || effectiveShadowRadius < 0.0) {
                return false;
            }
        }

        // EntityRenderer already inflated this AABB by 0.5 blocks. Extend it
        // using primitive coordinates, avoiding another AABB allocation on
        // the entity-extraction hot path.
        double rawSizeX = renderBounds.maxX - renderBounds.minX;
        double rawSizeY = renderBounds.maxY - renderBounds.minY;
        double rawSizeZ = renderBounds.maxZ - renderBounds.minZ;
        if (rawSizeX >= 3.9 && rawSizeY >= 3.9 && rawSizeZ >= 3.9) {
            // EntityRenderer uses a 4x4x4 fallback for invalid/empty bounds.
            return false;
        }

        // Vanilla extracts shadows from floored block coordinates. Their
        // footprint can extend one full block beyond the radius, and their
        // vertical depth depends on getShadowStrength and getShadowRadius.
        // Union that complete shadow envelope with the interpolated entity AABB.
        double shadowHorizontalPadding = OcclusionShadowBounds.horizontalPadding(effectiveShadowRadius);
        double shadowDropPadding = OcclusionShadowBounds.verticalDropPadding(
                effectiveShadowRadius, shadowStrength);
        double shadowRisePadding = OcclusionShadowBounds.verticalRisePadding(effectiveShadowRadius);

        double previousX = Math.min(entity.xOld, entity.getX());
        double previousY = Math.min(entity.yOld, entity.getY());
        double previousZ = Math.min(entity.zOld, entity.getZ());
        double currentX = Math.max(entity.xOld, entity.getX());
        double currentY = Math.max(entity.yOld, entity.getY());
        double currentZ = Math.max(entity.zOld, entity.getZ());

        double minX = Math.min(renderBounds.minX - motionX,
                previousX - shadowHorizontalPadding);
        double minY = Math.min(renderBounds.minY - motionY,
                previousY - shadowDropPadding);
        double minZ = Math.min(renderBounds.minZ - motionZ,
                previousZ - shadowHorizontalPadding);
        double maxX = Math.max(renderBounds.maxX + motionX,
                currentX + shadowHorizontalPadding);
        double maxY = Math.max(renderBounds.maxY + motionY,
                currentY + shadowRisePadding);
        double maxZ = Math.max(renderBounds.maxZ + motionZ,
                currentZ + shadowHorizontalPadding);

        double sizeX = maxX - minX;
        double sizeY = maxY - minY;
        double sizeZ = maxZ - minZ;
        if (sizeX < 1.01 || sizeY < 1.01 || sizeZ < 1.01
                || sizeX > 4.5 || sizeY > 4.5 || sizeZ > 4.5) {
            return false;
        }

        double dx = (minX + maxX) * 0.5 - cameraX;
        double dy = (minY + maxY) * 0.5 - cameraY;
        double dz = (minZ + maxZ) * 0.5 - cameraZ;
        if (dx * dx + dy * dy + dz * dz > MAX_DISTANCE_SQUARED
                || (cameraX >= minX && cameraX <= maxX
                && cameraY >= minY && cameraY <= maxY
                && cameraZ >= minZ && cameraZ <= maxZ)) {
            return false;
        }

        prepareLevel(level);
        long gameTime = level.getGameTime();
        if (budgetTick != gameTime) {
            budgetTick = gameTime;
            queriesThisTick = 0;
        }

        CacheEntry cached = CACHE.get(entity);
        if (cached != null && cached.matches(
                worldRevision, cameraX, cameraY, cameraZ,
                minX, minY, minZ, maxX, maxY, maxZ)) {
            if (EntityOcclusionDiagnostics.ENABLED) {
                EntityOcclusionDiagnostics.recordCacheHit(cached.occluded);
            }
            return cached.occluded;
        }

        // Rotate a bounded entity-ID window each tick. This avoids spending the
        // entire query budget on the same first entities every frame/tick.
        int windowStart = (int) Math.floorMod(
                gameTime * MAX_QUERIES_PER_TICK, QUERY_ID_BUCKETS);
        int entityBucket = Math.floorMod(entity.getId(), QUERY_ID_BUCKETS);
        int bucketOffset = Math.floorMod(
                entityBucket - windowStart, QUERY_ID_BUCKETS);
        if (bucketOffset >= MAX_QUERIES_PER_TICK
                || queriesThisTick >= MAX_QUERIES_PER_TICK) {
            if (EntityOcclusionDiagnostics.ENABLED) {
                EntityOcclusionDiagnostics.recordBudgetSkip();
            }
            return false;
        }

        queriesThisTick++;
        boolean occluded = false;
        long queryStarted = EntityOcclusionDiagnostics.ENABLED ? System.nanoTime() : 0L;
        try {
            occluded = hasOpaqueBlockPlaneOcclusion(
                    level, entity, cameraX, cameraY, cameraZ,
                    minX, minY, minZ, maxX, maxY, maxZ);
        } catch (RuntimeException ignored) {
            // World/raycast failures are uncertainty. Never hide an entity.
            occluded = false;
        } finally {
            if (EntityOcclusionDiagnostics.ENABLED) {
                EntityOcclusionDiagnostics.recordOcclusionQuery(
                        System.nanoTime() - queryStarted, occluded);
            }
        }

        CACHE.put(entity, new CacheEntry(
                worldRevision,
                cameraX, cameraY, cameraZ,
                minX, minY, minZ, maxX, maxY, maxZ,
                occluded));

        if (CACHE.size() > MAX_CACHE_ENTRIES) {
            CACHE.clear();
        }

        return occluded;
    }

    public static void beginMainEntityExtraction(
            net.minecraft.client.renderer.culling.Frustum frustum
    ) {
        mainEntityFrustum = frustum;
    }

    public static void endMainEntityExtraction(
            net.minecraft.client.renderer.culling.Frustum frustum
    ) {
        if (mainEntityFrustum == frustum) {
            mainEntityFrustum = null;
        }
    }

    public static boolean isMainEntityExtraction(
            net.minecraft.client.renderer.culling.Frustum frustum
    ) {
        return frustum != null && mainEntityFrustum == frustum;
    }

    /**
     * Called for every client-world block mutation and chunk load/unload.
     * Invalidating globally is intentionally conservative and avoids stale
     * occlusion after an occluding wall is removed.
     */
    public static void invalidateWorld() {
        // A previous "visible" result is conservative forever: keeping it can
        // only miss a future culling opportunity. An "occluded" result can
        // become incorrect when any block changes, so its revision must match.
        // This avoids scanning/clearing the entire weak cache for every block
        // update in busy worlds.
        worldRevision++;
    }

    private static void prepareLevel(ClientLevel level) {
        if (activeLevel.get() != level) {
            activeLevel = new WeakReference<>(level);
            worldRevision++;
            CACHE.clear();
            budgetTick = Long.MIN_VALUE;
            queriesThisTick = 0;
        }
    }

    private static boolean hasOpaqueBlockPlaneOcclusion(
            ClientLevel level,
            Entity entity,
            double cameraX, double cameraY, double cameraZ,
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ
    ) {
        double targetX = (minX + maxX) * 0.5;
        double targetY = (minY + maxY) * 0.5;
        double targetZ = (minZ + maxZ) * 0.5;
        Vec3 cameraPosition = new Vec3(cameraX, cameraY, cameraZ);
        Vec3 target = new Vec3(targetX, targetY, targetZ);
        BlockHitResult hit = level.clip(new ClipContext(
                cameraPosition,
                target,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                entity));

        if (hit.getType() != HitResult.Type.BLOCK || hit.isInside() || hit.isWorldBorderHit()) {
            return false;
        }

        var occluderState = level.getBlockState(hit.getBlockPos());
        if (!occluderState.isSolidRender()
                || !occluderState.getBlock().getClass().getName()
                .startsWith("net.minecraft.world.level.block.")) {
            return false;
        }

        Direction face = hit.getDirection();
        BlockPos block = hit.getBlockPos();
        int axis = face.getAxis() == Direction.Axis.X ? 0
                : face.getAxis() == Direction.Axis.Y ? 1 : 2;
        int normalStep = axis == 0 ? face.getStepX()
                : axis == 1 ? face.getStepY() : face.getStepZ();

        double plane = axis == 0 ? block.getX()
                : axis == 1 ? block.getY() : block.getZ();
        if (normalStep > 0) {
            plane += 1.0;
        }

        return isCoveredByOpaqueBlockPlane(
                level, block, axis, normalStep, plane,
                minX, minY, minZ, maxX, maxY, maxZ,
                cameraX, cameraY, cameraZ);
    }

    private static boolean isCoveredByOpaqueBlockPlane(
            ClientLevel level, BlockPos hitBlock,
            int axis, int normalStep, double plane,
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            double cameraX, double cameraY, double cameraZ
    ) {
        if (!OcclusionSampling.projectAabbOntoPlane(
                minX, minY, minZ, maxX, maxY, maxZ,
                cameraX, cameraY, cameraZ, axis, normalStep, plane,
                PROJECTED_FACE_BOUNDS)
                || !OcclusionSampling.getFaceCellRange(
                PROJECTED_FACE_BOUNDS, FACE_CELL_RANGE, MAX_FACE_CELLS)) {
            return false;
        }

        if (EntityOcclusionDiagnostics.ENABLED) {
            long width = (long) FACE_CELL_RANGE[1] - FACE_CELL_RANGE[0] + 1L;
            long height = (long) FACE_CELL_RANGE[3] - FACE_CELL_RANGE[2] + 1L;
            EntityOcclusionDiagnostics.recordFaceCellsPlanned((int) (width * height));
        }

        int fixedBlockCoordinate = axis == 0 ? hitBlock.getX()
                : axis == 1 ? hitBlock.getY() : hitBlock.getZ();
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();

        // The entire projected AABB is inside this rectangle of adjacent
        // full-block faces. Checking every cell proves the union covers it;
        // sparse rays alone would not prove the absence of holes.
        for (long u = FACE_CELL_RANGE[0]; u <= FACE_CELL_RANGE[1]; u++) {
            for (long v = FACE_CELL_RANGE[2]; v <= FACE_CELL_RANGE[3]; v++) {
                if (axis == 0) {
                    position.set(fixedBlockCoordinate, (int) u, (int) v);
                } else if (axis == 1) {
                    position.set((int) u, fixedBlockCoordinate, (int) v);
                } else {
                    position.set((int) u, (int) v, fixedBlockCoordinate);
                }

                var state = level.getBlockState(position);
                // In 1.21.11, isSolidRender is derived from
                // Block.isShapeFullBlock(state.getOcclusionShape()), so each
                // accepted cell contributes a complete opaque unit face.
                if (!state.isSolidRender()
                        || !state.getBlock().getClass().getName()
                        .startsWith("net.minecraft.world.level.block.")) {
                    return false;
                }
            }
        }

        return true;
    }

    private record CacheEntry(
            long worldRevision,
            double cameraX, double cameraY, double cameraZ,
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            boolean occluded
    ) {
        private boolean matches(
                long revision, double cameraX, double cameraY, double cameraZ,
                double minX, double minY, double minZ,
                double maxX, double maxY, double maxZ
        ) {
            return OcclusionCachePolicy.isReusable(occluded, worldRevision, revision)
                    && this.cameraX == cameraX && this.cameraY == cameraY && this.cameraZ == cameraZ
                    && this.minX == minX && this.minY == minY && this.minZ == minZ
                    && this.maxX == maxX && this.maxY == maxY && this.maxZ == maxZ;
        }
    }
}
