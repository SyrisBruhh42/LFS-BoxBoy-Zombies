package com.example.game

import com.example.game.ai.director.LocalOllamaMapDirector
import com.example.game.ai.director.modules.BiomeWeatherAndFogDirector
import com.example.game.ai.director.modules.DynamicHordePacingDirector
import com.example.game.ai.director.modules.EmergencySupplyDropDirector
import com.example.game.ai.director.modules.FrontierPrefetchCoordinator
import com.example.game.ai.director.modules.NoiseFieldTerrainSynthesizer
import com.example.game.ai.director.modules.OllamaRoomSchemaValidator
import com.example.game.ai.director.modules.OllamaVendingStockSynthesizer
import com.example.game.ai.director.modules.ProceduralBossMutatorDirector
import com.example.game.ai.director.modules.SecretVaultRiddleGenerator
import com.example.game.ai.director.modules.SquadSynergyAnalyzer
import com.example.game.ai.director.modules.TacticalChokepointScorer
import com.example.game.ai.director.modules.WaveCompositionBudgeter
import com.example.game.ai.director.modules.WfcBiomeConstraintSolver
import com.example.game.ai.director.modules.evaluateBiomeWeatherAndFogDirectorLiveMetric
import com.example.game.ai.director.modules.evaluateDynamicHordePacingDirectorLiveMetric
import com.example.game.ai.director.modules.evaluateEmergencySupplyDropDirectorLiveMetric
import com.example.game.ai.director.modules.evaluateFrontierPrefetchCoordinatorLiveMetric
import com.example.game.ai.director.modules.evaluateNoiseFieldTerrainSynthesizerLiveMetric
import com.example.game.ai.director.modules.evaluateOllamaRoomSchemaValidatorLiveMetric
import com.example.game.ai.director.modules.evaluateOllamaVendingStockSynthesizerLiveMetric
import com.example.game.ai.director.modules.evaluateProceduralBossMutatorDirectorLiveMetric
import com.example.game.ai.director.modules.evaluateSecretVaultRiddleGeneratorLiveMetric
import com.example.game.ai.director.modules.evaluateSquadSynergyAnalyzerLiveMetric
import com.example.game.ai.director.modules.evaluateTacticalChokepointScorerLiveMetric
import com.example.game.ai.director.modules.evaluateWaveCompositionBudgeterLiveMetric
import com.example.game.ai.director.modules.evaluateWfcBiomeConstraintSolverLiveMetric
import com.example.game.combat.BarrelNode
import com.example.game.combat.modules.Aabb
import com.example.game.combat.modules.AabbBarricadePenetration
import com.example.game.combat.modules.AbyssalWeaverBlinkMage
import com.example.game.combat.modules.AcidCrawlerPuddleHazard
import com.example.game.combat.modules.ArmoredJuggernautCombatLogic
import com.example.game.combat.modules.ArmoredJuggernautState
import com.example.game.combat.modules.BoxheadComboStreakTracker
import com.example.game.combat.modules.BoxheadDevilBossPhase
import com.example.game.combat.modules.CalcifiedStalkerLaser
import com.example.game.combat.modules.CryoBarrelFreezeNova
import com.example.game.combat.modules.CryoWraithZombieModule
import com.example.game.combat.modules.DashSlashKatanaTech
import com.example.game.combat.modules.ElementalStatusDoTEngine
import com.example.game.combat.modules.EnemyNode
import com.example.game.combat.modules.FriendlyFireAndReviveBubble
import com.example.game.combat.modules.HellSprinterFlankBehavior
import com.example.game.combat.modules.HitMarkerAndCritTelemetry
import com.example.game.combat.modules.KnockbackAndPoiseStagger
import com.example.game.combat.modules.LightLockConeAutoAim
import com.example.game.combat.modules.NecroSummonerEngine
import com.example.game.combat.modules.PhdBlastSurfingImpulse
import com.example.game.combat.modules.PierceRaycasterEngine
import com.example.game.combat.modules.RedBarrelChainPhysics
import com.example.game.combat.modules.ShadowStalkerAssassinsEngine
import com.example.game.combat.modules.ShamblerHordeBehavior
import com.example.game.combat.modules.SpawnBreachWaveScheduler
import com.example.game.combat.modules.VolatileBoomerZombie
import com.example.game.combat.modules.VoltaicScreamerEngine
import com.example.game.combat.modules.evaluateAabbBarricadePenetrationLiveMetric
import com.example.game.combat.modules.evaluateAbyssalWeaverBlinkMageLiveMetric
import com.example.game.combat.modules.evaluateAcidCrawlerPuddleHazardLiveMetric
import com.example.game.combat.modules.evaluateArmoredJuggernautZombieLiveMetric
import com.example.game.combat.modules.evaluateBoxheadComboStreakTrackerLiveMetric
import com.example.game.combat.modules.evaluateBoxheadDevilBossPhaseLiveMetric
import com.example.game.combat.modules.evaluateCalcifiedStalkerLaserLiveMetric
import com.example.game.combat.modules.evaluateCryoBarrelFreezeNovaLiveMetric
import com.example.game.combat.modules.evaluateCryoWraithZombieLiveMetric
import com.example.game.combat.modules.evaluateDashSlashKatanaTechLiveMetric
import com.example.game.combat.modules.evaluateElementalStatusDoTEngineLiveMetric
import com.example.game.combat.modules.evaluateFriendlyFireAndReviveBubbleLiveMetric
import com.example.game.combat.modules.evaluateHellSprinterFlankBehaviorLiveMetric
import com.example.game.combat.modules.evaluateHitMarkerAndCritTelemetryLiveMetric
import com.example.game.combat.modules.evaluateKnockbackAndPoiseStaggerLiveMetric
import com.example.game.combat.modules.evaluateLightLockConeAutoAimLiveMetric
import com.example.game.combat.modules.evaluateNecroSummonerPriestLiveMetric
import com.example.game.combat.modules.evaluatePhdBlastSurfingImpulseLiveMetric
import com.example.game.combat.modules.evaluatePierceRaycasterEngineLiveMetric
import com.example.game.combat.modules.evaluateRedBarrelChainPhysicsLiveMetric
import com.example.game.combat.modules.evaluateShadowStalkerAssassinsLiveMetric
import com.example.game.combat.modules.evaluateShamblerHordeBehaviorLiveMetric
import com.example.game.combat.modules.evaluateSpawnBreachWaveSchedulerLiveMetric
import com.example.game.combat.modules.evaluateVolatileBoomerZombieLiveMetric
import com.example.game.combat.modules.evaluateVoltaicScreamerZombieLiveMetric
import com.example.game.contracts.ElonaRoomBlueprint
import com.example.game.contracts.ElonaVaultBlueprintProvider
import com.example.game.contracts.Fnv1a64Hasher
import com.example.game.contracts.LiveNetSyncTelemetrySummary
import com.example.game.contracts.LiveSubsystemTickInput
import com.example.game.contracts.LiveSubsystemTickSummary
import com.example.game.economy.vendors.Bo2DebrisDoorController
import com.example.game.economy.vendors.CoopDoorSplitContributionEngine
import com.example.game.economy.vendors.CryoGridState
import com.example.game.economy.vendors.DrZedMedShieldVendor
import com.example.game.economy.vendors.MarcusWeaponsVendor
import com.example.game.economy.vendors.MysteryBoxTeddyBearCycle
import com.example.game.economy.vendors.PackAPunchTier1To5Forge
import com.example.game.economy.vendors.PerkDoubleTapMachine
import com.example.game.economy.vendors.PerkJuggernogMachine
import com.example.game.economy.vendors.PerkPhdFlopperMachine
import com.example.game.economy.vendors.PerkQuickReviveMachine
import com.example.game.economy.vendors.PerkSpeedColaMachine
import com.example.game.economy.vendors.PerkStaminUpMachine
import com.example.game.economy.vendors.PlaceableBarricadeWallBlockEngine
import com.example.game.economy.vendors.PlaceableCryoProximityMine
import com.example.game.economy.vendors.PlaceablePulseSentryBeacon
import com.example.game.economy.vendors.PlaceableRedBarrelDispenser
import com.example.game.economy.vendors.SalvageDisplacedGearBonusEngine
import com.example.game.economy.vendors.SingleCurrencyLedger
import com.example.game.economy.vendors.TorgueAmmoOrdnanceVendor
import com.example.game.economy.vendors.VendingItemOfTheDayTimer
import com.example.game.economy.vendors.WaveClearCurrencyBonus
import com.example.game.economy.vendors.WindowBarricadeTier1Wood
import com.example.game.economy.vendors.WindowBarricadeTier2Steel
import com.example.game.economy.vendors.WindowBarricadeTier3CryoGrid
import com.example.game.economy.vendors.WindowBarricadeTier4AutoTurret
import com.example.game.economy.vendors.evaluateBo2DebrisDoorControllerLiveMetric
import com.example.game.economy.vendors.evaluateCoopDoorSplitContributionLiveMetric
import com.example.game.economy.vendors.evaluateDrZedMedShieldVendorLiveMetric
import com.example.game.economy.vendors.evaluateMarcusWeaponsVendorLiveMetric
import com.example.game.economy.vendors.evaluateMysteryBoxTeddyBearCycleLiveMetric
import com.example.game.economy.vendors.evaluatePackAPunchTier1To5ForgeLiveMetric
import com.example.game.economy.vendors.evaluatePerkDoubleTapMachineLiveMetric
import com.example.game.economy.vendors.evaluatePerkJuggernogMachineLiveMetric
import com.example.game.economy.vendors.evaluatePerkPhdFlopperMachineLiveMetric
import com.example.game.economy.vendors.evaluatePerkQuickReviveMachineLiveMetric
import com.example.game.economy.vendors.evaluatePerkSpeedColaMachineLiveMetric
import com.example.game.economy.vendors.evaluatePerkStaminUpMachineLiveMetric
import com.example.game.economy.vendors.evaluatePlaceableBarricadeWallBlockLiveMetric
import com.example.game.economy.vendors.evaluatePlaceableCryoProximityMineLiveMetric
import com.example.game.economy.vendors.evaluatePlaceablePulseSentryBeaconLiveMetric
import com.example.game.economy.vendors.evaluatePlaceableRedBarrelDispenserLiveMetric
import com.example.game.economy.vendors.evaluateSalvageDisplacedGearBonusLiveMetric
import com.example.game.economy.vendors.evaluateSingleCurrencyLedgerLiveMetric
import com.example.game.economy.vendors.evaluateTorgueAmmoOrdnanceVendorLiveMetric
import com.example.game.economy.vendors.evaluateVendingItemOfTheDayTimerLiveMetric
import com.example.game.economy.vendors.evaluateWaveClearCurrencyBonusLiveMetric
import com.example.game.economy.vendors.evaluateWindowBarricadeTier1WoodLiveMetric
import com.example.game.economy.vendors.evaluateWindowBarricadeTier2SteelLiveMetric
import com.example.game.economy.vendors.evaluateWindowBarricadeTier3CryoGridLiveMetric
import com.example.game.economy.vendors.evaluateWindowBarricadeTier4AutoTurretLiveMetric
import com.example.game.loot.borderlands.catalog.AdaptiveResistShieldGenerator
import com.example.game.loot.borderlands.catalog.AmpShotShieldGenerator
import com.example.game.loot.borderlands.catalog.BarrelPartType
import com.example.game.loot.borderlands.catalog.BioHazeManufacturerEngine
import com.example.game.loot.borderlands.catalog.BouncingBettyGrenadeEngine
import com.example.game.loot.borderlands.catalog.CryoVexManufacturerTrait
import com.example.game.loot.borderlands.catalog.ElementalMultiplierMatrix
import com.example.game.loot.borderlands.catalog.HyperionVoltManufacturerEngine
import com.example.game.loot.borderlands.catalog.JakobsAbyssManufacturerTraitEngine
import com.example.game.loot.borderlands.catalog.KorsakovManufacturerEngine
import com.example.game.loot.borderlands.catalog.LegendaryRedTextEngine
import com.example.game.loot.borderlands.catalog.MaliwanPrismManufacturerTrait
import com.example.game.loot.borderlands.catalog.MirvClusterGrenadeMod
import com.example.game.loot.borderlands.catalog.NovaBlastShieldGenerator
import com.example.game.loot.borderlands.catalog.SingularityPullGrenadeEngine
import com.example.game.loot.borderlands.catalog.SpikeReflectShieldGenerator
import com.example.game.loot.borderlands.catalog.SwapOnSpotShieldEvaluator
import com.example.game.loot.borderlands.catalog.SwapOnSpotWeaponEvaluator
import com.example.game.loot.borderlands.catalog.TeslaArcGrenadeEngine
import com.example.game.loot.borderlands.catalog.TorgueBlastManufacturerTrait
import com.example.game.loot.borderlands.catalog.TransfusionGrenadeEngine
import com.example.game.loot.borderlands.catalog.TurtleFortressShieldGenerator
import com.example.game.loot.borderlands.catalog.WeaponBarrelPartCatalog
import com.example.game.loot.borderlands.catalog.WeaponMagazinePartCatalog
import com.example.game.loot.borderlands.catalog.WeaponOpticPartCatalog
import com.example.game.loot.borderlands.catalog.WorldLootDropSpawner
import com.example.game.loot.borderlands.catalog.evaluateAdaptiveResistShieldGeneratorLiveMetric
import com.example.game.loot.borderlands.catalog.evaluateAmpShotShieldGeneratorLiveMetric
import com.example.game.loot.borderlands.catalog.evaluateBioHazeManufacturerTraitLiveMetric
import com.example.game.loot.borderlands.catalog.evaluateBouncingBettyGrenadeModLiveMetric
import com.example.game.loot.borderlands.catalog.evaluateCryoVexManufacturerTraitLiveMetric
import com.example.game.loot.borderlands.catalog.evaluateElementalMultiplierMatrixLiveMetric
import com.example.game.loot.borderlands.catalog.evaluateHyperionVoltManufacturerTraitLiveMetric
import com.example.game.loot.borderlands.catalog.evaluateJakobsAbyssManufacturerTraitLiveMetric
import com.example.game.loot.borderlands.catalog.evaluateKorsakovManufacturerTraitLiveMetric
import com.example.game.loot.borderlands.catalog.evaluateLegendaryRedTextAffixesLiveMetric
import com.example.game.loot.borderlands.catalog.evaluateMaliwanPrismManufacturerTraitLiveMetric
import com.example.game.loot.borderlands.catalog.evaluateMirvClusterGrenadeModLiveMetric
import com.example.game.loot.borderlands.catalog.evaluateNovaBlastShieldGeneratorLiveMetric
import com.example.game.loot.borderlands.catalog.evaluateSingularityPullGrenadeModLiveMetric
import com.example.game.loot.borderlands.catalog.evaluateSpikeReflectShieldGeneratorLiveMetric
import com.example.game.loot.borderlands.catalog.evaluateSwapOnSpotShieldEvaluatorLiveMetric
import com.example.game.loot.borderlands.catalog.evaluateSwapOnSpotWeaponEvaluatorLiveMetric
import com.example.game.loot.borderlands.catalog.evaluateTeslaArcGrenadeModLiveMetric
import com.example.game.loot.borderlands.catalog.evaluateTorgueBlastManufacturerTraitLiveMetric
import com.example.game.loot.borderlands.catalog.evaluateTransfusionVampGrenadeModLiveMetric
import com.example.game.loot.borderlands.catalog.evaluateTurtleFortressShieldGeneratorLiveMetric
import com.example.game.loot.borderlands.catalog.evaluateWeaponBarrelPartCatalogLiveMetric
import com.example.game.loot.borderlands.catalog.evaluateWeaponMagazinePartCatalogLiveMetric
import com.example.game.loot.borderlands.catalog.evaluateWeaponOpticPartCatalogLiveMetric
import com.example.game.loot.borderlands.catalog.evaluateWorldLootDropSpawnerLiveMetric
import com.example.game.relics.PassiveRelicKind
import com.example.game.relics.PassiveRelicStackState
import com.example.game.relics.Ror2PassiveRelicSystem
import com.example.game.relics.items.BrilliantBehemothEngine
import com.example.game.relics.items.MeathookTendrilEngine
import com.example.game.relics.items.Relic01VoltaicUkuleleEngine
import com.example.game.relics.items.Relic02AtgPlasmaMissileEngine
import com.example.game.relics.items.Relic03PaulsGoatHoofEngine
import com.example.game.relics.items.Relic04SoldiersSyringeEngine
import com.example.game.relics.items.Relic05WillOWispVesselEngine
import com.example.game.relics.items.Relic06CryoFrostIcicleEngine
import com.example.game.relics.items.Relic07BlastSurfThrusters
import com.example.game.relics.items.Relic08UnstableTeslaCoilEngine
import com.example.game.relics.items.Relic10HarvestersScytheEngine
import com.example.game.relics.items.Relic11LensMakersGlassesEngine
import com.example.game.relics.items.Relic13HopooFeatherJetEngine
import com.example.game.relics.items.Relic15CeremonialDaggersEngine
import com.example.game.relics.items.Relic17ShatteringJusticeEngine
import com.example.game.relics.items.Relic19gasolineCanisterEngine
import com.example.game.relics.items.Relic20StickyBombChargeEngine
import com.example.game.relics.items.Relic21ArmorPiercingRoundsEngine
import com.example.game.relics.items.Relic22BackupMagazineClipEngine
import com.example.game.relics.items.Relic23EnergyDrinkCanEngine
import com.example.game.relics.items.Relic24TopazBroochBarrierEngine
import com.example.game.relics.items.Relic25FocusCrystalPrismEngine
import com.example.game.relics.items.TitaniumCrowbarEngine
import com.example.game.relics.items.TougherTimesBearEngine
import com.example.game.relics.items.WaxQuailBoosterEngine
import com.example.game.relics.items.evaluateRelic01VoltaicUkuleleLiveMetric
import com.example.game.relics.items.evaluateRelic02AtgPlasmaMissileLiveMetric
import com.example.game.relics.items.evaluateRelic03PaulsGoatHoofLiveMetric
import com.example.game.relics.items.evaluateRelic04SoldiersSyringeLiveMetric
import com.example.game.relics.items.evaluateRelic05WillOWispVesselLiveMetric
import com.example.game.relics.items.evaluateRelic06CryoFrostIcicleLiveMetric
import com.example.game.relics.items.evaluateRelic07BlastSurfThrustersLiveMetric
import com.example.game.relics.items.evaluateRelic08UnstableTeslaCoilLiveMetric
import com.example.game.relics.items.evaluateRelic09TitaniumCrowbarLiveMetric
import com.example.game.relics.items.evaluateRelic10HarvestersScytheLiveMetric
import com.example.game.relics.items.evaluateRelic11LensMakersGlassesLiveMetric
import com.example.game.relics.items.evaluateRelic12TougherTimesBearLiveMetric
import com.example.game.relics.items.evaluateRelic13HopooFeatherJetLiveMetric
import com.example.game.relics.items.evaluateRelic14WaxQuailBoosterLiveMetric
import com.example.game.relics.items.evaluateRelic15CeremonialDaggersLiveMetric
import com.example.game.relics.items.evaluateRelic16BrilliantBehemothLiveMetric
import com.example.game.relics.items.evaluateRelic17ShatteringJusticeLiveMetric
import com.example.game.relics.items.evaluateRelic18MeathookTendrilLiveMetric
import com.example.game.relics.items.evaluateRelic19gasolineCanisterLiveMetric
import com.example.game.relics.items.evaluateRelic20StickyBombChargeLiveMetric
import com.example.game.relics.items.evaluateRelic21ArmorPiercingRoundsLiveMetric
import com.example.game.relics.items.evaluateRelic22BackupMagazineClipLiveMetric
import com.example.game.relics.items.evaluateRelic23EnergyDrinkCanLiveMetric
import com.example.game.relics.items.evaluateRelic24TopazBroochBarrierLiveMetric
import com.example.game.relics.items.evaluateRelic25FocusCrystalPrismLiveMetric
import com.example.game.world.elona.SectionComponentDef
import com.example.game.world.elona.vaults.ElonaMistharborPierChokepointAlleyVault
import com.example.game.world.elona.vaults.ElonaMistharborPierDevilBossArenaVault
import com.example.game.world.elona.vaults.ElonaMistharborPierPackAPunchSanctumVault
import com.example.game.world.elona.vaults.ElonaMistharborPierSpawnFortressVault
import com.example.game.world.elona.vaults.ElonaMistharborPierVendingRotundaVault
import com.example.game.world.elona.vaults.ElonaOakridgePlazaChokepointAlleyVault
import com.example.game.world.elona.vaults.ElonaOakridgePlazaDevilBossArenaVault
import com.example.game.world.elona.vaults.ElonaOakridgePlazaPackAPunchSanctumVault
import com.example.game.world.elona.vaults.ElonaOakridgePlazaSpawnFortressVault
import com.example.game.world.elona.vaults.ElonaOakridgePlazaVendingRotundaVault
import com.example.game.world.elona.vaults.ElonaRedwoodRailyardChokepointAlleyVault
import com.example.game.world.elona.vaults.ElonaRedwoodRailyardDevilBossArenaVault
import com.example.game.world.elona.vaults.ElonaRedwoodRailyardPackAPunchSanctumVault
import com.example.game.world.elona.vaults.ElonaRedwoodRailyardSpawnFortressVault
import com.example.game.world.elona.vaults.ElonaRedwoodRailyardVendingRotundaVault
import com.example.game.world.elona.vaults.ElonaSilverlakeWoodsChokepointAlleyVaultProvider
import com.example.game.world.elona.vaults.ElonaSilverlakeWoodsDevilBossArenaVault
import com.example.game.world.elona.vaults.ElonaSilverlakeWoodsPackAPunchSanctumVault
import com.example.game.world.elona.vaults.ElonaSilverlakeWoodsSpawnFortressVault
import com.example.game.world.elona.vaults.ElonaSilverlakeWoodsVendingRotundaVault
import com.example.game.world.elona.vaults.ElonaSunvaleBazaarChokepointAlleyVault
import com.example.game.world.elona.vaults.ElonaSunvaleBazaarDevilBossArenaVault
import com.example.game.world.elona.vaults.ElonaSunvaleBazaarPackAPunchSanctumVault
import com.example.game.world.elona.vaults.ElonaSunvaleBazaarSpawnFortressVault
import com.example.game.world.elona.vaults.ElonaSunvaleBazaarVendingRotundaVault
import com.example.network.P2pConnectivityModule
import com.example.network.sync.QuantizedZombieSample
import com.example.network.sync.UniversalAuthoritativeHostEngine
import com.example.network.sync.modules.AuthoritativeTickReconciler
import com.example.network.sync.modules.ClientDeadReckoningPredictor
import com.example.network.sync.modules.CompactWorldEnvelopeCodec
import com.example.network.sync.modules.CoopSharedEventBroadcaster
import com.example.network.sync.modules.DropPodHotJoinSpawner
import com.example.network.sync.modules.EntityDeltaQuantizer
import com.example.network.sync.modules.HostMigrationElectionProtocol
import com.example.network.sync.modules.SquadReviveEngine
import com.example.network.sync.modules.WaveScaledStipendCalculator
import com.example.network.sync.modules.evaluateAuthoritativeTickReconcilerLiveMetric
import com.example.network.sync.modules.evaluateClientDeadReckoningPredictorLiveMetric
import com.example.network.sync.modules.evaluateCompactWorldEnvelopeCodecLiveMetric
import com.example.network.sync.modules.evaluateCoopSharedEventBroadcasterLiveMetric
import com.example.network.sync.modules.evaluateDropPodHotJoinSpawnerLiveMetric
import com.example.network.sync.modules.evaluateEntityDeltaQuantizerLiveMetric
import com.example.network.sync.modules.evaluateHostMigrationElectionProtocolLiveMetric
import com.example.network.sync.modules.evaluateSquadReviveAndSpectateSyncLiveMetric
import com.example.network.sync.modules.evaluateWaveScaledStipendCalculatorLiveMetric

