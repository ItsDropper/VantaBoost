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

## Research state — 2026-11-23

Source-level research is documented in [RESEARCH.md](RESEARCH.md). No VantaBoost optimization has been implemented or accepted, and no VantaBoost runtime baseline has been captured.

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

### [VB-R03] Entity and block-entity occlusion culling

- Status: Proposed
- Target Minecraft version: 1.21.11
- Subsystem: Entity/block-entity rendering
- Relevant source: Entity Culling `CullTask` and `OcclusionCullingInstance`
- Profile artifact and commit: None
- Measured baseline cost: Unknown
- Hypothesis: Asynchronous conservative visibility tests can avoid rendering hidden entity/block-entity bounds in crowded scenes.
- Correctness constraints: Fail open on uncertainty, conservative bounds, velocity expansion, camera freshness, render bounds and whitelist support.
- Compatibility risks: Worker-thread snapshots, animated/modded entities, shader shadow passes and CPU contention.
- Decision: Prototype only after measuring object rendering cost and designing stale-result/false-cull tests.

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
