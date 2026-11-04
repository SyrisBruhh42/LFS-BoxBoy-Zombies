1. **Explore & Analyze**: Understood the structures `AssembledSectionReceipt`, `PlacedSectionNode`, `SparseWorldStateCache`, etc.
2. **Implement `CompactWorldEnvelopeCodec.kt`**:
   - Define `@Keep` annotated `CompactWorldEnvelopeConfig` and `CompactWorldEnvelopeSnapshot` with `toMap()`/`fromMap()`.
   - Implement `CompactWorldEnvelopeCodec` engine with `tick(dtSeconds: Float)` for bounds clamping and zero-allocation updates.
   - Implement binary packing/unpacking `encodeEnvelope(receipt, cache)` and `decodeEnvelope(bytes)` with FNV-1a checksums, primitive arrays, and `< 15 KB` guarantees.
   - Add graceful degradation limits (e.g. max 150 rooms, 300 zombies) for extreme load.
3. **Implement `CompactWorldEnvelopeCodecTest.kt`**:
   - Write >= 12 test cases covering nominal execution, determinism (bit-identical outputs), boundary conditions, zero/negative inputs, max-capacity stress, and serialization roundtrip parity.
4. **Pre-commit Steps**:
   - Complete pre-commit steps to ensure proper testing, verification, review, and reflection are done.
5. **Submit Change**:
   - Verify tests pass cleanly and submit.
