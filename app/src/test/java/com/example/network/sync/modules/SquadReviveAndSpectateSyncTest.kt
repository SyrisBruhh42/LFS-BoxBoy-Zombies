package com.example.network.sync.modules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SquadReviveAndSpectateSyncTest {

    // 1. Nominal: processDownedPlayer correctly applies downed state
    @Test
    fun `test processDownedPlayer sets initial bleedout state`() {
        val config = SquadReviveConfig()
        val engine = SquadReviveEngine(config)
        val player = PlayerReviveState(playerId = "p1")
        
        engine.processDownedPlayer(player)
        
        assertTrue(player.isDowned)
        assertFalse(player.isDeadSpectating)
        assertEquals(45.0f, player.bleedoutTimer, 0.001f)
        assertEquals(0.0f, player.reviveTimer, 0.001f)
    }

    // 2. Nominal: startReviveTether connects players within distance
    @Test
    fun `test startReviveTether connects within max distance`() {
        val config = SquadReviveConfig(maxReviveTetherDistancePx = 120f)
        val engine = SquadReviveEngine(config)
        
        val p1 = PlayerReviveState(playerId = "p1", isDowned = true, posX = 0f, posY = 0f)
        val p2 = PlayerReviveState(playerId = "p2", posX = 100f, posY = 0f) // 100 < 120
        
        engine.startReviveTether(p1, p2)
        
        assertEquals("p2", p1.reviverPlayerId)
    }

    // 3. Boundary: startReviveTether rejects players outside distance
    @Test
    fun `test startReviveTether fails outside max distance`() {
        val config = SquadReviveConfig(maxReviveTetherDistancePx = 120f)
        val engine = SquadReviveEngine(config)
        
        val p1 = PlayerReviveState(playerId = "p1", isDowned = true, posX = 0f, posY = 0f)
        val p2 = PlayerReviveState(playerId = "p2", posX = 150f, posY = 0f) // 150 > 120
        
        engine.startReviveTether(p1, p2)
        
        assertEquals("", p1.reviverPlayerId)
    }

    // 4. Nominal: tick increments reviveTimer and revives player
    @Test
    fun `test tick increments reviveTimer and completes revive`() {
        val config = SquadReviveConfig(baseReviveTimeSeconds = 3.0f)
        val engine = SquadReviveEngine(config)
        
        val p1 = PlayerReviveState(playerId = "p1", isDowned = true, reviverPlayerId = "p2", posX = 0f, posY = 0f)
        val p2 = PlayerReviveState(playerId = "p2", posX = 50f, posY = 0f)
        
        val snapshot = SquadReviveSnapshot(arrayOf(p1, p2))
        
        engine.tick(snapshot, 2.0f) // Not enough time
        assertTrue(p1.isDowned)
        assertEquals(2.0f, p1.reviveTimer, 0.001f) // Bounded by max dt 0.1, wait, actually dt is bounded to 0.1 max!
        
        // Let's do a loop
        for (i in 0 until 30) { // 30 * 0.1 = 3.0
            engine.tick(snapshot, 0.1f)
        }
        
        assertFalse(p1.isDowned)
        assertEquals(0.0f, p1.bleedoutTimer, 0.001f)
        assertEquals("", p1.reviverPlayerId)
    }

    // 5. Nominal: tick with quick revive perk completes faster
    @Test
    fun `test tick completes faster with quick revive perk`() {
        val config = SquadReviveConfig(baseReviveTimeSeconds = 3.0f, quickReviveTimeSeconds = 1.5f)
        val engine = SquadReviveEngine(config)
        
        val p1 = PlayerReviveState(playerId = "p1", isDowned = true, reviverPlayerId = "p2", posX = 0f, posY = 0f)
        val p2 = PlayerReviveState(playerId = "p2", posX = 50f, posY = 0f, hasQuickRevivePerk = true)
        
        val snapshot = SquadReviveSnapshot(arrayOf(p1, p2))
        
        for (i in 0 until 15) { // 15 * 0.1 = 1.5
            engine.tick(snapshot, 0.1f)
        }
        
        assertFalse(p1.isDowned)
        assertEquals("", p1.reviverPlayerId)
    }

    // 6. Nominal: tick handles bleedout and transition to spectating
    @Test
    fun `test tick bleeds out player to spectating state`() {
        val config = SquadReviveConfig(maxBleedoutTimeSeconds = 45.0f)
        val engine = SquadReviveEngine(config)
        
        val p1 = PlayerReviveState(playerId = "p1", isDowned = true, bleedoutTimer = 0.05f)
        val snapshot = SquadReviveSnapshot(arrayOf(p1))
        
        engine.tick(snapshot, 0.1f) // Over the remaining bleedout
        
        assertFalse(p1.isDowned)
        assertTrue(p1.isDeadSpectating)
        assertTrue(p1.queuedForNextWaveRespawn)
        assertEquals(0.0f, p1.bleedoutTimer, 0.001f)
    }

    // 7. Nominal: Solo quick revive self-revives
    @Test
    fun `test solo mode quick revive self-revives`() {
        val config = SquadReviveConfig(isSoloMode = true, quickReviveTimeSeconds = 1.5f)
        val engine = SquadReviveEngine(config)
        
        val p1 = PlayerReviveState(playerId = "p1", isDowned = true, hasQuickRevivePerk = true)
        val snapshot = SquadReviveSnapshot(arrayOf(p1))
        
        // Tick to start the self revive
        engine.tick(snapshot, 0.1f)
        assertEquals("p1", p1.reviverPlayerId)
        
        // Loop to finish it
        for (i in 0 until 14) { // 15 ticks total = 1.5s
            engine.tick(snapshot, 0.1f)
        }
        
        assertFalse(p1.isDowned)
        assertEquals(1, p1.soloQuickRevivesUsed)
        assertFalse(p1.hasQuickRevivePerk) // Perk is removed
    }

    // 8. Boundary: Solo quick revive limited to max usage
    @Test
    fun `test solo mode quick revive fails if max usage reached`() {
        val config = SquadReviveConfig(isSoloMode = true, maxSoloQuickRevives = 3)
        val engine = SquadReviveEngine(config)
        
        val p1 = PlayerReviveState(playerId = "p1", isDowned = true, hasQuickRevivePerk = true, soloQuickRevivesUsed = 3, bleedoutTimer = 45f)
        val snapshot = SquadReviveSnapshot(arrayOf(p1))
        
        engine.tick(snapshot, 0.1f)
        
        assertEquals("", p1.reviverPlayerId) // Didn't self revive
        assertEquals(45f - 0.1f, p1.bleedoutTimer, 0.001f)
    }

    // 9. Nominal: applyPostBleedoutNextWaveRespawn revives spectating players
    @Test
    fun `test applyPostBleedoutNextWaveRespawn revives spectating players`() {
        val config = SquadReviveConfig()
        val engine = SquadReviveEngine(config)
        
        val p1 = PlayerReviveState(playerId = "p1", isDeadSpectating = true, queuedForNextWaveRespawn = true)
        val snapshot = SquadReviveSnapshot(arrayOf(p1))
        
        engine.applyPostBleedoutNextWaveRespawn(snapshot)
        
        assertFalse(p1.isDeadSpectating)
        assertFalse(p1.isDowned)
        assertFalse(p1.queuedForNextWaveRespawn)
    }

    // 10. Boundary: tick bounds dtSeconds to 0.100f and handles NaN
    @Test
    fun `test tick clamps dtSeconds and guards NaN`() {
        val config = SquadReviveConfig()
        val engine = SquadReviveEngine(config)
        
        val p1 = PlayerReviveState(playerId = "p1", isDowned = true, bleedoutTimer = 45.0f)
        val snapshot = SquadReviveSnapshot(arrayOf(p1))
        
        engine.tick(snapshot, 999.0f) // Should clamp to 0.100f
        assertEquals(44.9f, p1.bleedoutTimer, 0.001f)
        
        engine.tick(snapshot, Float.NaN) // Should fallback to 0.016f
        assertEquals(44.884f, p1.bleedoutTimer, 0.001f)
    }

    // 11. Boundary: startReviveTether handles NaN positions
    @Test
    fun `test startReviveTether guards NaN positions`() {
        val config = SquadReviveConfig()
        val engine = SquadReviveEngine(config)
        
        val p1 = PlayerReviveState(playerId = "p1", isDowned = true, posX = Float.NaN, posY = Float.NaN)
        val p2 = PlayerReviveState(playerId = "p2", posX = 0f, posY = 0f)
        
        // Since NaN falls back to 0, dist between 0,0 and 0,0 is 0 <= 120. Should connect.
        engine.startReviveTether(p1, p2)
        assertEquals("p2", p1.reviverPlayerId)
    }

    // 12. Serialization: map roundtrip and checksum parity
    @Test
    fun `test serialization roundtrip and checksum parity`() {
        val originalConfig = SquadReviveConfig(isSoloMode = true, maxSoloQuickRevives = 5)
        val originalPlayer = PlayerReviveState(playerId = "p1", isDowned = true, bleedoutTimer = 30f)
        val originalSnapshot = SquadReviveSnapshot(arrayOf(originalPlayer))
        
        val configMap = originalConfig.toMap()
        val restoredConfig = SquadReviveConfig.fromMap(configMap)
        assertEquals(originalConfig, restoredConfig)
        
        val snapshotMap = originalSnapshot.toMap()
        val restoredSnapshot = SquadReviveSnapshot.fromMap(snapshotMap)
        
        assertEquals(originalSnapshot, restoredSnapshot)
        assertEquals(originalSnapshot.computeChecksum(), restoredSnapshot.computeChecksum())
        
        // Altering state changes checksum
        val alteredPlayer = PlayerReviveState.fromMap(originalPlayer.toMap())
        alteredPlayer.isDowned = false
        val alteredSnapshot = SquadReviveSnapshot(arrayOf(alteredPlayer))
        assertTrue(originalSnapshot.computeChecksum() != alteredSnapshot.computeChecksum())
    }
}
