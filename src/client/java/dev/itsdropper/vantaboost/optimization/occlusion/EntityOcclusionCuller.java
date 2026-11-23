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

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Render-thread-only entity occlusion cache.
 *
 * <p>World access stays on the client/render thread. Only stationary entities
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
    private static final int MAX_CACHE_ENTRIES = 4096;

    private static final Map<Entity, CacheEntry> CACHE = new WeakHashMap<>();

    private static ClientLevel activeLevel;
    private static long worldRevision;
    private static long budgetTick = Long.MIN_VALUE;
    private static int queriesThisTick;

    private EntityOcclusionCuller() {
    }

    public static boolean shouldCull(Entity entity, AABB renderBounds, Vec3 cameraPosition) {
        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;

        if (level == null || entity.level() != level || entity.isRemoved()
                || entity.isCurrentlyGlowing() || entity.isOnFire() || entity.hasCustomName()
                || entity.isPassenger() || entity.isVehicle() || !entity.onGround()
                || (entity instanceof net.minecraft.world.entity.Mob mob && mob.isLeashed())
                || entity instanceof net.minecraft.world.entity.player.Player
                || entity == client.player
                || renderBounds == null || cameraPosition == null) {
            return false;
        }

        if (entity.getDeltaMovement().lengthSqr() > MAX_VELOCITY_SQUARED) {
            return false;
        }

        AABB bounds = renderBounds.inflate(BOUNDS_PADDING);
        double sizeX = bounds.maxX - bounds.minX;
        double sizeY = bounds.maxY - bounds.minY;
        double sizeZ = bounds.maxZ - bounds.minZ;
        if (sizeX < 0.2 || sizeY < 0.2 || sizeZ < 0.2
                || sizeX > 6.0 || sizeY > 6.0 || sizeZ > 6.0) {
            return false;
        }

        double dx = (bounds.minX + bounds.maxX) * 0.5 - cameraPosition.x;
        double dy = (bounds.minY + bounds.maxY) * 0.5 - cameraPosition.y;
        double dz = (bounds.minZ + bounds.maxZ) * 0.5 - cameraPosition.z;
        if (dx * dx + dy * dy + dz * dz > MAX_DISTANCE_SQUARED
                || bounds.contains(cameraPosition)) {
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
                worldRevision, cameraPosition, bounds)) {
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
        boolean occluded = isBlockedByOpaqueFullBlock(
                level, entity, cameraPosition, bounds);

        CACHE.put(entity, new CacheEntry(
                worldRevision,
                cameraPosition.x, cameraPosition.y, cameraPosition.z,
                bounds.minX, bounds.minY, bounds.minZ,
                bounds.maxX, bounds.maxY, bounds.maxZ,
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
        if (activeLevel != level) {
            activeLevel = level;
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
            AABB bounds
    ) {
        double targetX = (bounds.minX + bounds.maxX) * 0.5;
        double targetY = (bounds.minY + bounds.maxY) * 0.5;
        double targetZ = (bounds.minZ + bounds.maxZ) * 0.5;
        Vec3 target = new Vec3(targetX, targetY, targetZ);
        BlockHitResult hit = level.clip(new ClipContext(
                cameraPosition,
                target,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                entity));

        if (hit.getType() != HitResult.Type.BLOCK
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
                bounds.minX, bounds.minY, bounds.minZ,
                bounds.maxX, bounds.maxY, bounds.maxZ,
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
        private boolean matches(long revision, Vec3 camera, AABB bounds) {
            return worldRevision == revision
                    && cameraX == camera.x && cameraY == camera.y && cameraZ == camera.z
                    && minX == bounds.minX && minY == bounds.minY && minZ == bounds.minZ
                    && maxX == bounds.maxX && maxY == bounds.maxY && maxZ == bounds.maxZ;
        }
    }
}
