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

An experimental entity-occlusion prototype is implemented with geometric correctness tests and a headless client mixin-application smoke test. It is not performance-accepted: there is no controlled baseline or FPS result yet. Block-entity culling and the configuration UI are not implemented.

The prototype only skips conservative vanilla entities fully hidden by one vanilla opaque block face. It fails open for uncertain cases and is disabled with Iris, Canvas, VulkanMod, EntityCulling, or Entity Model Features installed. To disable it manually, launch with `-Dvantaboost.disableEntityOcclusion=true`.

See the [performance ledger](docs/PERFORMANCE_LEDGER.md) for implementation details and validation status.
