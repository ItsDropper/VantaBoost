# Repository instructions

Read the performance engineering skill, docs/ARCHITECTURE.md, and docs/PERFORMANCE_LEDGER.md before changing code.

- Initial target: Minecraft 1.21.11, Fabric, Java 21, official Mojang mappings.
- Verify Minecraft methods, descriptors, mappings, and mixin targets against the resolved target game jar.
- Never add an optimization without a falsifiable hypothesis and bottleneck evidence.
- Vanta Client is optional. Runtime code must never link against Vanta Client classes.
- The standalone UI and Vanta Client adapter must consume the same configuration API.
- Keep optimization logic independent from presentation code.
- Do not add speculative toggles, empty mixins, placeholders, or unused abstractions.
- Do not claim performance gains without controlled benchmark results.
- Prefer small, independently verifiable changes.
