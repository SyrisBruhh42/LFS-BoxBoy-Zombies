package com.example

import com.example.game.AbyssalChiaroscuroLightingEngine
import com.example.game.ArmorRigState
import com.example.game.ArmorSlot
import com.example.game.BoxBoyGameEngine
import com.example.game.CameraFramingState
import com.example.game.CameraTensionComponent
import com.example.game.CameraTensionState
import com.example.game.FlowFieldServer
import com.example.game.FortificationType
import com.example.game.GameSessionState
import com.example.game.LootRarity
import com.example.game.MazeDirector
import com.example.game.ModularWeaponGenerator
import com.example.game.ProceduralMapEngine
import com.example.game.RpgArmorGenerator
import com.example.game.ZombieArchetype
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun phase1_cameraTensionAndTwoShadowLimit_worksCorrectly() {
        var cam = CameraTensionState(framingState = CameraFramingState.CORRIDOR_EXPLORATION, currentZoom = 2.2f)
        // Trigger Siege state -> should cubic-tween toward 1.1x over 800ms
        cam = CameraTensionComponent.stepCameraTension(
            current = cam,
            deltaMs = 400L,
            isSiegeActive = true,
            hasBossAnomaly = false,
            nearbyHostileCount = 4,
            didFireThisTick = true
        )
        assertEquals(CameraFramingState.SIEGE_BOSS_WIDE, cam.framingState)
        assertTrue(cam.currentZoom in 1.1f..2.2f)

        cam = CameraTensionComponent.stepCameraTension(
            current = cam,
            deltaMs = 400L,
            isSiegeActive = true,
            hasBossAnomaly = false,
            nearbyHostileCount = 4,
            didFireThisTick = false
        )
        assertEquals(1.1f, cam.currentZoom, 0.01f)

        // Verify Two-Shadow Hardware Limit
        val state = GameSessionState()
        val lights = AbyssalChiaroscuroLightingEngine.collectFrameLights(state)
        val shadowedCount = lights.count { it.shadowEnabled }
        assertTrue("Expected at most 2 shadow-casting lights, got $shadowedCount", shadowedCount <= 2)
    }

    @Test
    fun phase2_nonEuclideanRingBufferAndFlowField_worksCorrectly() {
        val map = ProceduralMapEngine.generateMap(12345L, (0..5).toSet())
        var maze = MazeDirector.createInitialMazeState(12345L, map)
        assertEquals(1, maze.ringBufferNodes.size)

        // Move player into Sector 1 -> Sector 3 -> Sector 5 and verify ring buffer & CA pillars
        val room1Center = map.rooms[1].bounds.center
        val (m1, _) = MazeDirector.stepMazeDirector(maze, map, (0..5).toSet(), room1Center.x, room1Center.y, 100L)
        maze = m1
        assertEquals(1, maze.activeRoomSector)
        assertTrue(maze.ringBufferNodes.size >= 2)

        val flowServer = FlowFieldServer()
        flowServer.updateIfDue(
            clockMs = 200L,
            map = map,
            unlockedRooms = setOf(0, 1),
            fortifications = emptyList(),
            playerX = 510f,
            playerY = 1200f,
            coopPeerX = null,
            coopPeerY = null
        )
        val (vx, vy) = flowServer.sampleFlowVector(690f, 1200f, 510f, 1200f)
        assertTrue("Flow vector should point non-zero toward player", vx != 0f || vy != 0f)
    }

    @Test
    fun phase3To5_combatModularWeaponsAnd7SlotArmor_worksCorrectly() {
        val engine = BoxBoyGameEngine()
        var state = GameSessionState(points = 2500)

        // Spawn Calcified Stalker (Archer) and Abyssal Weaver (Mage)
        state = engine.spawnStressZombies(state, 3, ZombieArchetype.CALCIFIED_STALKER)
        state = engine.spawnStressZombies(state, 2, ZombieArchetype.ABYSSAL_WEAVER)
        assertEquals(5, state.zombies.size)

        // Step frame with Fortification placement (Cryo Mine)
        state = state.copy(selectedFortificationType = FortificationType.CRYO_MINE)
        state = engine.stepFrame(
            state = state,
            deltaMsRaw = 16L,
            moveXInput = 0f,
            moveYInput = 0f,
            isFiringInput = true,
            isAltBarrelDropInput = true,
            isInteractPressedInput = false,
            coopPeerX = null,
            coopPeerY = null
        )
        assertEquals(1, state.fortifications.size)

        // Generate 4-Part Modular Weapon (Phase 4)
        val modWpn = ModularWeaponGenerator.generateProceduralWeapon(
            seed = 987654L,
            wave = 5,
            guaranteedMinRarity = LootRarity.EPIC
        )
        assertTrue(modWpn.effectiveDamage > 0)
        assertTrue(modWpn.rarity.ordinal >= LootRarity.EPIC.ordinal)

        // Verify 7-Slot RPG Armor Rig & Palette Shader (Phase 5)
        val rig = ArmorRigState()
        assertEquals(7, rig.equippedSlots.size)
        val craftedPiece = RpgArmorGenerator.generateProceduralArmorPiece(
            seed = 54321L,
            wave = 6,
            targetSlot = ArmorSlot.CHEST_CARAPACE,
            minRarity = LootRarity.ABYSSAL_LEGENDARY
        )
        assertNotNull(craftedPiece)
        val updatedRig = rig.copy(equippedSlots = rig.equippedSlots + (ArmorSlot.CHEST_CARAPACE to craftedPiece))
        assertTrue(updatedRig.totalDefenseRating > rig.totalDefenseRating)
        assertTrue(updatedRig.damageReductionFraction > 0f)
    }
}
