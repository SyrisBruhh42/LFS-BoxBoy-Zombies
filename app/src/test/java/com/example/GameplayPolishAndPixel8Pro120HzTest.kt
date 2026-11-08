package com.example

import com.example.audio.CombatAudioEngine
import com.example.data.CryptoSyncEngine
import com.example.data.DEFAULT_WEAPONS
import com.example.game.BoxBoyGameEngine
import com.example.game.BoxBoysLiveSubsystemBridge
import com.example.game.ExplosiveBarrel
import com.example.game.GameSessionState
import com.example.game.HordeSteeringEngine
import com.example.game.InteractiveStation
import com.example.game.LaserTelegraph
import com.example.game.ModularWeaponGenerator
import com.example.game.OfflineTacticalAiEngine
import com.example.game.StationKind
import com.example.game.TacticalCombatCoordinator
import com.example.game.WeaponUpgradeProgression
import com.example.game.WorldChunkStreamer
import com.example.game.ZombieArchetype
import com.example.game.ZombieEntity
import com.example.game.ai.director.LocalOllamaMapDirector
import com.example.game.relics.PassiveRelicKind
import com.example.game.relics.PassiveRelicStackState
import com.example.game.relics.Ror2PassiveRelicSystem
import com.example.game.world.elona.ElonaSocketFrontierEngine
import com.example.game.world.elona.SparseWorldStateCache
import com.example.input.ControllerManager
import com.example.ui.BioNoirSpriteAtlasRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.hypot

class GameplayPolishAndPixel8Pro120HzTest {

    @Test
    fun verify4kInkedAtlasesAndMapsBundledAndDecoded() {
        val assetsRoot = sequenceOf(
            File("src/main/assets"),
            File("app/src/main/assets")
        ).firstOrNull { it.exists() }
        assertNotNull("Expected assets directory to exist", assetsRoot)
        val root = assetsRoot!!

        val requiredAtlases = listOf(
            "inked-4k/hero_tuxedo_atlas.png",
            "inked-4k/zombie_suit_atlas.png",
            "inked-4k/wolf_dire_atlas.png",
            "inked-4k/props_wasteland_atlas.png"
        )
        for (rel in requiredAtlases) {
            val f = File(root, rel)
            assertTrue("Expected bundled 4K atlas $rel", f.exists() && f.length() > 1024L)
            val img = javax.imageio.ImageIO.read(f)
            assertNotNull("Expected valid PNG image for $rel", img)
            assertTrue("Expected non-empty atlas dimensions for $rel", img.width >= 256 && img.height >= 256)
        }

        val requiredMaps = listOf(
            "maps/boxhead_2play_classic_rooms.boxboys.json",
            "maps/elona_12x12_vault_template.boxboys.json",
            "maps/sunvale_desert_bazaar_25d.boxboys.json"
        )
        for (rel in requiredMaps) {
            val f = File(root, rel)
            assertTrue("Expected bundled map $rel", f.exists() && f.length() > 64L)
        }

        val loadedFromContext = BioNoirSpriteAtlasRegistry.ensureInitialized(null)
        assertFalse("Expected null context in headless JVM test to use procedural fallback without throwing", loadedFromContext)
    }

    @Test
    fun verifyBug01HarvestersScytheCritProbabilityAndBug06LiveBridge() {
        val emptyRelics = PassiveRelicStackState()
        assertEquals(0, emptyRelics.critHealAmount)

        val twoScythes = Ror2PassiveRelicSystem.collectRelicDrop(
            emptyRelics,
            PassiveRelicKind.HARVESTERS_SCYTHE,
            2
        )
        assertEquals(2, twoScythes.countOf(PassiveRelicKind.HARVESTERS_SCYTHE))
        assertEquals(12, twoScythes.critHealAmount)

        val engine = BoxBoyGameEngine()
        val initial = GameSessionState()
        val stepped = engine.stepFrame(
            state = initial,
            deltaMsRaw = 8L,
            moveXInput = 0f,
            moveYInput = 0f,
            isFiringInput = false,
            isAltBarrelDropInput = false,
            isInteractPressedInput = false,
            coopPeerX = null,
            coopPeerY = null
        )
        assertNotNull("Expected BUG-06 liveSubsystemSummary to be populated on tick 1", stepped.liveSubsystemSummary)
        assertNotNull("Expected engine.lastLiveSubsystemSummary to be populated", engine.lastLiveSubsystemSummary)
        val summary = BoxBoysLiveSubsystemBridge.stepLiveSubsystems()
        assertTrue(summary.totalTrackModulesEvaluated > 0)
    }

