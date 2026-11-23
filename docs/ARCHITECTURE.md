# VantaBoost architecture

## Product boundary

VantaBoost is an independent client-side Fabric mod. It must remain fully usable when Vanta Client is absent.

Three concerns are separated:

1. Optimization runtime: optimization behavior, lifecycle, compatibility checks, and diagnostics.
2. Standalone client UI: a small Minecraft-native configuration screen shipped in the VantaBoost JAR.
3. Optional Vanta Client integration: an adapter exposing the same settings and diagnostics through Vanta Client.

Neither UI owns optimization behavior. Both consume the same public configuration and diagnostics interfaces. Runtime code must never require Vanta Client classes to load.

## Dependency direction

    Vanta Client (optional)
             |
      Integration adapter
             |
    Public config/API ---- Optimization runtime
             |                    |
    Standalone UI            Minecraft/Fabric

The optimization runtime does not depend on either UI. The standalone UI does not depend on Vanta Client. The integration adapter is never required for initialization.

## Intended source hierarchy

    src/
      main/
        java/dev/itsdropper/vantaboost/
          api/             Public interfaces consumed by adapters
          config/          Schema, validation, migration, persistence
          compatibility/   Capability and mod compatibility detection
          diagnostics/     Low-overhead counters and snapshots
          optimization/    Optimization contracts and lifecycle
          runtime/         Bootstrap and orchestration
        resources/
          fabric.mod.json
      client/
        java/dev/itsdropper/vantaboost/
          client/          Client lifecycle wiring
          ui/              Standalone vanilla-style screens and widgets
          integration/     Optional Vanta Client adapter boundary
          mixin/           Verified client mixins grouped by subsystem

    docs/
      ARCHITECTURE.md
      BENCHMARKING.md
      PERFORMANCE_LEDGER.md
      RESEARCH.md
    .agent/
      skills/minecraft-performance-engineering/SKILL.md

This is a package plan, not a reason to create empty packages. Add a package only when it contains real code.

## Runtime lifecycle

1. Fabric loads the mod entrypoint.
2. Runtime initializes the configuration store and validates/migrates the schema.
3. Compatibility detection records relevant loaded mods and capabilities.
4. Optimization registrations are evaluated against prerequisites.
5. Optimizations initialize at the lifecycle point they require.
6. Diagnostics expose measurements without forcing GPU synchronization or expensive work into normal frames.
7. Shutdown releases owned resources and stops background work.

Each optimization must declare whether it is startup-only, reloadable, or safely toggleable at runtime. A UI must not imply that a restart-required mixin can be dynamically applied or removed.

## Configuration contract

- One versioned configuration model is authoritative.
- Both UIs read and write through the same API.
- Validate values at the boundary; never trust hand-edited config files.
- Persist changes atomically and preserve recoverable data on malformed files.
- Migrate known older schemas explicitly; never silently discard unknown settings.
- Defaults must be conservative and correctness-preserving.
- Do not add settings before a real optimization or user-facing behavior needs them.

## Optional integration

No Vanta Client dependency belongs in mod metadata or the runtime classpath. Implement the adapter only after inspecting the actual Vanta Client extension/API contract. Do not invent entrypoint names, classes, or reflection targets.

## Mixin policy

Do not create a mixin configuration just to reserve a directory. For each future mixin, record its target class, exact method descriptor, mapping namespace, injection point, expected match count, and compatibility risks in the ledger. Enable strict injection-count checks where practical.

## Target toolchain

- Minecraft 1.21.11
- Fabric Loader 0.18.1
- Fabric Loom 1.14.10
- Java 21
- Official Mojang mappings

Version values live in gradle.properties and must be updated as a coordinated, tested target-version change.

## Experimental entity occlusion integration

The current prototype is client-only and does not add a Vanta Client dependency.

- `EntityRenderer.shouldRender(Entity, Frustum, double, double, double)Z` redirects its `Frustum.isVisible(AABB)Z` call. The redirect preserves vanilla frustum visibility and reuses the exact AABB vanilla already computed.
- ClientLevel direct, verified, and predicted block-state updates, section activation, chunk loads, and chunk unloads invalidate cached visibility.
- The occlusion test accepts only a contiguous coplanar rectangle of vanilla opaque full-block faces that covers the complete projected conservative AABB. It uses the Frustum's camera origin, and the redirect targets only ordinal 0 so vanilla leash-holder fallback checks remain untouched. A LevelRenderer mixin scopes it to the main entity extraction interval. Uncertainty always renders.
- Modded entity classes and renderer classes, players and the active camera entity, entities for which the active renderer extracts a name tag, glowing/burning entities, sleeping/dying/auto-spin living entities, leash attachments, end crystals, guardians, wardens, and entities with passenger relationships are excluded. Previous-to-current position interpolation is enclosed by a bounded per-axis expansion (up to 1.5 blocks); larger movement fails open. Renderer-specific name-tag policy/submission, culling-bound/affectedByCulling/shouldRender policy, custom render-state extraction/finalization, submit, living-model scale/rotation, shadow-radius, shadow-strength, and render-offset overrides fail open, except the known vanilla LivingEntityRenderer bounds, submit, and transforms. The built-in LivingEntityRenderer bounding-box expansion is retained because its vanilla AABB is already passed to the proof. Vanilla living renderers are handled using their actual entity scale and (for mob renderers) age scale. Iris, Canvas, VulkanMod, Entity Culling, and Entity Model Features installations disable this optimization.
- Vanilla shadow bounds include the floored horizontal radius footprint, the strength-dependent vertical depth, and floor-rounding margins above and below. Renderers with nonstandard `getShadowRadius` or `getShadowStrength` overrides fail open. The hot path reuses vanilla's AABB and does not allocate a second one.
- The cache is client-thread-only and holds a weak reference to the active world. This remains experimental until CI build, actual launch/mixin application, visual regression checks, compatibility checks, and controlled profiling are verified.

## Initialization non-goals

No speculative additional culling, caching, batching, pooling, or multithreading beyond the explicitly documented experimental entity-occlusion prototype. No claimed FPS gains. No mandatory Vanta Client integration. No server-side component. No empty optimization registry or unused plugin framework.
