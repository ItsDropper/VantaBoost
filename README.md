# VantaBoost

An independent Minecraft client performance mod focused on measurable improvements, rendering correctness, and compatibility.

VantaBoost is designed to run without Vanta Client. It provides its own lightweight Minecraft-style configuration screen; Vanta Client may optionally integrate with the same configuration and diagnostics APIs.

## Initial target

- Minecraft: 1.21.11
- Mod loader: Fabric
- Java: 21
- Fabric Loom: 1.14.10
- Fabric Loader: 0.18.1

## Engineering standard

VantaBoost does not ship speculative optimizations. Every optimization must have a documented hypothesis, evidence, correctness constraints, and repeatable validation. See [the performance engineering skill](.agent/skills/minecraft-performance-engineering/SKILL.md) and [the architecture guide](docs/ARCHITECTURE.md).

## Project status

Repository and architecture initialization. No performance improvements are claimed yet.