    @Test
    fun verifyBug02AndBug03SecondaryAndBarrelKillsAwardComboPointsAndDecals() {
        val engine = BoxBoyGameEngine()
        val baseState = GameSessionState()
        val relicsWithFrostAndWisp = Ror2PassiveRelicSystem.collectRelicDrop(
            Ror2PassiveRelicSystem.collectRelicDrop(
                PassiveRelicStackState(),
                PassiveRelicKind.FROST_RELIC_ICICLE,
                3
            ),
            PassiveRelicKind.WILL_O_WISP_JAR,
            1
        )
        val lowHpZombie = ZombieEntity(
            id = 501,
            archetype = ZombieArchetype.SHAMBLER,
            x = baseState.playerX + 18f,
            y = baseState.playerY,
            hp = 4,
            maxHp = 80
        )
        val stateWithAuraKill = baseState.copy(
            zombies = listOf(lowHpZombie),
            passiveRelics = relicsWithFrostAndWisp,
            points = 500,
            totalKills = 0,
            comboCount = 0
        )

        val afterAuraTick = engine.stepFrame(
            state = stateWithAuraKill,
            deltaMsRaw = 16L,
            moveXInput = 0f,
            moveYInput = 0f,
            isFiringInput = false,
            isAltBarrelDropInput = false,
            isInteractPressedInput = false,
            coopPeerX = null,
            coopPeerY = null
        )
        assertTrue("Expected Frost Relic aura to kill low-HP zombie", afterAuraTick.zombies.isEmpty())
        assertEquals(1, afterAuraTick.totalKills)
        assertEquals(1, afterAuraTick.comboCount)
        assertTrue("Expected points to increase from secondary aura kill", afterAuraTick.points > 500)
        assertTrue("Expected ichor & wisp surface decals from secondary kill", afterAuraTick.surfaceDecals.size >= 2)
    }

    @Test
    fun verifyBug04PackAPunchMagCapacityAndBug05AnvilActiveSlotTempering() {
        val engine = BoxBoyGameEngine()
        val baseState = GameSessionState()
        val papStation = InteractiveStation(
            id = "9001",
            kind = StationKind.PACK_A_PUNCH,
            label = "Pack-a-Punch",
            x = baseState.playerX,
            y = baseState.playerY,
            cost = 500,
            roomIndex = 0
        )
        val stateAtPap = baseState.copy(
            points = 5000,
            map = baseState.map.copy(stations = baseState.map.stations + papStation)
        )
        val afterPap = engine.stepFrame(
            state = stateAtPap,
            deltaMsRaw = 16L,
            moveXInput = 0f,
            moveYInput = 0f,
            isFiringInput = false,
            isAltBarrelDropInput = false,
            isInteractPressedInput = true,
            coopPeerX = null,
            coopPeerY = null
        )
        assertEquals(2, afterPap.currentWeaponLevel)
        val activeMod = afterPap.activeModularWeaponForCurrentSlot
        val expectedMag = WeaponUpgradeProgression.calculateEffectiveMagSize(
            baseMag = DEFAULT_WEAPONS.first().magazineSize,
            upgradeLevel = 2,
            magazineBonus = activeMod.magazine.magSizeBonus,
            anvilTier = activeMod.anvilUpgradeTier
        )
        assertEquals(expectedMag, afterPap.currentMagAmmo["pistol"])

        val uziMod = ModularWeaponGenerator.createStarterModularWeapon("uzi")
        val anvilStation = InteractiveStation(
            id = "9002",
            kind = StationKind.UPGRADE_ANVIL,
            label = "Upgrade Anvil",
            x = baseState.playerX,
            y = baseState.playerY,
            cost = 500,
            roomIndex = 0
        )
        val stateAtAnvil = baseState.copy(
            points = 5000,
            unlockedWeaponIds = listOf("pistol", "uzi"),
            currentWeaponIndex = 1,
            modularWeaponInventory = mapOf(
                "pistol" to ModularWeaponGenerator.createStarterModularWeapon("pistol"),
                "uzi" to uziMod
            ),
            map = baseState.map.copy(stations = listOf(anvilStation))
        )
        val anvilEngine = BoxBoyGameEngine()
        val afterAnvil = anvilEngine.stepFrame(
            state = stateAtAnvil,
            deltaMsRaw = 16L,
            moveXInput = 0f,
            moveYInput = 0f,
            isFiringInput = false,
            isAltBarrelDropInput = false,
            isInteractPressedInput = true,
            coopPeerX = null,
            coopPeerY = null
        )
        assertEquals(1, afterAnvil.modularWeaponInventory["uzi"]?.anvilUpgradeTier)
        assertEquals(1, afterAnvil.equippedModularWeapon.anvilUpgradeTier)
    }

