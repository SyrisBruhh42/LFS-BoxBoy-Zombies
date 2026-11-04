package com.example.game.loot.borderlands.catalog

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class CryoVexManufacturerTraitTest {

    private lateinit var engine: CryoVexManufacturerTrait
    private lateinit var config: CryoVexTraitConfig

    @Before
    fun setup() {
        config = CryoVexTraitConfig()
        engine = CryoVexManufacturerTrait(config)
    }

    @Test
    fun testNominalGameplay_applyHitWithoutLightLock_noCrit() {
        val result = engine.applyHit(entityId = 1, baseDamage = 50f, isLightLocked = false, seed = 12345L, timestampMs = 1000L)
        assertEquals(50f, result.finalDamage)
        assertFalse(result.wasCrit)

        val state = engine.getTargetState(1)
        assertNotNull(state)
        assertTrue(state!!.buildup > 0f)
        assertFalse(state.isFrozen)
    }

    @Test
    fun testNominalGameplay_applyHitWithLightLock_appliesCrit() {
        val result = engine.applyHit(entityId = 1, baseDamage = 50f, isLightLocked = true, seed = 12345L, timestampMs = 1000L)
        assertEquals(50f * config.lightLockCritMultiplier, result.finalDamage, 0.001f)
        assertTrue(result.wasCrit)
    }

    @Test
    fun testDeterminism_sameSeedAndInputs_yieldSameBuildup() {
        engine.applyHit(entityId = 2, baseDamage = 40f, isLightLocked = false, seed = 999L, timestampMs = 1000L)
        val state1 = engine.getTargetState(2)!!.buildup

        engine.clear()

        engine.applyHit(entityId = 2, baseDamage = 40f, isLightLocked = false, seed = 999L, timestampMs = 1000L)
        val state2 = engine.getTargetState(2)!!.buildup

        assertEquals(state1, state2, 0.0001f)
    }

    @Test
    fun testDeterminism_differentSeed_yieldsDifferentBuildup() {
        engine.applyHit(entityId = 3, baseDamage = 40f, isLightLocked = false, seed = 111L, timestampMs = 1000L)
        val b1 = engine.getTargetState(3)!!.buildup

        engine.clear()

        engine.applyHit(entityId = 3, baseDamage = 40f, isLightLocked = false, seed = 222L, timestampMs = 1000L)
        val b2 = engine.getTargetState(3)!!.buildup

        assertNotEquals(b1, b2, 0.0001f)
    }

    @Test
    fun testBoundary_negativeDamage_isIgnored() {
        val res = engine.applyHit(entityId = 4, baseDamage = -10f, isLightLocked = false, seed = 0L, timestampMs = 1000L)
        assertEquals(0f, res.finalDamage)
        assertNull(engine.getTargetState(4))
    }

    @Test
    fun testBoundary_nanDamage_isIgnored() {
        val res = engine.applyHit(entityId = 5, baseDamage = Float.NaN, isLightLocked = false, seed = 0L, timestampMs = 1000L)
        assertEquals(0f, res.finalDamage)
        assertNull(engine.getTargetState(5))
    }

    @Test
    fun testBoundary_negativeEntityId_isIgnored() {
        val res = engine.applyHit(entityId = -1, baseDamage = 50f, isLightLocked = false, seed = 0L, timestampMs = 1000L)
        assertEquals(0f, res.finalDamage)
    }

    @Test
    fun testStateTransition_buildupTriggersFreeze() {
        // High damage to ensure threshold is crossed
        val result = engine.applyHit(entityId = 6, baseDamage = 300f, isLightLocked = false, seed = 0L, timestampMs = 1000L)
        assertTrue(result.triggeredFreeze)

        val state = engine.getTargetState(6)!!
        assertTrue(state.isFrozen)
        assertEquals(config.freezeDurationSeconds, state.freezeRemainingSeconds)
    }

    @Test
    fun testStateTransition_freezeExpiresOverTime() {
        engine.applyHit(entityId = 7, baseDamage = 300f, isLightLocked = false, seed = 0L, timestampMs = 1000L)
        val state = engine.getTargetState(7)!!
        assertTrue(state.isFrozen)

        // Step just before expire (dt is maxed at 0.1, so we loop)
        val stepsNeeded = ((config.freezeDurationSeconds - 0.1f) / 0.1f).toInt()
        for (i in 0 until stepsNeeded) {
            engine.step(0.1f)
        }
        assertTrue(state.isFrozen)

        // Step past expire
        for (i in 0 until 5) {
            engine.step(0.1f)
        }
        assertFalse(state.isFrozen)
        assertEquals(0f, state.buildup)
    }

    @Test
    fun testStateTransition_buildupDecaysOverTime() {
        // Needs < threshold buildup
        engine.applyHit(entityId = 8, baseDamage = 50f, isLightLocked = false, seed = 0L, timestampMs = 1000L)
        val state = engine.getTargetState(8)!!
        val initialBuildup = state.buildup
        assertTrue(initialBuildup > 0f)

        engine.step(1.0f) // clamp might reduce it to max dt (0.1), let's loop

        val targetDecay = config.buildupDecayPerSecond * 0.1f // 1 max step

        val newBuildup = state.buildup
        assertTrue(newBuildup < initialBuildup)

        // Loop step to decay to zero
        for (i in 0..100) {
            engine.step(0.1f)
        }

        // It should be evicted or zero buildup
        val stateAfter = engine.getTargetState(8)
        assertNull(stateAfter)
    }

    @Test
    fun testMaxCapacity_evictsOldestNonFrozen() {
        // Fill up to max
        for (i in 0 until CryoVexManufacturerTrait.MAX_TRACKED_ENTITIES) {
            engine.applyHit(entityId = i, baseDamage = 10f, isLightLocked = false, seed = 0L, timestampMs = 1000L + i)
        }

        assertEquals(CryoVexManufacturerTrait.MAX_TRACKED_ENTITIES, engine.getActiveStateCount())

        // Ensure entity 0 is there
        assertNotNull(engine.getTargetState(0))

        // Add one more, should evict entity 0 (oldest timestamp)
        engine.applyHit(entityId = 9999, baseDamage = 10f, isLightLocked = false, seed = 0L, timestampMs = 9999L)

        assertEquals(CryoVexManufacturerTrait.MAX_TRACKED_ENTITIES, engine.getActiveStateCount())
        assertNull(engine.getTargetState(0))
        assertNotNull(engine.getTargetState(9999))
    }

    @Test
    fun testSerializationParity_configAndState() {
        val cfgMap = config.toMap()
        val cfg2 = CryoVexTraitConfig.fromMap(cfgMap)
        assertEquals(config, cfg2)

        val state = CryoVexTargetState(42, 50.5f, true, 2.5f, 123456789L)
        val stateMap = state.toMap()
        val state2 = CryoVexTargetState.fromMap(stateMap)
        assertEquals(state, state2)
    }

    @Test
    fun testChecksumConsistency() {
        engine.applyHit(entityId = 1, baseDamage = 40f, isLightLocked = false, seed = 0L, timestampMs = 1000L)
        engine.applyHit(entityId = 2, baseDamage = 50f, isLightLocked = true, seed = 0L, timestampMs = 1000L)

        val checksum1 = engine.computeStateChecksum()

        // Identical state via different order shouldn't matter since it's XOR based
        engine.clear()
        engine.applyHit(entityId = 2, baseDamage = 50f, isLightLocked = true, seed = 0L, timestampMs = 1000L)
        engine.applyHit(entityId = 1, baseDamage = 40f, isLightLocked = false, seed = 0L, timestampMs = 1000L)

        val checksum2 = engine.computeStateChecksum()
        assertEquals(checksum1, checksum2)
    }

    @Test
    fun testStep_infiniteOrNanDt_handledGracefully() {
        engine.applyHit(entityId = 1, baseDamage = 40f, isLightLocked = false, seed = 0L, timestampMs = 1000L)
        val state = engine.getTargetState(1)!!
        val before = state.buildup

        engine.step(Float.NaN)
        assertTrue(state.buildup < before)

        val before2 = state.buildup
        engine.step(Float.POSITIVE_INFINITY)
        assertTrue(state.buildup < before2)
    }
}
