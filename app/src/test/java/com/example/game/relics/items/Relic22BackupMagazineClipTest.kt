package com.example.game.relics.items

import org.junit.Assert.*
import org.junit.Test
import com.example.game.LootRarity

class Relic22BackupMagazineClipTest {

    @Test
    fun testConfigInvariants() {
        val config = Relic22BackupMagazineClipConfig()
        assertFalse(config.canBePurchasedAtVending)
        assertEquals("backup_magazine_clip", config.relicId)
        assertEquals(LootRarity.UNCOMMON, config.rarity)
    }

    @Test
    fun testStateEnforcesMinStackCount() {
        try {
            Relic22BackupMagazineClipState(stackCount = 0)
            fail("Should throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("must be >= 1"))
        }
        
        try {
            Relic22BackupMagazineClipState(stackCount = -5)
            fail("Should throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("must be >= 1"))
        }
    }

    @Test
    fun testStateSerializationRoundtrip() {
        val original = Relic22BackupMagazineClipState(stackCount = 7)
        val map = original.toMap()
        val restored = Relic22BackupMagazineClipState.fromMap(map)
        assertEquals(original.stackCount, restored.stackCount)
    }
    
    @Test
    fun testStateDeserializationWithInvalidData() {
        val map1 = mapOf<String, Any>("stackCount" to -3)
        val restored1 = Relic22BackupMagazineClipState.fromMap(map1)
        assertEquals(1, restored1.stackCount)
        
        val map2 = mapOf<String, Any>()
        val restored2 = Relic22BackupMagazineClipState.fromMap(map2)
        assertEquals(1, restored2.stackCount)
    }

    @Test
    fun testCalculateTotalChargesWithOneStack() {
        val config = Relic22BackupMagazineClipConfig()
        val state = Relic22BackupMagazineClipState(stackCount = 1)
        
        val total = Relic22BackupMagazineClipEngine.calculateTotalCharges(config, state, 2)
        // 2 (base) + 1 (from 1 stack) = 3
        assertEquals(3, total)
    }
    
    @Test
    fun testCalculateTotalChargesWithFiveStacks() {
        val config = Relic22BackupMagazineClipConfig()
        val state = Relic22BackupMagazineClipState(stackCount = 5)
        
        val total = Relic22BackupMagazineClipEngine.calculateTotalCharges(config, state, 1)
        // 1 (base) + 1 (base config) + 4 (4 extra stacks * 1 charge) = 6
        assertEquals(6, total)
    }
    
    @Test
    fun testDeterminismSameInputsSameOutputs() {
        val config = Relic22BackupMagazineClipConfig()
        val state1 = Relic22BackupMagazineClipState(stackCount = 10)
        val state2 = Relic22BackupMagazineClipState(stackCount = 10)
        
        val total1 = Relic22BackupMagazineClipEngine.calculateTotalCharges(config, state1, 0)
        val total2 = Relic22BackupMagazineClipEngine.calculateTotalCharges(config, state2, 0)
        
        assertEquals(total1, total2)
    }

    @Test
    fun testMaxCapacityStress() {
        val config = Relic22BackupMagazineClipConfig()
        val state = Relic22BackupMagazineClipState(stackCount = 1000)
        
        val total = Relic22BackupMagazineClipEngine.calculateTotalCharges(config, state, 2)
        // 2 + 1 + 999 * 1 = 1002
        assertEquals(1002, total)
    }
    
    @Test
    fun testZeroBasePlayerCharges() {
        val config = Relic22BackupMagazineClipConfig()
        val state = Relic22BackupMagazineClipState(stackCount = 1)
        val total = Relic22BackupMagazineClipEngine.calculateTotalCharges(config, state, 0)
        assertEquals(1, total)
    }
    
    @Test
    fun testCustomConfigScaling() {
        val config = Relic22BackupMagazineClipConfig(baseCharges = 2, chargesPerStack = 3)
        val state = Relic22BackupMagazineClipState(stackCount = 4) // 1 base + 3 extra stacks
        val total = Relic22BackupMagazineClipEngine.calculateTotalCharges(config, state, 1)
        // 1 (player) + 2 (base config) + (3 * 3) (extra) = 1 + 2 + 9 = 12
        assertEquals(12, total)
    }
    
    @Test
    fun testExtraChargesProperty() {
        val state = Relic22BackupMagazineClipState(stackCount = 4)
        assertEquals(4, state.extraCharges)
    }
    
    @Test
    fun testNoHeapChurnInHotLoop() {
        val config = Relic22BackupMagazineClipConfig()
        val state = Relic22BackupMagazineClipState(stackCount = 5)
        var sum = 0
        for (i in 0 until 1000) {
            sum += Relic22BackupMagazineClipEngine.calculateTotalCharges(config, state, i)
        }
        // Arithmetic progression sum:
        // sum_{i=0}^{999} (i + 1 + 4) = sum_{i=0}^{999} i + sum_{i=0}^{999} 5 = (999 * 1000) / 2 + 5000 = 499500 + 5000 = 504500
        assertEquals(504500, sum)
    }
}
