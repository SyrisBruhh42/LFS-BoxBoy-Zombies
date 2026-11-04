package com.example.game.combat.modules

import com.example.game.ElementalAffinity
import com.example.game.ZombieArchetype
import com.example.game.ZombieEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ArmoredJuggernautZombieTest {

    private fun createJuggernaut(hp: Int = 100, armorHp: Int = 50): ArmoredJuggernautZombie {
        val entity = ZombieEntity(
            id = 1,
            archetype = ZombieArchetype.SHAMBLER, // Using shambler for simplicity
            x = 0f,
            y = 0f,
            hp = hp,
            maxHp = hp
        )
        return ArmoredJuggernautZombie(entity, armorHp, armorHp)
    }

    @Test
    fun testKineticDamageHighlyMitigatedByArmor() {
        val juggernaut = createJuggernaut(hp = 100, armorHp = 50)
        
        // 100 base damage -> 10% damage to armor -> 10 damage to armor
        juggernaut.takeDamage(100, ElementalAffinity.KINETIC, isExplosiveBarrel = false)
        
        assertEquals("Armor should absorb most kinetic damage", 40, juggernaut.armorHp)
        assertEquals("Entity HP should be untouched while armor holds", 100, juggernaut.entity.hp)
    }

    @Test
    fun testCorrosiveDamageStripsArmorEfficiently() {
        val juggernaut = createJuggernaut(hp = 100, armorHp = 50)
        
        // 40 base damage -> 100% damage to armor -> 40 damage to armor
        juggernaut.takeDamage(40, ElementalAffinity.CORROSIVE, isExplosiveBarrel = false)
        
        assertEquals("Corrosive should deal full damage to armor", 10, juggernaut.armorHp)
        assertEquals("Entity HP should be untouched while armor holds", 100, juggernaut.entity.hp)
    }

    @Test
    fun testExplosiveBarrelStripsArmorEfficiently() {
        val juggernaut = createJuggernaut(hp = 100, armorHp = 50)
        
        // 40 base damage from explosive barrel -> 100% damage to armor
        juggernaut.takeDamage(40, ElementalAffinity.KINETIC, isExplosiveBarrel = true)
        
        assertEquals("Explosive barrel should deal full damage to armor", 10, juggernaut.armorHp)
        assertEquals("Entity HP should be untouched while armor holds", 100, juggernaut.entity.hp)
    }

    @Test
    fun testArmorBreakOverflowDamageCorrosive() {
        val juggernaut = createJuggernaut(hp = 100, armorHp = 50)
        
        // 70 base damage Corrosive -> breaks 50 armor, leaves 20 damage overflowing
        juggernaut.takeDamage(70, ElementalAffinity.CORROSIVE, isExplosiveBarrel = false)
        
        assertEquals("Armor should be broken", 0, juggernaut.armorHp)
        assertEquals("Remaining damage should overflow to entity HP", 80, juggernaut.entity.hp)
    }
    
    @Test
    fun testArmorBreakOverflowDamageKinetic() {
        val juggernaut = createJuggernaut(hp = 100, armorHp = 50)
        
        // 600 base damage Kinetic -> needs 500 damage to break 50 armor (since it does 10%)
        // Remaining base damage is 100, which overflows as normal damage
        juggernaut.takeDamage(600, ElementalAffinity.KINETIC, isExplosiveBarrel = false)
        
        assertEquals("Armor should be broken", 0, juggernaut.armorHp)
        assertEquals("Remaining damage should overflow to entity HP", 0, juggernaut.entity.hp)
    }

    @Test
    fun testDamageWithoutArmor() {
        val juggernaut = createJuggernaut(hp = 100, armorHp = 0)
        
        // 30 base damage when armor is already 0
        juggernaut.takeDamage(30, ElementalAffinity.KINETIC, isExplosiveBarrel = false)
        
        assertEquals("Armor should remain 0", 0, juggernaut.armorHp)
        assertEquals("Damage should go directly to entity HP", 70, juggernaut.entity.hp)
    }
}
