# BoxBoy Zombies — Security Specification (Phase 0 TDD)

## 1. Data Invariants
1. **Multi-User Isolation (`userId`)**: Every document in `/users/{userId}/save_slots/{slotId}` and `/users/{userId}/controller_profiles/{profileId}` MUST belong exclusively to `request.auth.uid == userId` and carry `data.userId == request.auth.uid`.
2. **Path Variable Hardening (`isValidId`)**: All path IDs (`userId`, `slotId`, `profileId`) must be non-empty alphanumeric/underscore/hyphen strings of length <= 128 (`^[a-zA-Z0-9_\-]+$`).
3. **Strict Schema & Bounded Sizes**:
   - `SaveSlot`: `slotName` (1..60 chars), `highestWave` (int 1..9999), `totalKills` (int 0..100000000), `points` (int 0..100000000), `loadoutSummary` (1..500 chars), `encryptedPayload` (1..8000 chars), `deviceTag` (1..80 chars), `createdAt` & `updatedAt` (`timestamp <= request.time`).
   - `ControllerProfile`: `profileName` (1..50 chars), `dpadScale` (int 50..200), `buttonOpacity` (int 10..100), `keyMapCode` (1..1000 chars), `createdAt` & `updatedAt` (`timestamp <= request.time`).
4. **Immutable Creation Fields**: `userId` and `createdAt` cannot be mutated on `update`.

## 2. The "Dirty Dozen" Adversarial Payloads
1. **Unauthenticated Read/List**: `unauthDb.collection("users/alice_123/save_slots").get()` -> Reject
2. **Cross-User Read (`get`)**: Alice reading `/users/bob_456/save_slots/slot_1` -> Reject
3. **Cross-User List (`list`)**: Alice listing `/users/bob_456/save_slots` -> Reject
4. **Identity Spoofing on Create**: Alice creating `/users/alice_123/save_slots/slot_1` with `userId: "bob_456"` -> Reject
5. **Shadow Field Injection on Create**: Creating a `SaveSlot` with an extra field `isAdmin: true` -> Reject (`hasOnly`)
6. **Shadow Field Injection on Update**: Updating a `SaveSlot` with an unauthorized key `hacked: true` -> Reject (`affectedKeys().hasOnly(...)`)
7. **Immutable Field Mutation (`createdAt`)**: Updating `createdAt` on an existing `SaveSlot` -> Reject
8. **Immutable Field Mutation (`userId`)**: Updating `userId` on an existing `SaveSlot` -> Reject
9. **Value Poisoning (`highestWave` negative or wrong type)**: Setting `highestWave: -5` or `"999"` -> Reject (`isValidSaveSlot`)
10. **Denial-of-Wallet Oversized String**: Setting `slotName` to 500 chars or `encryptedPayload` to 20,000 chars -> Reject
11. **Controller Profile Out-of-Range Scale**: Setting `dpadScale: 500` or `buttonOpacity: 0` -> Reject (`isValidControllerProfile`)
12. **Future Timestamp Spoofing**: Setting `createdAt` in the future (`> request.time`) or as an integer epoch -> Reject