    @Test
    fun verifyPixel8ProInputDeadzoneAndHapticPrimitives() {
        ControllerManager.updateDeadzone(0.14f)
        val nanFiltered = ControllerManager.filterDpadVector(Float.NaN, Float.POSITIVE_INFINITY)
        assertEquals(0f, nanFiltered.first, 0.0001f)
        assertEquals(0f, nanFiltered.second, 0.0001f)

        val insideDeadzone = ControllerManager.filterDpadVector(0.05f, -0.05f)
        assertEquals(0f, insideDeadzone.first, 0.0001f)
        assertEquals(0f, insideDeadzone.second, 0.0001f)

        val outsideDeadzone = ControllerManager.filterDpadVector(0.6f, 0.8f)
        assertTrue(hypot(outsideDeadzone.first, outsideDeadzone.second) in 0.99f..1.0001f)
        ControllerManager.resetToDefault()

        val audioEngine = CombatAudioEngine(null)
        val (_, lightScale) = audioEngine.selectPixel8ProHapticPrimitive(18L, 120)
        assertTrue(lightScale in 0.25f..1.0f)
        val (_, heavyScale) = audioEngine.selectPixel8ProHapticPrimitive(50L, 220)
        assertTrue(heavyScale in 0.45f..1.0f)
    }

