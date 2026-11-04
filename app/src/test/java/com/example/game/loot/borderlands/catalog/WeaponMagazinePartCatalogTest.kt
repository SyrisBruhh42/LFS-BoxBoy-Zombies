package com.example.game.loot.borderlands.catalog

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

class WeaponMagazinePartCatalogTest {

    @Before
    fun setup() {
        WeaponMagazinePartCatalog.clearReloadStates()
    }

    @Test
    fun `test all 16 magazine parts exist and values match`() {
        assertEquals(16, WeaponMagazinePartCatalog.PARTS.size)
        val part1 = WeaponMagazinePartCatalog.getPart(1)
        assertEquals("Standard Mag", part1.name)
        assertEquals(MagazineElementalAffinity.NONE, part1.elementalAffinity)

        val part16 = WeaponMagazinePartCatalog.getPart(16)
        assertEquals("Explosive Drum", part16.name)
        assertEquals(MagazineElementalAffinity.EXPLOSIVE, part16.elementalAffinity)
        assertEquals(2.0f, part16.capacityMultiplier)
        assertEquals(0.6f, part16.reloadSpeedMultiplier)
    }

    @Test
    fun `test generatePartProcedurally determinism`() {
        val seed = 123456789L
        val level = 5
        val p1 = WeaponMagazinePartCatalog.generatePartProcedurally(seed, level)
        val p2 = WeaponMagazinePartCatalog.generatePartProcedurally(seed, level)
        assertEquals(p1, p2)

        val p3 = WeaponMagazinePartCatalog.generatePartProcedurally(seed + 1, level)
        assertNotEquals(p1.partId, p3.partId) // In most cases they differ
    }

    @Test
    fun `test startReload nominal execution`() {
        val success = WeaponMagazinePartCatalog.startReload(entityId = 42, x = 100f, y = 200f, partId = 3, baseReloadDuration = 1.5f)
        assertTrue(success)

        val state = WeaponMagazinePartCatalog.getReloadState(42)
        assertNotNull(state)
        assertEquals(42, state!!.entityId)
        assertEquals(100f, state.x)
        assertEquals(200f, state.y)
        assertTrue(state.isReloading)
        // part 3 is Quick Mag: reloadSpeedMultiplier = 1.5f
        // base = 1.5f, final = 1.5f / 1.5f = 1.0f
        assertEquals(1.0f, state.reloadDuration)
    }

    @Test
    fun `test startReload updates existing entityId`() {
        WeaponMagazinePartCatalog.startReload(entityId = 99, x = 0f, y = 0f, partId = 1, baseReloadDuration = 2.0f)
        WeaponMagazinePartCatalog.startReload(entityId = 99, x = 50f, y = -50f, partId = 2, baseReloadDuration = 2.0f)

        val state = WeaponMagazinePartCatalog.getReloadState(99)
        assertNotNull(state)
        assertEquals(50f, state!!.x)
        assertEquals(-50f, state.y)
        assertEquals(2, state.partId)
    }

    @Test
    fun `test stepReloads increments progress and completes`() {
        WeaponMagazinePartCatalog.startReload(entityId = 77, x = 0f, y = 0f, partId = 1, baseReloadDuration = 1.0f)

        // 5 steps of 0.1f = 0.5f
        for (i in 0 until 5) {
            WeaponMagazinePartCatalog.stepReloads(0.1f, playerX = 0f, playerY = 0f)
        }
        var state = WeaponMagazinePartCatalog.getReloadState(77)
        assertNotNull(state)
        assertTrue(abs(0.5f - state!!.reloadProgress) < 0.001f)
        assertTrue(state.isReloading)

        // 6 steps of 0.1f = 0.6f (Total = 1.1f, which exceeds 1.0f)
        for (i in 0 until 6) {
            WeaponMagazinePartCatalog.stepReloads(0.1f, playerX = 0f, playerY = 0f)
        }
        state = WeaponMagazinePartCatalog.getReloadState(77)
        // Note: completion removes it from active set!
        assertNull(state)
    }

    @Test
    fun `test stepReloads spatial validation max distance`() {
        WeaponMagazinePartCatalog.startReload(entityId = 10, x = 3000f, y = 0f, partId = 1, baseReloadDuration = 1.0f)

        // Player is at 0,0, dist = 3000. Max dist = 2400.
        // It shouldn't increment.
        WeaponMagazinePartCatalog.stepReloads(0.1f, playerX = 0f, playerY = 0f, maxActiveDistance = 2400f)
        val state = WeaponMagazinePartCatalog.getReloadState(10)
        assertNotNull(state)
        assertEquals(0f, state!!.reloadProgress)

        // Player moves closer
        WeaponMagazinePartCatalog.stepReloads(0.1f, playerX = 1000f, playerY = 0f, maxActiveDistance = 2400f)
        val state2 = WeaponMagazinePartCatalog.getReloadState(10)
        assertNotNull(state2)
        assertTrue(abs(0.1f - state2!!.reloadProgress) < 0.001f)
    }

