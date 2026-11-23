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

An experimental entity-occlusion prototype is implemented with geometric tests. The Gradle build, JUnit suite, headless client mixin-application smoke test, and JAR upload passed in GitHub Actions run [#277](https://github.com/ItsDropper/VantaBoost/actions/runs/45264229255) for commit `f605407`. This verifies compilation, tests, and mixin application—not visual correctness or performance. The prototype is not performance-accepted: no controlled baseline or FPS result exists. Block-entity culling and the configuration UI are not implemented.

**Important:** the current prototype is not yet a competitor to Entity Culling. It currently skips conservative vanilla entities fully hidden behind a contiguous coplanar rectangle of vanilla opaque full-block faces, using a synchronous raycast. Moving entities are eligible when their previous-to-current interpolation fits inside a bounded expanded AABB; teleports and larger movements fail open. Renderer-visible name tags fail open after checking the active renderer's actual name-tag visibility rules; eligibility therefore depends on renderer type, team visibility, and client settings. Entity Culling has broader multi-voxel sampling, asynchronous processing, velocity-expanded bounds, block-entity support, and earlier render-state extraction avoidance. See [the source comparison and required roadmap](docs/RESEARCH.md#a-entity-and-block-entity-occlusion-culling).

The prototype fails open for uncertain cases. Its VulkanMod integration uses a chainable MixinExtras wrapper so VulkanMod's own entity-frustum redirect is preserved. Its Iris integration runs only when no shader pack is active; culling automatically fails open while a shader pack is active or when Iris's public API cannot be queried. Canvas, Entity Culling, and Entity Model Features still disable the prototype. These are source-level compatibility integrations, not yet runtime-verified mod combinations. VulkanMod's renderer is itself incompatible with Sodium and Iris, so treat VulkanMod and Iris as separate renderer configurations—not a stack that VantaBoost can make coexist. To disable the prototype manually, launch with `-Dvantaboost.disableEntityOcclusion=true`. Opt-in culler diagnostics are available with `-Dvantaboost.profileEntityOcclusion=true`; see [the benchmarking protocol](docs/BENCHMARKING.md#entity-occlusion-diagnostic-mode).

See the [performance ledger](docs/PERFORMANCE_LEDGER.md) for implementation details and validation status.
