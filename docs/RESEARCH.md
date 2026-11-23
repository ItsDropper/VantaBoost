# VantaBoost performance research

**Research date:** 2026-11-23  
**Target:** Minecraft 1.21.11, Fabric Loader 0.18.1, Java 21, official Mojang mappings  
**Scope:** source-level investigation plus an experimental entity-occlusion prototype. No VantaBoost runtime benchmark has been run.

## Executive conclusion

VantaBoost should not begin by implementing a generic culling system. The target renderer already has substantial optimizations, and the most valuable opportunities depend on whether a scene is CPU-bound or GPU-bound.

At 1,000 FPS, the frame budget is 1 ms. That budget includes game/render-state extraction, render submission, GPU work, synchronization and presentation. A single optimization cannot guarantee 1,000 FPS across hardware and scenes.

The strongest research directions are:

1. **Measure first**, including CPU render-thread time and GPU time. FPS alone cannot distinguish the bottleneck.
2. **Avoid redundant framebuffer transitions** where the exact 1.21.11 OpenGL path and state lifetime permit it. ImmediatelyFast has a small, directly relevant implementation for this target.
3. **Reuse Sodium's visibility results** for particle section culling rather than running a second visibility system over the same sections.
4. **Investigate entity/block-entity occlusion culling** for scenes with many objects hidden behind walls. Entity Culling demonstrates an asynchronous CPU path with conservative AABB handling and caches.
5. **Investigate deferred sky and GPU terrain occlusion** for GPU-bound scenes. Polydium's implementations are sophisticated and conditional; they are not safe generic mixins.
6. **Keep GUI-only and tick-only optimizations separate** from the core world-rendering FPS target. They help specific workloads, not every frame.

## Target renderer facts

### Minecraft 1.21.11 uses extraction and drawing phases

Fabric's versioned 1.21.11 rendering documentation describes the split between extraction/preparation and drawing/rendering. Rendering data is extracted into buffered/render state, then drawn; the architectural goal is to overlap drawing the previous frame with extraction of the next frame.

