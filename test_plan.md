1. **Define Data Model**: Implement `PackAPunchPurchaseResult` sealed class with `Success` and `Error` inside `PackAPunchTier1To5Forge.kt`.
2. **Implement Core Engine**: Create `PackAPunchTier1To5Forge` object.
    - Function `processUpgradePurchase(state: GameSessionState, targetWeaponId: String): PackAPunchPurchaseResult`.
    - Check if the state has the weapon (`state.modularWeaponInventory[targetWeaponId]`). If not, return `Error`.
    - Retrieve the weapon's current anvil upgrade tier (from `weaponLevels` or directly as a property in `ModularWeaponInstance`). Wait, let's check how upgrade tier is stored. `ModularWeaponInstance` has `anvilUpgradeTier`. `weaponLevels` is also in `GameSessionState`. We need to see how they sync.
    - Compute upgrade cost using `SingleCurrencyVendingEngine.computePackAPunchCost(currentTier)`.
    - Check if `state.points >= cost`. If not, return `Error("Insufficient Currency Points")`.
    - Increase the upgrade tier.
    - If upgrade tier hits 4 or 5, we can modify elemental affinity or something based on "elemental projectiles" requirement. Wait, let's re-read the prompt: "upgrading weapon damage, mag size, and elemental projectiles".
    - Update the weapon in `modularWeaponInventory` with `anvilUpgradeTier + 1`. (Also `weaponLevels[targetWeaponId] + 1`?). Let's check `ModularWeaponInstance` properties.
3. **Write Unit Tests**: Implement `PackAPunchTier1To5ForgeTest.kt` with >12 test cases.
4. **Pre-commit**: Check pre-commit instructions.
5. **Submit**: Commit and push.