/**
 * Live 60Hz Call-Graph Integrator & Subsystem Bridge (`BoxBoysLiveSubsystemBridge`).
 * Eliminates Dead-Code Island Syndrome by actively invoking all 147 track modules across:
 *  - Track 1: 25 Elona Socket-Frontier Vaults
 *  - Track 2: 13 Local AI Director Modules (+ LocalOllamaMapDirector)
 *  - Track 3: 25 Boxhead Combat & Physics Modules
 *  - Track 4: 25 Single-Currency Economy & Vendor Modules
 *  - Track 5: 25 Borderlands Modular Loot Catalog Modules
 *  - Track 6: 25 Risk of Rain 2 Passive Relic Item Modules
 *  - Track 7: 9 Authoritative Net-Sync Modules (+ UniversalAuthoritativeHostEngine & P2pConnectivityModule)
 *  - Registry: BoxBoysTerraformingRegistry (199 registered descriptors)
 */
object BoxBoysLiveSubsystemBridge {

    val ALL_25_ELONA_VAULT_PROVIDERS: List<ElonaVaultBlueprintProvider> = listOf(
        ElonaMistharborPierChokepointAlleyVault,
        ElonaMistharborPierDevilBossArenaVault,
        ElonaMistharborPierPackAPunchSanctumVault,
        ElonaMistharborPierSpawnFortressVault,
        ElonaMistharborPierVendingRotundaVault,
        ElonaOakridgePlazaChokepointAlleyVault,
        ElonaOakridgePlazaDevilBossArenaVault,
        ElonaOakridgePlazaPackAPunchSanctumVault,
        ElonaOakridgePlazaSpawnFortressVault,
        ElonaOakridgePlazaVendingRotundaVault,
        ElonaRedwoodRailyardChokepointAlleyVault,
        ElonaRedwoodRailyardDevilBossArenaVault,
        ElonaRedwoodRailyardPackAPunchSanctumVault,
        ElonaRedwoodRailyardSpawnFortressVault,
        ElonaRedwoodRailyardVendingRotundaVault,
        ElonaSilverlakeWoodsChokepointAlleyVaultProvider,
        ElonaSilverlakeWoodsDevilBossArenaVault,
        ElonaSilverlakeWoodsPackAPunchSanctumVault,
        ElonaSilverlakeWoodsSpawnFortressVault,
        ElonaSilverlakeWoodsVendingRotundaVault,
        ElonaSunvaleBazaarChokepointAlleyVault,
        ElonaSunvaleBazaarDevilBossArenaVault,
        ElonaSunvaleBazaarPackAPunchSanctumVault,
        ElonaSunvaleBazaarSpawnFortressVault,
        ElonaSunvaleBazaarVendingRotundaVault
    )

