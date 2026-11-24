# Performance research ledger

Record every investigated optimization, including rejected experiments. Compilation or a successful launch is not acceptance.

## Status definitions

- Proposed
- Investigating
- Implemented
- Accepted
- Rejected
- Reverted

## Entry template

### [ID] Optimization name

- Status:
- Target Minecraft version:
- Subsystem:
- Relevant source paths / upstream references:
- Profile artifact and commit:
- Measured baseline cost:
- Root cause:
- Hypothesis:
- Proposed mechanism:
- Expected metrics:
- Correctness constraints:
- Compatibility risks:
- Implementation commit:
- Benchmark scenarios and repetitions:
- Before/after distributions:
- Regressions:
- Validation evidence:
- Rollback condition:
- Decision and rationale:

---

## Research state — 2026-11-24

Source-level research is documented in [RESEARCH.md](RESEARCH.md). Experimental entity and block-entity occlusion prototypes are implemented, but no VantaBoost runtime baseline has been captured and no optimization has been accepted on performance evidence.

### [VB-R01] Redundant framebuffer unbind avoidance

- Status: Investigating
- Target Minecraft version: 1.21.11
- Subsystem: OpenGL render-pass state management
- Relevant source: ImmediatelyFast 1.21.11 `MixinGlCommandEncoder`
- Profile artifact and commit: None
- Measured baseline cost: Unknown
- Root cause: Candidate redundant framebuffer unbind in `GlCommandEncoder.finishRenderPass`
- Hypothesis: Avoiding the intermediate unbind and explicitly restoring framebuffer 0 before presentation can reduce driver/state-transition overhead without changing rendered output.
- Correctness constraints: Framebuffer 0 must be bound before presentation; all third-party render passes must observe valid state.
- Compatibility risks: Shader/render mods and other mixins may rely on the unbind.
- Decision: Do not implement until exact target jar call site and baseline profile are verified.

### [VB-R02] Sodium-aware particle section culling

- Status: Proposed
- Target Minecraft version: 1.21.11
- Subsystem: Particle extraction/rendering
- Relevant source: Polydium `MixinParticleSectionCull`; Sodium `SodiumWorldRenderer.isBoxVisible`
- Profile artifact and commit: None
- Measured baseline cost: Unknown
- Hypothesis: Reusing Sodium's section visibility can skip particle extraction/render work for already-hidden sections with lower overhead than a second culler.
- Correctness constraints: Particle bounds, frustum behavior, and Particle Core compatibility.
- Compatibility risks: Sodium internals and particle mods.
- Decision: Prototype only if particle-heavy profiling supports it.

### [VB-R03] Conservative entity occlusion culling

