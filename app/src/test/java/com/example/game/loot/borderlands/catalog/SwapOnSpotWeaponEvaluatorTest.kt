package com.example.game.loot.borderlands.catalog

import com.example.game.ElementalAffinity
import com.example.game.LootDropEntity
import com.example.game.LootPayloadKind
import com.example.game.LootRarity
import com.example.game.ModularWeaponInstance
import com.example.game.WeaponBarrelPart
import com.example.game.WeaponMagazinePart
import com.example.game.WeaponManufacturer
import com.example.game.WeaponOpticPart
import com.example.game.WeaponReceiverPart
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class SwapOnSpotWeaponEvaluatorTest {

    private fun createMockWeapon(
        id: String,
        damageBonus: Float = 1.0f,
        fireRateBonus: Float = 1.0f,
        magSizeBonus: Int = 0,
        reloadMultiplier: Float = 1.0f,
        critMultiplier: Float = 1.25f,
        itemLevel: Int = 1
    ): ModularWeaponInstance {
        return ModularWeaponInstance(
            instanceId = id,
            baseWeaponId = "pistol",
            customTitle = "Test Weapon",
            rarity = LootRarity.COMMON,
            itemLevel = itemLevel,
            receiver = WeaponReceiverPart(
                name = "Test Receiver",
                manufacturer = WeaponManufacturer.KORSAKOV_ARMORIES,
                baseDamageBonus = damageBonus,
                fireRateBonus = fireRateBonus
            ),
            barrel = WeaponBarrelPart(
                name = "Test Barrel",
                spreadModifier = 1.0f,
                bonusPierce = 0,
                bonusPellets = 0,
                rangeBonusPx = 0f
            ),
            magazine = WeaponMagazinePart(
                name = "Test Magazine",
                magSizeBonus = magSizeBonus,
                reloadSpeedMultiplier = reloadMultiplier,
                elementalAffinity = ElementalAffinity.KINETIC
            ),
            optic = WeaponOpticPart(
                name = "Test Optic",
                lightLockCritMultiplier = critMultiplier,
                lanternRangeBonusPx = 20f,
                lightLockConeBonusRad = 0.05f
            )
        )
    }

    private fun createDrop(id: Int, x: Float, y: Float, weapon: ModularWeaponInstance): LootDropEntity {
        return LootDropEntity(
            id = id,
            x = x,
            y = y,
            rarity = LootRarity.COMMON,
            payloadKind = LootPayloadKind.MODULAR_WEAPON,
            weaponPayload = weapon
        )
    }

    @Test
    fun testNominalAllUpgrades() {
        val equipped = createMockWeapon("w_eq", damageBonus = 1.0f, fireRateBonus = 1.0f, magSizeBonus = 0, reloadMultiplier = 1.0f, critMultiplier = 1.2f)
        val candidate = createMockWeapon("w_cd", damageBonus = 2.0f, fireRateBonus = 2.0f, magSizeBonus = 10, reloadMultiplier = 2.0f, critMultiplier = 2.0f)

        val activeDrops = listOf(createDrop(1, 10f, 10f, candidate))

        val result = SwapOnSpotWeaponEvaluator.evaluateClosestWeapon(
            playerX = 10f,
            playerY = 10f,
            activeDrops = activeDrops,
            equippedWeapon = equipped
        )

        assertNotNull(result.comparisonResult)
        val comp = result.comparisonResult!!

        assertEquals(StatDeltaIndicator.UP_GREEN, comp.damageIndicator)
        assertEquals(StatDeltaIndicator.UP_GREEN, comp.fireRateIndicator)
        assertEquals(StatDeltaIndicator.UP_GREEN, comp.magSizeIndicator)
        assertEquals(StatDeltaIndicator.UP_GREEN, comp.reloadIndicator)
        assertEquals(StatDeltaIndicator.UP_GREEN, comp.critIndicator)
    }

    @Test
    fun testNominalAllDowngrades() {
        val equipped = createMockWeapon("w_eq", damageBonus = 2.0f, fireRateBonus = 2.0f, magSizeBonus = 10, reloadMultiplier = 2.0f, critMultiplier = 2.0f)
        val candidate = createMockWeapon("w_cd", damageBonus = 1.0f, fireRateBonus = 1.0f, magSizeBonus = 0, reloadMultiplier = 1.0f, critMultiplier = 1.2f)

        val activeDrops = listOf(createDrop(1, 10f, 10f, candidate))

        val result = SwapOnSpotWeaponEvaluator.evaluateClosestWeapon(
            playerX = 10f,
            playerY = 10f,
            activeDrops = activeDrops,
            equippedWeapon = equipped
        )

        assertNotNull(result.comparisonResult)
        val comp = result.comparisonResult!!

        assertEquals(StatDeltaIndicator.DOWN_RED, comp.damageIndicator)
        assertEquals(StatDeltaIndicator.DOWN_RED, comp.fireRateIndicator)
        assertEquals(StatDeltaIndicator.DOWN_RED, comp.magSizeIndicator)
        assertEquals(StatDeltaIndicator.DOWN_RED, comp.reloadIndicator)
        assertEquals(StatDeltaIndicator.DOWN_RED, comp.critIndicator)
    }

    @Test
    fun testNominalAllEqual() {
        val w1 = createMockWeapon("w1", damageBonus = 1.0f)
        val w2 = createMockWeapon("w2", damageBonus = 1.0f) // Identical stats

        val activeDrops = listOf(createDrop(1, 10f, 10f, w2))

        val result = SwapOnSpotWeaponEvaluator.evaluateClosestWeapon(
            playerX = 10f,
            playerY = 10f,
            activeDrops = activeDrops,
            equippedWeapon = w1
        )

        assertNotNull(result.comparisonResult)
        val comp = result.comparisonResult!!

        assertEquals(StatDeltaIndicator.EQUAL_GRAY, comp.damageIndicator)
        assertEquals(StatDeltaIndicator.EQUAL_GRAY, comp.fireRateIndicator)
        assertEquals(StatDeltaIndicator.EQUAL_GRAY, comp.magSizeIndicator)
        assertEquals(StatDeltaIndicator.EQUAL_GRAY, comp.reloadIndicator)
        assertEquals(StatDeltaIndicator.EQUAL_GRAY, comp.critIndicator)
    }

    @Test
    fun testOutsideInteractionRadius() {
        val equipped = createMockWeapon("eq")
        val candidate = createMockWeapon("cd")

        // 121 units away (max is 120)
        val activeDrops = listOf(createDrop(1, 121f, 0f, candidate))

        val result = SwapOnSpotWeaponEvaluator.evaluateClosestWeapon(
            playerX = 0f,
            playerY = 0f,
            activeDrops = activeDrops,
            equippedWeapon = equipped
        )

        assertNull(result.closestLootId)
        assertNull(result.comparisonResult)
    }

    @Test
    fun testClosestLootSelected() {
        val equipped = createMockWeapon("eq")
        val wFar = createMockWeapon("far", damageBonus = 1.0f)
        val wClose = createMockWeapon("close", damageBonus = 5.0f)

        val activeDrops = listOf(
            createDrop(1, 100f, 0f, wFar),
            createDrop(2, 50f, 0f, wClose)
        )

        val result = SwapOnSpotWeaponEvaluator.evaluateClosestWeapon(
            playerX = 0f,
            playerY = 0f,
            activeDrops = activeDrops,
            equippedWeapon = equipped
        )

        assertEquals(2, result.closestLootId)
        assertEquals(StatDeltaIndicator.UP_GREEN, result.comparisonResult?.damageIndicator) // because wClose is much better
    }

    @Test
    fun testBoundaryNaNAndInfinityCoords() {
        val equipped = createMockWeapon("eq")
        val candidate = createMockWeapon("cd")

        // Drop at 0,0. Player coordinates are NaN and Infinity
        val activeDrops = listOf(createDrop(1, 0f, 0f, candidate))

        val result = SwapOnSpotWeaponEvaluator.evaluateClosestWeapon(
            playerX = Float.NaN,
            playerY = Float.POSITIVE_INFINITY,
            activeDrops = activeDrops,
            equippedWeapon = equipped
        )

        // Evaluator maps non-finite coords to 0,0. Dist from 0,0 to drop 0,0 is 0. So it should find it.
        assertEquals(1, result.closestLootId)
        assertNotNull(result.comparisonResult)
    }

    @Test
    fun testMaxCapacityStressIteration() {
        val equipped = createMockWeapon("eq")
        val drops = mutableListOf<LootDropEntity>()

        // 500 random drops outside radius
        val rng = Random(123)
        for (i in 1..500) {
            val drop = createDrop(i, rng.nextFloat() * 1000f + 200f, rng.nextFloat() * 1000f + 200f, createMockWeapon("w_$i"))
            drops.add(drop)
        }

        // 1 inside radius
        drops.add(createDrop(999, 10f, 10f, createMockWeapon("w_999")))

        val start = System.nanoTime()
        val result = SwapOnSpotWeaponEvaluator.evaluateClosestWeapon(
            playerX = 0f,
            playerY = 0f,
            activeDrops = drops,
            equippedWeapon = equipped
        )
        val end = System.nanoTime()

        assertEquals(999, result.closestLootId)
        assertTrue("Processing 500+ items should be fast enough (< 50ms). Took ${(end-start)/1000000}ms", (end - start) < 50_000_000)
    }

    @Test
    fun testDeterminismSameSeedsAndInputs() {
        val wEq1 = createMockWeapon("eq1", damageBonus = 1.0f)
        val wCd1 = createMockWeapon("cd1", damageBonus = 1.5f)

        val res1 = SwapOnSpotWeaponEvaluator.evaluateClosestWeapon(10f, 10f, listOf(createDrop(1, 10f, 12f, wCd1)), wEq1)
        val res2 = SwapOnSpotWeaponEvaluator.evaluateClosestWeapon(10f, 10f, listOf(createDrop(1, 10f, 12f, wCd1)), wEq1)

        assertEquals(res1.checksum, res2.checksum)
        assertEquals(res1.comparisonResult?.damageDelta, res2.comparisonResult?.damageDelta)
    }

    @Test
    fun testDeterminismDifferentInputs() {
        val wEq1 = createMockWeapon("eq1", damageBonus = 1.0f)
        val wCd1 = createMockWeapon("cd1", damageBonus = 1.5f)
        val wCd2 = createMockWeapon("cd2", damageBonus = 2.0f)

        val res1 = SwapOnSpotWeaponEvaluator.evaluateClosestWeapon(10f, 10f, listOf(createDrop(1, 10f, 12f, wCd1)), wEq1)
        val res2 = SwapOnSpotWeaponEvaluator.evaluateClosestWeapon(10f, 10f, listOf(createDrop(1, 10f, 12f, wCd2)), wEq1)

        assertNotEquals(res1.checksum, res2.checksum)
    }

    @Test
    fun testSerializationRoundtripState() {
        val eq = createMockWeapon("eq", damageBonus = 1.0f)
        val cd = createMockWeapon("cd", damageBonus = 2.0f, fireRateBonus = 1.5f)
        val state = SwapOnSpotWeaponEvaluator.evaluateClosestWeapon(0f, 0f, listOf(createDrop(1, 0f, 0f, cd)), eq)

        val map = state.toMap()
        val restored = SwapOnSpotEvaluationState.fromMap(map)

        assertEquals(state.closestLootId, restored.closestLootId)
        assertEquals(state.checksum, restored.checksum)
        assertEquals(state.comparisonResult?.damageDelta, restored.comparisonResult?.damageDelta)
        assertEquals(state.comparisonResult?.fireRateIndicator, restored.comparisonResult?.fireRateIndicator)
    }

    @Test
    fun testSerializationRoundtripWeaponStatsCard() {
        val card = WeaponStatsCard(damage = 50, fireRatePerSecond = 5.5f, magSize = 30, reloadMs = 1500L, critMultiplier = 1.5f)
        val map = card.toMap()
        val restored = WeaponStatsCard.fromMap(map)

        assertEquals(card.damage, restored.damage)
        assertEquals(card.fireRatePerSecond, restored.fireRatePerSecond, 0.001f)
        assertEquals(card.magSize, restored.magSize)
        assertEquals(card.reloadMs, restored.reloadMs)
        assertEquals(card.critMultiplier, restored.critMultiplier, 0.001f)
    }

    @Test
    fun testSerializationRoundtripComparisonResult() {
        val comp = SwapOnSpotComparisonResult(
            damageDelta = 10,
            damageIndicator = StatDeltaIndicator.UP_GREEN,
            fireRateDelta = -0.5f,
            fireRateIndicator = StatDeltaIndicator.DOWN_RED,
            magSizeDelta = 0,
            magSizeIndicator = StatDeltaIndicator.EQUAL_GRAY,
            reloadMsDelta = -100L,
            reloadIndicator = StatDeltaIndicator.UP_GREEN,
            critMultiplierDelta = 0.2f,
            critIndicator = StatDeltaIndicator.UP_GREEN
        )

        val map = comp.toMap()
        val restored = SwapOnSpotComparisonResult.fromMap(map)

        assertEquals(comp.damageDelta, restored.damageDelta)
        assertEquals(comp.damageIndicator, restored.damageIndicator)
        assertEquals(comp.fireRateDelta, restored.fireRateDelta, 0.001f)
        assertEquals(comp.fireRateIndicator, restored.fireRateIndicator)
        assertEquals(comp.magSizeDelta, restored.magSizeDelta)
        assertEquals(comp.magSizeIndicator, restored.magSizeIndicator)
        assertEquals(comp.reloadMsDelta, restored.reloadMsDelta)
        assertEquals(comp.reloadIndicator, restored.reloadIndicator)
        assertEquals(comp.critMultiplierDelta, restored.critMultiplierDelta, 0.001f)
        assertEquals(comp.critIndicator, restored.critIndicator)
    }
}
