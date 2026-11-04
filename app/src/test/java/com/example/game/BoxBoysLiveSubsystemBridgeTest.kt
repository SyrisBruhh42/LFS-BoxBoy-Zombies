package com.example.game

import com.example.game.ai.director.modules.SquadSynergyAnalyzer
import com.example.game.combat.modules.Aabb
import com.example.game.combat.modules.AabbBarricadePenetration
import com.example.game.combat.modules.ArmoredJuggernautCombatLogic
import com.example.game.combat.modules.BoxheadComboStreakTracker
import com.example.game.combat.modules.CryoBarrelFreezeNova
import com.example.game.combat.modules.DashSlashKatanaTech
import com.example.game.contracts.LiveSubsystemTickInput
import com.example.game.economy.SingleCurrencyVendingEngine
import com.example.game.economy.vendors.MarcusWeaponsVendor
import com.example.game.economy.vendors.SingleCurrencyLedger
import com.example.game.economy.vendors.TorgueAmmoOrdnanceVendor
import com.example.game.economy.vendors.WindowBarricadeTier3CryoGrid
import com.example.game.economy.vendors.CryoGridState
import com.example.game.relics.PassiveRelicKind
import com.example.game.relics.PassiveRelicStackState
import com.example.game.relics.Ror2PassiveRelicSystem
import com.example.game.relics.items.Relic22BackupMagazineClipEngine
import com.example.game.world.elona.ElonaSocketFrontierEngine
import com.example.network.OasisHostedServerClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoxBoysLiveSubsystemBridgeTest {

    @Test
    fun `test all 25 elona vault blueprints and hazard ticks are wired and deterministic`() {
        val blueprints = BoxBoysLiveSubsystemBridge.buildAll25ElonaVaultBlueprints()
        assertEquals(25, blueprints.size)
        assertEquals(25, ElonaSocketFrontierEngine.DEFAULT_VAULT_LIBRARY.size)
        assertTrue(ElonaSocketFrontierEngine.DEFAULT_COMPONENT_CATALOG.size >= 28)

        for (bp in blueprints) {
            assertEquals(12, bp.componentDef.rows.size)
            assertTrue(bp.sockets.isNotEmpty())
            assertTrue(bp.baseThreatRating > 0.0)
            assertNotEquals(0L, bp.checksum)
            val scaled = bp.scaleThreatForWave(wave = 5, playersInVault = 3)
            assertTrue(scaled > bp.baseThreatRating)
        }

        val hazards = BoxBoysLiveSubsystemBridge.evaluateAll25ElonaVaultHazards(tick = 60L, playersInVault = 2, wave = 4)
        assertEquals(25, hazards.size)
        for ((_, metrics) in hazards) {
            assertTrue(metrics.isNotEmpty())
            assertTrue(metrics.values.all { !it.isNaN() && it.isFinite() })
        }
    }

    @Test
    fun `test all 13 ai director modules evaluate cleanly with non-NaN metrics`() {
        val input = LiveSubsystemTickInput(tick = 15L, wave = 6, seed = 98765L)
        val metrics = BoxBoysLiveSubsystemBridge.evaluateAiDirectorModules(input)
        assertEquals(13, metrics.size)
        assertTrue(metrics.values.all { !it.isNaN() && it.isFinite() && it > 0.0 })
    }

    @Test
    fun `test all 25 combat and barricade modules evaluate cleanly`() {
        val input = LiveSubsystemTickInput(tick = 30L, wave = 4, seed = 112233L)
        val metrics = BoxBoysLiveSubsystemBridge.evaluateCombatAndBarricadeModules(input)
        assertEquals(25, metrics.size)
        assertTrue(metrics.values.all { !it.isNaN() && it.isFinite() && it > 0.0 })
    }

    @Test
    fun `test all 25 economy and vendor modules evaluate cleanly and integrate with SingleCurrencyVendingEngine`() {
        val input = LiveSubsystemTickInput(tick = 45L, wave = 7, currencyPoints = 3800, seed = 445566L)
        val metrics = BoxBoysLiveSubsystemBridge.evaluateEconomyAndVendors(input)
        assertEquals(25, metrics.size)
        assertTrue(metrics.values.all { !it.isNaN() && it.isFinite() && it > 0.0 })

        val viaEngine = SingleCurrencyVendingEngine.evaluateLiveVendorTelemetry(wave = 7, tick = 45L, currencyPoints = 3800)
        assertEquals(25, viaEngine.size)
    }

    @Test
    fun `test all 25 borderlands loot catalog modules and part conversion bridges`() {
        val input = LiveSubsystemTickInput(tick = 20L, wave = 5, seed = 778899L)
        val metrics = BoxBoysLiveSubsystemBridge.evaluateBorderlandsLootCatalog(input)
        assertEquals(25, metrics.size)
        assertTrue(metrics.values.all { !it.isNaN() && it.isFinite() && it > 0.0 })

        val starter = ModularWeaponGenerator.createStarterModularWeapon("pistol")
        val catBarrel = starter.barrel.toCatalogBarrelConfig(seed = 42)
        val catMag = starter.magazine.toCatalogMagazinePart(partId = 2)
        val catOptic = starter.optic.toCatalogOpticPart(id = "OPTIC_02")

        assertEquals(2, catMag.partId)
        assertEquals("OPTIC_02", catOptic.id)
        assertTrue(catBarrel.rangeMultiplier > 0f)

        val roundTripMag = catMag.toGameMagazinePart()
        val roundTripOptic = catOptic.toGameOpticPart()
        assertEquals(starter.magazine.name, roundTripMag.name)
        assertEquals(starter.optic.name, roundTripOptic.name)
    }

    @Test
    fun `test all 25 relic items and ceremonial daggers snapshot evaluation`() {
        var relicState = PassiveRelicStackState()
        for (kind in PassiveRelicKind.entries) {
            relicState = Ror2PassiveRelicSystem.collectRelicDrop(relicState, kind, 2)
        }
        assertEquals(3, relicState.homingDaggerCount)
        assertEquals(3.0f, relicState.ceremonialDaggerDamageMultiplier, 0.001f)

        val snapshot = Ror2PassiveRelicSystem.computePassiveRelicSnapshot(relicState, wave = 8, seed = 101L)
        assertEquals(3, snapshot.homingDaggerCount)
        assertEquals(3.0f, snapshot.ceremonialDaggerDamageMultiplier, 0.001f)
        assertTrue(snapshot.plasmaMissileDamageMultiplier >= 6.0f)
        assertTrue(snapshot.willOWispBlastDamageMultiplier >= 6.0f)
        assertTrue(snapshot.frostStormRadiusPx >= 132f)
        assertTrue(snapshot.teslaCoilArcCount >= 4)
        assertNotEquals(0L, snapshot.checksum)

        val relicMetrics = BoxBoysLiveSubsystemBridge.evaluateAll25RelicItems(
            LiveSubsystemTickInput(tick = 12L, wave = 8, seed = 101L),
            relicState
        )
        assertEquals(25, relicMetrics.size)
        assertTrue(relicMetrics.values.all { !it.isNaN() && it.isFinite() && it > 0.0 })
    }

    @Test
    fun `test all 9 net sync pipeline modules and OasisHostedServerClient integration`() {
        val netSummary = OasisHostedServerClient.evaluateAuthoritativeSyncTelemetry(
            wave = 6,
            tick = 120L,
            activeZombieCount = 12
        )
        assertEquals(120L, netSummary.reconciledTick)
        assertEquals(9, netSummary.broadcastedEvents)
        assertEquals(96, netSummary.quantizedPayloadBytes) // 12 zombies * 8 bytes
        assertTrue(netSummary.hotJoinStipend >= 3750)
        assertNotEquals(0L, netSummary.checksum)
    }

    @Test
    fun `test stepLiveSubsystems evaluates all 147 track modules and deterministic FNV1a checksum`() {
        val input = LiveSubsystemTickInput(tick = 60L, wave = 5, seed = 20261104L)
        val summaryA = BoxBoysLiveSubsystemBridge.stepLiveSubsystems(input)
        val summaryB = BoxBoysLiveSubsystemBridge.stepLiveSubsystems(input)

        assertTrue(summaryA.isHealthy)
        assertEquals(25, summaryA.evaluatedVaultCount)
        assertEquals(13, summaryA.evaluatedDirectorModules)
        assertEquals(25, summaryA.evaluatedCombatModules)
        assertEquals(25, summaryA.evaluatedEconomyModules)
        assertEquals(25, summaryA.evaluatedLootCatalogModules)
        assertEquals(25, summaryA.evaluatedRelicModules)
        assertEquals(9, summaryA.evaluatedNetSyncModules)
        assertEquals(147, summaryA.totalTrackModulesEvaluated)
        assertTrue(summaryA.registeredModuleCount >= 199)
        assertEquals(summaryA.deterministicChecksum, summaryB.deterministicChecksum)
    }

    @Test
    fun `test BoxBoyGameEngine live subsystem bridge integration`() {
        val engine = BoxBoyGameEngine()
        val session = GameSessionState(mapSeed = 556677L)
        val summary = engine.evaluateLiveSubsystemBridge(session, tick = 10L)
        assertTrue(summary.isHealthy)
        assertEquals(147, summary.totalTrackModulesEvaluated)
        val counts = engine.verifySubsystemRegistryCounts()
        assertTrue((counts["TOTAL_REGISTERED_MODULES"] ?: 0) >= 199)
    }

    @Test
    fun `test upgraded SquadSynergyAnalyzer analyzeSynergy and report`() {
        val score = SquadSynergyAnalyzer.analyzeSynergy(listOf("CRYO_VEX", "TORGUE_FIRE", "VOLTAIC"))
        assertTrue(score > 1.2)
        val w1 = ModularWeaponGenerator.generateProceduralWeapon(seed = 10L, wave = 3)
        val report = SquadSynergyAnalyzer.evaluateSquadSynergyReport(listOf(w1))
        assertTrue(report.synergyScore >= 1.0)
        assertNotEquals(0L, report.checksum)
    }

    @Test
    fun `test upgraded CryoBarrelFreezeNova triggerNova and shatter damage`() {
        val nova = CryoBarrelFreezeNova.triggerNova(100f, 100f, 150f, emptyList())
        assertTrue(nova.affectedEnemyIds.isEmpty())
        val baseDmg = CryoBarrelFreezeNova.computeBrittleShatterDamage(100, 99, nova)
        assertEquals(100, baseDmg)
    }

    @Test
    fun `test upgraded WindowBarricadeTier3CryoGrid breach damage and checksum`() {
        val state = CryoGridState()
        val afterHp = WindowBarricadeTier3CryoGrid.applyZombieBreachDamage(state, attackingZombies = 3, deltaTimeSec = 0.05f)
        assertTrue(afterHp < state.barricadeTier.maxHp)
        assertTrue(state.integrityRatio in 0f..1f)
        assertNotEquals(0L, state.computeChecksum())
    }

    @Test
    fun `test upgraded AabbBarricadePenetration multi-barricade resolution`() {
        val entity = Aabb.fromCenterAndRadius(100f, 100f, 16f)
        val wall = Aabb.fromCenterAndRadius(108f, 100f, 16f)
        val (resolved, checksum) = AabbBarricadePenetration.resolveMultiBarricadePenetration(entity, listOf(wall))
        assertTrue(resolved.centerX < entity.centerX)
        assertNotEquals(0L, checksum)
    }

    @Test
    fun `test upgraded ArmoredJuggernautZombie and BoxheadComboStreakTracker snapshots`() {
        val jugg = ArmoredJuggernautCombatLogic.spawnWaveScaledJuggernaut(zombieId = 7, wave = 5)
        assertFalse(jugg.isArmorStripped)
        assertEquals(0.72f, ArmoredJuggernautCombatLogic.computeJuggernautSpeedMultiplier(jugg), 0.001f)
        assertNotEquals(0L, jugg.computeChecksum())

        val tracker = BoxheadComboStreakTracker()
        val tiers = tracker.onMultiKillBatch(26)
        assertEquals(listOf( com.example.game.combat.modules.RelicTier.TIER_25), tiers)
        val snap = tracker.snapshot()
        assertEquals(26, snap.currentCombo)
        assertEquals(26, snap.peakCombo)
        assertNotEquals(0L, snap.checksum)
    }

    @Test
    fun `test upgraded DashSlashKatanaTech, Marcus, Torgue, SingleCurrencyLedger, and Relic22`() {
        val slashDmg = DashSlashKatanaTech.computeSlashDamage(wave = 4, comboMultiplier = 1.5f, hitOrderIndex = 0)
        assertTrue(slashDmg > DashSlashKatanaTech.BASE_KATANA_DAMAGE)

        val marcusManifest = MarcusWeaponsVendor.generateSignedManifest(wave = 3, stepSeed = 99L)
        assertEquals(2, marcusManifest.offers.size)
        assertNotEquals(0L, marcusManifest.checksum)

        val torgueBundle = TorgueAmmoOrdnanceVendor.buildOrdnanceBundle(wave = 8)
        assertEquals(3, torgueBundle.offers.size)
        assertTrue(torgueBundle.bonusBarrelCharges >= 4)

        val ledger = SingleCurrencyLedger(500)
        ledger.recordHit()
        ledger.recordKill(100)
        ledger.recordBoardRepair()
        assertTrue(ledger.attemptPurchase(200))
        val ledgerSnap = ledger.snapshot()
        assertEquals(430, ledgerSnap.balance)
        assertEquals(200, ledgerSnap.totalSpent)

        val relic22Snap = Relic22BackupMagazineClipEngine.evaluateSnapshot()
        assertEquals(2, relic22Snap.totalCharges)
        assertNotEquals(0L, relic22Snap.checksum)
    }
}