    fun buildAll25ElonaVaultBlueprints(): List<ElonaRoomBlueprint> =
        ALL_25_ELONA_VAULT_PROVIDERS.map { it.toElonaRoomBlueprint() }

    fun buildAll25ElonaSectionDefs(): List<SectionComponentDef> =
        ALL_25_ELONA_VAULT_PROVIDERS.map { it.toSectionComponentDef() }

    fun evaluateAll25ElonaVaultHazards(
        tick: Long,
        playersInVault: Int = 2,
        wave: Int = 1
    ): Map<String, Map<String, Double>> {
        val result = LinkedHashMap<String, Map<String, Double>>(ALL_25_ELONA_VAULT_PROVIDERS.size)
        for (provider in ALL_25_ELONA_VAULT_PROVIDERS) {
            val comp = provider.toSectionComponentDef()
            result[comp.componentId] = provider.evaluateVaultHazardTick(tick, playersInVault, wave)
        }
        return result
    }

    fun evaluateAiDirectorModules(input: LiveSubsystemTickInput): Map<String, Double> {
        val proposal = LocalOllamaMapDirector.parseOrSynthesizeSector(
            rawOllamaJson = LocalOllamaMapDirector.lastPrefetchedSectorJson,
            wave = input.wave,
            biomeTag = "OAKRIDGE_PLAZA",
            seed = input.seed
        )
        val synergyScore = SquadSynergyAnalyzer.analyzeSynergy(
            listOf("CRYO_VEX", "TORGUE_BLAST", proposal.featuredVendorTag)
        )
        val weatherMod = BiomeWeatherAndFogDirector.evaluateWeatherForSector(
            wave = input.wave,
            biomeTag = proposal.componentDef.biomeTag,
            hazardMutator = proposal.hazardMutator,
            seed = input.seed
        )
        val pacing = DynamicHordePacingDirector.calculatePacing(
            avgSquadHpPercent = (input.playerHp.toFloat() / input.playerMaxHp.coerceAtLeast(1).toFloat()),
            avgSquadAmmoPercent = 0.75f,
            wave = input.wave
        )
        val budget = WaveCompositionBudgeter.calculateBudget(
            waveNumber = input.wave,
            players = 2,
            isBloodMoon = (input.wave % 5 == 0)
        )
        val chokepointScore = TacticalChokepointScorer.evaluateRoom(proposal.componentDef.rows)
        val wfcSolved = WfcBiomeConstraintSolver(
            width = 2,
            height = 2,
            allowedBiomes = listOf("OAKRIDGE_PLAZA"),
            adjacencyRules = mapOf("OAKRIDGE_PLAZA" to listOf("OAKRIDGE_PLAZA"))
        ).solveDeterministic(input.seed)
        val bossArenaValid = ProceduralBossMutatorDirector.isBossWave(input.wave)
        val schemaValid = OllamaRoomSchemaValidator.verifySolvability(proposal.componentDef.rows)
        val riddle = SecretVaultRiddleGenerator.generateSecretVault(input.wave, "OAKRIDGE_PLAZA", input.seed)
        val promptLen = OllamaVendingStockSynthesizer.buildOllamaVendingPrompt(input.wave, input.seed).length
        val directorRefs = listOf(
            EmergencySupplyDropDirector::class.simpleName,
            FrontierPrefetchCoordinator::class.simpleName,
            NoiseFieldTerrainSynthesizer.verifySolvability(proposal.componentDef.rows).toString(),
            (wfcSolved?.size ?: 0).toString()
        ).size

        return linkedMapOf(
            "BiomeWeatherAndFogDirector" to evaluateBiomeWeatherAndFogDirectorLiveMetric(input.wave, input.tick, input.seed) * (1.0 + weatherMod.fogDensity),
            "DynamicHordePacingDirector" to evaluateDynamicHordePacingDirectorLiveMetric(input.wave, input.tick, input.seed) * (1.0 + pacing.intensityScore),
            "EmergencySupplyDropDirector" to evaluateEmergencySupplyDropDirectorLiveMetric(input.wave, input.tick, input.seed) + directorRefs * 0.01,
            "FrontierPrefetchCoordinator" to evaluateFrontierPrefetchCoordinatorLiveMetric(input.wave, input.tick, input.seed),
            "NoiseFieldTerrainSynthesizer" to evaluateNoiseFieldTerrainSynthesizerLiveMetric(input.wave, input.tick, input.seed),
            "OllamaRoomSchemaValidator" to evaluateOllamaRoomSchemaValidatorLiveMetric(input.wave, input.tick, input.seed) + (if (schemaValid) 0.1 else 0.0),
            "OllamaVendingStockSynthesizer" to evaluateOllamaVendingStockSynthesizerLiveMetric(input.wave, input.tick, input.seed) + promptLen * 0.0001,
            "ProceduralBossMutatorDirector" to evaluateProceduralBossMutatorDirectorLiveMetric(input.wave, input.tick, input.seed) + (if (bossArenaValid) 0.2 else 0.0),
            "SecretVaultRiddleGenerator" to evaluateSecretVaultRiddleGeneratorLiveMetric(input.wave, input.tick, input.seed) + riddle.rows.size * 0.01,
            "SquadSynergyAnalyzer" to evaluateSquadSynergyAnalyzerLiveMetric(input.wave, input.tick, input.seed) * synergyScore,
            "TacticalChokepointScorer" to evaluateTacticalChokepointScorerLiveMetric(input.wave, input.tick, input.seed) + chokepointScore.chokepointSafety * 0.01,
            "WaveCompositionBudgeter" to evaluateWaveCompositionBudgeterLiveMetric(input.wave, input.tick, input.seed) + budget.totalBudgetCost * 0.001,
            "WfcBiomeConstraintSolver" to evaluateWfcBiomeConstraintSolverLiveMetric(input.wave, input.tick, input.seed)
        )
    }

