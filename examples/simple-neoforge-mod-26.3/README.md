# Minecraft 26.3 consumer

This example uses the source branch's matching NeoForge 26.3 adapters. First publish the branch, then run the isolated consumer:

```sh
./gradlew verifyReleaseReadiness
./gradlew -p examples/simple-neoforge-mod-26.3 compileBenchJava check
./gradlew -p examples/simple-neoforge-mod-26.3 verifyBenchServer
```

Requires Java 25. NeoForge is pinned to `26.3.0.45-beta`, Minecraft to `26.3`, and ModBench to `0.1.3-beta-mc26.3`. Maven Local is intentionally selected until these adapter artifacts are released. The plugin infers `26.3` from the public ModDev version API and injects `bench-api-neoforge-26.3` plus `bench-runtime-neoforge-26.3` only into benchmark configurations.

The existing `simple-neoforge-mod` example still exercises the released 26.1 baseline. Client rendering, GUI, and paired scenarios need a graphics-capable environment; compiling those providers does not verify their runtime behavior.
