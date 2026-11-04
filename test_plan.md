1. **Analyze Requirements**: Implement `NoiseFieldTerrainSynthesizer.kt` and its test for Unified BoxBoys Track 2. It requires a seeded Simplex/Value noise surface patch generator for outdoor sectors. It should produce `SectionComponentDef` layouts like `LocalOllamaMapDirector` does, but utilizing noise for terrain features. We need to implement a class or object in `app/src/main/java/com/example/game/ai/director/modules/NoiseFieldTerrainSynthesizer.kt` and unit test it in `app/src/test/java/com/example/game/ai/director/modules/NoiseFieldTerrainSynthesizerTest.kt`.
2. **Implement Noise Generator**: Implement a basic Value Noise or Simplex Noise 2D generator within the class.
3. **Implement Terrain Synthesizer**: Given a seed, biome, and wave, generate a 12x12 terrain patch using the noise. Map noise values to terrain features: 
    * Walkable path `.`
    * Obstacles/Walls `#`
    * Occasional barrels `B` or other features based on noise threshold.
    * Ensure boundaries allow entry/exit points (e.g. doors `D`, windows `W`).
    * Implement a validation step ensuring topological reachability (BFS) of the 12x12 grid.
4. **Implement Unit Tests**: 
    * Test determinism (same seed = same map).
    * Test BFS reachability (ensure map is navigable, no isolated floor tiles).
    * Test boundaries and required elements.
    * Execute tests using `./gradlew testDebugUnitTest --tests "*NoiseFieldTerrainSynthesizerTest" --no-configuration-cache`.
5. **Run Pre-Commit & Submit**: Follow the specified rules to complete pre-commit and submit the patch autonomously.