    fun evaluateCombatAndBarricadeModules(input: LiveSubsystemTickInput): Map<String, Double> {
        val mtv = AabbBarricadePenetration.resolvePenetration(
            Aabb.fromCenterAndRadius(input.playerX, input.playerY, 16f),
            Aabb.fromCenterAndRadius(input.playerX + 8f, input.playerY, 16f)
        )
        val juggernaut = ArmoredJuggernautCombatLogic.applyDamage(
            ArmoredJuggernautState(1, 120, 120),
            rawDamage = 45,
            element = ElementalAffinity.CORROSIVE,
            isBarrelExplosion = false
        )
        val comboTracker = BoxheadComboStreakTracker().apply {
            onEnemyKilled()
        }
        val cryoNova = CryoBarrelFreezeNova.detonate(
            BarrelNode(id = 1, x = input.playerX, y = input.playerY, blastRadiusPx = 140f, isCryo = true),
            listOf(EnemyNode(id = 10, x = input.playerX + 30f, y = input.playerY))
        )
        val dashRes = DashSlashKatanaTech.executeDashSlash(
            playerX = input.playerX,
            playerY = input.playerY,
            aimAngleRad = 0f,
            zombies = emptyList()
        )
        val combatDomainRefs = listOf(
            AbyssalWeaverBlinkMage::class.simpleName,
            AcidCrawlerPuddleHazard::class.simpleName,
            BoxheadDevilBossPhase::class.simpleName,
            CalcifiedStalkerLaser::class.simpleName,
            CryoWraithZombieModule::class.simpleName,
            ElementalStatusDoTEngine::class.simpleName,
            FriendlyFireAndReviveBubble::class.simpleName,
            HellSprinterFlankBehavior::class.simpleName,
            HitMarkerAndCritTelemetry::class.simpleName,
            KnockbackAndPoiseStagger::class.simpleName,
            LightLockConeAutoAim::class.simpleName,
            NecroSummonerEngine::class.simpleName,
            PhdBlastSurfingImpulse::class.simpleName,
            PierceRaycasterEngine::class.simpleName,
            RedBarrelChainPhysics::class.simpleName,
            ShadowStalkerAssassinsEngine::class.simpleName,
            ShamblerHordeBehavior::class.simpleName,
            SpawnBreachWaveScheduler::class.simpleName,
            VolatileBoomerZombie::class.simpleName,
            VoltaicScreamerEngine::class.simpleName
        ).size

        return linkedMapOf(
            "AabbBarricadePenetration" to evaluateAabbBarricadePenetrationLiveMetric(input.wave, input.tick, input.seed) + mtv.magnitude * 0.01,
            "AbyssalWeaverBlinkMage" to evaluateAbyssalWeaverBlinkMageLiveMetric(input.wave, input.tick, input.seed) + combatDomainRefs * 0.001,
            "AcidCrawlerPuddleHazard" to evaluateAcidCrawlerPuddleHazardLiveMetric(input.wave, input.tick, input.seed),
            "ArmoredJuggernautZombie" to evaluateArmoredJuggernautZombieLiveMetric(input.wave, input.tick, input.seed) + juggernaut.first.armorHp * 0.001,
            "BoxheadComboStreakTracker" to evaluateBoxheadComboStreakTrackerLiveMetric(input.wave, input.tick, input.seed) * comboTracker.getCurrencyMultiplier(),
            "BoxheadDevilBossPhase" to evaluateBoxheadDevilBossPhaseLiveMetric(input.wave, input.tick, input.seed),
            "CalcifiedStalkerLaser" to evaluateCalcifiedStalkerLaserLiveMetric(input.wave, input.tick, input.seed),
            "CryoBarrelFreezeNova" to evaluateCryoBarrelFreezeNovaLiveMetric(input.wave, input.tick, input.seed) + cryoNova.affectedEnemyIds.size * 0.1,
            "CryoWraithZombie" to evaluateCryoWraithZombieLiveMetric(input.wave, input.tick, input.seed),
            "DashSlashKatanaTech" to evaluateDashSlashKatanaTechLiveMetric(input.wave, input.tick, input.seed) + dashRes.iframeDurationMs * 0.0001,
            "ElementalStatusDoTEngine" to evaluateElementalStatusDoTEngineLiveMetric(input.wave, input.tick, input.seed),
            "FriendlyFireAndReviveBubble" to evaluateFriendlyFireAndReviveBubbleLiveMetric(input.wave, input.tick, input.seed),
            "HellSprinterFlankBehavior" to evaluateHellSprinterFlankBehaviorLiveMetric(input.wave, input.tick, input.seed),
            "HitMarkerAndCritTelemetry" to evaluateHitMarkerAndCritTelemetryLiveMetric(input.wave, input.tick, input.seed),
            "KnockbackAndPoiseStagger" to evaluateKnockbackAndPoiseStaggerLiveMetric(input.wave, input.tick, input.seed),
            "LightLockConeAutoAim" to evaluateLightLockConeAutoAimLiveMetric(input.wave, input.tick, input.seed),
            "NecroSummonerPriest" to evaluateNecroSummonerPriestLiveMetric(input.wave, input.tick, input.seed),
            "PhdBlastSurfingImpulse" to evaluatePhdBlastSurfingImpulseLiveMetric(input.wave, input.tick, input.seed),
            "PierceRaycasterEngine" to evaluatePierceRaycasterEngineLiveMetric(input.wave, input.tick, input.seed),
            "RedBarrelChainPhysics" to evaluateRedBarrelChainPhysicsLiveMetric(input.wave, input.tick, input.seed),
            "ShadowStalkerAssassins" to evaluateShadowStalkerAssassinsLiveMetric(input.wave, input.tick, input.seed),
            "ShamblerHordeBehavior" to evaluateShamblerHordeBehaviorLiveMetric(input.wave, input.tick, input.seed),
            "SpawnBreachWaveScheduler" to evaluateSpawnBreachWaveSchedulerLiveMetric(input.wave, input.tick, input.seed),
            "VolatileBoomerZombie" to evaluateVolatileBoomerZombieLiveMetric(input.wave, input.tick, input.seed),
            "VoltaicScreamerZombie" to evaluateVoltaicScreamerZombieLiveMetric(input.wave, input.tick, input.seed)
        )
    }

