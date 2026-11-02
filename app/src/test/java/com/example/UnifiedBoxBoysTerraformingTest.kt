package com.example

import com.example.game.ModularWeaponGenerator
import com.example.game.ai.director.LocalOllamaMapDirector
import com.example.game.combat.BarrelNode
import com.example.game.combat.BoxheadComboAndPhysicsRegistry
import com.example.game.combat.BoxheadComboState
import com.example.game.economy.SingleCurrencyVendingEngine
import com.example.game.loot.borderlands.EquippedOnlyLoadout
import com.example.game.loot.borderlands.SwapOnSpotLoadoutManager
import com.example.game.relics.PassiveRelicKind
import com.example.game.relics.PassiveRelicStackState
import com.example.game.relics.Ror2PassiveRelicSystem
import com.example.game.world.elona.ChunkMutationType
import com.example.game.world.elona.ElonaSocketFrontierEngine
import com.example.game.world.elona.SparseWorldStateCache
import com.example.network.p2p.OfflineHostTransportKind
import com.example.network.p2p.OfflineMultiTransportP2pBridge
import com.example.network.sync.UniversalAuthoritativeHostEngine
import com.example.ui.hud.SwapCardAndRelicRibbonHud
import com.example.ui.render.VoxelBoxIsometricPipeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedBoxBoysTerraformingTest {

    @Test
    fun `track 1 elona socket frontier generates solvable world receipt and expands doors`() {
        val stateCache = SparseWorldStateCache()
        val initial = ElonaSocketFrontierEngine.generateInitialWorldReceipt(
            seed = 20261102L,
            initialUnlockedRooms = 3
        )
        assertTrue(initial.solvable)
        assertEquals(3, initial.placedNodes.size)
        assertTrue(initial.frontierSockets.isNotEmpty())

        val firstDoor = initial.frontierSockets.first()
        val expanded = ElonaSocketFrontierEngine.unlockFrontierDoorAndExpand(
            currentReceipt = initial,
            frontierId = firstDoor.frontierId,
            stateCache = stateCache
        )
        assertTrue(expanded.solvable)
        assertEquals(4, expanded.placedNodes.size)
        assertTrue(stateCache.hasMutation(firstDoor.targetGridX, firstDoor.targetGridY, 0, 0))
    }

    @Test
    fun `track 2 local ollama map director synthesizes valid 12x12 room component`() {
        val proposal = LocalOllamaMapDirector.parseOrSynthesizeSector(
            rawOllamaJson = null,
            wave = 6,
            biomeTag = "CRYO_HARBOR_PIER",
            seed = 998877L
        )
        assertTrue(LocalOllamaMapDirector.validateRoomRows(proposal.componentDef.rows))
        assertEquals(4, proposal.componentDef.sockets.size)
    }

    @Test
    fun `track 3 boxhead barrel chain reaction and combo streak milestones`() {
        val barrels = listOf(
            BarrelNode(id = 1, x = 100f, y = 100f, blastRadiusPx = 145f),
            BarrelNode(id = 2, x = 210f, y = 100f, blastRadiusPx = 145f),
            BarrelNode(id = 3, x = 310f, y = 100f, blastRadiusPx = 145f)
        )
        val result = BoxheadComboAndPhysicsRegistry.resolveBarrelChainReaction(
            initialBarrelId = 1,
            barrels = barrels,
            playerX = 140f,
            playerY = 100f,
            blastSurfBootsStacks = 2
        )
        assertEquals(listOf(1, 2, 3), result.detonatedBarrelIds)
        assertEquals(0, result.playerSelfDamage)
        assertTrue(result.blastSurfImpulseVx > 0f)

        val (combo25, relicDrops) = BoxheadComboAndPhysicsRegistry.registerKillStreak(
            BoxheadComboState(),
            killsThisTick = 26
        )
        assertEquals(1, relicDrops)
        assertTrue(combo25.currencyMultiplier >= 1.75f)
    }

    @Test
    fun `track 4 and 6 single currency vending benches xp and excludes ror2 relics`() {
        assertTrue(SingleCurrencyVendingEngine.XP_SYSTEM_BENCHED)
        assertFalse(Ror2PassiveRelicSystem.PURCHASABLE_IN_SHOPS)
        assertTrue(PassiveRelicKind.entries.none { it.canBePurchasedAtVending })

        val stock = SingleCurrencyVendingEngine.generateWaveVendingStock(wave = 5, seed = 42L)
        assertTrue(stock.offers.any { it.isItemOfTheDay && it.weaponPayload != null })
        assertTrue(stock.offers.any { it.isItemOfTheDay && it.shieldArmorPayload != null })
    }

    @Test
    fun `track 5 borderlands swap on spot loadout has zero backpack clutter`() {
        assertFalse(SwapOnSpotLoadoutManager.HAS_BACKPACK_STORAGE)
        val w1 = ModularWeaponGenerator.generateProceduralWeapon(seed = 77L, wave = 1)
        val w2 = ModularWeaponGenerator.generateProceduralWeapon(seed = 88L, wave = 8)
        val card = SwapOnSpotLoadoutManager.buildWeaponComparisonCard(w1, w2)
        assertEquals(4, card.statDeltas.size)

        val initialLoadout = EquippedOnlyLoadout(primaryWeapon = w1)
        val (afterSecondSlot, displaced1) = SwapOnSpotLoadoutManager.swapWeaponOnSpot(initialLoadout, w2)
        assertNotNull(afterSecondSlot.secondaryWeapon)
        assertEquals(null, displaced1)
    }

    @Test
    fun `track 7 and 8 mid session drop pod catch up and under 15kb multi transport sync`() {
        val stateCache = SparseWorldStateCache()
        stateCache.recordMutation(1, 0, 5, 5, ChunkMutationType.WINDOW_BARRICADE_TIER, 3, "CRYO_GRID")
        val receipt = ElonaSocketFrontierEngine.generateInitialWorldReceipt(seed = 12345L)

        val dropPod = UniversalAuthoritativeHostEngine.buildMidSessionHotJoinPackage(
            joinerPeerId = "peer_mobile_2",
            currentWave = 11,
            squadLeaderX = 600f,
            squadLeaderY = 600f,
            worldReceipt = receipt,
            stateCache = stateCache
        )
        assertTrue(dropPod.waveScaledStartingCurrency >= 500 + 10 * 650)
        assertEquals(2, dropPod.catchUpRelics.totalRelicCount) // floor(11 / 5) = 2 relics

        val envelope = UniversalAuthoritativeHostEngine.serializeHotJoinWorldEnvelope(
            worldReceipt = receipt,
            stateCache = stateCache,
            currentWave = 11
        )
        assertTrue(envelope.toByteArray().size < ElonaSocketFrontierEngine.MAX_SYNC_PAYLOAD_BYTES)

        val interfaces = OfflineMultiTransportP2pBridge.discoverOfflineHostInterfaces()
        assertTrue(interfaces.any { it.transportKind == OfflineHostTransportKind.USB_DIRECT_BRIDGE })
        assertTrue(interfaces.any { it.transportKind == OfflineHostTransportKind.BLUETOOTH_RFCOMM_BLE })
    }

    @Test
    fun `track 9 and 10 voxel box isometric projection and hud state`() {
        val quad = VoxelBoxIsometricPipeline.projectVoxelBox25D(
            worldX = 200f,
            worldY = 300f,
            part = VoxelBoxIsometricPipeline.BOXBOY_HERO_RIG.first(),
            lightSourceX = 150f,
            lightSourceY = 250f,
            bloomBoost = 0.8f
        )
        assertTrue(quad.extrusionOffsetY > 0f)

        var relics = PassiveRelicStackState()
        relics = Ror2PassiveRelicSystem.collectRelicDrop(relics, PassiveRelicKind.UKULELE_ARC_LIGHTNING, 3)
        val hud = SwapCardAndRelicRibbonHud.buildHudViewState(
            currencyPoints = 4250,
            comboStreak = 30,
            comboMultiplier = 1.75f,
            relicState = relics,
            transport = OfflineHostTransportKind.USB_DIRECT_BRIDGE
        )
        assertFalse(hud.showXpBar)
        assertEquals(1, hud.relicChips.size)
        assertTrue(hud.relicChips.first().stackBadge.contains("x3"))
    }
}
