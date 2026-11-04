package com.example.game.combat.modules

import com.example.game.ElementalAffinity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArmoredJuggernautZombieTest {

    @Test
    fun testCorrosiveDamageIsHighlyEffective() {
        val state = ArmoredJuggernautState(baseZombieId = 1, armorHp = 100, maxArmorHp = 100)
        
        // Corrosive applies 2x multiplier
        val (newState, passThrough) = ArmoredJuggernautCombatLogic.applyDamage(
            state = state,
            rawDamage = 30, // 30 * 2.0 = 60
            element = ElementalAffinity.CORROSIVE,
            isBarrelExplosion = false
        )
        
        assertEquals(40, newState.armorHp)
        assertEquals(0, passThrough)
        assertFalse(newState.isArmorStripped)
    }

    @Test
    fun testKineticDamageIsIneffective() {
        val state = ArmoredJuggernautState(baseZombieId = 1, armorHp = 100, maxArmorHp = 100)
        
        // Kinetic applies 0.25x multiplier
        val (newState, passThrough) = ArmoredJuggernautCombatLogic.applyDamage(
            state = state,
            rawDamage = 40, // 40 * 0.25 = 10
            element = ElementalAffinity.KINETIC,
            isBarrelExplosion = false
        )
        
        assertEquals(90, newState.armorHp)
        assertEquals(0, passThrough)
        assertFalse(newState.isArmorStripped)
    }

    @Test
    fun testBarrelExplosionInstantlyStripsArmor() {
        val state = ArmoredJuggernautState(baseZombieId = 1, armorHp = 500, maxArmorHp = 500)
        
        val (newState, passThrough) = ArmoredJuggernautCombatLogic.applyDamage(
            state = state,
            rawDamage = 260,
            element = ElementalAffinity.KINETIC,
            isBarrelExplosion = true
        )
        
        assertEquals(0, newState.armorHp)
        assertEquals(0, passThrough) // Absorbs the hit that breaks it
        assertTrue(newState.isArmorStripped)
    }

    @Test
    fun testPassThroughDamageWhenArmorAlreadyStripped() {
        val state = ArmoredJuggernautState(baseZombieId = 1, armorHp = 0, maxArmorHp = 100)
        
        val (newState, passThrough) = ArmoredJuggernautCombatLogic.applyDamage(
            state = state,
            rawDamage = 40,
            element = ElementalAffinity.KINETIC,
            isBarrelExplosion = false
        )
        
        assertEquals(0, newState.armorHp)
        assertEquals(40, passThrough) // Direct damage to core HP
        assertTrue(newState.isArmorStripped)
    }
}