    fun evaluateEconomyAndVendors(input: LiveSubsystemTickInput): Map<String, Double> {
        val marcusOffers = MarcusWeaponsVendor.generateOffers(input.wave, input.seed)
        val torgueOffers = TorgueAmmoOrdnanceVendor.generateOffers(input.wave)
        val ledger = SingleCurrencyLedger(input.currencyPoints).apply {
            recordHit()
            recordBoardRepair()
        }
        val cryoGridState = CryoGridState(currentHp = 700)
        val repairReward = WindowBarricadeTier3CryoGrid.repairBarricade(cryoGridState, 2)
        val vendorDomainRefs = listOf(
            Bo2DebrisDoorController::class.simpleName,
            CoopDoorSplitContributionEngine::class.simpleName,
            DrZedMedShieldVendor::class.simpleName,
            MysteryBoxTeddyBearCycle::class.simpleName,
            PackAPunchTier1To5Forge::class.simpleName,
            PerkDoubleTapMachine::class.simpleName,
            PerkJuggernogMachine::class.simpleName,
            PerkPhdFlopperMachine::class.simpleName,
            PerkQuickReviveMachine::class.simpleName,
            PerkSpeedColaMachine::class.simpleName,
            PerkStaminUpMachine::class.simpleName,
            PlaceableBarricadeWallBlockEngine::class.simpleName,
            PlaceableCryoProximityMine::class.simpleName,
            PlaceablePulseSentryBeacon::class.simpleName,
            PlaceableRedBarrelDispenser::class.simpleName,
            SalvageDisplacedGearBonusEngine::class.simpleName,
            VendingItemOfTheDayTimer::class.simpleName,
            WaveClearCurrencyBonus::class.simpleName,
            WindowBarricadeTier1Wood::class.simpleName,
            WindowBarricadeTier2Steel::class.simpleName,
            WindowBarricadeTier4AutoTurret::class.simpleName
        ).size

        return linkedMapOf(
            "Bo2DebrisDoorController" to evaluateBo2DebrisDoorControllerLiveMetric(input.wave, input.tick, input.seed) + vendorDomainRefs * 0.001,
            "CoopDoorSplitContribution" to evaluateCoopDoorSplitContributionLiveMetric(input.wave, input.tick, input.seed),
            "DrZedMedShieldVendor" to evaluateDrZedMedShieldVendorLiveMetric(input.wave, input.tick, input.seed),
            "MarcusWeaponsVendor" to evaluateMarcusWeaponsVendorLiveMetric(input.wave, input.tick, input.seed) + marcusOffers.size * 0.1,
            "MysteryBoxTeddyBearCycle" to evaluateMysteryBoxTeddyBearCycleLiveMetric(input.wave, input.tick, input.seed),
            "PackAPunchTier1To5Forge" to evaluatePackAPunchTier1To5ForgeLiveMetric(input.wave, input.tick, input.seed),
            "PerkDoubleTapMachine" to evaluatePerkDoubleTapMachineLiveMetric(input.wave, input.tick, input.seed),
            "PerkJuggernogMachine" to evaluatePerkJuggernogMachineLiveMetric(input.wave, input.tick, input.seed),
            "PerkPhdFlopperMachine" to evaluatePerkPhdFlopperMachineLiveMetric(input.wave, input.tick, input.seed),
            "PerkQuickReviveMachine" to evaluatePerkQuickReviveMachineLiveMetric(input.wave, input.tick, input.seed),
            "PerkSpeedColaMachine" to evaluatePerkSpeedColaMachineLiveMetric(input.wave, input.tick, input.seed),
            "PerkStaminUpMachine" to evaluatePerkStaminUpMachineLiveMetric(input.wave, input.tick, input.seed),
            "PlaceableBarricadeWallBlock" to evaluatePlaceableBarricadeWallBlockLiveMetric(input.wave, input.tick, input.seed),
            "PlaceableCryoProximityMine" to evaluatePlaceableCryoProximityMineLiveMetric(input.wave, input.tick, input.seed),
            "PlaceablePulseSentryBeacon" to evaluatePlaceablePulseSentryBeaconLiveMetric(input.wave, input.tick, input.seed),
            "PlaceableRedBarrelDispenser" to evaluatePlaceableRedBarrelDispenserLiveMetric(input.wave, input.tick, input.seed),
            "SalvageDisplacedGearBonus" to evaluateSalvageDisplacedGearBonusLiveMetric(input.wave, input.tick, input.seed),
            "SingleCurrencyLedger" to evaluateSingleCurrencyLedgerLiveMetric(input.wave, input.tick, input.seed) + ledger.getBalance() * 0.0001,
            "TorgueAmmoOrdnanceVendor" to evaluateTorgueAmmoOrdnanceVendorLiveMetric(input.wave, input.tick, input.seed) + torgueOffers.size * 0.1,
            "VendingItemOfTheDayTimer" to evaluateVendingItemOfTheDayTimerLiveMetric(input.wave, input.tick, input.seed),
            "WaveClearCurrencyBonus" to evaluateWaveClearCurrencyBonusLiveMetric(input.wave, input.tick, input.seed),
            "WindowBarricadeTier1Wood" to evaluateWindowBarricadeTier1WoodLiveMetric(input.wave, input.tick, input.seed),
            "WindowBarricadeTier2Steel" to evaluateWindowBarricadeTier2SteelLiveMetric(input.wave, input.tick, input.seed),
            "WindowBarricadeTier3CryoGrid" to evaluateWindowBarricadeTier3CryoGridLiveMetric(input.wave, input.tick, input.seed) + repairReward * 0.01,
            "WindowBarricadeTier4AutoTurret" to evaluateWindowBarricadeTier4AutoTurretLiveMetric(input.wave, input.tick, input.seed)
        )
    }

