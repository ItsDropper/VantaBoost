# Benchmarking protocol

Performance claims require repeatable measurements on controlled hardware. Compilation, launch success, screenshots, and source-level reasoning do not prove an FPS improvement.

## Record before each comparison

- Commit SHA and mod build.
- CPU, GPU, RAM, OS, driver, and power/thermal state.
- Minecraft, Fabric Loader, Fabric API, Java runtime, and JVM arguments.
- Complete mod list and exact versions.
- Resolution, display mode, render/simulation distance, graphics settings, VSync, FPS cap, resource packs, and shaders.
- World identifier/hash, dimension, camera position, player state, and scenario setup.
- Warm-up, capture duration, repetitions, and tool versions.

## Representative scenarios

| Scenario | Primary subsystem |
| --- | --- |
| Empty world / stable camera | Baseline client overhead |
| Dense terrain / forest | Terrain and chunk rendering |
| Village with many block entities | Block entity rendering and ticking |
| High entity count | Entity rendering and ticking |
| Particle-heavy scene | Particle rendering |
| Complex inventory / menus | GUI rendering |
| Rapid movement through new terrain | Chunk scheduling and rebuilds |
| Shader-heavy scene | GPU workload |

These scenarios do not replace profiling the suspected bottleneck.

## Metrics

Collect frame-time distributions (median, p95, p99, 1% and 0.1% lows), average FPS, CPU main/render-thread time, GPU time when available, allocation rate, GC pauses, and subsystem counters relevant to the hypothesis.

Distinguish CPU command submission from GPU execution. Do not add blocking GPU readbacks or synchronization to normal gameplay.

## Entity occlusion diagnostic mode

The experimental culler exposes opt-in counters. Start Minecraft with:

```text
-Dvantaboost.profileEntityOcclusion=true
```

It emits a summary every 300 main entity-extraction frames. The report includes candidate calls, culls, average/maximum full-culler time, cache hits, cached occlusions, query-budget skips, planned face-cell checks, occlusion-query count, occlusion results, and average full occlusion-query time.

To measure the culler entry-point overhead with the optimization disabled, add:

```text
-Dvantaboost.disableEntityOcclusion=true
```

Run the same fixed scene twice: once with the disable property and once without it, keeping the profiling property enabled in both runs. This compares the culler entry-point cost and query effectiveness. It does not include the mixin's preceding vanilla frustum and name-tag checks, which execute in both runs.

**Important:** diagnostic mode calls `System.nanoTime()` for each candidate and therefore perturbs the hot path. Use it to understand query/cache behavior and approximate culler cost, not for final FPS or frame-time acceptance. For final performance comparisons, repeat the same runs with `vantaboost.profileEntityOcclusion` disabled and capture frame times/JFR separately.

## Entity occlusion correctness gate

Before treating the prototype as ready for performance comparison, validate these cases in a repeatable world:

- Fully hidden eligible entities behind opaque full blocks, with the wall normal along X, Y, and Z and with diagonal camera angles.
- Entities partially exposed at wall edges, above/below the wall, or around a one-block gap; all visible portions must remain rendered.
- Entities moving behind walls, crossing the occluder plane, and teleporting; uncertainty must render rather than disappear.
- Place and remove occluding blocks while entities remain stationary; a previously occluded entity must reappear immediately after the wall is removed.
- Chunk unload/reload, dimension changes, and world replacement while the culler cache contains positive results.
- Named entities, team-visible name tags, crosshair-target names, glowing/fire effects, leashes, passengers, and the active camera entity.
- Entity shadows at fractional positions and near the edge of the occluder; shadows must not pop or disappear independently of the entity.

Run with profiling enabled to confirm that the scene produces candidates, cache hits, occlusion queries, and positive occlusion results. Then repeat frame-time/JFR comparisons with profiling disabled. Do not proceed to another optimization until this correctness gate passes and the culler demonstrates a repeatable benefit without frame-time regressions.

## Comparison procedure

1. Build and benchmark the unmodified baseline commit.
2. Save raw results and environment metadata.
3. Build the candidate commit.
4. Run the exact same scenario and settings.
5. Repeat enough times to characterize variance.
6. Compare distributions, not the single best run.
7. Check other scenarios for regressions.
8. Validate visual and gameplay correctness separately.
9. Keep the change only when the measured effect is meaningful and reproducible.

GPU-sensitive comparisons require controlled hardware. Ordinary shared CI runners are suitable for compilation and correctness checks, not authoritative FPS thresholds.

Store machine-readable raw output (CSV or JSON) with environment metadata. Summaries must identify exact commits and link raw artifacts. Never replace missing measurements with estimates.
