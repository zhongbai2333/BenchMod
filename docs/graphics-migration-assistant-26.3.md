# Graphics migration assistant: Minecraft 26.3

This is an opt-in correctness suite for OpenGL → Blaze3D/Renderpearl migrations. It does not load NetMusic or require the paused main-mod port.

## What actually runs

`GraphicsMigrationSuite.register(registrar)` is a reusable API entry point. The standalone example invokes it from `src/bench`; its production mod is empty, and normal mod runs do not execute the suite.

Six required scenes all compare real GPU readback with an independent CPU fixture:

| Scene | Contract |
| --- | --- |
| rgba-stride-orientation | 17×9 asymmetric RGBA, padded/bottom-up source, nonzero buffer position, exact bytes |
| rg8-uv-channels | Native RG8_UNORM texture, nearest fragment sampling into RGBA, distinct R/G, alpha 255 |
| buffer-reuse | The same GPU upload/readback buffers across three completed write/copy/fence cycles; every cycle checked |
| shader-depth-blend | Compiled shaders, vertex binding, 16-byte std140 uniform binding, explicit depth attachment, red 0.5 over blue, green behind red but in front of blue rejected (proves translucent depth write) |
| offscreen-copy | Explicit offscreen color attachment, texture sampling, closed render pass, GPU copy into a different texture |
| resource-recreate | Sixteen GPU-complete allocate/upload/readback/close cycles, final image plus suite-owned handle accounting |

The public API façade discovers a runtime factory. The version-specific API JAR is marked FML GAMELIBRARY because its method signatures contain Minecraft types; a regression test verifies that its interfaces and RenderSystem share the game classloader. All executable probe code stays inside the Runtime mod. The 26.3 adapter uses public Renderpearl APIs only. It never casts an OpenGL texture or calls GL functions. It leaves submission/frame boundaries to Minecraft and waits non-blockingly across client ticks. Cancellation defers destruction until queued GPU work has completed. Custom shaders compile through the real device; no CPU rendering substitute is used.

Depth for the dedicated depth probe is explicitly forward `LESS_THAN_OR_EQUAL`, cleared to 1.0, and converted to the device's clip-depth convention. Green is drawn at depth 0.5, between red 0.25 and blue 0.75, so disabling the translucent depth write must fail the pixel oracle. It does not depend on Minecraft's reverse-depth default. A GPU device/backend change or fallback invalidates the run.

`engine-resource-reload` is explicitly SKIP in revision 1. Close/recreate accounting is not a claim about the full engine reload path or driver-global leak freedom.

## Build and run

Java 25 is required. From the repository root, first publish the current modules locally:

```sh
./gradlew check verifyReleaseReadiness -PmodBenchBinaryArtifacts=true
```

The binary-artifact option is ModDev's official patched-binary path, not a replacement API. Source-generated 26.3 mode also works.

On a graphics-capable machine, run the isolated consumer twice using the same Minecraft version, hardware, suite revision and seed:

```sh
./gradlew -p examples/graphics-migration-assistant verifyBenchClient \
  -PmodBenchLocal=true -PmodBenchBinaryArtifacts=true -PmodBench.client.graphicsBackend=opengl
cp -a examples/graphics-migration-assistant/build/modBench/raw-results/default/client /tmp/migration-opengl
./gradlew -p examples/graphics-migration-assistant verifyBenchClient \
  -PmodBenchLocal=true -PmodBenchBinaryArtifacts=true -PmodBench.client.graphicsBackend=vulkan
cp -a examples/graphics-migration-assistant/build/modBench/raw-results/default/client /tmp/migration-vulkan
python3 tools/graphics_migration_compare.py \
  /tmp/migration-opengl/artifacts/custom/graphics-migration.json \
  /tmp/migration-vulkan/artifacts/custom/graphics-migration.json \
  --output /tmp/migration-comparison.json --diff-dir /tmp/migration-diffs
```

The example defaults to seed 602263 and a 640×480 void world. Comparison is a stdlib-only Python tool. It validates schema/scene signatures, requested versus actual backend, environment validity, device/vendor identity, raw artifact lengths and SHA-256 before comparing every RGBA channel. Missing, tampered, skipped, blocked, mismatched or failed evidence cannot yield PASS. There is no automatic vertical-flip correction. Driver strings are recorded; different API driver strings are allowed, but device names must match exactly; only explicitly enumerated aliases for known vendors are normalized conservatively (for example NVIDIA Corporation → NVIDIA). Cross-version comparisons are intentionally rejected.

Every scene emits expected/actual raw RGBA, expected/actual PNG and a difference PNG; the sidecar uses `graphics-migration/1`. These are formal benchmark artifacts with hashes, not invented screenshots. Optional report differences also produce PPM heatmaps.

## Validation status

The base 26.3 port has 257 passing tests, green CI, real dedicated-server smoke, two independent-consumer E2E passes, publication/isolation verification and configuration-cache reuse.

The migration assistant's GPU adapter compiles against the real 26.3 APIs. Real ShaderC tests compile all five shader variants for both OpenGL and Vulkan targets, independently of a GPU device. CPU fixture/oracle, state-machine, no-device BLOCKED, schema and strict comparison tests are part of the checks. These tests are not evidence that graphical probes ran.

A graphical OpenGL/Vulkan client run has **not** been performed in this build environment: no display server, Vulkan ICD or graphics device is available. Therefore no visual parity, resource-leak freedom or successful hardware benchmark is claimed. Run the commands above before treating a renderer migration as verified.
