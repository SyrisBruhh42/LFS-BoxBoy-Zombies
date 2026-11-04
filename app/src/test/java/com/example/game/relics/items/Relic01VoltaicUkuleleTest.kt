package com.example.game.relics.items

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class Relic01VoltaicUkuleleTest {

    @Test
    fun testVoltaicUkulele_CanBePurchasedIsFalse() {
        assertFalse("Ukulele relic should not be purchasable from vending!", Relic01VoltaicUkuleleEngine.CAN_BE_PURCHASED_AT_VENDING)
    }

    @Test
    fun testVoltaicUkulele_ZeroStack_NoProc() {
        val arc = Relic01VoltaicUkuleleEngine.tryProc(
            stackCount = 0,
            baseDamage = 100f,
            hitX = 10f,
            hitY = 10f,
            hitEntityId = 1L,
            seed = 12345L
        )
        assertNull("Zero stacks should never proc", arc)
    }

    @Test
    fun testVoltaicUkulele_ProcChanceAndDamage() {
        // LCG Seed discovery: We want a seed that produces an LCG value <= 0.25
        // Seed 12345: (12345 * 1103515245 + 12345) % 2^31
        // Testing a few seeds to find one that procs
        var procSeed = -1L
        for (seed in 0L..1000L) {
            val nextSeed = (seed * 1103515245L + 12345L) and 0x7fffffffL
            val rand = (nextSeed.toFloat() / 0x7fffffffL)
            if (rand <= 0.25f) {
                procSeed = seed
                break
            }
        }
        
        val arc = Relic01VoltaicUkuleleEngine.tryProc(
            stackCount = 1,
            baseDamage = 100f,
            hitX = 10f,
            hitY = 10f,
            hitEntityId = 1L,
            seed = procSeed
        )
        assertNotNull("Should proc with chosen seed", arc)
        assertEquals(80f, arc!!.damage, 0.001f)
    }

    @Test
    fun testVoltaicUkulele_TargetCountScaling() {
        var procSeed = -1L
        for (seed in 0L..1000L) {
            val rand = ((seed * 1103515245L + 12345L) and 0x7fffffffL).toFloat() / 0x7fffffffL
            if (rand <= 0.25f) { procSeed = seed; break }
        }

        val arc1 = Relic01VoltaicUkuleleEngine.tryProc(1, 100f, 0f, 0f, 1L, procSeed)!!
        assertEquals("Base targets at stack 1 should be 3", 3, arc1.remainingBounces)

        val arc2 = Relic01VoltaicUkuleleEngine.tryProc(2, 100f, 0f, 0f, 1L, procSeed)!!
        assertEquals("Base targets at stack 2 should be 5", 5, arc2.remainingBounces)
    }

    @Test
    fun testVoltaicUkulele_RadiusScaling() {
        var procSeed = -1L
        for (seed in 0L..1000L) {
            val rand = ((seed * 1103515245L + 12345L) and 0x7fffffffL).toFloat() / 0x7fffffffL
            if (rand <= 0.25f) { procSeed = seed; break }
        }

        val arc1 = Relic01VoltaicUkuleleEngine.tryProc(1, 100f, 0f, 0f, 1L, procSeed)!!
        assertEquals("Base radius at stack 1 should be 60", 60f, arc1.bounceRadius, 0.001f)

        val arc2 = Relic01VoltaicUkuleleEngine.tryProc(2, 100f, 0f, 0f, 1L, procSeed)!!
        assertEquals("Base radius at stack 2 should be 120", 120f, arc2.bounceRadius, 0.001f)
    }

    @Test
    fun testVoltaicUkulele_Tick_ValidatesDistance() {
        val initialArc = ActiveArcLightning(
            arcId = 100L, x = 0f, y = 0f, damage = 80f,
            remainingBounces = 3, bounceRadius = 60f,
            visitedEntityIds = listOf(1L), timeToNextBounce = 0.00f
        )
        val snapshot = VoltaicUkuleleSnapshot(listOf(initialArc))
        
        // Entity 2 is within 60px (at x=50, y=0)
        // Entity 3 is outside 60px (at x=100, y=0)
        val entityIds = longArrayOf(2L, 3L)
        val entityXs = floatArrayOf(50f, 100f)
        val entityYs = floatArrayOf(0f, 0f)
        val entityHps = floatArrayOf(100f, 100f)
        
        val (newSnapshot, newHps) = Relic01VoltaicUkuleleEngine.tickArcs(
            snapshot, 0.05f, entityIds, entityXs, entityYs, entityHps
        )

        assertEquals("Entity 2 should take damage", 20f, newHps[0], 0.001f)
        assertEquals("Entity 3 should not take damage", 100f, newHps[1], 0.001f)
        
        assertEquals(1, newSnapshot.activeArcs.size)
        val bouncedArc = newSnapshot.activeArcs[0]
        assertEquals("Should decrement bounces", 2, bouncedArc.remainingBounces)
        assertEquals("Should move to Entity 2 location", 50f, bouncedArc.x, 0.001f)
        assertTrue(bouncedArc.visitedEntityIds.contains(2L))
    }

    @Test
    fun testVoltaicUkulele_Tick_CannotHitSameTargetTwice() {
        val initialArc = ActiveArcLightning(
            arcId = 100L, x = 0f, y = 0f, damage = 80f,
            remainingBounces = 3, bounceRadius = 60f,
            visitedEntityIds = listOf(1L, 2L), timeToNextBounce = 0.00f
        )
        val snapshot = VoltaicUkuleleSnapshot(listOf(initialArc))
        
        // Entity 2 is already in visited list
        val entityIds = longArrayOf(2L)
        val entityXs = floatArrayOf(10f)
        val entityYs = floatArrayOf(0f)
        val entityHps = floatArrayOf(100f)
        
        val (newSnapshot, newHps) = Relic01VoltaicUkuleleEngine.tickArcs(
            snapshot, 0.05f, entityIds, entityXs, entityYs, entityHps
        )

        assertEquals("Entity 2 should not take damage again", 100f, newHps[0], 0.001f)
        assertEquals("Arc should fizzle since no targets available", 0, newSnapshot.activeArcs.size)
    }

    @Test
    fun testVoltaicUkulele_Tick_HandlesExtremeLoad() {
        val initialArc = ActiveArcLightning(
            arcId = 100L, x = 0f, y = 0f, damage = 80f,
            remainingBounces = 5, bounceRadius = 600f, // huge radius
            visitedEntityIds = listOf(0L), timeToNextBounce = 0.00f
        )
        var snapshot = VoltaicUkuleleSnapshot(listOf(initialArc))
        
        val count = 300
        val entityIds = LongArray(count) { it.toLong() + 1 }
        val entityXs = FloatArray(count) { (it % 10) * 10f + 10f }
        val entityYs = FloatArray(count) { (it / 10) * 10f + 10f }
        val entityHps = FloatArray(count) { 100f }

        var hps = entityHps.copyOf()
        // Simulate 5 bounces
        for (i in 0 until 5) {
            val result = Relic01VoltaicUkuleleEngine.tickArcs(
                snapshot, 0.05f, entityIds, entityXs, entityYs, hps
            )
            snapshot = result.first
            hps = result.second
            
            // Advance time in arc if it was created
            if (snapshot.activeArcs.isNotEmpty()) {
                val updatedArc = snapshot.activeArcs[0].copy(timeToNextBounce = 0.00f)
                snapshot = VoltaicUkuleleSnapshot(listOf(updatedArc))
            } else {
                break
            }
        }
        
        var hitCount = 0
        for (hp in hps) {
            if (hp < 100f) hitCount++
        }
        assertEquals("Should hit exactly 5 targets out of 300", 5, hitCount)
    }

    @Test
    fun testVoltaicUkulele_Tick_IgnoresNaNAndInfinity() {
        val initialArc = ActiveArcLightning(
            arcId = 100L, x = 0f, y = 0f, damage = 80f,
            remainingBounces = 3, bounceRadius = 60f,
            visitedEntityIds = listOf(1L), timeToNextBounce = 0.00f
        )
        val snapshot = VoltaicUkuleleSnapshot(listOf(initialArc))
        
        val entityIds = longArrayOf(2L, 3L)
        val entityXs = floatArrayOf(Float.NaN, Float.POSITIVE_INFINITY)
        val entityYs = floatArrayOf(0f, 0f)
        val entityHps = floatArrayOf(100f, 100f)
        
        val (newSnapshot, newHps) = Relic01VoltaicUkuleleEngine.tickArcs(
            snapshot, 0.05f, entityIds, entityXs, entityYs, entityHps
        )

        assertEquals("Should ignore NaN x", 100f, newHps[0], 0.001f)
        assertEquals("Should ignore Infinity x", 100f, newHps[1], 0.001f)
        assertEquals("Arc should fizzle", 0, newSnapshot.activeArcs.size)
    }

    @Test
    fun testVoltaicUkulele_SerializationRoundtrip() {
        val arc = ActiveArcLightning(
            arcId = 999L, x = 12.3f, y = 45.6f, damage = 78.9f,
            remainingBounces = 2, bounceRadius = 120f,
            visitedEntityIds = listOf(1L, 2L, 3L), timeToNextBounce = 0.02f
        )
        val snapshot = VoltaicUkuleleSnapshot(listOf(arc))
        
        val map = snapshot.toMap()
        val restored = VoltaicUkuleleSnapshot.fromMap(map)
        
        assertEquals(snapshot.activeArcs.size, restored.activeArcs.size)
        val restoredArc = restored.activeArcs[0]
        assertEquals(arc.arcId, restoredArc.arcId)
        assertEquals(arc.x, restoredArc.x, 0.001f)
        assertEquals(arc.y, restoredArc.y, 0.001f)
        assertEquals(arc.damage, restoredArc.damage, 0.001f)
        assertEquals(arc.remainingBounces, restoredArc.remainingBounces)
        assertEquals(arc.bounceRadius, restoredArc.bounceRadius, 0.001f)
        assertEquals(arc.visitedEntityIds, restoredArc.visitedEntityIds)
        assertEquals(arc.timeToNextBounce, restoredArc.timeToNextBounce, 0.001f)
        
        assertEquals("Checksums must match after roundtrip", snapshot.checksum, restored.checksum)
    }

    @Test
    fun testVoltaicUkulele_ChecksumDeterminism() {
        val arc1 = ActiveArcLightning(
            arcId = 1L, x = 10f, y = 10f, damage = 50f, remainingBounces = 1, bounceRadius = 60f, visitedEntityIds = listOf(1L), timeToNextBounce = 0.05f
        )
        val arc2 = ActiveArcLightning(
            arcId = 1L, x = 10f, y = 10f, damage = 50f, remainingBounces = 1, bounceRadius = 60f, visitedEntityIds = listOf(1L), timeToNextBounce = 0.05f
        )
        assertEquals(arc1.checksum, arc2.checksum)
        
        val arc3 = arc1.copy(x = 10.0001f)
        assertTrue(arc1.checksum != arc3.checksum)
    }

    @Test
    fun testVoltaicUkulele_DtBoundsClamping() {
        val initialArc = ActiveArcLightning(
            arcId = 100L, x = 0f, y = 0f, damage = 80f,
            remainingBounces = 3, bounceRadius = 60f,
            visitedEntityIds = listOf(1L), timeToNextBounce = 0.05f
        )
        val snapshot = VoltaicUkuleleSnapshot(listOf(initialArc))
        
        val entityIds = longArrayOf(2L)
        val entityXs = floatArrayOf(10f)
        val entityYs = floatArrayOf(0f)
        val entityHps = floatArrayOf(100f)
        
        // Try dt = 10.0f (should clamp to 0.100f)
        val (newSnap1, _) = Relic01VoltaicUkuleleEngine.tickArcs(snapshot, 10.0f, entityIds, entityXs, entityYs, entityHps)
        
        // Wait, if it clamps to 0.100f, then timeToNextBounce (0.05f) - 0.100f = -0.05f <= 0, so it will bounce!
        // So the arc will bounce and its remaining bounces will decrease, AND it will hit entity 2.
        assertEquals(1, newSnap1.activeArcs.size)
        assertEquals(2, newSnap1.activeArcs[0].remainingBounces)
        
        // Try dt = 0.0001f (should clamp to 0.001f)
        val (newSnap2, _) = Relic01VoltaicUkuleleEngine.tickArcs(snapshot, 0.0001f, entityIds, entityXs, entityYs, entityHps)
        // timeToNextBounce (0.05f) - 0.001f = 0.049f > 0, so it won't bounce
        assertEquals(1, newSnap2.activeArcs.size)
        assertEquals(3, newSnap2.activeArcs[0].remainingBounces)
        assertEquals(0.049f, newSnap2.activeArcs[0].timeToNextBounce, 0.0001f)
    }
}