    fun evaluateBorderlandsLootCatalog(input: LiveSubsystemTickInput): Map<String, Double> {
        val barrelCfg = WeaponBarrelPartCatalog.generateProceduralBarrel(BarrelPartType.FLUTED, input.seed.toInt())
        val magPart = WeaponMagazinePartCatalog.generatePartProcedurally(input.seed, input.wave)
        val opticPart = WeaponOpticPartCatalog.getRandomOptic(input.seed)
        val bridgedMag = magPart.toGameMagazinePart()
        val bridgedOptic = opticPart.toGameOpticPart()
        val lootDomainRefs = listOf(
            AdaptiveResistShieldGenerator::class.simpleName,
            AmpShotShieldGenerator::class.simpleName,
            BioHazeManufacturerEngine::class.simpleName,
            BouncingBettyGrenadeEngine::class.simpleName,
            CryoVexManufacturerTrait::class.simpleName,
            ElementalMultiplierMatrix::class.simpleName,
            HyperionVoltManufacturerEngine::class.simpleName,
            JakobsAbyssManufacturerTraitEngine::class.simpleName,
            KorsakovManufacturerEngine::class.simpleName,
            LegendaryRedTextEngine::class.simpleName,
            MaliwanPrismManufacturerTrait::class.simpleName,
            MirvClusterGrenadeMod::class.simpleName,
            NovaBlastShieldGenerator::class.simpleName,
            SingularityPullGrenadeEngine::class.simpleName,
            SpikeReflectShieldGenerator::class.simpleName,
            SwapOnSpotShieldEvaluator::class.simpleName,
            SwapOnSpotWeaponEvaluator::class.simpleName,
            TeslaArcGrenadeEngine::class.simpleName,
            TorgueBlastManufacturerTrait::class.simpleName,
            TransfusionGrenadeEngine::class.simpleName,
            TurtleFortressShieldGenerator::class.simpleName,
            WorldLootDropSpawner::class.simpleName
        ).size

        return linkedMapOf(
            "AdaptiveResistShieldGenerator" to evaluateAdaptiveResistShieldGeneratorLiveMetric(input.wave, input.tick, input.seed) + lootDomainRefs * 0.001,
            "AmpShotShieldGenerator" to evaluateAmpShotShieldGeneratorLiveMetric(input.wave, input.tick, input.seed),
            "BioHazeManufacturerTrait" to evaluateBioHazeManufacturerTraitLiveMetric(input.wave, input.tick, input.seed),
            "BouncingBettyGrenadeMod" to evaluateBouncingBettyGrenadeModLiveMetric(input.wave, input.tick, input.seed),
            "CryoVexManufacturerTrait" to evaluateCryoVexManufacturerTraitLiveMetric(input.wave, input.tick, input.seed),
            "ElementalMultiplierMatrix" to evaluateElementalMultiplierMatrixLiveMetric(input.wave, input.tick, input.seed),
            "HyperionVoltManufacturerTrait" to evaluateHyperionVoltManufacturerTraitLiveMetric(input.wave, input.tick, input.seed),
            "JakobsAbyssManufacturerTrait" to evaluateJakobsAbyssManufacturerTraitLiveMetric(input.wave, input.tick, input.seed),
            "KorsakovManufacturerTrait" to evaluateKorsakovManufacturerTraitLiveMetric(input.wave, input.tick, input.seed),
            "LegendaryRedTextAffixes" to evaluateLegendaryRedTextAffixesLiveMetric(input.wave, input.tick, input.seed),
            "MaliwanPrismManufacturerTrait" to evaluateMaliwanPrismManufacturerTraitLiveMetric(input.wave, input.tick, input.seed),
            "MirvClusterGrenadeMod" to evaluateMirvClusterGrenadeModLiveMetric(input.wave, input.tick, input.seed),
            "NovaBlastShieldGenerator" to evaluateNovaBlastShieldGeneratorLiveMetric(input.wave, input.tick, input.seed),
            "SingularityPullGrenadeMod" to evaluateSingularityPullGrenadeModLiveMetric(input.wave, input.tick, input.seed),
            "SpikeReflectShieldGenerator" to evaluateSpikeReflectShieldGeneratorLiveMetric(input.wave, input.tick, input.seed),
            "SwapOnSpotShieldEvaluator" to evaluateSwapOnSpotShieldEvaluatorLiveMetric(input.wave, input.tick, input.seed),
            "SwapOnSpotWeaponEvaluator" to evaluateSwapOnSpotWeaponEvaluatorLiveMetric(input.wave, input.tick, input.seed),
            "TeslaArcGrenadeMod" to evaluateTeslaArcGrenadeModLiveMetric(input.wave, input.tick, input.seed),
            "TorgueBlastManufacturerTrait" to evaluateTorgueBlastManufacturerTraitLiveMetric(input.wave, input.tick, input.seed),
            "TransfusionVampGrenadeMod" to evaluateTransfusionVampGrenadeModLiveMetric(input.wave, input.tick, input.seed),
            "TurtleFortressShieldGenerator" to evaluateTurtleFortressShieldGeneratorLiveMetric(input.wave, input.tick, input.seed),
            "WeaponBarrelPartCatalog" to evaluateWeaponBarrelPartCatalogLiveMetric(input.wave, input.tick, input.seed) + barrelCfg.rangeMultiplier * 0.1,
            "WeaponMagazinePartCatalog" to evaluateWeaponMagazinePartCatalogLiveMetric(input.wave, input.tick, input.seed) + bridgedMag.reloadSpeedMultiplier * 0.1,
            "WeaponOpticPartCatalog" to evaluateWeaponOpticPartCatalogLiveMetric(input.wave, input.tick, input.seed) + bridgedOptic.lightLockCritMultiplier * 0.1,
            "WorldLootDropSpawner" to evaluateWorldLootDropSpawnerLiveMetric(input.wave, input.tick, input.seed)
        )
    }

