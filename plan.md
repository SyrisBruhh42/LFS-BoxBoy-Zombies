1. **Define Data Models**:
   - `PackAPunchPurchaseResult` sealed class with `Success` and `Error`.
2. **Implement Core Engine `PackAPunchTier1To5Forge.kt`**:
   - `processUpgradePurchase(state: GameSessionState, targetWeaponId: String): PackAPunchPurchaseResult`
   - Validate weapon exists in inventory.
   - Validate current tier < `WeaponUpgradeProgression.MAX_UPGRADE_LEVEL` (5).
   - Check if currency points are sufficient based on `SingleCurrencyVendingEngine.computePackAPunchCost(currentTier + 1)`. Wait, no, `computePackAPunchCost` takes the *current* tier and outputs the cost for the *next* tier? Let's check `computePackAPunchCost`. It uses `currentTier.coerceAtLeast(1)`. If `currentTier` is 1, cost is 2500? No, if current is 0, cost is 2500? Wait, base tier is 0 or 1? `WeaponUpgradeProgression.getTierStats(level)` assumes levels 1-5. Usually, standard weapon is level 0 or 1? `ModularWeaponInstance` defaults `anvilUpgradeTier` to 0. But `WeaponLevels` defaults to 1! Wait, let's verify.
   - If points are sufficient, deduct cost.
   - Upgrade weapon properties:
     - Increase `anvilUpgradeTier` by 1.
     - Increase `weaponLevels` by 1.
     - Also upgrade elemental projectiles if requested? The prompt says: "Pack-a-Punch Elemental Forge upgrading weapon damage, mag size, and elemental projectiles."
     - Let's check `WeaponUpgradeProgression`. It handles damage and mag size math, but `anvilUpgradeTier` also influences it directly in `ModularWeaponInstance`. We should increment `anvilUpgradeTier` on the weapon. Wait, does `anvilUpgradeTier` handle elemental? No, the element is in `magazine.elementalAffinity`. Maybe we upgrade it to `VOLTAIC` or randomly?
     - Wait, in `WeaponUpgradeProgression.getTierStats(4)` it says "Elemental Overcharge". So at tier 4 or 5, does it become elemental?
     - Let's make an explicit method to upgrade the weapon, returning a copy. If the tier hits 4 ("Elemental Overcharge"), if the weapon is `KINETIC`, we upgrade it to an elemental type based on a deterministic seed or something? Or maybe it always upgrades to random element? Or maybe we can choose a random one from `INCENDIARY`, `CRYO`, `CORROSIVE`, `VOLTAIC`. We can use `seed = state.mapSeed + targetWeaponId.hashCode() + newTier`.
     - The returned `GameSessionState` should have updated `points`, `weaponLevels`, `modularWeaponInventory`. And maybe refill `currentMagAmmo` for this weapon to the new max.

3. **Write Unit Tests `PackAPunchTier1To5ForgeTest.kt`**:
   - 12+ tests.
   - Nominal upgrade paths.
   - Maximum tier limits.
   - Insufficient funds.
   - Missing weapon.
   - Element upgrade at tier 4.
   - Serialization parity test (maybe we don't need to test serialization of `GameSessionState`, just `PackAPunchPurchaseResult` if it has it, but prompt says "Annotate all serializable / Firestore / network payload data class definitions with @androidx.annotation.Keep or deterministic toJsonMap() / fromJsonMap()"). We can add `toJsonMap()` to the result classes.

4. **Pre-commit Instructions**.
5. **Submit**.
