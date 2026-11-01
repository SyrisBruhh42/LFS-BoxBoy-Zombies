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

    @Test
    fun phase6_lfsHostedServerRoomCodeAnd4PlayerCoopSquad_worksCorrectly() {
        // 1. Verify Crockford Base32 LFS-XXXXXX Room Code generator & normalizer
        val code1 = com.example.network.OasisHostedServerClient.generateRoomCode()
        val code2 = com.example.network.OasisHostedServerClient.generateRoomCode()
        assertTrue("Room code must start with LFS-: $code1", code1.startsWith("LFS-"))
        assertEquals(10, code1.length)
        val crockfordAllowed = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toSet()
        assertTrue(code1.removePrefix("LFS-").all { it in crockfordAllowed })
        assertTrue(code2.removePrefix("LFS-").all { it in crockfordAllowed })
        assertEquals("LFS-011V8K", com.example.network.OasisHostedServerClient.normalizeRoomCode("lfs-oilu8k"))

        // 1b. Verify exponential backoff with jitter (500ms, 1000ms, 2000ms) & Staggered Happy-Eyeballs racing
        assertEquals(550L, com.example.network.OasisHostedServerClient.computeExponentialBackoffWithJitterMs(1, 500L, 50L))
        assertEquals(1050L, com.example.network.OasisHostedServerClient.computeExponentialBackoffWithJitterMs(2, 500L, 50L))
        assertEquals(2050L, com.example.network.OasisHostedServerClient.computeExponentialBackoffWithJitterMs(3, 500L, 50L))

        val payloadJson = com.example.network.OasisHostedServerClient.buildHosterControlPayload(
            roomCode = "LFS-8K4M2P",
            roomTitle = "Bio-Noir Squad",
            hostMode = com.example.data.LfsHostMode.LAUNCH_U2_HOSTING_SERVICE,
            lifecyclePolicy = com.example.data.LfsLifecyclePolicy.ON_DEMAND_WAKE,
            maxPlayers = 4,
            initialStateEnvelope = "BBZ1:TEST"
        )
        assertTrue(payloadJson.contains("\"roomCode\":\"LFS-8K4M2P\""))
        assertTrue(payloadJson.contains("\"room_code\":\"LFS-8K4M2P\""))
        assertTrue(payloadJson.contains("\"action\":\"launch-u2-service\""))

        val racedWinner = kotlinx.coroutines.runBlocking {
            com.example.network.OasisHostedServerClient.raceHappyEyeballsEndpoints(
                candidateUrls = listOf("http://unreachable-1.local", "https://play.lewisfamilysystems.com"),
                staggerMs = 10L
            ) { url ->
                if (url.contains("play.lewisfamilysystems.com")) "WINNER:$url" else null
            }
        }
        assertEquals("WINNER:https://play.lewisfamilysystems.com", racedWinner)


        // 2. Verify 4-Player AI Squad Bots (@willow, @cinder, @mote) & Co-Op Revive Mechanic
        val engine = BoxBoyGameEngine()
        var state = GameSessionState(points = 1000)
        state = engine.fillSquadWithBots(state)
        assertEquals("Expected 3 AI squad bots to fill 4/4 squad", 3, state.coopPeers.size)
        assertTrue(state.coopPeers.any { it.botPersonaId == "willow" })
        assertTrue(state.coopPeers.any { it.botPersonaId == "cinder" })
        assertTrue(state.coopPeers.any { it.botPersonaId == "mote" })

        // Simulate player downed with squad mates nearby -> squad bots should rush and revive player
        state = state.copy(
            hp = 1,
            isPlayerDowned = true,
            playerBleedoutRemainingMs = 30_000L,
            playerReviveProgress = 0.98f,
            coopPeers = state.coopPeers.map { it.copy(x = state.playerX + 20f, y = state.playerY + 20f) }
        )
        state = engine.stepFrame(
            state = state,
            deltaMsRaw = 100L,
            moveXInput = 0f,
            moveYInput = 0f,
            isFiringInput = false,
            isAltBarrelDropInput = false,
            isInteractPressedInput = false,
            coopPeerX = null,
            coopPeerY = null
        )
        assertTrue("Downed player should be revived by nearby @willow/@cinder/@mote squad mate", !state.isPlayerDowned)
        assertTrue("Player HP should be restored on revive", state.hp >= 65)
    }

    @Test
    fun phase7_worldChunkStreamerAndRenderGraphBatcher_worksCorrectly() {
        val map = ProceduralMapEngine.generateMap(12345L, (0..5).toSet())
        val engine = BoxBoyGameEngine()
        val stateWithSquad = engine.fillSquadWithBots(GameSessionState())
        val snap = com.example.game.WorldChunkStreamer.queryAndStreamSquadChunks(
            map = map,
            playerX = 1800f,
            playerY = 1500f,
            coopPeers = stateWithSquad.coopPeers
        )
        assertTrue("Active full-detail chunks should be populated", snap.activeChunkCount > 0)
        assertTrue("Active full-detail slices should be non-empty", snap.activeFullDetailChunks.isNotEmpty())

        val batchFrame = com.example.ui.RenderGraphBatcher.buildBatchFrameInternal(
            chunkSnapshot = snap,
            camMinX = 600f,
            camMaxX = 3000f,
            camMinY = 600f,
            camMaxY = 2400f
        )
        assertEquals(snap.activeChunkCount, batchFrame.activeChunkCount)
        assertTrue("Total batched nodes should be populated in viewport", batchFrame.totalBatchedNodes > 0)
    }

    @Test
    fun phase8_networkHealthServiceAndHighContrastAccessibility_worksCorrectly() {
        // 1. Verify Tailscale CGNAT (100.64.0.0/10) and ULA IPv6 (fd7a:115c:a1e0::) detection
        assertTrue(com.example.network.NetworkHealthService.isTailscaleCgnatIpv4("100.98.42.17"))
        assertTrue(com.example.network.NetworkHealthService.isTailscaleCgnatIpv4("100.64.0.1"))
        assertTrue(!com.example.network.NetworkHealthService.isTailscaleCgnatIpv4("192.168.1.50"))
        assertTrue(com.example.network.NetworkHealthService.isTailscaleUlaIpv6("fd7a:115c:a1e0::1234"))

        // 2. Verify interface evaluation when only public/standard LAN is present vs Tailscale/VPN
        val publicOnly = com.example.network.NetworkHealthService.evaluateInterfaces(
            records = listOf(
                com.example.network.InterfaceProbeRecord(
                    name = "wlan0",
                    isUp = true,
                    isLoopback = false,
                    isPointToPoint = false,
                    ipv4Addresses = listOf("192.168.1.44"),
                    ipv6Addresses = emptyList()
                )
            ),
            androidVpnTransportActive = false,
            manualPrivateOverride = false
        )
        assertTrue(!publicOnly.isPrivateNetworkDetected)
        assertTrue(!publicOnly.shouldShowOasisServerConfig)

        val tailscaleMesh = com.example.network.NetworkHealthService.evaluateInterfaces(
            records = listOf(
                com.example.network.InterfaceProbeRecord(
                    name = "tailscale0",
                    isUp = true,
                    isLoopback = false,
                    isPointToPoint = true,
                    ipv4Addresses = listOf("100.104.22.9"),
                    ipv6Addresses = listOf("fd7a:115c:a1e0::abcd")
                )
            ),
            androidVpnTransportActive = true,
            manualPrivateOverride = false
        )
        assertTrue(tailscaleMesh.isTailscaleActive)
        assertEquals("100.104.22.9", tailscaleMesh.tailscaleIp)
        assertTrue(tailscaleMesh.isPrivateNetworkDetected)
        assertTrue(tailscaleMesh.shouldShowOasisServerConfig)

        // 3. Verify High Contrast & Accessibility state mapping + ThemeConfig WCAG AA/AAA contrast
        val a11yCfg = com.example.viewmodel.ControllerConfigState(
            highContrastMode = true,
            largeTouchTargetsAndText = true,
            colorblindHazardMarkers = true,
            reduceMotionAndFlashes = true
        )
        val themeState = a11yCfg.toAccessibilityThemeState()
        assertTrue(themeState.highContrastMode)
        assertTrue(themeState.largeTouchTargetsAndText)
        assertTrue(themeState.colorblindHazardMarkers)
        assertTrue(themeState.reduceMotionAndFlashes)
        assertTrue(a11yCfg.toKeyMapCode().contains("HC:true"))

        val themeConfig = themeState.toThemeConfig()
        assertTrue("High-contrast ThemeConfig must meet WCAG AA (4.5:1)", themeConfig.meetsWcagAA)
        assertTrue("High-contrast ThemeConfig must meet WCAG AAA (7.0:1)", themeConfig.meetsWcagAAA)
        assertTrue("High-contrast yellow on black contrast ratio should exceed 19:1", themeConfig.primaryContrastRatio >= 19.0)
        assertTrue("Scaled typography bodyLarge should be enlarged when largeTouchTargetsAndText is active",
            themeConfig.scaledTypography.bodyLarge.fontSize.value > com.example.ui.theme.Typography.bodyLarge.fontSize.value
        )
    }

    @Test
    fun phase9_combatAudioEngineWeaponPitchAndFinalFantasyOverhaul_worksCorrectly() {
        val engine = com.example.audio.CombatAudioEngine(context = null)
        // 1. Verify weapon-stat pitch profile calculation scales with baseDamage, fireCooldownMs, pelletCount, isExplosive, and weaponLevel
        val uzi = com.example.data.DEFAULT_WEAPONS.first { it.id == "uzi" }
        val shotgun = com.example.data.DEFAULT_WEAPONS.first { it.id == "shotgun" }
        val railgun = com.example.data.DEFAULT_WEAPONS.first { it.id == "railgun" }

        val uziPitchLv1 = engine.computeWeaponPitchProfile(uzi, weaponLevel = 1)
        val uziPitchLv5 = engine.computeWeaponPitchProfile(uzi, weaponLevel = 5)
        val shotgunPitch = engine.computeWeaponPitchProfile(shotgun, weaponLevel = 1)
        val railgunPitch = engine.computeWeaponPitchProfile(railgun, weaponLevel = 1)

        // Rapid low-caliber UZI should have a significantly higher starting frequency than heavy Shotgun/Railgun
        assertTrue("UZI start frequency should exceed Shotgun start frequency", uziPitchLv1.startFrequencyHz > shotgunPitch.startFrequencyHz)
        assertTrue("UZI start frequency should exceed Railgun start frequency", uziPitchLv1.startFrequencyHz > railgunPitch.startFrequencyHz)

        // Upgrading weapon level increases pitch brilliance and crystalline aether chime mix
        assertTrue("Upgraded Lv.5 weapon should have higher pitch than Lv.1", uziPitchLv5.startFrequencyHz > uziPitchLv1.startFrequencyHz)
        assertTrue("Upgraded Lv.5 weapon should have stronger aether chime harmonic", uziPitchLv5.aetherChimeMix > uziPitchLv1.aetherChimeMix)

        // 2. Verify 16-bit PCM synthesis for gunfire, hit-reg impacts, and explosions
        val gunfirePcm = engine.generateGunfirePcm(uziPitchLv5, pelletCount = uzi.pelletCount)
        val regularImpactPcm = engine.generateImpactPcm(damageDealt = 35, isCritical = false, isKill = false)
        val critKillImpactPcm = engine.generateImpactPcm(damageDealt = 180, isCritical = true, isKill = true)
        val explosionPcm = engine.generateExplosionPcm(blastDamage = 220, isCryoShatter = false)

        assertTrue("Gunfire PCM buffer must not be empty", gunfirePcm.isNotEmpty())
        assertTrue("Regular impact PCM buffer must not be empty", regularImpactPcm.isNotEmpty())
        assertTrue("Crit/Kill impact PCM buffer should be longer than regular hit-reg tick", critKillImpactPcm.size > regularImpactPcm.size)
        assertTrue("Explosion PCM buffer must not be empty", explosionPcm.isNotEmpty())

        // 3. Verify Final Fantasy Crystalline default theme & non-retro scanlines default
        val defaultControls = com.example.viewmodel.ControllerConfigState()
        assertTrue("Retro CRT scanlines must default to false in Final Fantasy Crystalline edition", !defaultControls.crtScanlinesEnabled)
        val defaultThemeConfig = defaultControls.toAccessibilityThemeState().toThemeConfig()
        assertTrue("Standard Final Fantasy Crystalline theme must meet WCAG AA contrast", defaultThemeConfig.meetsWcagAA)

        // 4. Verify DriveSyncManager BoxBoyZombies/Saves JSON game-save envelope & SHA-256 integrity checksum
        val sampleSnapshot = com.example.data.CryptoSyncEngine.GameSaveSnapshot(
            slotName = "Citadel Run Wave 12",
            highestWave = 12,
            totalKills = 340,
            points = 8500,
            unlockedWeaponIds = listOf("pistol", "railgun"),
            weaponLevels = mapOf("pistol" to 2, "railgun" to 4),
            activePerks = listOf("JUGGERNOG"),
            unlockedRoomsCount = 5,
            mapSeed = 19960227L,
            deviceTag = "Pixel-Astral",
            timestampEpochMs = 1762031200000L
        )
        val encrypted = "v1:GCM:AETHER_CIPHERTEXT_PAYLOAD_WAVE12"
        val envelope = com.example.data.DriveSyncManager.createEnvelope(
            snapshot = sampleSnapshot,
            encryptedPayload = encrypted,
            loadoutSummary = "M1911 Lv2, RAIL Lv4",
            fileKind = com.example.data.DriveEnvelopeFileKind.PRIMARY_SLOT
        )
        assertEquals("BoxBoyZombies/Saves", envelope.folderPath)
        assertTrue("SHA-256 checksum must be 64 hex chars", envelope.sha256Checksum.length == 64)

        val serializedJson = com.example.data.DriveSyncManager.serializeEnvelopeToJson(envelope)
        val parsedEnvelope = com.example.data.DriveSyncManager.parseAndVerifyEnvelopeJson(serializedJson).getOrThrow()
        assertEquals("Citadel Run Wave 12", parsedEnvelope.slotName)
        assertEquals(12, parsedEnvelope.highestWave)
        assertEquals("BoxBoyZombies/Saves", parsedEnvelope.folderPath)
        assertTrue("Parsed envelope must pass SHA-256 verification", parsedEnvelope.checksumVerified)

        // Tamper with highestWave in JSON and verify checksumVerified turns false
        val tamperedJson = serializedJson.replace("\"highestWave\": 12", "\"highestWave\": 99")
        val tamperedEnvelope = com.example.data.DriveSyncManager.parseAndVerifyEnvelopeJson(tamperedJson).getOrThrow()
        assertTrue("Tampered envelope must fail SHA-256 verification", !tamperedEnvelope.checksumVerified)
    }
}





