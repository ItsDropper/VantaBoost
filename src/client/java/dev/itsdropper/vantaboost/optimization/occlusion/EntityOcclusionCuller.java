package dev.itsdropper.vantaboost.optimization.occlusion;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Client-thread-only entity occlusion cache used during entity extraction.
 *
 * <p>World access stays on the client thread. Only stationary entities
 * with vanilla renderers are considered. Every cache entry is invalidated by
 * camera movement, entity-bound movement, block changes, or chunk lifecycle
 * changes. Query work is bounded per client tick; when the budget is exhausted
 * entities are rendered normally.</p>
 */
public final class EntityOcclusionCuller {
    private static final int MAX_QUERIES_PER_TICK = 64;
    private static final int QUERY_ID_BUCKETS = 1024;
    private static final double MAX_DISTANCE_SQUARED = 48.0 * 48.0;
    private static final double MAX_VELOCITY_SQUARED = 1.0E-6;
    private static final double BOUNDS_PADDING = 0.5;
    private static final double SHADOW_DROP_PADDING = 2.0;
    private static final int MAX_CACHE_ENTRIES = 4096;

    private static final Map<Entity, CacheEntry> CACHE = new WeakHashMap<>();

    private static WeakReference<ClientLevel> activeLevel = new WeakReference<>(null);
    private static long worldRevision;
    private static long budgetTick = Long.MIN_VALUE;
    private static int queriesThisTick;

    private EntityOcclusionCuller() {
    }

    public static boolean shouldCull(
            Entity entity, AABB renderBounds, Vec3 cameraPosition, double shadowRadius
    ) {
        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;

        if (level == null || entity.level() != level || entity.isRemoved()
                || entity.isCurrentlyGlowing() || entity.isOnFire() || entity.hasCustomName()
                || entity.isPassenger() || entity.isVehicle() || !entity.onGround()
                || (entity instanceof net.minecraft.world.entity.Mob mob && mob.isLeashed())
                || entity instanceof net.minecraft.world.entity.player.Player
                || !entity.getClass().getName().startsWith("net.minecraft.world.entity.")
                || entity == client.player
                || renderBounds == null || cameraPosition == null) {
            return false;
        }

        double velocitySquared = entity.getDeltaMovement().lengthSqr();
        if (!Double.isFinite(velocitySquared) || velocitySquared > MAX_VELOCITY_SQUARED) {
            return false;
        }

        if (!Double.isFinite(shadowRadius) || shadowRadius < 0.0
                || !Double.isFinite(renderBounds.minX) || !Double.isFinite(renderBounds.minY)
                || !Double.isFinite(renderBounds.minZ) || !Double.isFinite(renderBounds.maxX)
                || !Double.isFinite(renderBounds.maxY) || !Double.isFinite(renderBounds.maxZ)
                || !Double.isFinite(cameraPosition.x) || !Double.isFinite(cameraPosition.y)
                || !Double.isFinite(cameraPosition.z)) {
            return false;
        }

        // Expand primitive coordinates instead of allocating another AABB for
        // every entity on every frame. The lower Y margin also covers shadow
        // pieces projected onto nearby lower terrain.
        double horizontalPadding = BOUNDS_PADDING + shadowRadius;
        double minX = renderBounds.minX - horizontalPadding;
        double minY = renderBounds.minY - BOUNDS_PADDING
                - (shadowRadius > 0.0 ? SHADOW_DROP_PADDING : 0.0);
        double minZ = renderBounds.minZ - horizontalPadding;
        double maxX = renderBounds.maxX + horizontalPadding;
        double maxY = renderBounds.maxY + BOUNDS_PADDING;
        double maxZ = renderBounds.maxZ + horizontalPadding;

        double sizeX = maxX - minX;
        double sizeY = maxY - minY;
        double sizeZ = maxZ - minZ;
        if (sizeX < 0.2 || sizeY < 0.2 || sizeZ < 0.2
                || sizeX > 6.0 || sizeY > 6.0 || sizeZ > 6.0) {
            return false;
        }

        double dx = (minX + maxX) * 0.5 - cameraPosition.x;
        double dy = (minY + maxY) * 0.5 - cameraPosition.y;
        double dz = (minZ + maxZ) * 0.5 - cameraPosition.z;
        if (dx * dx + dy * dy + dz * dz > MAX_DISTANCE_SQUARED
                || (cameraPosition.x >= minX && cameraPosition.x <= maxX
                && cameraPosition.y >= minY && cameraPosition.y <= maxY
                && cameraPosition.z >= minZ && cameraPosition.z <= maxZ)) {
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
                worldRevision, cameraPosition,
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
                    level, entity, cameraPosition,
                    minX, minY, minZ, maxX, maxY, maxZ);
        } catch (RuntimeException ignored) {
            // World/raycast failures are uncertainty. Never hide an entity.
            occluded = false;
        }

        CACHE.put(entity, new CacheEntry(
                worldRevision,
                cameraPosition.x, cameraPosition.y, cameraPosition.z,
                minX, minY, minZ, maxX, maxY, maxZ,
                occluded));

        if (CACHE.size() > MAX_CACHE_ENTRIES) {
            CACHE.clear();
        }

        return occluded;
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
            Vec3 cameraPosition,
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ
    ) {
        double targetX = (minX + maxX) * 0.5;
        double targetY = (minY + maxY) * 0.5;
        double targetZ = (minZ + maxZ) * 0.5;
        Vec3 target = new Vec3(targetX, targetY, targetZ);
        BlockHitResult hit = level.clip(new ClipContext(
                cameraPosition,
                target,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                entity));

        if (hit.getType() != HitResult.Type.BLOCK || hit.isInside() || hit.isWorldBorderHit()
                || !level.getBlockState(hit.getBlockPos()).isSolidRender()) {
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
                cameraPosition.x, cameraPosition.y, cameraPosition.z,
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
                long revision, Vec3 camera,
                double minX, double minY, double minZ,
                double maxX, double maxY, double maxZ
        ) {
            return worldRevision == revision
                    && cameraX == camera.x && cameraY == camera.y && cameraZ == camera.z
                    && this.minX == minX && this.minY == minY && this.minZ == minZ
                    && this.maxX == maxX && this.maxY == maxY && this.maxZ == maxZ;
        }
    }
}
