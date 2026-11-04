package com.example.game.combat.modules

import com.example.game.ElementalAffinity
import com.example.game.ElementalPuddle
import com.example.game.ZombieArchetype
import com.example.game.ZombieEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class CryoWraithZombieTest {

    @Test
    fun testStepLeavesIceTrails() {
        val zombie = ZombieEntity(
            id = 1,
            archetype = ZombieArchetype.SHAMBLER,
            x = 100f,
            y = 200f,
            hp = 100,
            maxHp = 100
        )
        val wraith = CryoWraithZombie(zombie)
        val puddles = mutableListOf<ElementalPuddle>()

        wraith.step(200L, puddles)
        assertEquals(0, puddles.size)

        wraith.step(350L, puddles) // Total 550ms, should spawn 1 puddle
        assertEquals(1, puddles.size)
        
        val p1 = puddles[0]
        assertEquals(100f, p1.x)
        assertEquals(200f, p1.y)
        assertEquals(ElementalAffinity.CRYO, p1.elementKind)

        zombie.x = 150f
        zombie.y = 250f
        wraith.step(500L, puddles) // Total 1050ms, should spawn 1 more puddle
        assertEquals(2, puddles.size)
        
        val p2 = puddles[1]
        assertEquals(150f, p2.x)
        assertEquals(250f, p2.y)
    }

    @Test
    fun testOnDeathDropsBonusCryoAmmo() {
        val zombie = ZombieEntity(
            id = 1,
            archetype = ZombieArchetype.SHAMBLER,
            x = 100f,
            y = 200f,
            hp = 100,
            maxHp = 100
        )
        val wraith = CryoWraithZombie(zombie)
        val ammoMap = mutableMapOf("pistol" to 15, "smg" to 100)

        wraith.onDeath("pistol", ammoMap)

        assertEquals(45, ammoMap["pistol"])
        assertEquals(100, ammoMap["smg"]) // Other weapon ammo is untouched
        
        wraith.onDeath("shotgun", ammoMap) // Adds entry if it doesn't exist
        assertEquals(30, ammoMap["shotgun"])
    }
}