Reference: [Fabric 1.21.11 — Rendering in the World](https://github.com/FabricMC/fabric-docs/blob/main/versions/1.21.11/develop/rendering/world.md).

**VantaBoost consequence:** do not casually move world access or renderer work across threads. Preserve the extraction/drawing boundary, render-thread ownership, immutable snapshots, and GPU-resource lifetime. A cache or worker thread that reads live world state at the wrong phase can introduce races and stale render state.

### Sodium already does significant chunk visibility and scheduling work

Inspected the 1.21.11 stable source:
- [Sodium RenderSectionManager](https://github.com/CaffeineMC/sodium/blob/1.21.11/stable/common/src/main/java/net/caffeinemc/mods/sodium/client/render/chunk/RenderSectionManager.java)
- [Sodium OcclusionCuller](https://github.com/CaffeineMC/sodium/tree/1.21.11/stable/common/src/main/java/net/caffeinemc/mods/sodium/client/render/chunk/occlusion)

The manager owns an `OcclusionCuller`, a chunk builder, render-section data, render lists, build-result queues, and upload budgeting. It schedules chunk mesh work and uses visibility results to construct render lists.

**VantaBoost consequence:** do not recreate chunk-section graph traversal, chunk meshing, or Sodium's basic occlusion path. Any Sodium-specific optimization should consume its existing render lists/visibility API where possible and must be disabled or adapted when Sodium is absent.

## Investigated implementations

### A. Entity and block-entity occlusion culling

Upstream: [Entity Culling](https://github.com/tr7zw/EntityCulling)

Inspected source:
- [CullTask.java](https://github.com/tr7zw/EntityCulling/blob/162c92af05a38db5a4b56bb12012520f462e1658/src/main/java/dev/tr7zw/entityculling/CullTask.java)
- [OcclusionCullingInstance.java](https://github.com/tr7zw/EntityCulling/blob/162c92af05a38db5a4b56bb12012520f462e1658/EntityCulling-Versionless/src/main/java/dev/tr7zw/entityculling/versionless/occlusionculling/OcclusionCullingInstance.java)
- [BlockEntityRenderDispatcherMixin.java](https://github.com/tr7zw/EntityCulling/blob/162c92af05a38db5a4b56bb12012520f462e1658/src/main/java/dev/tr7zw/entityculling/mixin/BlockEntityRenderDispatcherMixin.java)

Observed design in the current upstream source:
- `CullTask` performs culling on a dedicated background thread. The client side supplies entity/block-entity lists, camera position, and render metadata for the worker.
- `OcclusionCullingInstance` tests a target AABB against the voxel volume it spans. It checks cached voxel results first, then tests candidate boundary voxels and samples multiple points on the relevant voxel faces using a grid-traversal visibility routine.
- The worker reuses vectors, arrays and a `BitSet`; cache implementations store per-voxel visible/hidden states. `DirectProvider` reads block state directly, while `SimpleCacheProvider` is explicitly marked as a proof of concept and not fully thread-safe.
- Entity bounds are expanded in the direction of velocity. The task avoids oversized bounds, glowing/forced-visible objects and objects outside its tracing distance; dynamic whitelists are supported.
- On 1.21.9+, `WorldRendererMixin.extractEntity` short-circuits extraction for culled entities and constructs a minimal invisible render state, avoiding normal renderer-state extraction. This is a material architectural advantage over culling only inside `EntityRenderer.shouldRender`.
- Exceptions in the voxel solver fail open. Upstream also exposes safe-mode and tracing-distance controls.

Value: broad occlusion coverage for entities and block entities, with asynchronous work and early extraction avoidance. This is a substantially more capable baseline than VantaBoost's current prototype.

Risks: worker-thread data consistency, stale visibility during camera/object motion, false culls, large/custom render bounds, shader shadow passes, and CPU overhead. `DirectProvider` reads live world state from the worker; the alternative `SimpleCacheProvider` source itself warns that it is not yet fully synchronized.

**Direct comparison with VantaBoost:**
- Entity Culling: background culling task; multi-voxel/multi-sample visibility tests; velocity-expanded bounds; entity and block-entity coverage; can skip normal entity render-state extraction.
- VantaBoost today: synchronous `ClientLevel.clip` raycast; one ray to the AABB center; all cells in a bounded rectangle of adjacent opaque full-block faces on one plane must cover the projected conservative AABB; eligible vanilla entities whose previous-to-current interpolation fits a bounded displacement expansion; renderer-visible name tags fail open after the active renderer's actual name-tag visibility check; 64 new queries per tick; culling is evaluated in `EntityRenderer.shouldRender` during the visibility filter, before that entity's render state is extracted. Unlike Entity Culling, the visibility decision is not precomputed asynchronously.
- Therefore, VantaBoost is currently narrower and is not faster or more capable by evidence. The single-plane proof is stricter than sparse point sampling, but it still cannot use occluders on multiple depths or non-coplanar walls.

**Required path to a credible Entity Culling competitor:**
1. Keep all live `ClientLevel`/chunk access on the client thread. Build immutable, section-local opacity snapshots from chunk data and incremental block updates; workers must never query live Minecraft world state.
2. Replace the single-center ray with a conservative multi-point AABB/voxel visibility solver. Use a reusable DDA traversal and test only the AABB boundary points that can be visible from the camera octant; return visible as soon as any sample has an unobstructed path.
3. Run visibility work asynchronously over immutable entity snapshots. Expand bounds by velocity and renderer-specific conservative bounds; attach world, section and camera generations to each result so stale results fail open.
4. Apply results before expensive entity render-state extraction, preserving names, leash/vehicle behavior, shadows, glowing, crosshair selection and custom renderers.
5. Add block-entity support only after entity culling is correct, with explicit dynamic-renderer exclusions.
6. Benchmark against Entity Culling with identical worlds, camera paths, settings and hardware. Compare p50/p95/p99 frame time, render-thread time, worker CPU, allocation rate, number of visibility queries, and culled/rendered counts. Do not call VantaBoost better until it wins without visual regressions.

**Status:** The existing synchronous entity-only prototype is a correctness experiment, not a competitor. Block-entity culling and the asynchronous voxel solver are not implemented.

### B. Redundant framebuffer unbind avoidance

Upstream: [ImmediatelyFast 1.21.11 branch](https://github.com/RaphiMC/ImmediatelyFast/tree/1.21.11)

Inspected exact target-version source:
- [MixinGlCommandEncoder.java](https://github.com/RaphiMC/ImmediatelyFast/blob/1.21.11/common/src/main/java/net/raphimc/immediatelyfast/injection/mixins/avoid_redundant_framebuffer_switching/MixinGlCommandEncoder.java)
- [ImmediatelyFastConfig.java](https://github.com/RaphiMC/ImmediatelyFast/blob/1.21.11/common/src/main/java/net/raphimc/immediatelyfast/feature/core/ImmediatelyFastConfig.java)

Observed design on 1.21.11:
- Wraps the framebuffer unbind call in `GlCommandEncoder.finishRenderPass` and suppresses that unbind.
- Explicitly binds framebuffer 0 at the beginning of `presentTexture`.
- The config exposes this as `avoid_redundant_framebuffer_switching`.

Value: removes a redundant driver/state transition in a rendering path that may run frequently. It is intended to preserve output, unlike reducing quality or draw distance.

Risks: OpenGL-specific; framebuffer state must be correct at presentation and across third-party render passes. Modded render pipelines, shaders, and mixins can rely on state transitions. The target method/descriptor must be checked against the exact resolved 1.21.11 jar before any VantaBoost injection.

**Status:** Highest-priority small source-level candidate to benchmark, not yet proven to improve VantaBoost or any given scene.

### C. Immediate-mode batching

Upstream: [ImmediatelyFast](https://github.com/RaphiMC/ImmediatelyFast/tree/1.21.11)

The project describes a custom buffer implementation that batches draw calls and GPU uploads for entities, block entities, particles, text, GUI/HUD and mod immediate-mode rendering. Its published performance section says it primarily helps CPU-bound scenes. The displayed historical benchmark figures are from Minecraft 1.19.3 and must not be treated as expected gains for 1.21.11.

**Status:** Research further, but high compatibility surface. Batching can change ordering, blending, scissor and render-state behavior. Do not create a broad replacement buffer system without a profile proving immediate-mode submission is a dominant cost.

### D. Particle section culling using Sodium visibility

Upstream: [Polydium](https://github.com/Polyfrost/Polydium)

Inspected:
- [MixinParticleSectionCull.java](https://github.com/Polyfrost/Polydium/blob/6e13afbcc2df80cf91627640c6824cf11d7d3fec/src/main/java/org/polyfrost/polydium/mixin/client/perf/sodium/MixinParticleSectionCull.java)
- [PolydiumToggles.java](https://github.com/Polyfrost/Polydium/blob/6e13afbcc2df80cf91627640c6824cf11d7d3fec/src/main/java/org/polyfrost/polydium/client/PolydiumToggles.java)

Observed design:
- Reuses Sodium's `SodiumWorldRenderer.isBoxVisible` result to reject particles in invisible sections.
- Caches a positive section-visible result for the current tick to avoid repeating the same query.
- Has explicit compatibility handling for Particle Core and particles outside the frustum.

Value: avoids particle render work in sections Sodium has already deemed invisible. The visible output should remain the same if the bounds and visibility contract are correct.

Risks: requires Sodium integration; particle systems may have unusual bounds and Particle Core has its own extraction behavior. Do not run a duplicate expensive culler per particle.

**Status:** High-priority Sodium-specific candidate if particle-heavy profiling justifies it.

### E. Deferred sky rendering

Upstream: [Polydium SkyFarPlane.java](https://github.com/Polyfrost/Polydium/blob/6e13afbcc2df80cf91627640c6824cf11d7d3fec/src/main/java/org/polyfrost/polydium/client/perf/SkyFarPlane.java)

Polydium's 1.21.10+ toggle describes drawing the sky after terrain so depth testing can skip sky pixels hidden by terrain. The implementation patches known shader position paths and checks that relevant shaders were actually patched. It declines to defer when Iris shaders or unsupported sky mods/pipelines are active.

Value: targets GPU fragment/fill work, so it is more relevant than CPU-only micro-optimizations when GPU-bound.

Risks: shader/resource-pack compatibility, depth behavior, custom skies, transparency and render ordering. This is not a generic safe reorder. Needs image-diff testing and a fallback path.

**Status:** High-value GPU-bound research candidate; high implementation complexity.

### F. GPU terrain occlusion culling

Upstream: [Polydium OcclusionCuller.java](https://github.com/Polyfrost/Polydium/blob/6e13afbcc2df80cf91627640c6824cf11d7d3fec/src/main/java/org/polyfrost/polydium/client/perf/OcclusionCuller.java) and [GpuWaitMeter.java](https://github.com/Polyfrost/Polydium/blob/6e13afbcc2df80cf91627640c6824cf11d7d3fec/src/main/java/org/polyfrost/polydium/client/perf/GpuWaitMeter.java).

Observed design:
- Uses captured depth information and projected section bounds to identify hidden Sodium terrain sections.
- Maintains per-region hidden-section bitsets and invalidates results when section bounds/occluders change.
- Measures culling work and GPU wait behavior.
- Dynamically enables/disables culling when its expected benefit outweighs its cost.
- Disables itself in shader-pack situations it does not support.

Value: directly attacks GPU-bound terrain overdraw and can be more relevant to high-FPS goals than CPU-side entity logic.

Risks: very complex correctness problem (camera movement, depth freshness, animated geometry, transparent surfaces, section rebuilds, reprojection, resource lifecycle); Sodium internals; shader compatibility; CPU overhead can exceed GPU savings.

**Status:** Research-only, later-stage experiment. Do not attempt before baseline profiling and a simpler candidate.

### G. GUI item baking / cached item render output

Upstream: [Polydium GuiItemBake.java](https://github.com/Polyfrost/Polydium/blob/6e13afbcc2df80cf91627640c6824cf11d7d3fec/src/main/java/org/polyfrost/polydium/client/perf/GuiItemBake.java)

Observed design: records stamps for item model identities and redraws only when relevant animation/texture/glint state changes. Oversized GUI items and animated states receive special handling.

Value: avoids repeated item render preparation in inventories with many static items.

Risks: incorrect invalidation can freeze animation or stale item appearance; resource reloads and model identity changes must invalidate caches.

**Status:** GUI-specific; low priority for ordinary world FPS.

### H. Entity outline scissoring

Upstream: [Polydium OutlineScissor.java](https://github.com/Polyfrost/Polydium/blob/6e13afbcc2df80cf91627640c6824cf11d7d3fec/src/main/java/org/polyfrost/polydium/client/perf/OutlineScissor.java)

Observed design: computes conservative screen-space bounds from outline geometry and limits outline post-processing to the affected region. It verifies vanilla post-process resources and avoids unsupported/custom pipelines.

Value: can reduce full-screen post-processing when glowing outlines cover only a small portion of the screen.

Risks: conservative bounds, near-plane clipping, custom shaders and post-process chains. Only matters when outline passes are active.

**Status:** Conditional GPU optimization; low priority unless profiling shows outline post-processing cost.

### I. Animated sprite ordering

Upstream: [Polydium AnimatedSpriteOrder.java](https://github.com/Polyfrost/Polydium/blob/6e13afbcc2df80cf91627640c6824cf11d7d3fec/src/main/java/org/polyfrost/polydium/client/perf/AnimatedSpriteOrder.java)

For 1.21.11, Polydium groups interpolated animated sprite states ahead of frame-switching states, with the toggle applying after resource reload. The stated goal is to reduce repeated pipeline binds during atlas passes.

Value: reduces state switching during animated texture updates.

Risks: atlas update ordering and animation correctness. Likely a small, workload-specific win rather than a general 1,000-FPS lever.

**Status:** Lower priority.

### J. Text effect merging and debug-entry caching

Upstream:
- [Polydium MixinTextEffectMerge.java](https://github.com/Polyfrost/Polydium/blob/6e13afbcc2df80cf91627640c6824cf11d7d3fec/src/main/java/org/polyfrost/polydium/mixin/client/perf/MixinTextEffectMerge.java)
- [Polydium DebugEntryCache.java](https://github.com/Polyfrost/Polydium/blob/6e13afbcc2df80cf91627640c6824cf11d7d3fec/src/main/java/org/polyfrost/polydium/client/perf/DebugEntryCache.java)

Text effect merging combines adjacent underline/strikethrough runs with matching geometry/style into fewer effects. Debug caching records and replays vanilla debug entries on an interval, excluding FPS entries.

Value: lower GUI/text allocation and render submission when text-heavy overlays are visible.

Risks: text metrics/style boundaries and stale debug values. Debug caching should not include the FPS entry; the upstream implementation explicitly excludes it.

**Status:** UI-only and low priority for world rendering.

### K. Idle block-entity tick skipping

Upstream: [Polydium IdleBlockEntityTicks.java](https://github.com/Polyfrost/Polydium/blob/6e13afbcc2df80cf91627640c6824cf11d7d3fec/src/main/java/org/polyfrost/polydium/client/perf/IdleBlockEntityTicks.java)

It skips client ticking for resting chest lids and stationary spawners with no nearby player, with compatibility checks for Lithium and other block-entity implementations.

Value: reduces client tick CPU cost in specific scenes.

Risks: this is simulation/update scheduling, not merely rendering. It needs strict semantic checks and is not a priority for GPU-bound FPS.

### L. MoreCulling-style block-face culling

Upstream: [MoreCulling](https://github.com/FxMorin/MoreCulling)

Its API allows blocks to define whether faces should be drawn or culled against neighbors. This can reduce geometry and overdraw in foliage and partial blocks.

Value: potentially useful in forests and dense structures.

Risks: transparency, partial shapes, connected textures, custom models, resource packs and modded block behavior. It can change visible output if assumptions are wrong. It is not as universally safe as skipping a fully occluded entity.

**Status:** Only consider opt-in, conservative rules with visual regression tests. Do not globally classify blocks as opaque.

### M. Nvidium and Vulkan backend alternatives

- [Nvidium](https://modrinth.com/mod/nvidium) is a Sodium rendering-backend replacement using NVIDIA mesh-shader extensions and GPU-driven terrain culling. Its published compatibility includes Minecraft 1.21.6–1.21.11 and it requires a supported NVIDIA GPU. It is not an ordinary mixin-level optimization.
- [Polydium](https://modrinth.com/project/polydium) advertises a Vulkan backend for 1.21.11 and describes additional Vulkan-specific rendering optimizations.

Value: changing the rendering backend can have larger GPU-side effects than small Java optimizations.

Risks: GPU/vendor support, driver issues, shader and mod compatibility, renderer API surface, and substantial maintenance. VantaBoost should not try to replace Sodium/Nvidium's renderer. Treat these as external renderer choices or optional compatibility targets, not a first-party backend rewrite.

## Optimizations that are not direct FPS targets

- Lithium: primarily game logic/tick-loop efficiency; useful when client tick work is the bottleneck, not a substitute for rendering optimization.
- FerriteCore: reduces memory use and can reduce GC pressure; does not inherently reduce per-frame GPU work.
- ModernFix: startup, memory and vanilla bug/performance fixes; benefit depends on enabled features and workload.
- C2ME: chunk generation/loading/saving throughput; most relevant during exploration and chunk work, not stable-camera rendering.
- Krypton: network stack/packet processing; multiplayer/network workload-specific.
- spark: profiler/measurement tool, not an FPS optimization.

Do not add these to VantaBoost merely to make its feature list longer. Do not duplicate optimizations already provided by the user's renderer/modpack.

## Candidate ranking for VantaBoost

| Rank | Candidate | Best workload | Visual impact | Main dependency/risk |
|---|---|---|---|---|
| 0 | Baseline profiling and frame-time instrumentation | All | None | Requires repeatable scenarios |
| 1 | Redundant framebuffer unbind avoidance | OpenGL render-pass-heavy scenes | Intended none | OpenGL state and mod compatibility |
| 2 | Sodium-aware particle section culling | Particle-heavy scenes | None if bounds/visibility are correct | Sodium internals; Particle Core |
| 3 | Entity/block-entity occlusion culling | Crowded scenes with hidden objects | None intended; false culls possible | Async snapshots, custom bounds, CPU cost |
| 4 | Deferred sky | GPU-bound terrain/sky scenes | None intended when depth/shaders are compatible | Shader/resource-pack compatibility |
| 5 | GPU terrain occlusion | GPU-bound dense terrain | None intended; high correctness burden | Sodium internals, depth capture, shaders |
| 6 | GUI item baking / outline scissor / text merging | Inventory, glowing entities, text-heavy HUD | None intended with correct invalidation/bounds | UI-specific mixins |
| 7 | Sprite ordering / debug caching | Animated textures / F3 open | None intended | Reload and cache invalidation |
| 8 | Idle block-entity ticks | Many idle chests/spawners | No intended visual change | Simulation semantics and mod conflicts |
| 9 | MoreCulling block-face rules | Foliage and dense block geometry | Possible | Resource packs and custom models |

## Required benchmark matrix

Before selecting an optimization, run the same fixed scene with baseline and candidate builds:

1. Empty flat world, stationary camera — baseline overhead.
2. Dense forest — terrain geometry, foliage and overdraw.
3. Village with many block entities — block-entity rendering and ticking.
4. Large visible mob group — entity submission/rendering.
5. Same mob group behind opaque walls — entity occlusion culling.
6. Particle-heavy scene — particle extraction and drawing.
7. Inventory with many static items — GUI item baking.
8. Glowing entity scene — outline post-processing.
9. Rapid movement through new terrain — chunk build/scheduling, not stable FPS.
10. Shader/resource-pack scene — compatibility and fallback checks.

Record per run: commit, mod list, resolution, render distance, simulation distance, graphics settings, VSync/FPS cap, Java/JVM args, CPU/GPU, driver, warm-up, duration, repetitions, average FPS, median/p95/p99 frame time, CPU render-thread time, GPU time, GPU utilization, allocations/GC, and scenario identifier.

Use a GPU profiler/RenderDoc or vendor tooling for GPU-bound cases and JFR/async-profiler for Java CPU hotspots. Never force a GPU sync every frame just to collect timing. Shared CI can validate builds and tests, not provide authoritative FPS thresholds.

## Current repository observations

- An experimental entity-only occlusion runtime and two client mixins now exist; block-entity occlusion remains unimplemented.
- `build.gradle` and `fabric.mod.json` do not depend on Fabric API.
- `docs/ARCHITECTURE.md` and `fabric.mod.json` no longer declare a Fabric API dependency.
- The source skill and benchmarking protocol already require profiling, exact descriptors, controlled comparisons and no fabricated FPS claims. This report adds concrete upstream source research.

## Decision gate before implementation

No candidate is approved until:
1. The target 1.21.11 Minecraft jar and resolved Sodium (if present) bytecode are inspected for exact call sites/descriptors.
2. A baseline profile identifies a matching bottleneck.
3. The implementation has a falsifiable hypothesis and explicit visual/compatibility tests.
4. A candidate can be disabled cleanly when its prerequisite renderer/mod is absent.
5. Benchmark data shows improvement outside measurement noise without unacceptable regressions.

**Current decision:** the entity-only mixin is an experimental implementation authorized as a correctness prototype. It is intentionally conservative and may cull fewer entities than full voxel culling. It must not be called production-accepted until the 1.21.11 build, mixin application, launch behavior, visual correctness, compatibility, and controlled performance benchmarks are verified.
