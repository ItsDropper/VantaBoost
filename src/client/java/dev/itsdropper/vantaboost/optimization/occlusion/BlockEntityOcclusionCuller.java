package dev.itsdropper.vantaboost.optimization.occlusion;

import dev.itsdropper.vantaboost.optimization.occlusion.IrisShaderPackCompatibility;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Client-thread-only block-entity occlusion culling performed before render
 * state extraction. Only exact vanilla renderer/entity pairs with bounded
 * geometry are eligible; every uncertain case remains visible.
 */
public final class BlockEntityOcclusionCuller {
    private static final int MAX_QUERIES_PER_TICK = 32;
    private static final int QUERY_ID_BUCKETS = 512;
    private static final double MAX_DISTANCE_SQUARED = 48.0 * 48.0;
    private static final double BOUNDS_PADDING = 1.0;
    private static final int MAX_CACHE_ENTRIES = 8192;
    private static final int MAX_FACE_CELLS = 16;
    private static final double[] PROJECTED_FACE_BOUNDS = new double[4];
    private static final int[] FACE_CELL_RANGE = new int[4];
    private static final boolean ENABLED =
            !Boolean.getBoolean("vantaboost.disableBlockEntityOcclusion");
    private static final boolean UNSUPPORTED_RENDERER_LOADED =
            FabricLoader.getInstance().isModLoaded("canvas")
                    || FabricLoader.getInstance().isModLoaded("entityculling");

    private static final Map<BlockEntity, CacheEntry> CACHE = new WeakHashMap<>();
    private static WeakReference<ClientLevel> activeLevel = new WeakReference<>(null);
    private static boolean extractionActive;
    private static boolean irisShaderPackActive;
    private static double cameraX;
    private static double cameraY;
    private static double cameraZ;
    private static long worldRevision;
    private static long budgetTick = Long.MIN_VALUE;
    private static int queriesThisTick;

    private BlockEntityOcclusionCuller() {
    }

    public static void beginExtraction(Vec3 cameraPosition) {
        extractionActive = true;
        cameraX = cameraPosition.x;
        cameraY = cameraPosition.y;
        cameraZ = cameraPosition.z;
        irisShaderPackActive = IrisShaderPackCompatibility.isShaderPackActive(
                FabricLoader.getInstance().isModLoaded("iris"),
                BlockEntityOcclusionCuller.class.getClassLoader());
    }

    public static void endExtraction() {
        extractionActive = false;
        BlockEntityOcclusionDiagnostics.onExtractionFrame();
    }

    /**
     * Called only after vanilla BlockEntityRenderer.shouldRender returned true.
     */
    public static boolean shouldCull(
            BlockEntity blockEntity,
            BlockEntityRenderer renderer,
            Vec3 rendererCameraPosition
    ) {
        long started = BlockEntityOcclusionDiagnostics.ENABLED ? System.nanoTime() : 0L;
        boolean occluded = shouldCullInternal(blockEntity, renderer, rendererCameraPosition);
        if (BlockEntityOcclusionDiagnostics.ENABLED) {
            BlockEntityOcclusionDiagnostics.recordCandidate(
                    System.nanoTime() - started, occluded);
        }
        return occluded;
    }

    private static boolean shouldCullInternal(
            BlockEntity blockEntity,
            BlockEntityRenderer renderer,
            Vec3 rendererCameraPosition
    ) {
        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        if (!ENABLED || UNSUPPORTED_RENDERER_LOADED || irisShaderPackActive
                || !extractionActive || level == null || client.player == null
                || blockEntity == null || renderer == null
                || blockEntity.getLevel() != level || blockEntity.isRemoved()
                || !BlockEntityOcclusionPolicy.isSupported(
                        renderer.getClass().getName(), blockEntity.getClass().getName())
                || renderer.shouldRenderOffScreen()
                || level.getGloballyRenderedBlockEntities().contains(blockEntity)
                || rendererCameraPosition == null
                || rendererCameraPosition.x != cameraX
                || rendererCameraPosition.y != cameraY
                || rendererCameraPosition.z != cameraZ) {
            return false;
        }

        BlockPos pos = blockEntity.getBlockPos();
        if (pos == null) {
            return false;
        }

        // The one-block margin encloses vanilla double chests, beds, and small
        // model/item protrusions. Renderers with larger or unbounded geometry
        // are deliberately absent from the allowlist.
        double minX = pos.getX() - BOUNDS_PADDING;
        double minY = pos.getY() - BOUNDS_PADDING;
        double minZ = pos.getZ() - BOUNDS_PADDING;
        double maxX = pos.getX() + 1.0 + BOUNDS_PADDING;
        double maxY = pos.getY() + 1.0 + BOUNDS_PADDING;
        double maxZ = pos.getZ() + 1.0 + BOUNDS_PADDING;

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

        CacheEntry cached = CACHE.get(blockEntity);
        if (cached != null && cached.matches(
                worldRevision, cameraX, cameraY, cameraZ,
                minX, minY, minZ, maxX, maxY, maxZ)) {
            if (BlockEntityOcclusionDiagnostics.ENABLED) {
                BlockEntityOcclusionDiagnostics.recordCacheHit(cached.occluded);
            }
            return cached.occluded;
        }

        int bucket = Math.floorMod((int) (pos.asLong() ^ (pos.asLong() >>> 32)),
                QUERY_ID_BUCKETS);
        int windowStart = (int) Math.floorMod(
                gameTime * MAX_QUERIES_PER_TICK, QUERY_ID_BUCKETS);
        int bucketOffset = Math.floorMod(bucket - windowStart, QUERY_ID_BUCKETS);
        if (bucketOffset >= MAX_QUERIES_PER_TICK
                || queriesThisTick >= MAX_QUERIES_PER_TICK) {
            if (BlockEntityOcclusionDiagnostics.ENABLED) {
                BlockEntityOcclusionDiagnostics.recordBudgetSkip();
            }
            return false;
        }

        queriesThisTick++;
        boolean occluded = false;
        long queryStarted = BlockEntityOcclusionDiagnostics.ENABLED
                ? System.nanoTime() : 0L;
        try {
            Entity context = client.getCameraEntity();
            if (context != null) {
                occluded = hasOpaqueBlockPlaneOcclusion(
                        level, context, cameraX, cameraY, cameraZ,
                        minX, minY, minZ, maxX, maxY, maxZ);
            }
        } catch (RuntimeException ignored) {
            // Raycast/world failures are uncertainty. Never hide a block entity.
            occluded = false;
        } finally {
            if (BlockEntityOcclusionDiagnostics.ENABLED) {
                BlockEntityOcclusionDiagnostics.recordOcclusionQuery(
                        System.nanoTime() - queryStarted, occluded);
            }
        }

        CACHE.put(blockEntity, new CacheEntry(
                worldRevision, cameraX, cameraY, cameraZ,
                minX, minY, minZ, maxX, maxY, maxZ, occluded));
        if (CACHE.size() > MAX_CACHE_ENTRIES) {
            CACHE.clear();
        }
        return occluded;
    }