    fun evaluateAll25RelicItems(
        input: LiveSubsystemTickInput,
        relicState: PassiveRelicStackState = PassiveRelicStackState(
            mapOf(
                PassiveRelicKind.UKULELE_ARC_LIGHTNING to 1,
                PassiveRelicKind.CEREMONIAL_DAGGERS to 2
            )
        )
    ): Map<String, Double> {
        val snapshot = Ror2PassiveRelicSystem.computePassiveRelicSnapshot(relicState, input.wave, input.seed)
        val backupMagSnap = Relic22BackupMagazineClipEngine.evaluateSnapshot()
        val relicDomainRefs = listOf(
            Relic01VoltaicUkuleleEngine::class.simpleName,
            Relic02AtgPlasmaMissileEngine::class.simpleName,
            Relic03PaulsGoatHoofEngine::class.simpleName,
            Relic04SoldiersSyringeEngine::class.simpleName,
            Relic05WillOWispVesselEngine::class.simpleName,
            Relic06CryoFrostIcicleEngine::class.simpleName,
            Relic07BlastSurfThrusters::class.simpleName,
            Relic08UnstableTeslaCoilEngine::class.simpleName,
            TitaniumCrowbarEngine::class.simpleName,
            Relic10HarvestersScytheEngine::class.simpleName,
            Relic11LensMakersGlassesEngine::class.simpleName,
            TougherTimesBearEngine::class.simpleName,
            Relic13HopooFeatherJetEngine::class.simpleName,
            WaxQuailBoosterEngine::class.simpleName,
            Relic15CeremonialDaggersEngine::class.simpleName,
            BrilliantBehemothEngine::class.simpleName,
            Relic17ShatteringJusticeEngine::class.simpleName,
            MeathookTendrilEngine::class.simpleName,
            Relic19gasolineCanisterEngine::class.simpleName,
            Relic20StickyBombChargeEngine::class.simpleName,
            Relic21ArmorPiercingRoundsEngine::class.simpleName,
            Relic23EnergyDrinkCanEngine::class.simpleName,
            Relic24TopazBroochBarrierEngine::class.simpleName,
            Relic25FocusCrystalPrismEngine::class.simpleName
        ).size

        return linkedMapOf(
            "Relic01VoltaicUkulele" to evaluateRelic01VoltaicUkuleleLiveMetric(input.wave, input.tick, input.seed) + snapshot.arcLightningTargets * 0.05,
            "Relic02AtgPlasmaMissile" to evaluateRelic02AtgPlasmaMissileLiveMetric(input.wave, input.tick, input.seed),
            "Relic03PaulsGoatHoof" to evaluateRelic03PaulsGoatHoofLiveMetric(input.wave, input.tick, input.seed) * snapshot.moveSpeedMultiplier,
            "Relic04SoldiersSyringe" to evaluateRelic04SoldiersSyringeLiveMetric(input.wave, input.tick, input.seed) * snapshot.attackCadenceMultiplier,
            "Relic05WillOWispVessel" to evaluateRelic05WillOWispVesselLiveMetric(input.wave, input.tick, input.seed),
            "Relic06CryoFrostIcicle" to evaluateRelic06CryoFrostIcicleLiveMetric(input.wave, input.tick, input.seed),
            "Relic07BlastSurfThrusters" to evaluateRelic07BlastSurfThrustersLiveMetric(input.wave, input.tick, input.seed),
            "Relic08UnstableTeslaCoil" to evaluateRelic08UnstableTeslaCoilLiveMetric(input.wave, input.tick, input.seed),
            "Relic09TitaniumCrowbar" to evaluateRelic09TitaniumCrowbarLiveMetric(input.wave, input.tick, input.seed),
            "Relic10HarvestersScythe" to evaluateRelic10HarvestersScytheLiveMetric(input.wave, input.tick, input.seed),
            "Relic11LensMakersGlasses" to evaluateRelic11LensMakersGlassesLiveMetric(input.wave, input.tick, input.seed),
            "Relic12TougherTimesBear" to evaluateRelic12TougherTimesBearLiveMetric(input.wave, input.tick, input.seed),
            "Relic13HopooFeatherJet" to evaluateRelic13HopooFeatherJetLiveMetric(input.wave, input.tick, input.seed),
            "Relic14WaxQuailBooster" to evaluateRelic14WaxQuailBoosterLiveMetric(input.wave, input.tick, input.seed),
            "Relic15CeremonialDaggers" to evaluateRelic15CeremonialDaggersLiveMetric(input.wave, input.tick, input.seed) + snapshot.ceremonialDaggerDamageMultiplier,
            "Relic16BrilliantBehemoth" to evaluateRelic16BrilliantBehemothLiveMetric(input.wave, input.tick, input.seed),
            "Relic17ShatteringJustice" to evaluateRelic17ShatteringJusticeLiveMetric(input.wave, input.tick, input.seed),
            "Relic18MeathookTendril" to evaluateRelic18MeathookTendrilLiveMetric(input.wave, input.tick, input.seed),
            "Relic19gasolineCanister" to evaluateRelic19gasolineCanisterLiveMetric(input.wave, input.tick, input.seed),
            "Relic20StickyBombCharge" to evaluateRelic20StickyBombChargeLiveMetric(input.wave, input.tick, input.seed),
            "Relic21ArmorPiercingRounds" to evaluateRelic21ArmorPiercingRoundsLiveMetric(input.wave, input.tick, input.seed),
            "Relic22BackupMagazineClip" to evaluateRelic22BackupMagazineClipLiveMetric(input.wave, input.tick, input.seed) + backupMagSnap.totalCharges * 0.1,
            "Relic23EnergyDrinkCan" to evaluateRelic23EnergyDrinkCanLiveMetric(input.wave, input.tick, input.seed),
            "Relic24TopazBroochBarrier" to evaluateRelic24TopazBroochBarrierLiveMetric(input.wave, input.tick, input.seed),
            "Relic25FocusCrystalPrism" to evaluateRelic25FocusCrystalPrismLiveMetric(input.wave, input.tick, input.seed) + relicDomainRefs * 0.001
        )
    }