- Status: Implemented — experimental entity-renderer mixin; not performance-accepted
- Target Minecraft version: 1.21.11
- Subsystem: Entity extraction/rendering
- Relevant source: Entity Culling `CullTask` and `OcclusionCullingInstance`
- Profile artifact and commit: None
- Measured baseline cost: Unknown
- Hypothesis: A cached single-plane face-coverage proof can skip rendering eligible entities fully hidden behind a bounded rectangle of adjacent opaque full blocks in crowded scenes.
- Correctness constraints: Fail open on uncertainty, project the complete conservative render and shadow envelope through a fully opaque rectangle of adjacent full-block faces on one plane, invalidate on camera/bounds/world changes, and preserve renderer-visible names, fire, glow, leashes, and custom renderers.
- Compatibility risks: Renderer-specific geometry beyond the reported bounds, block-state opacity semantics, world mutation invalidation, and CPU cost of ray queries.
- Decision: Keep the prototype narrow. Bounded interpolation is supported, but do not broaden to modded renderers or block entities until runtime visual checks and profiles justify it.
- Implementation: `LevelRendererEntityOcclusionMixin` marks the main `extractVisibleEntities` interval. `EntityRendererOcclusionMixin` redirects ordinal 0 of `Frustum.isVisible(AABB)Z` inside official-mapped `EntityRenderer.shouldRender(Entity, Frustum, double, double, double)Z`; later leash-holder fallback calls remain vanilla. It reuses the exact AABB vanilla already computed instead of calling `getBoundingBoxForCulling` a second time. The Frustum origin is used for the ray so captured-frustum rendering stays consistent. It only considers vanilla-package entity classes and renderers whose vanilla distance/culling predicates accepted the entity. The cache is client-thread-only, weak-keyed by entity identity, and holds only a weak reference to the active world.
- Occlusion proof: one block-collision ray from the exact Frustum camera origin to the conservative bounds center identifies a candidate face plane. The plane must lie between the camera and every AABB corner; the projected AABB must fit strictly inside a bounded rectangle of adjacent block-face cells, and every cell in that rectangle must be a vanilla full opaque block (`BlockState.isSolidRender()`). The rectangle is capped at 16 cells. Canvas, EntityCulling, and Entity Model Features installations disable this optimization. VulkanMod integration is implemented by wrapping (rather than redirecting) the vanilla `Frustum.isVisible(AABB)` invocation with MixinExtras `@WrapOperation`, so VulkanMod's own redirect can be preserved and composed. Iris integration is implemented for the no-shader-pack path; the culler queries Iris's public `IrisApi` once at the start of each main entity-extraction frame and fails open while a pack is active or if the API is unavailable. This avoids applying vanilla entity bounds to shader-deformed entity geometry. Both integrations still require runtime launch and visual-correctness verification with the respective renderer versions. Set `-Dvantaboost.disableEntityOcclusion=true` to disable it manually. If any condition fails, render normally.
- Eligibility: non-player vanilla entities whose interpolated position is conservatively enclosed by expanding the vanilla culling AABB by the complete previous-to-current displacement on each axis (up to 1.5 blocks per axis; larger motion/teleports fail open). Rotation interpolation does not rotate the axis-aligned entity bounds. Scoreboard/status glowing, fire animation, sleeping/dying/auto-spin living entities, leash attachments, mounted/passenger relationships, the active camera entity, entities for which the active renderer reports that it will extract a name tag, end crystals, guardians, wardens, modded entities, and custom renderers fail open. Renderer-specific name-tag policy/submission, culling-bound, affectedByCulling, or shouldRender policy overrides, custom render-state extraction/finalization, submit overrides, custom LivingEntityRenderer scale/rotation transforms, shadow-radius overrides outside the vanilla LivingEntityRenderer/MobRenderer scaling path, and any shadow-strength or render-offset override also fail open. Vanilla living renderers use entity scale and mob age scale when expanding shadow bounds. EntityRenderDispatcher applies renderer position offsets after extraction, so arbitrary renderer-specific offsets are not covered by the vanilla culling AABB. The tested bounds include the full floored shadow footprint and strength-dependent vertical projection.
- Cache validity: exact conservative world-space bounds snapshots are part of each entry. ClientLevel direct, verified, and predicted block-state updates, section activation, chunk loads, chunk unloads, and world changes advance a world revision. Positive (occluded) entries require the same revision and exact camera snapshot. Negative (visible/not-proven-occluded) entries remain reusable across camera movement and world revisions because reusing them can only cause extra rendering, never hide newly visible geometry. This avoids clearing/scanning the entire weak cache for every block update and avoids repeating failed proofs solely because the camera moved. A rotating entity-ID window limits first-time queries to 64 per game tick.
- CI coverage: JUnit coverage includes all three face axes, both camera sides, projected bounds outside the plane, negative and out-of-range block coordinates, bounded face-cell ranges, 8,000 deterministic interior projection samples, and renderer override detection for name-tag, culling, state extraction/finalization, submission, and model-transform hooks. `verifyClientMixinConfiguration` checks metadata registration, required injection policy, and compiled client mixin classes. A headless client smoke run forces `EntityRenderer`, `LivingEntityRenderer`, `MobRenderer`, `ClientLevel`, and `LevelRenderer` through the Mixin transformer, then verifies that both living-renderer name-tag invokers were applied before exiting. GitHub Actions run [#277](https://github.com/ItsDropper/VantaBoost/actions/runs/45264229255) on commit `f605407` completed successfully: `clean build` (including `:test` and `:check`), the client mixin smoke run, and JAR upload all passed. This validates compilation, tests, and mixin-application smoke only; it is not a visual regression test or a performance benchmark.
- Diagnostics: opt-in profiling via `-Dvantaboost.profileEntityOcclusion=true` emits 300-frame summaries for candidate calls, culls, average/maximum full-culler time, cache hits, cached occlusions, query-budget skips, planned face-cell checks, occlusion-query count, occlusion results, and average full occlusion-query time. Full-culler and full occlusion-query timing are collected only when explicitly enabled.
- Limitations: this is a single-plane contiguous-face occlusion test, not a general multi-plane voxel occlusion solver. It deliberately misses many valid culling opportunities. No VantaBoost interactive visual regression test, controlled runtime baseline, or FPS measurement exists; performance acceptance is therefore pending. The headless CI client launch and mixin-application smoke test passed.

### [VB-R09] Conservative block-entity occlusion culling

- Status: Implemented — experimental pre-extraction prototype; not performance-accepted
- Target Minecraft version: 1.21.11
- Subsystem: Block-entity render-state extraction and submission
- Relevant source paths / upstream references: Minecraft LevelRenderer.extractVisibleBlockEntities(Camera,float,LevelRenderState), BlockEntityRenderDispatcher.tryExtractRenderState(BlockEntity,float,CrumblingOverlay), and BlockEntityRenderer.shouldRender; Entity Culling's BlockEntityRenderDispatcherMixin
- Profile artifact and commit: None
- Measured baseline cost: Unknown; no block-entity render-state or submission profile has been captured
- Root cause: In block-entity-dense scenes, vanilla can pass a block entity through its renderer distance check and still create a render state and submit geometry even when a full opaque wall hides the complete conservative render envelope.
- Hypothesis: Rejecting proven-occluded block entities immediately after vanilla's renderer-specific shouldRender check, but before createRenderState and extractRenderState, can reduce extraction, render submission, and GPU work in dense builds.
- Proposed mechanism: LevelRendererBlockEntityOcclusionMixin scopes checks to the main visible-block-entity extraction pass. BlockEntityRenderDispatcherOcclusionMixin wraps the renderer's existing shouldRender result and preserves it; only a vanilla-eligible object can be considered. A strict exact-class allowlist covers chest, bed, shulker box, decorated pot, bell, enchanting table, lectern, and copper-golem-statue renderer/entity pairs. The culler uses a conservative three-block AABB envelope, one center ray, and the same all-cells-on-one-coplanar-plane proof as the entity prototype. A 32-query-per-tick budget, rotating position buckets, weak-key cache, exact bounds snapshots, exact-camera reuse for positive occlusion results, and conservative reuse of negative results across camera/world changes bound the work.
- Expected metrics: Fewer block-entity render states, renderer submissions, and GPU work in occluded block-entity-heavy scenes; possible render-thread overhead from raycasts and cache checks.
- Correctness constraints: Preserve vanilla shouldRender; exclude globally rendered and off-screen renderers; exact vanilla class matches only; fail open for unknown renderers/entities, shader-pack-active Iris, missing camera/world state, budget exhaustion, raycast errors, partial/holed occluders, or any uncertain projection. The three-block envelope is intentionally broad; it must be visually validated for every allowlisted renderer, especially double chests, beds, and animated models.
- Compatibility risks: Renderer mods can alter block-entity geometry or extraction flow. Canvas and Entity Culling disable this prototype. Iris shader packs cause fail-open. Other renderer combinations still require runtime testing.
- Implementation commits: ae5abee through a75ec58 (main)
- Benchmark scenarios and repetitions: Not run. Required: identical dense village/build scene with many visible and wall-occluded block entities, plus empty-world and ordinary gameplay controls; repeat with profiling disabled for final frame-time comparison.
- Before/after distributions: None
- Regressions: Unknown
- Validation evidence: JUnit tests cover every allowlisted renderer/entity pair, reject mismatched and modded pairs, reject off-screen/global cases, and verify conservative cache reuse across camera motion. `OcclusionCachePolicyTest` separately exercises the shared cache contract: visible results remain reusable after world/camera changes, while positive occlusion results require matching world revisions and camera snapshots. GitHub Actions run [#331](https://github.com/ItsDropper/VantaBoost/actions/runs/45398307885) on commit `9b4318a` passed `clean build` (including JUnit tests and `verifyClientMixinConfiguration`), the headless client mixin-application smoke test, and JAR upload. This does not verify in-game visual correctness or performance; no controlled runtime benchmark exists.
- Rollback condition: Revert/disable if any allowlisted renderer is visibly clipped, if occlusion invalidation is stale, or if the culler overhead exceeds saved extraction/submission time in representative scenes.
- Decision and rationale: Keep as an experimental implementation disabled by default. Enable manually with -Dvantaboost.enableBlockEntityOcclusion=true; force it off with -Dvantaboost.disableBlockEntityOcclusion=true. Do not claim FPS gains or mark accepted until the visual gate and controlled benchmark pass.

### [VB-R04] Deferred sky rendering

- Status: Proposed
- Target Minecraft version: 1.21.11
- Subsystem: GPU fragment/fill work
- Relevant source: Polydium `SkyFarPlane` and `MixinDeferredSky`
- Profile artifact and commit: None
- Measured baseline cost: Unknown
- Hypothesis: Drawing compatible sky geometry after opaque terrain allows depth testing to reject sky pixels hidden behind terrain.
- Correctness constraints: Exact depth, fog, sky and transparency ordering; shader/resource-pack fallback.
- Compatibility risks: Iris and custom sky/resource-pack pipelines.
- Decision: Research/visual-prototype only after confirming GPU-bound scenes.

### [VB-R05] GPU terrain occlusion culling

- Status: Proposed
- Target Minecraft version: 1.21.11
- Subsystem: Sodium terrain rendering
- Relevant source: Polydium `OcclusionCuller` and `GpuWaitMeter`; Sodium 1.21.11 `RenderSectionManager`
- Profile artifact and commit: None
- Measured baseline cost: Unknown
- Hypothesis: Depth-derived visibility can reject hidden terrain sections and reduce GPU geometry/fragment work when the GPU is saturated.
- Correctness constraints: Depth freshness, camera reprojection, animated geometry, bounds invalidation, transparency and fail-open behavior.
- Compatibility risks: Sodium internals, shaders, GPU capture overhead and resource lifetime.
- Decision: Defer; high complexity and not a first implementation.

### [VB-R06] GUI item baking, outline scissoring and text-effect merging

- Status: Proposed
- Target Minecraft version: 1.21.11
- Subsystem: GUI and post-processing
- Relevant source: Polydium `GuiItemBake`, `OutlineScissor`, `MixinTextEffectMerge`
- Profile artifact and commit: None
- Measured baseline cost: Unknown
- Hypothesis: Cache unchanged GUI item render output, bound outline post-processing to the affected screen region, and merge adjacent text decoration geometry.
- Correctness constraints: Cache invalidation, animated items, resource reloads, text style boundaries and conservative screen bounds.
- Compatibility risks: GUI/render mods and custom post-processing.
- Decision: Only pursue if GUI/outline workloads are part of the target.

### [VB-R07] Animated sprite ordering

- Status: Proposed
- Target Minecraft version: 1.21.11
- Subsystem: Texture atlas updates
- Relevant source: Polydium `AnimatedSpriteOrder`
- Profile artifact and commit: None
- Measured baseline cost: Unknown
- Hypothesis: Grouping interpolation-driven sprite states can reduce pipeline switches during animation updates.
- Correctness constraints: Preserve frame interpolation and atlas update order.
- Compatibility risks: Texture animation/resource reload interactions.
- Decision: Low priority; measure atlas update cost first.

### [VB-R08] Idle block-entity tick skipping

- Status: Proposed
- Target Minecraft version: 1.21.11
- Subsystem: Client simulation
- Relevant source: Polydium `IdleBlockEntityTicks`
- Profile artifact and commit: None
- Measured baseline cost: Unknown
- Hypothesis: Skipping provably idle chest/spawner client updates can reduce client tick cost in dense scenes.
- Correctness constraints: Never skip active animation or nearby-player spawner behavior.
- Compatibility risks: Other block-entity optimization mods and custom subclasses.
- Decision: Not a rendering priority; only investigate if client tick profiling identifies it.

---

## Initial state

The initial repository commit established project structure only and made no performance claims. Research entries above are hypotheses, not accepted optimizations.
