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
- Occlusion proof: one block-collision ray from the exact Frustum camera origin to the conservative bounds center identifies a candidate face plane. The plane must lie between the camera and every AABB corner; the projected AABB must fit strictly inside a bounded rectangle of adjacent block-face cells, and every cell in that rectangle must be a vanilla full opaque block (`BlockState.isSolidRender()`). The rectangle is capped at 16 cells. Canvas, EntityCulling, and Entity Model Features installations disable this optimization. VulkanMod integration is implemented by wrapping (rather than redirecting) the vanilla `Frustum.isVisible(AABB)` invocation with MixinExtras `@WrapOperation`, so VulkanMod's own redirect can be preserved and composed. Iris integration is implemented for the no-shader-pack path; the culler queries Iris's public `IrisApi` once at the start of each main entity-extraction frame and fails open while a pack is active or if the API is unavailable. This avoids applying vanilla entity bounds to shader-deformed entity geometry. Both integrations still require runtime launch and visual-correctness verification with the respective renderer versions. The prototype is disabled by default; enable it with `-Dvantaboost.enableEntityOcclusion=true` and force it off with `-Dvantaboost.disableEntityOcclusion=true`. If any condition fails, render normally.
- Eligibility: non-player vanilla entities whose interpolated position is conservatively enclosed by expanding the vanilla culling AABB by the complete previous-to-current displacement on each axis (up to 1.5 blocks per axis; larger motion/teleports fail open). Rotation interpolation does not rotate the axis-aligned entity bounds. Scoreboard/status glowing, fire animation, sleeping/dying/auto-spin living entities, leash attachments, mounted/passenger relationships, the active camera entity, entities for which the active renderer reports that it will extract a name tag, end crystals, guardians, wardens, modded entities, and custom renderers fail open. Renderer-specific name-tag policy/submission, culling-bound, affectedByCulling, or shouldRender policy overrides, custom render-state extraction/finalization, submit overrides, custom LivingEntityRenderer scale/rotation transforms, shadow-radius overrides outside the vanilla LivingEntityRenderer/MobRenderer scaling path, and any shadow-strength or render-offset override also fail open. Vanilla living renderers use entity scale and mob age scale when expanding shadow bounds. EntityRenderDispatcher applies renderer position offsets after extraction, so arbitrary renderer-specific offsets are not covered by the vanilla culling AABB. The tested bounds include the full floored shadow footprint and strength-dependent vertical projection.
- Cache validity: exact conservative world-space bounds snapshots are part of each entry. ClientLevel direct, verified, and predicted block-state updates, section activation, chunk loads, chunk unloads, and world changes advance a world revision. Positive (occluded) entries require the same revision and exact camera snapshot. Negative (visible/not-proven-occluded) entries remain reusable across camera movement and world revisions because reusing them can only cause extra rendering, never hide newly visible geometry. This avoids clearing/scanning the entire weak cache for every block update and avoids repeating failed proofs solely because the camera moved. A rotating entity-ID window limits first-time queries to 64 per game tick.
- CI coverage: JUnit coverage includes all three face axes, both camera sides, projected bounds outside the plane, negative and out-of-range block coordinates, bounded face-cell ranges, 8,000 deterministic interior projection samples, and renderer override detection for name-tag, culling, state extraction/finalization, submission, and model-transform hooks. `verifyClientMixinConfiguration` checks metadata registration, required injection policy, and compiled client mixin classes. A headless client smoke run forces `EntityRenderer`, `LivingEntityRenderer`, `MobRenderer`, `ClientLevel`, `LevelRenderer`, and `BlockEntityRenderDispatcher` through the Mixin transformer. It verifies both living-renderer name-tag invokers and checks that the entity culling wrapper, block-entity dispatcher wrapper, both LevelRenderer extraction scopes, and all six ClientLevel block/chunk invalidation handlers were merged into their targets before exiting. GitHub Actions run [#339](https://github.com/ItsDropper/VantaBoost/actions/runs/45401790815) on commit `4fb47b7` completed successfully: `clean build` (including `:test` and `:check`), the expanded client mixin smoke run, and JAR upload all passed. This validates compilation, tests, and mixin-application smoke only; it is not a visual regression test or a performance benchmark.
- Diagnostics: enable the feature with `-Dvantaboost.enableEntityOcclusion=true` and opt-in profiling with `-Dvantaboost.profileEntityOcclusion=true`. While the feature is active, it emits 300-frame summaries for candidate calls, culls, average/maximum full-culler time, cache hits, cached occlusions, query-budget skips, planned face-cell checks, occlusion-query count, occlusion results, and average full occlusion-query time. The disabled feature path skips Iris compatibility reflection and diagnostic counters; full-culler and full occlusion-query timing are collected only when explicitly enabled.
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
- Compatibility risks: Renderer mods can alter block-entity geometry or extraction flow. Canvas and Entity Culling disable this prototype. Iris shader packs cause fail-open. VulkanMod's custom `WorldRenderer` bypasses vanilla `LevelRenderer.extractVisibleBlockEntities`; the extraction-scope guard therefore leaves the block-entity culler inactive on that path. This is source-level fail-open behavior, not a live compatibility test. Other renderer combinations still require runtime testing.
- Implementation commits: ae5abee through a75ec58 (main)
- Benchmark scenarios and repetitions: Not run. Required: identical dense village/build scene with many visible and wall-occluded block entities, plus empty-world and ordinary gameplay controls; repeat with profiling disabled for final frame-time comparison.
- Before/after distributions: None
- Regressions: Unknown
- Validation evidence: JUnit tests cover every allowlisted renderer/entity pair, reject mismatched and modded pairs, reject off-screen/global cases, and verify conservative cache reuse across camera motion. `OcclusionCachePolicyTest` separately exercises the shared cache contract: visible results remain reusable after world/camera changes, while positive occlusion results require matching world revisions and camera snapshots. GitHub Actions run [#331](https://github.com/ItsDropper/VantaBoost/actions/runs/45398307885) on commit `9b4318a` passed `clean build` (including JUnit tests and `verifyClientMixinConfiguration`), the headless client mixin-application smoke test, and JAR upload. The expanded run [#339](https://github.com/ItsDropper/VantaBoost/actions/runs/45401790815) verified that the block-entity dispatcher wrapper and both block-entity extraction-scope handlers were merged into their targets. Run [#343](https://github.com/ItsDropper/VantaBoost/actions/runs/45402612236) then verified all six ClientLevel block/chunk invalidation handlers in the same headless smoke test; build, JUnit tests, mixin smoke, and JAR upload passed. These CI checks do not verify in-game visual correctness or performance; no controlled runtime benchmark exists.
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

### [VB-R10] Sodium-aware particle section culling

- Status: Proposed — source-level mechanism inspected; not implemented
- Target Minecraft version: 1.21.11
- Subsystem: Particle render-state extraction
- Relevant source: Minecraft `QuadParticleGroup.extractRenderState`; Polydium `MixinParticleSectionCull` at commit `a9ac437`; Sodium `SodiumWorldRenderer.isBoxVisible`, `RenderSectionManager.isBoxVisible`, and `SectionTree.isBoxVisible` at commit `3a99e4e`
- Profile artifact and commit: None
- Measured baseline cost: Unknown; no particle-heavy VantaBoost profile exists
- Root cause: Vanilla checks `Frustum.pointInFrustum` for each `SingleQuadParticle` before calling its extraction method, but does not additionally use Sodium's already-computed section-occlusion visibility graph for that particle position.
- Hypothesis: For particles in non-empty sections that Sodium's visibility graph currently considers hidden, a second conservative visibility predicate can reject the particle before quad extraction and downstream submission.
- Proposed mechanism: When Sodium is present, preserve the existing vanilla frustum result and consult the public `SodiumWorldRenderer.isBoxVisible` API for the particle's section position. Fail open if Sodium or its render tree is unavailable. Cache only positive section-visible results for the current tick; do not cache hidden results. Compose with the existing MixinExtras expression value so Particle Core's existing wrapper is not replaced or duplicated.
- Expected metrics: Fewer `SingleQuadParticle.extract` calls and less particle render-state/vertex submission in particle-heavy scenes behind occluded terrain; extra per-particle section-visibility work may outweigh savings at low particle counts.
- Correctness constraints: Preserve the vanilla frustum result; treat empty, unloaded, out-of-height, null-render-tree, and uncertain sections as visible. Use the actual interpolated quad size, not only the base `Particle` AABB: vanilla examples include a firework overlay reaching 7.1, explosion-sized particles, growing dragon-breath particles, scaled campfire smoke, and vibration particles whose quad size exceeds their default bounds. Cover the previous-to-current interpolated particle center segment as well. Unknown/custom extraction subclasses fail open until their render envelopes are proven.
- Compatibility risks: Sodium API/version drift; Particle Core wraps the same frustum expression; custom particle renderers and large billboard particles; stale/rebuilding Sodium visibility graphs.
- Implementation commit: None
- Benchmark scenarios and repetitions: Not run. Required: particle-heavy scene with many particles in hidden non-empty sections, ordinary gameplay control, and particles in empty/unloaded sections; compare Sodium visibility culling on/off with profiling disabled for final frame-time measurements.
- Before/after distributions: None
- Regressions: Unknown
- Validation evidence: Source inspection confirms the early extraction gate in Minecraft 1.21.11 and the section-visibility semantics in the inspected Sodium source. An opt-in baseline profiler now measures `ParticleEngine.extract` duration, `QuadParticleGroup` duration, per-extraction-call candidate counts, and vanilla frustum survivors/rejections with `-Dvantaboost.profileParticleExtraction=true`; it does not change visibility results. CI run [#45418003923](https://github.com/ItsDropper/VantaBoost/actions/runs/45418003923) passed build/tests, the headless client mixin smoke test for both profiler targets, and JAR upload. The Mixin config plugin excludes both particle diagnostics mixins entirely when the profiler property is absent, so no per-particle callback is installed in normal launches. CI enables the property to verify both profiler targets. No profiler capture or runtime benchmark has been collected yet. Detailed source analysis and implementation gate are in [RESEARCH.md](RESEARCH.md#d-sodium-aware-particle-section-culling).
- Rollback condition: Do not enable by default; disable the integration if any particle disappears while its billboard intersects a visible section or if added query overhead exceeds saved extraction work.
- Decision and rationale: Research candidate only. Do not implement until the vanilla particle subclass bounds are audited and a particle-heavy baseline demonstrates a meaningful render-thread cost.

### [VB-R11] Disabled occlusion dispatch fast path

- Status: Implemented — unbenchmarked
- Target Minecraft version: 1.21.11
- Subsystem: Entity and block-entity visibility filtering
- Relevant source paths: `EntityRendererOcclusionMixin`, `BlockEntityRenderDispatcherOcclusionMixin`, both occlusion cullers, and `VantaBoostMixinPlugin`
- Profile artifact and commit: None; implementation commits [47e3ae7](https://github.com/ItsDropper/VantaBoost/commit/47e3ae76bbb1db6a0d53852252d4a62bfd2ea5c4), [a475e5e](https://github.com/ItsDropper/VantaBoost/commit/a475e5e3bbe9a51570d237c1df0f5e812448706c), and [e23309a](https://github.com/ItsDropper/VantaBoost/commit/e23309ad2db70785ff2988b2b6d6eea9f6bc97e9)
- Measured baseline cost: Unknown; no runtime capture exists.
- Root cause: Initially, disabled culling wrappers still entered VantaBoost-specific dispatch on vanilla-visible candidates. The entity wrapper additionally evaluated renderer/name-tag eligibility before the culler rejected the disabled feature.
- Hypothesis: An early enable check avoids the policy path, while omitting culling mixins entirely when disabled removes the remaining wrapper and extraction-scope overhead.
- Proposed mechanism: Preserve defensive early returns and apply entity/block-entity mixins only when the matching startup enable flag is true and its force-disable flag is false. Apply shared ClientLevel invalidation only when either feature is active.
- Expected metrics: Lower render-thread CPU work in default-disabled entity-heavy and block-entity-heavy scenes. No numerical improvement is claimed.
- Correctness constraints: When enabled, preserve the wrapped vanilla/third-party operation exactly once and keep fail-open behavior. When disabled, relevant mixin handlers must not appear in transformed target classes.
- Compatibility risks: MixinExtras wrapper composition and mixin-config plugin name matching; validate both enabled and disabled target transformation.
- Implementation commits: [47e3ae7](https://github.com/ItsDropper/VantaBoost/commit/47e3ae76bbb1db6a0d53852252d4a62bfd2ea5c4), [a475e5e](https://github.com/ItsDropper/VantaBoost/commit/a475e5e3bbe9a51570d237c1df0f5e812448706c), and [e23309a](https://github.com/ItsDropper/VantaBoost/commit/e23309ad2db70785ff2988b2b6d6eea9f6bc97e9)
- Benchmark scenarios and repetitions: Not run. Required: identical entity-dense and village/block-entity scenes with properties omitted, comparing the original branch and current default launch using render-thread JFR and frame-time distributions; repeat with each culler enabled.
- Before/after distributions: None
- Regressions: Unknown
- Validation evidence: [Run #45424271521](https://github.com/ItsDropper/VantaBoost/actions/runs/45424271521) passed the initial disabled-path code build/smoke. [Run #45426844973](https://github.com/ItsDropper/VantaBoost/actions/runs/45426844973) passed build/tests, enabled-feature mixin smoke, default-disabled mixin smoke, and JAR upload. No runtime FPS benchmark has been collected.
- Rollback condition: Revert if enabled-mode behavior changes or if controlled measurements show no benefit and the gating introduces compatibility problems.
- Decision and rationale: Keep as a narrow default-overhead reduction. It is not performance-accepted until runtime measurement and visual correctness gates are complete.

### [VB-R12] Cache Iris shader-pack API resolution

- Status: Implemented — unbenchmarked
- Target Minecraft version: 1.21.11
- Subsystem: Entity-occlusion compatibility checks
- Relevant source path: `IrisShaderPackCompatibility`
- Profile artifact and commit: None
- Measured baseline cost: Unknown; no runtime profile isolates Iris API lookup cost.
- Root cause: The entity culler checks Iris once per main entity-extraction frame, but the compatibility helper repeated class lookup, method lookup, and singleton retrieval on every frame.
- Hypothesis: Caching the Iris API instance and method after the first successful resolution removes repeated reflection discovery from the per-frame compatibility check while preserving the same shader-pack decision.
- Proposed mechanism: Lazily resolve and cache the public Iris API on first use. Cache an unavailable sentinel on lookup/linkage/security failures and fail open; invoke the cached method on subsequent frames. Do not initialize or query Iris when the mod is absent.
- Expected metrics: Lower CPU time in the Iris-enabled entity-occlusion frame setup path. No numerical improvement is claimed.
- Correctness constraints: Active shader packs and unknown API states must continue to disable entity occlusion. No hard Iris dependency may be introduced.
- Compatibility risks: Iris API method visibility/signature changes; failures must remain fail-open.
- Implementation and test commits: [2ce93a5](https://github.com/ItsDropper/VantaBoost/commit/2ce93a5fb97fbc7d80eff74e9b6a53c0b466a546), [66f19d2](https://github.com/ItsDropper/VantaBoost/commit/66f19d2f806ab7bf5fadf29e129571012bbb7711), [bc829a4](https://github.com/ItsDropper/VantaBoost/commit/bc829a4571afecbf105076befc2e73b61d83982c), and [3016c8d](https://github.com/ItsDropper/VantaBoost/commit/3016c8d57c5b01c00bf26088ddd967c123671ea7)
- Benchmark scenarios and repetitions: Not run. Required: compare entity-extraction frame setup with Iris loaded and no shader pack, then with a shader pack active, before/after the cache; verify active-pack and API-failure paths never enable culling.
- Before/after distributions: None
- Regressions: Unknown
- Validation evidence: [Run #45425081341](https://github.com/ItsDropper/VantaBoost/actions/runs/45425081341) passed build/tests, mixin smoke, and JAR upload. [Run #45427657074](https://github.com/ItsDropper/VantaBoost/actions/runs/45427657074) passed the expanded test suite, including singleton discovery once, per-frame shader-pack state queries, and fail-open behavior when the optional Iris API throws; both enabled and default-disabled client smoke tests and JAR upload also passed.
- Rollback condition: Revert if Iris versions fail to resolve correctly, active shader packs do not reliably fail open, or profiling shows no benefit and the cache adds complexity.
- Decision and rationale: Keep as a narrow compatibility-path overhead reduction, not a performance-accepted optimization.

### [VB-R13] Remove disabled particle-profiler mixins from the hot path

- Status: Implemented — unbenchmarked
- Target Minecraft version: 1.21.11
- Subsystem: Particle render-state extraction diagnostics
- Relevant source paths: `VantaBoostMixinPlugin`, `ParticleEngineDiagnosticsMixin`, and `QuadParticleGroupDiagnosticsMixin`
- Profile artifact and commit: None; implementation commit [bdb7eb4](https://github.com/ItsDropper/VantaBoost/commit/bdb7eb4cb78f3ccec40296ab7ec41467e82d1be5)
- Measured baseline cost: Unknown; no runtime capture isolates the disabled profiler callback.
- Root cause: The frustum-expression diagnostics handler was invoked for every candidate particle even when its internal enabled flag immediately returned the original result. A branch inside the callback did not remove the callback from the per-particle path.
- Hypothesis: Excluding both profiler mixins at startup when the property is absent removes the per-particle instrumentation call entirely.
- Proposed mechanism: Apply the two particle diagnostics mixins only when `vantaboost.profileParticleExtraction=true`. The CI smoke launch explicitly enables the property to validate both targets.
- Expected metrics: No profiler callback or branch in the ordinary per-particle frustum path. No FPS gain is claimed.
- Correctness constraints: When profiling is enabled, preserve the original frustum result and counters must not alter visibility decisions.
- Compatibility risks: Mixin config plugin must match both fully qualified mixin names; CI verifies the enabled path and default-disabled smoke verifies their absence.
- Implementation commit: [bdb7eb4](https://github.com/ItsDropper/VantaBoost/commit/bdb7eb4cb78f3ccec40296ab7ec41467e82d1be5)
- Benchmark scenarios and repetitions: Not run. Required: compare default launch and profiler-enabled launch in a particle-heavy scene; use profiler-enabled runs only for diagnosis, not final frame-time comparison.
- Before/after distributions: None
- Regressions: Unknown
- Validation evidence: [Run #45425326353](https://github.com/ItsDropper/VantaBoost/actions/runs/45425326353) passed build/tests, enabled profiler-mixin smoke, and JAR upload. [Run #45426844973](https://github.com/ItsDropper/VantaBoost/actions/runs/45426844973) also passed the default-disabled mixin smoke.
- Rollback condition: Revert if opt-in profiler mixins fail to apply or their enabled output changes particle visibility.
- Decision and rationale: Keep; removing diagnostics from ordinary hot paths is preferable to relying on a per-particle disabled branch.

### [VB-R14] Conditionally apply optional culling and CI mixins

- Status: Implemented — unbenchmarked
- Target Minecraft version: 1.21.11
- Subsystem: Mixin startup and optional feature activation
- Relevant source paths: `VantaBoostMixinPlugin`, `ClientSmokeTestMixin`, and the entity/block-entity occlusion mixins
- Profile artifact and commit: None; implementation commits [a475e5e](https://github.com/ItsDropper/VantaBoost/commit/a475e5e3bbe9a51570d237c1df0f5e812448706c), [e23309a](https://github.com/ItsDropper/VantaBoost/commit/e23309ad2db70785ff2988b2b6d6eea9f6bc97e9), [913d523](https://github.com/ItsDropper/VantaBoost/commit/913d523bd1a34442dced6d900e551fd9fc4ab03e), [0378631](https://github.com/ItsDropper/VantaBoost/commit/03786315fb9d6464f0742c0cefd3f7a77f1801e9), and [03153ef](https://github.com/ItsDropper/VantaBoost/commit/03153ef682d22e4280ee9ba321d496b8dea0aaca)
- Measured baseline cost: Unknown; no runtime capture exists.
- Root cause: Experimental culling wrappers, shared invalidation hooks, and the CI-only tick probe were registered even when their features were disabled.
- Hypothesis: Applying only the mixins required by enabled startup properties removes unnecessary transformed handlers from normal launches.
- Proposed mechanism: Gate entity mixins on the entity enable/force-disable pair, block-entity mixins on the block-entity pair, shared ClientLevel invalidation on either active feature, and the smoke-test mixin on `vantaboost.ciSmoke`. Keep profiler mixins under their separate profiling property.
- Expected metrics: Fewer injected wrappers and no CI-only tick callback in normal launches. No numerical improvement is claimed.
- Correctness constraints: Preserve required mixins when enabled; omit them when disabled; do not directly load mixin classes from the smoke test. All feature properties are startup-only.
- Compatibility risks: Future mixin additions must be added to the plugin's feature sets and smoke assertions.
- Benchmark scenarios and repetitions: Not run; this is a startup/disabled-overhead reduction rather than a claimed rendering optimization.
- Before/after distributions: None
- Regressions: The first disabled-mode smoke exposed an illegal direct reference to an optional mixin interface. The probe now checks transformed interface names without loading mixin classes directly.
- Validation evidence: [Run #45428832038](https://github.com/ItsDropper/VantaBoost/actions/runs/45428832038) passed build/tests, full-enabled, entity-only, block-entity-only, and default-disabled mixin smoke tests, plus JAR upload.
- Rollback condition: Revert if any required enabled mixin is omitted or any disabled optional mixin remains applied.
- Decision and rationale: Keep; optional features should not impose injected runtime work when they are not enabled.

### [VB-R12] Cache Iris shader-pack API resolution

- Status: Implemented — unbenchmarked
- Target Minecraft version: 1.21.11
- Subsystem: Entity-occlusion compatibility checks
- Relevant source path: `IrisShaderPackCompatibility`
- Profile artifact and commit: None; implementation commit is recorded below.
- Measured baseline cost: Unknown; no runtime profile isolates Iris API lookup cost.
- Root cause: The entity culler checks Iris once per main entity-extraction frame, but the compatibility helper repeated class lookup, method lookup, and singleton retrieval on every frame.
- Hypothesis: Caching the Iris API instance and method after the first successful resolution removes repeated reflection discovery from the per-frame compatibility check while preserving the same shader-pack decision.
- Proposed mechanism: Lazily resolve and cache the public Iris API on first use. Cache an unavailable sentinel on lookup/linkage/security failures and fail open; invoke the cached method on subsequent frames. Do not initialize or query Iris when the mod is absent.
- Expected metrics: Lower CPU time in the Iris-enabled entity-occlusion frame setup path. No numerical improvement is claimed.
- Correctness constraints: Active shader packs and unknown API states must continue to disable entity occlusion. No hard Iris dependency may be introduced.
- Compatibility risks: Iris API method visibility/signature changes; failures must remain fail-open.
- Implementation commits: [2ce93a5](https://github.com/ItsDropper/VantaBoost/commit/2ce93a5fb97fbc7d80eff74e9b6a53c0b466a546) (implementation) and [66f19d2](https://github.com/ItsDropper/VantaBoost/commit/66f19d2f806ab7bf5fadf29e129571012bbb7711) (Java multi-catch correction).
- Benchmark scenarios and repetitions: Not run. Required: compare entity-extraction frame setup with Iris loaded and no shader pack, then with a shader pack active, before/after the cache; verify the active-pack path never enables culling.
- Before/after distributions: None
- Regressions: Unknown
- Validation evidence: Source inspection confirms the repeated reflective discovery was in the once-per-frame call path. Build and runtime verification are pending.
- Rollback condition: Revert if Iris versions fail to resolve correctly, active shader packs do not reliably fail open, or profiling shows no benefit and the cache adds complexity.
- Decision and rationale: Keep as a narrow compatibility-path overhead reduction, not a performance-accepted optimization.

### [VB-R15] Cache Iris mod presence at startup

- Status: Implemented — unbenchmarked
- Target Minecraft version: 1.21.11
- Subsystem: Entity and block-entity occlusion compatibility checks
- Relevant source paths: `EntityOcclusionCuller` and `BlockEntityOcclusionCuller`
- Profile artifact and commit: None; implementation commit [810c169](https://github.com/ItsDropper/VantaBoost/commit/810c169892e8258494530a598aa8f34094edf8e8)
- Measured baseline cost: Unknown; no profile isolates FabricLoader lookup cost.
- Root cause: Both extraction-start methods queried `FabricLoader.isModLoaded("iris")` on every frame even though the loaded mod set is fixed after startup.
- Hypothesis: Caching the Iris-presence result once per culler removes a redundant loader lookup from each extraction frame.
- Proposed mechanism: Initialize a static final `IRIS_LOADED` only when that culler is enabled and no unsupported renderer has disabled it; pass the cached value to the Iris compatibility helper. This follows the same startup-cache pattern used by [Nvidium's IrisCheck](https://github.com/MCRcortex/nvidium/blob/dev/src/main/java/me/cortex/nvidium/sodiumCompat/IrisCheck.java).
- Expected metrics: Fewer FabricLoader lookups in enabled entity/block-entity extraction paths. No numerical improvement is claimed.
- Correctness constraints: Preserve the shader-pack fail-open check when Iris is loaded; skip the lookup entirely when the culler is disabled or already incompatible.
- Compatibility risks: Mod presence is assumed immutable after Fabric startup, which matches Fabric's normal lifecycle.
- Benchmark scenarios and repetitions: Not run. Required: compare extraction-frame setup with Iris installed and absent, with each culler enabled, using render-thread JFR; no runtime claim is made from source inspection alone.
- Before/after distributions: None
- Regressions: Unknown
- Validation evidence: [Run #45430122580](https://github.com/ItsDropper/VantaBoost/actions/runs/45430122580) passed build/tests, full-enabled, entity-only, block-entity-only, and default-disabled client smoke tests, plus JAR upload. This verifies the cached flag does not disturb any mixin configuration; it does not measure runtime lookup savings.
- Rollback condition: Revert if Iris is not detected correctly in supported launch configurations or if runtime profiling shows no benefit and the cached state complicates compatibility.
- Decision and rationale: Keep as a narrow immutable-state lookup reduction; not performance-accepted until runtime measurement.

### [VB-R16] Circuit-break failed Iris API invocations

- Status: Implemented — unbenchmarked
- Target Minecraft version: 1.21.11
- Subsystem: Optional Iris shader-pack compatibility
- Relevant source path: `IrisShaderPackCompatibility`
- Profile artifact and commit: None; implementation commit is recorded below.
- Measured baseline cost: Unknown; no profile isolates reflective invocation failure overhead.
- Root cause: API discovery failures are cached, but a successfully resolved Iris API whose `isShaderPackInUse` invocation later throws is invoked again on every entity/block-entity extraction frame.
- Hypothesis: Marking the optional API unavailable after the first invocation failure preserves fail-open correctness and avoids repeated failing reflection calls.
- Proposed mechanism: Replace the cached access with the existing unavailable sentinel in the invocation exception handler. Subsequent checks return active/unsafe immediately without calling Iris again.
- Expected metrics: At most one failed API invocation per process/classloader cache lifetime instead of one per extraction frame after failure. No numerical frame-time improvement is claimed.
- Correctness constraints: Any unknown Iris state must continue to disable occlusion. A transient API exception intentionally disables the optimization for the remainder of the process; restart Minecraft to retry.
- Compatibility risks: A temporary Iris API failure becomes sticky until restart. This is conservative because resuming culling without a trustworthy shader-pack state could hide rendered geometry.
- Benchmark scenarios and repetitions: Not run; the change affects only an exceptional compatibility path.
- Before/after distributions: None
- Regressions: None known.
- Validation evidence: Unit test verifies the first failed invocation fails open and later checks do not invoke the failing method again; CI is pending.
- Rollback condition: Revert only if supported Iris versions are observed to throw transiently and require automatic recovery during the same process.
- Decision and rationale: Keep; fail-open compatibility failures should not generate repeated reflective work on every frame.

### [VB-R17] Fail closed when renderer reflection cannot resolve signatures

- Status: Implemented — compatibility hardening
- Target Minecraft version: 1.21.11
- Subsystem: Entity renderer eligibility detection
- Relevant source path: `RendererOverridePolicy`
- Profile artifact and commit: Not applicable; this is a correctness/compatibility guard.
- Root cause: Renderer inspection catches `SecurityException` only. Java reflection can also throw linkage or runtime failures when optional types referenced by a modded renderer's method signatures cannot be resolved.
- Hypothesis: Treating every reflection inspection failure as an unsafe renderer prevents an optional/modded renderer from crashing entity extraction or being incorrectly accepted for occlusion.
- Proposed mechanism: Catch `LinkageError` and `RuntimeException` around declared-method and parameter-type inspection, and return the existing fail-closed result.
- Expected metrics: No performance claim. The change only affects renderer classes whose reflection inspection fails.
- Correctness constraints: Any uncertain renderer must remain visible; do not swallow fatal VM errors outside the narrowly scoped reflection operation.
- Compatibility risks: A renderer with an unusual signature that cannot be inspected will be excluded from occlusion, reducing culling opportunities rather than correctness.
- Benchmark scenarios and repetitions: Not applicable.
- Before/after distributions: None
- Regressions: None known.
- Validation evidence: CI build, unit tests, and all four mixin launch smoke paths pending.
- Rollback condition: Revert only if the broader catch masks a recoverable condition needed for supported renderer inspection; visible fallback is intentional.
- Decision and rationale: Keep; compatibility uncertainty must disable culling, not crash the render path.

### [VB-R18] Add bounded p95/p99 latency reporting to occlusion diagnostics

- Status: Implemented — diagnostic instrumentation only
- Target Minecraft version: 1.21.11
- Subsystem: Entity and block-entity occlusion diagnostics
- Relevant source paths: `NanoHistogram`, `EntityOcclusionDiagnostics`, and `BlockEntityOcclusionDiagnostics`
- Profile artifact and commit: None; this adds measurement resolution, not a performance optimization.
- Root cause: Diagnostic reports exposed average and maximum candidate/query duration only. Averages hide tail latency, while a single maximum is highly sensitive to one-off scheduling pauses.
- Hypothesis: Fixed-memory logarithmic histograms can report approximate p95/p99 upper bounds for candidate and query durations without allocating per sample.
- Proposed mechanism: Record timings in 64 power-of-two nanosecond buckets only when the corresponding opt-in profiler is enabled; report bucket upper bounds at each 300-frame interval and reset the histograms with the existing counters.
- Expected metrics: p95/p99 culler and query latency become observable alongside averages and maxima. No runtime FPS gain is claimed.
- Correctness constraints: Diagnostics must not change visibility decisions. Histograms use constant memory, and the disabled profiler path must not record samples.
- Compatibility risks: Percentiles are bucket upper bounds, not exact order statistics; the output labels and docs must make that approximation clear.
- Benchmark scenarios and repetitions: Use the existing entity-dense and village/block-entity diagnostic scenarios; repeat each 300-frame report at least five times before interpreting tails.
- Before/after distributions: None; this improves instrumentation rather than changing the culling algorithm.
- Regressions: Profiling-enabled runs perform a small fixed-array increment per timing sample. Histogram storage is allocated only when the corresponding profiler property is enabled; normal launches allocate no histogram arrays.
- Validation evidence: Unit tests cover percentile rank, empty/reset behavior, negative-duration clamping, and invalid percentile rejection. CI pending.
- Rollback condition: Revert if profiling overhead becomes material or histogram output is mistaken for exact p95/p99 measurements.
- Decision and rationale: Keep; tail latency is required to evaluate whether experimental culling work causes frame-time spikes.

## Initial state

The initial repository commit established project structure only and made no performance claims. Research entries above are hypotheses, not accepted optimizations.