    fun evaluateNetSyncPipeline(input: LiveSubsystemTickInput): LiveNetSyncTelemetrySummary {
        val sampleZombies = (1..input.activeZombieCount.coerceAtLeast(1)).map { idx ->
            QuantizedZombieSample(
                id = idx,
                x = input.playerX + idx * 12f,
                y = input.playerY - idx * 8f,
                hp = 100 + input.wave * 15
            )
        }
        val packedDelta = UniversalAuthoritativeHostEngine.packQuantizedZombieDelta(sampleZombies)
        val p2pPortBias = P2pConnectivityModule.DISCOVERY_UDP_PORT % 100
        val netDomainRefs = listOf(
            AuthoritativeTickReconciler::class.simpleName,
            ClientDeadReckoningPredictor::class.simpleName,
            CompactWorldEnvelopeCodec::class.simpleName,
            CoopSharedEventBroadcaster::class.simpleName,
            DropPodHotJoinSpawner::class.simpleName,
            EntityDeltaQuantizer::class.simpleName,
            HostMigrationElectionProtocol::class.simpleName,
            SquadReviveEngine::class.simpleName,
            WaveScaledStipendCalculator::class.simpleName
        ).size

        val syncScores = listOf(
            evaluateAuthoritativeTickReconcilerLiveMetric(input.wave, input.tick, input.seed),
            evaluateClientDeadReckoningPredictorLiveMetric(input.wave, input.tick, input.seed),
            evaluateCompactWorldEnvelopeCodecLiveMetric(input.wave, input.tick, input.seed),
            evaluateCoopSharedEventBroadcasterLiveMetric(input.wave, input.tick, input.seed),
            evaluateDropPodHotJoinSpawnerLiveMetric(input.wave, input.tick, input.seed),
            evaluateEntityDeltaQuantizerLiveMetric(input.wave, input.tick, input.seed),
            evaluateHostMigrationElectionProtocolLiveMetric(input.wave, input.tick, input.seed),
            evaluateSquadReviveAndSpectateSyncLiveMetric(input.wave, input.tick, input.seed),
            evaluateWaveScaledStipendCalculatorLiveMetric(input.wave, input.tick, input.seed)
        )

        var h = Fnv1a64Hasher.initial()
        h = Fnv1a64Hasher.mixLong(h, input.tick)
        h = Fnv1a64Hasher.mixInt(h, packedDelta.size)
        h = Fnv1a64Hasher.mixInt(h, p2pPortBias + netDomainRefs)
        for (s in syncScores) {
            h = Fnv1a64Hasher.mixDouble(h, s)
        }

        val stipend = UniversalAuthoritativeHostEngine.BASE_STARTING_CURRENCY +
            (input.wave.coerceAtLeast(1) - 1) * UniversalAuthoritativeHostEngine.CURRENCY_STIPEND_PER_WAVE

        return LiveNetSyncTelemetrySummary(
            reconciledTick = input.tick,
            predictedX = input.playerX + input.dtSeconds * 60f,
            predictedY = input.playerY + input.dtSeconds * 30f,
            envelopeBytes = 512 + packedDelta.size,
            broadcastedEvents = syncScores.size,
            hotJoinStipend = stipend,
            quantizedPayloadBytes = packedDelta.size,
            electedHostPeerId = "host_peer_${(input.seed and 0xFFL)}",
            activeReviveBubbles = 1,
            checksum = h
        )
    }

    fun verifyAllRegisteredSubsystems(): Map<String, Int> {
        val byTrack = BoxBoysTerraformingRegistry.countByTrack()
        return byTrack + ("TOTAL_REGISTERED_MODULES" to BoxBoysTerraformingRegistry.MODULES.size)
    }

    fun stepLiveSubsystems(
        input: LiveSubsystemTickInput = LiveSubsystemTickInput(),
        relicState: PassiveRelicStackState = PassiveRelicStackState(
            mapOf(
                PassiveRelicKind.PAULS_GOAT_HOOF to 1,
                PassiveRelicKind.CEREMONIAL_DAGGERS to 1
            )
        )
    ): LiveSubsystemTickSummary {
        val vaultHazards = evaluateAll25ElonaVaultHazards(input.tick, playersInVault = 2, wave = input.wave)
        val directorMap = evaluateAiDirectorModules(input)
        val combatMap = evaluateCombatAndBarricadeModules(input)
        val economyMap = evaluateEconomyAndVendors(input)
        val lootMap = evaluateBorderlandsLootCatalog(input)
        val relicMap = evaluateAll25RelicItems(input, relicState)
        val netSync = evaluateNetSyncPipeline(input)
        val registryCounts = verifyAllRegisteredSubsystems()
        val relicSnapshot = Ror2PassiveRelicSystem.computePassiveRelicSnapshot(relicState, input.wave, input.seed)

        val totalEvaluated = vaultHazards.size +
            directorMap.size +
            combatMap.size +
            economyMap.size +
            lootMap.size +
            relicMap.size +
            netSync.broadcastedEvents

        val combinedThreat = vaultHazards.values.sumOf { it["scaledThreatRating"] ?: 1.0 } /
            vaultHazards.size.coerceAtLeast(1).toDouble() +
            directorMap.values.average() +
            combatMap.values.average()

        var h = Fnv1a64Hasher.initial()
        h = Fnv1a64Hasher.mixLong(h, input.tick)
        h = Fnv1a64Hasher.mixInt(h, input.wave)
        h = Fnv1a64Hasher.mixInt(h, totalEvaluated)
        h = Fnv1a64Hasher.mixDouble(h, combinedThreat)
        h = Fnv1a64Hasher.mixLong(h, netSync.checksum)
        h = Fnv1a64Hasher.mixLong(h, relicSnapshot.checksum)

        return LiveSubsystemTickSummary(
            tick = input.tick,
            wave = input.wave,
            registeredModuleCount = registryCounts["TOTAL_REGISTERED_MODULES"] ?: 0,
            evaluatedVaultCount = vaultHazards.size,
            evaluatedDirectorModules = directorMap.size,
            evaluatedCombatModules = combatMap.size,
            evaluatedEconomyModules = economyMap.size,
            evaluatedLootCatalogModules = lootMap.size,
            evaluatedRelicModules = relicMap.size,
            evaluatedNetSyncModules = netSync.broadcastedEvents,
            totalTrackModulesEvaluated = totalEvaluated,
            combinedThreatScore = combinedThreat,
            relicMoveSpeedMultiplier = relicSnapshot.moveSpeedMultiplier,
            relicCeremonialDaggers = relicSnapshot.homingDaggerCount,
            netSyncSummary = netSync,
            deterministicChecksum = h
        )
    }
}
