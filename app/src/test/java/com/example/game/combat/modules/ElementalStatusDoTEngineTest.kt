package com.example.game.combat.modules

import com.example.game.ElementalAffinity
import com.example.game.ZombieArchetype
import com.example.game.ZombieEntity
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ElementalStatusDoTEngineTest {

    private lateinit var engine: ElementalStatusDoTEngine
    private lateinit var zombies: MutableList<ZombieEntity>

    @Before
    fun setup() {
        engine = ElementalStatusDoTEngine()
        zombies = mutableListOf(
            ZombieEntity(1, ZombieArchetype.SHAMBLER, 0f, 0f, 100, 100),
            ZombieEntity(2, ZombieArchetype.SHAMBLER, 0f, 0f, 100, 100),
            ZombieEntity(3, ZombieArchetype.SHAMBLER, 0f, 0f, 100, 100)
        )
    }

    @Test
    fun `test INCENDIARY DoT ticks and deals damage over time`() {
        // Apply 40 base damage INCENDIARY -> 10 damage per tick (burnDamageMultiplier = 0.25f)
        engine.applyStatusEffect(1, ElementalAffinity.INCENDIARY, 40f)

        // First tick
        val results1 = engine.tick(500L, zombies)
        assertEquals(1, results1.size)
        assertEquals(1, results1[0].zombieId)
        assertEquals(10, results1[0].damageTaken)
        assertFalse(results1[0].isLethal)
        assertEquals(90, zombies[0].hp)

        // Second tick
        val results2 = engine.tick(500L, zombies)
        assertEquals(1, results2.size)
        assertEquals(10, results2[0].damageTaken)
        assertEquals(80, zombies[0].hp)
    }

    @Test
    fun `test CRYO freezes instead of standard DoT`() {
        // Apply 20 base damage CRYO -> 20 * 100L = 2000ms freeze
        engine.applyStatusEffect(1, ElementalAffinity.CRYO, 20f)

        assertTrue(engine.isFrozen(1))

        // Tick 1000ms
        val results = engine.tick(1000L, zombies)
        assertTrue(results.isEmpty()) // No damage from freeze
        assertTrue(engine.isFrozen(1)) // Still 1000ms left

        // Tick 1001ms
        engine.tick(1001L, zombies)
        assertFalse(engine.isFrozen(1)) // Freeze expired
    }

    @Test
    fun `test CORROSIVE and VOLTAIC tick properly with multipliers`() {
        // CORROSIVE 0.3f multiplier
        engine.applyStatusEffect(1, ElementalAffinity.CORROSIVE, 30f) // 9 damage per tick

        // VOLTAIC 0.4f multiplier
        engine.applyStatusEffect(2, ElementalAffinity.VOLTAIC, 25f) // 10 damage per tick

        val results = engine.tick(500L, zombies)
        assertEquals(2, results.size)

        val res1 = results.find { it.zombieId == 1 }!!
        assertEquals(9, res1.damageTaken)
        assertEquals(91, zombies[0].hp)

        val res2 = results.find { it.zombieId == 2 }!!
        assertEquals(10, res2.damageTaken)
        assertEquals(90, zombies[1].hp)
    }

    @Test
    fun `test lethal DoT cleans up engine state`() {
        zombies[0].hp = 5 // low health zombie

        engine.applyStatusEffect(1, ElementalAffinity.INCENDIARY, 40f) // 10 dmg per tick
        engine.applyStatusEffect(1, ElementalAffinity.CRYO, 10f) // Freeze

        assertTrue(engine.isFrozen(1))

        val results = engine.tick(500L, zombies)
        assertEquals(1, results.size)
        assertTrue(results[0].isLethal)
        assertTrue(zombies[0].hp <= 0)

        // Next tick, the engine shouldn't track this zombie anymore
        val results2 = engine.tick(500L, zombies)
        assertTrue(results2.isEmpty())
        assertFalse(engine.isFrozen(1))
    }

    @Test
    fun `test DoT expiration`() {
        engine.applyStatusEffect(1, ElementalAffinity.VOLTAIC, 10f)

        // Tick maxTicks times
        for (i in 1..engine.maxTicks) {
            val res = engine.tick(500L, zombies)
            assertEquals(1, res.size)
        }

        // Effect should have expired now
        val resExpired = engine.tick(500L, zombies)
        assertTrue(resExpired.isEmpty())
    }
}
