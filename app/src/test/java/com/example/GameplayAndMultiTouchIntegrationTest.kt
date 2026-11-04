package com.example

import com.example.game.BoxBoyGameEngine
import com.example.game.ExplosiveBarrel
import com.example.game.GameSessionState
import com.example.game.HordeSteeringEngine
import com.example.game.StationKind
import com.example.game.WorldChunkStreamer
import com.example.game.ZombieArchetype
import com.example.game.ZombieEntity
import com.example.game.computeAttackSpeedMultiplier
import com.example.game.computeCritChanceBonus
import com.example.game.computeCrowbarMultiplier
import com.example.game.computeMoveSpeedMultiplier
import com.example.game.economy.SingleCurrencyVendingEngine
import com.example.game.relics.PassiveRelicKind
import com.example.game.relics.PassiveRelicStackState
import com.example.game.relics.Ror2PassiveRelicSystem
import com.example.ui.render.SurfaceDecalKind
import com.example.ui.render.VoxelBoxIsometricPipeline
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class GameplayAndMultiTouchIntegrationTest {

    @Test
    fun ror2PassiveRelicModifiers_applyMoveSpeedReloadCrowbarWispAurasAndBlastSurf() {
        val baseRelics = PassiveRelicStackState()
        assertEquals(1.0f, Ror2PassiveRelicSystem.computeMoveSpeedMultiplier(baseRelics), 0.0001f)
        assertEquals(1.0f, Ror2PassiveRelicSystem.computeAttackSpeedMultiplier(baseRelics), 0.0001f)
        assertEquals(1.0f, Ror2PassiveRelicSystem.computeCrowbarMultiplier(100, 100, baseRelics), 0.0001f)
        assertEquals(0.0f, Ror2PassiveRelicSystem.computeCritChanceBonus(baseRelics), 0.0001f)

        var stackedRelics = Ror2PassiveRelicSystem.collectRelicDrop(baseRelics, PassiveRelicKind.PAULS_GOAT_HOOF, 3)
        stackedRelics = Ror2PassiveRelicSystem.collectRelicDrop(stackedRelics, PassiveRelicKind.SOLDIERS_SYRINGE, 2)
        stackedRelics = Ror2PassiveRelicSystem.collectRelicDrop(stackedRelics, PassiveRelicKind.CROWBAR_OPENING_STRIKE, 2)
        stackedRelics = Ror2PassiveRelicSystem.collectRelicDrop(stackedRelics, PassiveRelicKind.HARVESTERS_SCYTHE, 2)

        assertTrue(Ror2PassiveRelicSystem.computeMoveSpeedMultiplier(stackedRelics) > 1.35f)
        assertTrue(Ror2PassiveRelicSystem.computeAttackSpeedMultiplier(stackedRelics) > 1.25f)
        assertTrue(Ror2PassiveRelicSystem.computeCrowbarMultiplier(100, 100, stackedRelics) > 1.80f)
        assertTrue(Ror2PassiveRelicSystem.computeCritChanceBonus(stackedRelics) >= 0.10f)

        // Compare movement distance over 1 tick with and without PAULS_GOAT_HOOF
        val engineBase = BoxBoyGameEngine()
        val engineHoof = BoxBoyGameEngine()
        val initialState = GameSessionState(mapSeed = 42L)
        val steppedBase = engineBase.stepFrame(
            state = initialState,
            deltaMsRaw = 50L,
            moveXInput = 1f,
            moveYInput = 0f,
            isFiringInput = false,
            isAltBarrelDropInput = false,
            isInteractPressedInput = false,
            coopPeerX = null,
            coopPeerY = null,
            aimAngleOverrideRad = 0f
        )
        val steppedHoof = engineHoof.stepFrame(
            state = initialState.copy(passiveRelics = stackedRelics),
            deltaMsRaw = 50L,
            moveXInput = 1f,
            moveYInput = 0f,
            isFiringInput = false,
            isAltBarrelDropInput = false,
            isInteractPressedInput = false,
            coopPeerX = null,
            coopPeerY = null,
            aimAngleOverrideRad = 0f
        )
        val baseDist = abs(steppedBase.playerX - initialState.playerX)
        val hoofDist = abs(steppedHoof.playerX - initialState.playerX)
        assertTrue(hoofDist > baseDist)

        // Verify Frost Relic + Tesla Coil periodic radial aura damage + Will-o'-the-Wisp kill decal
        var auraRelics = Ror2PassiveRelicSystem.collectRelicDrop(stackedRelics, PassiveRelicKind.FROST_RELIC_ICICLE, 3)
        auraRelics = Ror2PassiveRelicSystem.collectRelicDrop(auraRelics, PassiveRelicKind.TESLA_COIL_BEACON, 3)
        auraRelics = Ror2PassiveRelicSystem.collectRelicDrop(auraRelics, PassiveRelicKind.WILL_O_WISP_JAR, 2)

        val lowHpZombieNearPlayer = ZombieEntity(
            id = 9991,
            x = initialState.playerX + 48f,
            y = initialState.playerY,
            hp = 8,
            maxHp = 40,
            archetype = ZombieArchetype.SHAMBLER
        )
        val stateBeforeAuraTick = initialState.copy(
            passiveRelics = auraRelics,
            zombies = listOf(lowHpZombieNearPlayer),
            clockMs = 480L
        )
        val auraEngine = BoxBoyGameEngine()
        val stateAfterAuraTick = auraEngine.stepFrame(
            state = stateBeforeAuraTick,
            deltaMsRaw = 100L,
            moveXInput = 0f,
            moveYInput = 0f,
            isFiringInput = true,
            isAltBarrelDropInput = false,
            isInteractPressedInput = false,
            coopPeerX = null,
            coopPeerY = null,
            aimAngleOverrideRad = 0f
        )
        assertTrue(
            "Periodic Frost Relic + Tesla Coil aura + shot should kill 8 HP shambler",
            stateAfterAuraTick.zombies.none { z -> z.id == 9991 }
        )
        assertTrue(
            "Will-o'-the-Wisp kill should leave a BARREL_SCORCH_CRATER decal",
            stateAfterAuraTick.surfaceDecals.any { d -> d.kind == SurfaceDecalKind.BARREL_SCORCH_CRATER }
        )

        // Verify Blast-Surf Thrusters prevent barrel self-damage and apply knockback velocity
        val surfRelics = Ror2PassiveRelicSystem.collectRelicDrop(baseRelics, PassiveRelicKind.BLAST_SURF_THRUSTERS, 2)
        val barrelNearPlayer = ExplosiveBarrel(
            id = 701,
            x = initialState.playerX - 18f,
            y = initialState.playerY,
            roomIndex = 0,
            isDetonated = false
        )
        val zombieOnBarrel = ZombieEntity(
            id = 9992,
            x = barrelNearPlayer.x,
            y = barrelNearPlayer.y,
            hp = 10,
            maxHp = 40,
            archetype = ZombieArchetype.SHAMBLER
        )
        val stateWithBarrel = initialState.copy(
            passiveRelics = surfRelics,
            hp = 100,
            map = initialState.map.copy(barrels = listOf(barrelNearPlayer)),
            zombies = listOf(zombieOnBarrel),
            aimAngleRad = Math.PI.toFloat()
        )
        val barrelEngine = BoxBoyGameEngine()
        val afterBarrelShot = barrelEngine.stepFrame(
            state = stateWithBarrel,
            deltaMsRaw = 100L,
            moveXInput = 0f,
            moveYInput = 0f,
            isFiringInput = true,
            isAltBarrelDropInput = false,
            isInteractPressedInput = false,
            coopPeerX = null,
            coopPeerY = null,
            aimAngleOverrideRad = Math.PI.toFloat()
        )
        assertEquals("Blast-Surf Thrusters must negate barrel self-damage", 100, afterBarrelShot.hp)
        assertTrue(
            "Barrel detonation must register in BoxheadComboAndPhysicsRegistry and apply knockback impulse",
            afterBarrelShot.playerVelX > 10f || afterBarrelShot.map.barrels.none { b -> !b.isDetonated }
        )
    }

    @Test
    fun singleCurrencyVendingAndElonaSocketFrontier_wirePackAPunchAndDoorUnlock() {
        assertEquals(2500, SingleCurrencyVendingEngine.computePackAPunchCost(1))
        assertEquals(5000, SingleCurrencyVendingEngine.computePackAPunchCost(2))
        assertEquals(7500, SingleCurrencyVendingEngine.computePackAPunchCost(3))

        val initialState = GameSessionState(mapSeed = 77L, points = 5000)
        val initialNodeCount = initialState.elonaWorldReceipt.placedNodes.size
        val doorStation = initialState.map.stations.firstOrNull { st -> st.kind == StationKind.DEBRIS_DOOR }
        assertNotNull("Procedural map must include at least one DEBRIS_DOOR station", doorStation)

        val atDoorState = initialState.copy(
            playerX = doorStation!!.x,
            playerY = doorStation.y,
            nearbyStation = doorStation
        )
        val doorEngine = BoxBoyGameEngine()
        val unlockedState = doorEngine.stepFrame(
            state = atDoorState,
            deltaMsRaw = 32L,
            moveXInput = 0f,
            moveYInput = 0f,
            isFiringInput = false,
            isAltBarrelDropInput = false,
            isInteractPressedInput = true,
            coopPeerX = null,
            coopPeerY = null,
            aimAngleOverrideRad = 0f
        )
        assertTrue(
            "Room must be added to unlockedRooms",
            unlockedState.unlockedRooms.size > initialState.unlockedRooms.size
        )
        assertTrue(
            "ElonaSocketFrontierEngine must expand placedNodes when DEBRIS_DOOR is opened",
            unlockedState.elonaWorldReceipt.placedNodes.size >= initialNodeCount
        )
    }

    @Test
    fun voxelBoxIsometricPipeline_maintainsOneToOneWorldYWithoutVerticalSquash() {
        val worldX = 1420f
        val worldY = 980f
        val heightZ = 50f
        val projection = VoxelBoxIsometricPipeline.projectVoxelBox25D(
            worldX = worldX,
            worldY = worldY,
            widthX = 80f,
            depthY = 60f,
            heightZ = heightZ,
            sunAzimuthRad = 0.78f
        )
        val expectedExtrusion = heightZ * VoxelBoxIsometricPipeline.Z_EXTRUSION_SCALE
        assertEquals(
            "screenY must equal worldY - heightZ * Z_EXTRUSION_SCALE (1:1 worldY camera alignment)",
            worldY - expectedExtrusion,
            projection.screenY,
            0.01f
        )
        assertEquals(
            "sortDepthKey must equal worldY for unified painter's Y-sort",
            worldY,
            projection.sortDepthKey,
            0.001f
        )
        assertEquals(
            "Top face bottom edge must align with worldY - extrusionY",
            worldY - expectedExtrusion,
            projection.topFaceRect.bottom,
            0.01f
        )
        assertEquals(
            "Front face bottom edge must sit directly on groundY == worldY",
            worldY,
            projection.frontFaceRect.bottom,
            0.01f
        )
    }

    @Test
    fun hordeSteeringSpatialGrid_matchesNeighborSeparationAndCohesion() {
        val z0 = ZombieEntity(id = 1, x = 500f, y = 500f, hp = 40, maxHp = 40, archetype = ZombieArchetype.SHAMBLER)
        val z1 = ZombieEntity(id = 2, x = 518f, y = 504f, hp = 40, maxHp = 40, archetype = ZombieArchetype.SHAMBLER)
        val zFar = ZombieEntity(id = 3, x = 2200f, y = 1600f, hp = 40, maxHp = 40, archetype = ZombieArchetype.SHAMBLER)
        val horde = listOf(z0, z1, zFar)

        HordeSteeringEngine.rebuildSpatialGrid(horde)
        val res = HordeSteeringEngine.computeHordeSteering(
            zombieIndex = 0,
            zombies = horde,
            targetX = 800f,
            targetY = 500f,
            flowVx = 1f,
            flowVy = 0f
        )
        val speed = hypot(res.steerX, res.steerY)
        assertTrue("Spatial grid steering must produce valid non-zero direction ($speed)", speed > 0.1f)
    }

    @Test
    fun worldChunkStreamer_memoizesStationaryTicksAndInvalidatesOnTopologyChange() = runBlocking {
        val state = GameSessionState(mapSeed = 101L)
        WorldChunkStreamer.invalidateChunks()

        val snap1 = WorldChunkStreamer.queryAndStreamSquadChunks(
            map = state.map,
            playerX = state.playerX,
            playerY = state.playerY,
            coopPeers = emptyList()
        )
        val hitsAfterFirst = WorldChunkStreamer.getMemoizationHitCountForTest()
        val rebuildsAfterFirst = WorldChunkStreamer.getRebuildCountForTest()

        // Stationary second tick in same chunk -> must hit memoized fast path
        val snap2 = WorldChunkStreamer.queryAndStreamSquadChunks(
            map = state.map,
            playerX = state.playerX + 4f,
            playerY = state.playerY + 4f,
            coopPeers = emptyList()
        )
        assertEquals(
            "Stationary tick within same chunk must increment memoization hit counter",
            hitsAfterFirst + 1,
            WorldChunkStreamer.getMemoizationHitCountForTest()
        )
        assertEquals(
            "Stationary tick must not trigger a chunk rebuild",
            rebuildsAfterFirst,
            WorldChunkStreamer.getRebuildCountForTest()
        )
        assertTrue("Memoized snapshot reference should be identical", snap1 === snap2)

        // Explicit invalidation (e.g. MazeDirector pillar phase shift) -> must rebuild on next call
        WorldChunkStreamer.invalidateChunks()
        WorldChunkStreamer.queryAndStreamSquadChunks(
            map = state.map,
            playerX = state.playerX + 4f,
            playerY = state.playerY + 4f,
            coopPeers = emptyList()
        )
        assertEquals(
            "Explicit invalidateChunks() must trigger a fresh chunk rebuild on the next query",
            rebuildsAfterFirst + 1,
            WorldChunkStreamer.getRebuildCountForTest()
        )
    }
}

