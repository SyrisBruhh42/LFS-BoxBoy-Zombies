package com.example.game.loot.borderlands.catalog

import com.example.game.ElementalAffinity
import org.junit.Assert.*
import org.junit.Test

class ElementalMultiplierMatrixTest {

    @Test
    fun testNominalKinetic() {
        val config = ElementalMatrixConfig()
        assertEquals(1.0f, ElementalMultiplierMatrix.getMultiplier(ElementalAffinity.KINETIC, TargetDefenseType.FLESH, config), 0.001f)
        assertEquals(0.8f, ElementalMultiplierMatrix.getMultiplier(ElementalAffinity.KINETIC, TargetDefenseType.ARMOR, config), 0.001f)
        assertEquals(0.8f, ElementalMultiplierMatrix.getMultiplier(ElementalAffinity.KINETIC, TargetDefenseType.SHIELD, config), 0.001f)
    }

    @Test
    fun testNominalIncendiary() {
        val config = ElementalMatrixConfig()
        assertEquals(1.75f, ElementalMultiplierMatrix.getMultiplier(ElementalAffinity.INCENDIARY, TargetDefenseType.FLESH, config), 0.001f)
        assertEquals(0.5f, ElementalMultiplierMatrix.getMultiplier(ElementalAffinity.INCENDIARY, TargetDefenseType.ARMOR, config), 0.001f)
        assertEquals(0.4f, ElementalMultiplierMatrix.getMultiplier(ElementalAffinity.INCENDIARY, TargetDefenseType.SHIELD, config), 0.001f)
    }

    @Test
    fun testNominalCryo() {
        val config = ElementalMatrixConfig()
        assertEquals(1.0f, ElementalMultiplierMatrix.getMultiplier(ElementalAffinity.CRYO, TargetDefenseType.FLESH, config), 0.001f)
        assertEquals(1.2f, ElementalMultiplierMatrix.getMultiplier(ElementalAffinity.CRYO, TargetDefenseType.ARMOR, config), 0.001f)
        assertEquals(1.0f, ElementalMultiplierMatrix.getMultiplier(ElementalAffinity.CRYO, TargetDefenseType.SHIELD, config), 0.001f)
    }

    @Test
    fun testNominalCorrosive() {
        val config = ElementalMatrixConfig()
        assertEquals(0.75f, ElementalMultiplierMatrix.getMultiplier(ElementalAffinity.CORROSIVE, TargetDefenseType.FLESH, config), 0.001f)
        assertEquals(1.75f, ElementalMultiplierMatrix.getMultiplier(ElementalAffinity.CORROSIVE, TargetDefenseType.ARMOR, config), 0.001f)
        assertEquals(0.4f, ElementalMultiplierMatrix.getMultiplier(ElementalAffinity.CORROSIVE, TargetDefenseType.SHIELD, config), 0.001f)
    }

    @Test
    fun testNominalVoltaic() {
        val config = ElementalMatrixConfig()
        assertEquals(0.75f, ElementalMultiplierMatrix.getMultiplier(ElementalAffinity.VOLTAIC, TargetDefenseType.FLESH, config), 0.001f)
        assertEquals(1.0f, ElementalMultiplierMatrix.getMultiplier(ElementalAffinity.VOLTAIC, TargetDefenseType.ARMOR, config), 0.001f)
        assertEquals(2.5f, ElementalMultiplierMatrix.getMultiplier(ElementalAffinity.VOLTAIC, TargetDefenseType.SHIELD, config), 0.001f)
    }

    @Test
    fun testDisabledConfig() {
        val config = ElementalMatrixConfig(isEnabled = false)
        assertEquals(1.0f, ElementalMultiplierMatrix.getMultiplier(ElementalAffinity.VOLTAIC, TargetDefenseType.SHIELD, config), 0.001f)
        assertEquals(1.0f, ElementalMultiplierMatrix.getMultiplier(ElementalAffinity.INCENDIARY, TargetDefenseType.FLESH, config), 0.001f)
    }

    @Test
    fun testConfigSerializationRoundtrip() {
        val original = ElementalMatrixConfig(version = 2, isEnabled = false)
        val map = original.toMap()
        val restored = ElementalMatrixConfig.fromMap(map)
        assertEquals(original, restored)
    }

    @Test
    fun testSnapshotSerializationRoundtrip() {
        val original = ElementalMatrixSnapshot(
            lastEvaluatedElementId = ElementalAffinity.CORROSIVE.ordinal,
            lastEvaluatedDefenseId = TargetDefenseType.ARMOR.id,
            calculatedMultiplier = 1.75f,
            checksum = 123456789L
        )
        val map = original.toMap()
        val restored = ElementalMatrixSnapshot.fromMap(map)
        assertEquals(original, restored)
    }

    @Test
    fun testEvaluateDeterminismAndChecksum() {
        val config = ElementalMatrixConfig()
        val snapshot1 = ElementalMultiplierMatrix.evaluate(ElementalAffinity.INCENDIARY, TargetDefenseType.FLESH, config)
        val snapshot2 = ElementalMultiplierMatrix.evaluate(ElementalAffinity.INCENDIARY, TargetDefenseType.FLESH, config)
        
        assertEquals(1.75f, snapshot1.calculatedMultiplier, 0.001f)
        assertEquals(snapshot1.checksum, snapshot2.checksum)
        assertEquals(snapshot1.lastEvaluatedElementId, snapshot2.lastEvaluatedElementId)
        assertEquals(snapshot1.lastEvaluatedDefenseId, snapshot2.lastEvaluatedDefenseId)
    }

    @Test
    fun testMaxCapacityStress() {
        val config = ElementalMatrixConfig()
        val elements = ElementalAffinity.entries.toTypedArray()
        val defenses = TargetDefenseType.entries.toTypedArray()
        
        // Simulating high load / many entities
        var sum = 0f
        val iterations = 100_000
        for (i in 0 until iterations) {
            val el = elements[i % elements.size]
            val def = defenses[i % defenses.size]
            sum += ElementalMultiplierMatrix.getMultiplier(el, def, config)
        }
        
        assertTrue(sum > 0f)
        assertTrue(sum.isFinite())
    }

    @Test
    fun testChecksumFNV1aProperties() {
        val checksum1 = ElementalMultiplierMatrix.computeStateChecksum(1, 1, 1.0f)
        val checksum2 = ElementalMultiplierMatrix.computeStateChecksum(1, 1, 1.000001f)
        val checksum3 = ElementalMultiplierMatrix.computeStateChecksum(1, 2, 1.0f)
        
        assertNotEquals(checksum1, checksum2)
        assertNotEquals(checksum1, checksum3)
    }

    @Test
    fun testTargetDefenseTypeFromId() {
        assertEquals(TargetDefenseType.FLESH, TargetDefenseType.fromId(0))
        assertEquals(TargetDefenseType.ARMOR, TargetDefenseType.fromId(1))
        assertEquals(TargetDefenseType.SHIELD, TargetDefenseType.fromId(2))
        assertEquals(TargetDefenseType.FLESH, TargetDefenseType.fromId(999)) // Invalid ID gracefully degrades to FLESH
    }
}