    public static void invalidateWorld() {
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
            ClientLevel level, Entity context,
            double cameraX, double cameraY, double cameraZ,
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ
    ) {
        Vec3 cameraPosition = new Vec3(cameraX, cameraY, cameraZ);
        Vec3 target = new Vec3(
                (minX + maxX) * 0.5,
                (minY + maxY) * 0.5,
                (minZ + maxZ) * 0.5);
        BlockHitResult hit = level.clip(new ClipContext(
                cameraPosition, target,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, context));

        if (hit.getType() != HitResult.Type.BLOCK
                || hit.isInside() || hit.isWorldBorderHit()) {
            return false;
        }

        BlockState hitState = level.getBlockState(hit.getBlockPos());
        if (!hitState.isSolidRender()
                || !hitState.getBlock().getClass().getName()
                .startsWith("net.minecraft.world.level.block.")) {
            return false;
        }

        Direction face = hit.getDirection();
        BlockPos hitBlock = hit.getBlockPos();
        int axis = face.getAxis() == Direction.Axis.X ? 0
                : face.getAxis() == Direction.Axis.Y ? 1 : 2;
        int normalStep = axis == 0 ? face.getStepX()
                : axis == 1 ? face.getStepY() : face.getStepZ();
        double plane = axis == 0 ? hitBlock.getX()
                : axis == 1 ? hitBlock.getY() : hitBlock.getZ();
        if (normalStep > 0) {
            plane += 1.0;
        }

        if (!OcclusionSampling.projectAabbOntoPlane(
                minX, minY, minZ, maxX, maxY, maxZ,
                cameraX, cameraY, cameraZ, axis, normalStep, plane,
                PROJECTED_FACE_BOUNDS)
                || !OcclusionSampling.getFaceCellRange(
                PROJECTED_FACE_BOUNDS, FACE_CELL_RANGE, MAX_FACE_CELLS)) {
            return false;
        }

        if (BlockEntityOcclusionDiagnostics.ENABLED) {
            long width = (long) FACE_CELL_RANGE[1] - FACE_CELL_RANGE[0] + 1L;
            long height = (long) FACE_CELL_RANGE[3] - FACE_CELL_RANGE[2] + 1L;
            BlockEntityOcclusionDiagnostics.recordFaceCellsPlanned((int) (width * height));
        }

        int fixedBlockCoordinate = axis == 0 ? hitBlock.getX()
                : axis == 1 ? hitBlock.getY() : hitBlock.getZ();
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        for (long u = FACE_CELL_RANGE[0]; u <= FACE_CELL_RANGE[1]; u++) {
            for (long v = FACE_CELL_RANGE[2]; v <= FACE_CELL_RANGE[3]; v++) {
                if (axis == 0) {
                    position.set(fixedBlockCoordinate, (int) u, (int) v);
                } else if (axis == 1) {
                    position.set((int) u, fixedBlockCoordinate, (int) v);
                } else {
                    position.set((int) u, (int) v, fixedBlockCoordinate);
                }

                BlockState state = level.getBlockState(position);
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
            boolean cameraSnapshotMatches = this.cameraX == cameraX
                    && this.cameraY == cameraY && this.cameraZ == cameraZ;
            return OcclusionCachePolicy.isReusable(
                    occluded, worldRevision, revision, cameraSnapshotMatches)
                    && this.minX == minX && this.minY == minY && this.minZ == minZ
                    && this.maxX == maxX && this.maxY == maxY && this.maxZ == maxZ;
        }
    }
}