    @Test
    fun verifyDefectFixesAcrossSteeringDirectorElonaAndCrypto() {
        // D00-7baa77a1: Zero-distance zombies separate cleanly
        val z1 = ZombieEntity(1, ZombieArchetype.SHAMBLER, 200f, 200f, 100, 100)
        val z2 = ZombieEntity(2, ZombieArchetype.SHAMBLER, 200f, 200f, 100, 100)
        val s1 = HordeSteeringEngine.computeHordeSteering(0, listOf(z1, z2), 400f, 200f, 1f, 0f).separationVector
        val s2 = HordeSteeringEngine.computeHordeSteering(1, listOf(z1, z2), 400f, 200f, 1f, 0f).separationVector
        assertTrue("Expected non-zero separation for overlapping zombies", hypot(s1.first, s1.second) > 0.1f)
        assertTrue("Expected opposing separation vectors", s1.first * s2.first + s1.second * s2.second < 0f)

        // D00-6fcbc672 & D00-76424682: LocalOllamaMapDirector JSON braces & connectivity validation
        val validRows = listOf(
            "##W##DD##W##",
            "#..........#",
            "#..........#",
            "#..........#",
            "#..........#",
            "D..........D",
            "D..........D",
            "#..........#",
            "#..........#",
            "#..........#",
            "#..........#",
            "##W##DD##W##"
        )
        assertTrue(LocalOllamaMapDirector.validateRoomRows(validRows))
        val blockedDoorRows = listOf(
            "##W##DD##W##",
            "############",
            "#..........#",
            "#..........#",
            "#..........#",
            "D#........#D",
            "D#........#D",
            "#..........#",
            "#..........#",
            "#..........#",
            "############",
            "##W##DD##W##"
        )
        assertFalse("Blocked border doors must fail validateRoomRows", LocalOllamaMapDirector.validateRoomRows(blockedDoorRows))

        // D00-b5870b61: Unknown frontier ID does not expand arbitrary frontier
        val receipt = ElonaSocketFrontierEngine.generateInitialWorldReceipt(42L)
        val cache = SparseWorldStateCache()
        val unchanged = ElonaSocketFrontierEngine.unlockFrontierDoorAndExpand(receipt, "non_existent_frontier_id", cache)
        assertEquals(receipt.placedNodes.size, unchanged.placedNodes.size)

        // D00-605c2f3a: Telegraph firing at rem <= 0L
        val stalker = ZombieEntity(99, ZombieArchetype.CALCIFIED_STALKER, 195f, 100f, 80, 80)
        val telegraphs = mutableListOf(
            LaserTelegraph(
                sourceZombieId = 99,
                startX = 195f,
                startY = 100f,
                endX = 300f,
                endY = 100f,
                aimAngleRad = 0f,
                chargeRemainingMs = 10L
            )
        )
        val (dmg, _) = TacticalCombatCoordinator.stepPhase3Combat(
            deltaMs = 16L,
            dtSec = 0.016f,
            playerX = 200f,
            playerY = 100f,
            zombies = mutableListOf(stalker),
            telegraphs = telegraphs,
            enemyProjectiles = mutableListOf(),
            elementalPuddles = mutableListOf(),
            fortifications = mutableListOf(),
            tracers = mutableListOf(),
            particles = mutableListOf(),
            banners = mutableListOf(),
            nextEntityIdProvider = { 1 }
        )
        assertTrue("Expected laser telegraph to deal damage when chargeRemainingMs reaches 0", dmg > 0)
        assertTrue("Expected expired telegraph to be removed after firing", telegraphs.isEmpty())

        // D00-6547deb1: Deterministic catch-up relics
        Ror2PassiveRelicSystem.resetCatchUpTracking()
        val catchUpWave5 = Ror2PassiveRelicSystem.rollMidSessionCatchUpRelics(wave = 5, seed = 777L)
        val catchUpWave5Repeat = Ror2PassiveRelicSystem.rollMidSessionCatchUpRelics(wave = 5, seed = 777L)
        assertEquals(1, catchUpWave5.totalRelicCount)
        assertEquals(0, catchUpWave5Repeat.totalRelicCount)

        // D00-6920076e: OfflineTacticalAiEngine Long.MIN_VALUE safety
        val directive = OfflineTacticalAiEngine.synthesizeOfflineDirective(
            "Cryo vault defense",
            GameSessionState(mapSeed = Long.MIN_VALUE, wave = 4)
        )
        assertNotNull(directive)
        assertTrue(directive.operationTitle.isNotBlank())

        // D01-3a81f793: CryptoSyncEngine metadata HMAC binding
        val snap = CryptoSyncEngine.GameSaveSnapshot(
            slotName = "Test",
            highestWave = 5,
            totalKills = 42,
            points = 1200,
            unlockedWeaponIds = listOf("pistol"),
            weaponLevels = mapOf("pistol" to 1),
            activePerks = emptyList(),
            mapSeed = 42L,
            unlockedRoomsCount = 1,
            deviceTag = "Pixel8Pro",
            timestampEpochMs = 1700000000000L
        )
        val sealedObj = CryptoSyncEngine.sealSixGateAegisEnvelopeV1(
            slotKey = "slot_02",
            snapshot = snap,
            generation = 5,
            roomId = "room_alpha"
        )
        val sealed = sealedObj.toString()
        val unsealed = CryptoSyncEngine.unsealSixGateAegisEnvelopeV1(sealed).getOrThrow()
        assertEquals(1200, unsealed.points)
        val tampered = sealed
            .replace("\"slot_key\":\"slot_02\"", "\"slot_key\":\"slot_03\"")
            .replace("\"slotKey\":\"slot_02\"", "\"slotKey\":\"slot_03\"")
        val tamperedResult = CryptoSyncEngine.unsealSixGateAegisEnvelopeV1(tampered)
        assertFalse("Expected metadata-tampered envelope to fail HMAC verification", tamperedResult.isSuccess)

        // D01-7e5a627a: WorldChunkStreamer check
        val st = GameSessionState()
        WorldChunkStreamer.queryAndStreamSquadChunks(st.map, st.playerX, st.playerY)
        val walkable = WorldChunkStreamer.isWalkableInChunkedWorld(st.playerX, st.playerY, st.map, st.unlockedRooms)
        assertTrue(walkable)
    }
}
