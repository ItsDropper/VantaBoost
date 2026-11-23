package dev.itsdropper.vantaboost.optimization.occlusion;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.EntityRenderer;
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
 * <p>World access stays on the client thread. Only stationary vanilla entities
 * with vanilla renderers are considered. A cached result is reused only while
 * the Frustum origin and conservative bounds are unchanged; block and chunk
 * mutations advance a world revision. Query work is bounded per client tick,
 * and every uncertain state fails open.</p>
 */
public final class EntityOcclusionCuller {
    private static final int MAX_QUERIES_PER_TICK = 64;
    private static final int QUERY_ID_BUCKETS = 1024;
    private static final double MAX_DISTANCE_SQUARED = 48.0 * 48.0;
    private static final double MAX_VELOCITY_SQUARED = 1.0E-6;
    private static final double SHADOW_DROP_PADDING = 1.0;
    private static final int MAX_CACHE_ENTRIES = 16384;
    private static final boolean ENABLED =
            !Boolean.getBoolean("vantaboost.disableEntityOcclusion");

    private static final Map<Entity, CacheEntry> CACHE = new WeakHashMap<>();
    private static final boolean UNSUPPORTED_RENDERER_LOADED =
            FabricLoader.getInstance().isModLoaded("iris")
                    || FabricLoader.getInstance().isModLoaded("canvas")
                    || FabricLoader.getInstance().isModLoaded("vulkanmod")
                    || FabricLoader.getInstance().isModLoaded("entityculling");
    private static final ClassValue<Boolean> HAS_CUSTOM_SHADOW_RADIUS = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> rendererType) {
            for (Class<?> type = rendererType;
                 type != null && type != EntityRenderer.class;
                 type = type.getSuperclass()) {
                try {
                    type.getDeclaredMethod("getShadowRadius", EntityRenderState.class);
                    return true;
                } catch (NoSuchMethodException ignored) {
                    // Continue through the renderer's superclass chain.
                } catch (SecurityException ignored) {
                    return true;
                }
            }
            return false;
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
            double shadowRadius, Class<?> rendererType
    ) {
        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;

        if (!ENABLED || UNSUPPORTED_RENDERER_LOADED
                || level == null || entity.level() != level || entity.isRemoved()
                || client.shouldEntityAppearGlowing(entity) || entity.displayFireAnimation()
                || entity.isInvisible() || (client.player != null && entity.isInvisibleTo(client.player))
                || entity.hasCustomName() || entity.shouldShowName()
                || entity.isPassenger() || entity.isVehicle() || !entity.onGround()
                || (entity instanceof Leashable leashable && leashable.getLeashHolder() != null)
                || entity instanceof net.minecraft.world.entity.boss.enderdragon.EndCrystal
                || entity instanceof net.minecraft.world.entity.monster.Guardian
                || entity instanceof net.minecraft.world.entity.monster.warden.Warden
                || entity instanceof net.minecraft.world.entity.monster.EnderMan
                || entity instanceof net.minecraft.world.entity.monster.Witch
                || entity instanceof net.minecraft.world.entity.player.Player
                || !entity.getClass().getName().startsWith("net.minecraft.world.entity.")
                || entity == client.player
                || renderBounds == null || rendererType == null) {
            return false;
        }

        double velocitySquared = entity.getDeltaMovement().lengthSqr();
        if (!Double.isFinite(velocitySquared) || velocitySquared > MAX_VELOCITY_SQUARED
                || entity.xOld != entity.getX() || entity.yOld != entity.getY()
                || entity.zOld != entity.getZ()
                || entity.yRotO != entity.getYRot() || entity.xRotO != entity.getXRot()
                || (entity instanceof LivingEntity living
                && (living.yBodyRotO != living.yBodyRot || living.yHeadRotO != living.yHeadRot))) {
            // EntityRenderer extracts interpolated positions/rotations. Do not
            // cull against current bounds while the render state is interpolating.
            return false;
        }

        if (!Double.isFinite(shadowRadius) || shadowRadius < 0.0
                || HAS_CUSTOM_SHADOW_RADIUS.get(rendererType)
                || !Double.isFinite(renderBounds.minX) || !Double.isFinite(renderBounds.minY)
                || !Double.isFinite(renderBounds.minZ) || !Double.isFinite(renderBounds.maxX)
                || !Double.isFinite(renderBounds.maxY) || !Double.isFinite(renderBounds.maxZ)
                || !Double.isFinite(cameraX) || !Double.isFinite(cameraY)
                || !Double.isFinite(cameraZ)) {
            return false;
        }

        // EntityRenderer already inflated this AABB by 0.5 blocks. Add only
        // the base shadow footprint using primitive coordinates, avoiding a
        // second AABB allocation on the entity-extraction hot path.
        double rawSizeX = renderBounds.maxX - renderBounds.minX;
        double rawSizeY = renderBounds.maxY - renderBounds.minY;
        double rawSizeZ = renderBounds.maxZ - renderBounds.minZ;
        if (rawSizeX >= 3.9 && rawSizeY >= 3.9 && rawSizeZ >= 3.9) {
            // EntityRenderer uses a 4x4x4 fallback for invalid/empty bounds.
            return false;
        }

        double minX = renderBounds.minX - shadowRadius;
        double minY = renderBounds.minY
                - (shadowRadius > 0.0 ? SHADOW_DROP_PADDING : 0.0);
        double minZ = renderBounds.minZ - shadowRadius;
        double maxX = renderBounds.maxX + shadowRadius;
        double maxY = renderBounds.maxY;
        double maxZ = renderBounds.maxZ + shadowRadius;

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
            return false;
        }

        queriesThisTick++;
        boolean occluded;
        try {
            occluded = isBlockedByOpaqueFullBlock(
                    level, entity, cameraX, cameraY, cameraZ,
                    minX, minY, minZ, maxX, maxY, maxZ);
        } catch (RuntimeException ignored) {
            // World/raycast failures are uncertainty. Never hide an entity.
            occluded = false;
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
        worldRevision++;
        CACHE.clear();
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

    private static boolean isBlockedByOpaqueFullBlock(
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

        double minU = axis == 0 ? block.getY()
                : block.getX();
        double maxU = minU + 1.0;
        double minV = axis == 0 ? block.getZ()
                : axis == 1 ? block.getZ() : block.getY();
        double maxV = minV + 1.0;

        return OcclusionSampling.isFullyOccludedByBlockFace(
                minX, minY, minZ,
                maxX, maxY, maxZ,
                cameraX, cameraY, cameraZ,
                axis, normalStep, plane, minU, maxU, minV, maxV);
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
            return worldRevision == revision
                    && this.cameraX == cameraX && this.cameraY == cameraY && this.cameraZ == cameraZ
                    && this.minX == minX && this.minY == minY && this.minZ == minZ
                    && this.maxX == maxX && this.maxY == maxY && this.maxZ == maxZ;
        }
    }
}