    @Test
    fun `test boundary conditions NaN and Infinity in startReload`() {
        val success = WeaponMagazinePartCatalog.startReload(entityId = 1, x = Float.NaN, y = Float.POSITIVE_INFINITY, partId = 1, baseReloadDuration = Float.NaN)
        assertTrue(success)

        val state = WeaponMagazinePartCatalog.getReloadState(1)
        assertNotNull(state)
        assertEquals(0f, state!!.x)
        assertEquals(0f, state.y)
        assertEquals(1.0f, state.reloadDuration)
    }

    @Test
    fun `test boundary conditions dtSeconds bounds clamping`() {
        WeaponMagazinePartCatalog.startReload(entityId = 2, x = 0f, y = 0f, partId = 1, baseReloadDuration = 2.0f)

        // Over max (0.100f) -> clamps to 0.100f
        WeaponMagazinePartCatalog.stepReloads(5.0f, playerX = 0f, playerY = 0f)
        var state = WeaponMagazinePartCatalog.getReloadState(2)
        assertTrue(abs(0.100f - state!!.reloadProgress) < 0.001f)

        // NaN -> min (0.001f)
        WeaponMagazinePartCatalog.stepReloads(Float.NaN, playerX = 0f, playerY = 0f)
        state = WeaponMagazinePartCatalog.getReloadState(2)
        // 0.100 + 0.001 = 0.101
        assertTrue(abs(0.101f - state!!.reloadProgress) < 0.001f)
    }

    @Test
    fun `test max capacity stress`() {
        for (i in 0 until WeaponMagazinePartCatalog.MAX_RELOAD_STATES) {
            val success = WeaponMagazinePartCatalog.startReload(i, 0f, 0f, 1, 1.0f)
            assertTrue(success)
        }

        // Try adding one more -> should fail
        val overflowSuccess = WeaponMagazinePartCatalog.startReload(WeaponMagazinePartCatalog.MAX_RELOAD_STATES, 0f, 0f, 1, 1.0f)
        assertFalse(overflowSuccess)
    }

    @Test
    fun `test state checksum changes when progress changes`() {
        WeaponMagazinePartCatalog.startReload(entityId = 111, x = 0f, y = 0f, partId = 1, baseReloadDuration = 1.0f)
        val cs1 = WeaponMagazinePartCatalog.computeStateChecksum()

        WeaponMagazinePartCatalog.stepReloads(0.05f, 0f, 0f)
        val cs2 = WeaponMagazinePartCatalog.computeStateChecksum()

        assertNotEquals(cs1, cs2)
    }

    @Test
    fun `test state checksum determinism`() {
        WeaponMagazinePartCatalog.startReload(entityId = 222, x = 10f, y = 10f, partId = 5, baseReloadDuration = 2.0f)
        val cs1 = WeaponMagazinePartCatalog.computeStateChecksum()

        WeaponMagazinePartCatalog.clearReloadStates()
        WeaponMagazinePartCatalog.startReload(entityId = 222, x = 10f, y = 10f, partId = 5, baseReloadDuration = 2.0f)
        val cs2 = WeaponMagazinePartCatalog.computeStateChecksum()

        assertEquals(cs1, cs2)
    }

    @Test
    fun `test serialization roundtrip for WeaponMagazinePart`() {
        val part = WeaponMagazinePartCatalog.getPart(16)
        val map = part.toMap()
        val restored = WeaponMagazinePart.fromMap(map)
        assertEquals(part, restored)
    }

    @Test
    fun `test serialization roundtrip for MagazineReloadState`() {
        WeaponMagazinePartCatalog.startReload(entityId = 333, x = 50f, y = -25f, partId = 10, baseReloadDuration = 1.5f)
        WeaponMagazinePartCatalog.stepReloads(0.05f, 0f, 0f)
        val state = WeaponMagazinePartCatalog.getReloadState(333)!!

        val map = state.toMap()
        val restored = MagazineReloadState.fromMap(map)

        assertEquals(state.entityId, restored.entityId)
        assertEquals(state.x, restored.x)
        assertEquals(state.y, restored.y)
        assertEquals(state.reloadProgress, restored.reloadProgress)
        assertEquals(state.reloadDuration, restored.reloadDuration)
        assertEquals(state.isReloading, restored.isReloading)
        assertEquals(state.partId, restored.partId)
    }
}
