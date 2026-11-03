1. **Create `FrontierPrefetchCoordinator.kt`**:
   - Implement `FrontierPrefetchCoordinator` in `app/src/main/java/com/example/game/ai/director/modules/`.
   - The coordinator will run a background coroutine pool.
   - It will monitor a list of available `FrontierDoorSocket`s and keep up to 3 `AiSectorBlueprintProposal` pre-solved via `LocalOllamaMapDirector.parseOrSynthesizeSector` (or `LocalOllamaMapDirector.synthesizeDeterministicWfcSector`).
   - Allow querying the prefetched blueprints synchronously so gameplay expansion does not drop frames.
2. **Create `FrontierPrefetchCoordinatorTest.kt`**:
   - Write unit tests in `app/src/test/java/com/example/game/ai/director/modules/FrontierPrefetchCoordinatorTest.kt`.
   - Use `kotlinx.coroutines.test.runTest` to verify that 3 sectors are prefetched.
   - Mock/simulate frontier door lists, test cache hits, and pool capacity limits.
3. Complete pre-commit steps to ensure proper testing, verification, review, and reflection are done.
4. Submit the patch.
