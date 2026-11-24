# VantaBoost

An independent Minecraft client performance mod focused on measurable improvements, rendering correctness, and compatibility.

VantaBoost is designed to run without Vanta Client. It will ship with a lightweight Minecraft-native configuration screen; Vanta Client may optionally integrate with the same configuration and diagnostics APIs.

## Initial target

- Minecraft: 1.21.11
- Mod loader: Fabric
- Java: 21
- Fabric Loom: 1.14.10
- Fabric Loader: 0.18.1

## Engineering standard

VantaBoost does not ship speculative optimizations. Every optimization must have a documented hypothesis, evidence, correctness constraints, and repeatable validation. See [the performance engineering skill](.agent/skills/minecraft-performance-engineering/SKILL.md) and [the architecture guide](docs/ARCHITECTURE.md).

## Project status

Two experimental occlusion prototypes are implemented. Entity culling runs during entity visibility filtering. Block-entity culling runs after vanilla's renderer-specific distance check but before render-state creation and extraction, so a successful cull avoids the rest of that block entity's render preparation and submission. Both use a conservative single-plane opaque-face proof, bounded synchronous ray queries, weak caches, and world-change invalidation. The block-entity path currently allows only exact vanilla renderer/entity pairs for chests, beds, shulker boxes, decorated pots, bells, enchanting tables, lecterns, and copper-golem statues. Both prototypes remain experimental and are not performance-accepted: no controlled baseline, in-game visual correctness pass, or FPS result exists. The block-entity implementation's Gradle tests and expanded headless mixin-application smoke test passed in [GitHub Actions run #343](https://github.com/ItsDropper/VantaBoost/actions/runs/45402612236). The smoke test verifies that the entity and block-entity culling wrappers, extraction scopes, and all six ClientLevel block/chunk invalidation handlers were merged into their Minecraft targets. This still does not verify in-game rendering correctness or performance. The opt-in particle extraction profiler is independently covered by [run #454180](https://github.com/ItsDropper/VantaBoost/actions/runs/45418003923), which verifies the `ParticleEngine` and `QuadParticleGroup` mixins.

**Important:** the current prototype is not yet a competitor to Entity Culling. It currently skips conservative vanilla entities fully hidden behind a contiguous coplanar rectangle of vanilla opaque full-block faces, using a synchronous raycast. Moving entities are eligible when their previous-to-current interpolation fits inside a bounded expanded AABB; teleports and larger movements fail open. Renderer-visible name tags fail open after checking the active renderer's actual name-tag visibility rules; eligibility therefore depends on renderer type, team visibility, and client settings. Entity Culling has broader multi-voxel sampling, asynchronous processing, and broader renderer coverage. VantaBoost's block-entity prototype is intentionally narrower than Entity Culling's block-entity implementation. See [the source comparison and required roadmap](docs/RESEARCH.md#a-entity-and-block-entity-occlusion-culling).

The prototype fails open for uncertain cases. Its VulkanMod integration uses a chainable MixinExtras wrapper so VulkanMod's own entity-frustum redirect is preserved. VulkanMod's custom block-entity traversal bypasses the vanilla LevelRenderer extraction scope, so the block-entity culler fails open on that path. Its Iris integration runs only when no shader pack is active; culling automatically fails open while a shader pack is active or when Iris's public API cannot be queried. Canvas and Entity Culling disable both prototypes; Entity Model Features disables the entity prototype. The block-entity prototype fails open while an Iris shader pack is active. These are source-level compatibility integrations, not yet runtime-verified mod combinations. VulkanMod's renderer is itself incompatible with Sodium and Iris, so treat VulkanMod and Iris as separate renderer configurations—not a stack that VantaBoost can make coexist. Both prototypes are disabled by default until their in-game visual-correctness gates and controlled benchmarks are completed. Enable entity occlusion with `-Dvantaboost.enableEntityOcclusion=true` and block-entity occlusion with `-Dvantaboost.enableBlockEntityOcclusion=true`; either can be force-disabled with its corresponding `-Dvantaboost.disable...=true` property. Opt-in diagnostics are available with `-Dvantaboost.profileEntityOcclusion=true` and `-Dvantaboost.profileBlockEntityOcclusion=true`; see [the benchmarking protocol](docs/BENCHMARKING.md).

An opt-in particle extraction profiler is also available with `-Dvantaboost.profileParticleExtraction=true`; it reports `ParticleEngine.extract` and quad-particle extraction costs without changing visibility decisions. Use it to establish a particle-heavy baseline before considering Sodium-aware particle section culling. See the [benchmarking protocol](docs/BENCHMARKING.md) and [particle-culling research](docs/RESEARCH.md#d-sodium-aware-particle-section-culling).

See the [performance ledger](docs/PERFORMANCE_LEDGER.md) for implementation details and validation status.
