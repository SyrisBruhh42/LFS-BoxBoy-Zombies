Plan: Implement Pack-a-Punch Elemental Forge
1. **Data Model**:
   - `sealed class PackAPunchPurchaseResult` with `@Keep` on data classes (or explicit `toJsonMap`/`fromJsonMap`).
   - `Success` containing `val newState: GameSessionState`, `val message: String`, `val cost: Int`.
   - `Error` containing `val reason: String`.

2. **Core Algorithm (`PackAPunchTier1To5Forge.kt`)**:
   - `object PackAPunchTier1To5Forge` with function `processUpgradePurchase(state: GameSessionState, targetWeaponId: String): PackAPunchPurchaseResult`.
   - Lookup weapon: `state.modularWeaponInventory[targetWeaponId]`. Return `Error("Weapon not found")` if missing.
   - Current upgrade tier: `weapon.anvilUpgradeTier`.
   - Max check: `if (currentTier >= WeaponUpgradeProgression.MAX_UPGRADE_LEVEL) return Error(...)`.
   - Calculate cost: `val cost = SingleCurrencyVendingEngine.computePackAPunchCost(currentTier + 1)`. Wait, if currentTier is 0, cost is `computePackAPunchCost(1) = 2500`.
   - Point check: `if (state.points < cost) return Error(...)`.
   - Apply Upgrade:
     - New tier: `currentTier + 1`.
     - Copy the weapon: `val upgradedWeapon = weapon.copy(anvilUpgradeTier = newTier)`.
     - At tier 4 ("Elemental Overcharge"), if the weapon is kinetic (`ElementalAffinity.KINETIC`), deterministic upgrade to an elemental type using `val rnd = Random(state.mapSeed + targetWeaponId.hashCode() + newTier); val newElement = listOf(INCENDIARY, CRYO, CORROSIVE, VOLTAIC).random(rnd)`. Update `magazine = magazine.copy(elementalAffinity = newElement)`.
   - State modification:
     - Deduct points.
     - Update `modularWeaponInventory` with `upgradedWeapon`.
     - Update `weaponLevels` map with `newTier`. (Some things use this).
     - Refill the magazine ammo to max for this weapon: `currentMagAmmo = state.currentMagAmmo + (targetWeaponId to upgradedWeapon.effectiveMagSize)`.
   
3. **Unit Tests (`PackAPunchTier1To5ForgeTest.kt`)**:
   - Provide > 12 test cases.
   - Setup `GameSessionState` and mock weapons.
   - Tiers 1-5 tests.
   - Elemental Overcharge test (Tier 4 element switch).
   - Cost deductions test.
   - Edge case: not enough points.
   - Edge case: unknown weapon.
   - Edge case: Max level exceeded.
   - Determinism test (same seed -> same element).

4. **Verify / Pre-commit**:
   - Run tests.
   - Run pre-commit instructions.
   - Push code.
