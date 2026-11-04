package com.example.game.contracts

import com.example.game.world.elona.RoomSocketSpec
import com.example.game.world.elona.SectionComponentDef

/**
 * Shared subsystem contracts, Elona Vault blueprint descriptors, and live 60Hz tick telemetry
 * envelopes unifying all 147 BoxBoys Terraforming track modules.
 */
data class ElonaRoomBlueprint(
    val componentDef: SectionComponentDef,
    val biomeTheme: String,
    val vaultArchetype: String,
    val hazardLayout: List<String>,
    val barricadeAnchors: List<Pair<Int, Int>>,
    val explosiveBarrelAnchors: List<Pair<Int, Int>>,
    val vendingMachineAnchors: List<Pair<Int, Int>>,
    val lootChestSpawnWeights: Map<String, Double>,
    val baseThreatRating: Double,
    val checksum: Long
) {
    val componentId: String
        get() = componentDef.componentId

    val sockets: List<RoomSocketSpec>
        get() = componentDef.sockets

    fun scaleThreatForWave(wave: Int, playersInVault: Int): Double {
        val safeWave = wave.coerceAtLeast(1)
        val safePlayers = playersInVault.coerceIn(1, 4)
        return baseThreatRating * (1.0 + (safeWave - 1) * 0.14) * (1.0 + (safePlayers - 1) * 0.22)
    }
}

interface ElonaVaultBlueprintProvider {
    fun toSectionComponentDef(): SectionComponentDef
    fun toElonaRoomBlueprint(): ElonaRoomBlueprint
    fun evaluateVaultHazardTick(tick: Long, playersInVault: Int, wave: Int): Map<String, Double>
    fun computeChecksum(): Long
}

data class LiveSubsystemTickInput(
    val tick: Long = 1L,
    val wave: Int = 1,
    val dtSeconds: Float = 0.016f,
    val playerX: Float = 420f,
    val playerY: Float = 420f,
    val playerHp: Int = 100,
    val playerMaxHp: Int = 100,
    val currencyPoints: Int = 2500,
    val activeZombieCount: Int = 8,
    val comboStreak: Int = 5,
    val seed: Long = 20261104L
)

data class LiveNetSyncTelemetrySummary(
    val reconciledTick: Long,
    val predictedX: Float,
    val predictedY: Float,
    val envelopeBytes: Int,
    val broadcastedEvents: Int,
    val hotJoinStipend: Int,
    val quantizedPayloadBytes: Int,
    val electedHostPeerId: String,
    val activeReviveBubbles: Int,
    val checksum: Long
)

data class LiveSubsystemTickSummary(
    val tick: Long,
    val wave: Int,
    val registeredModuleCount: Int,
    val evaluatedVaultCount: Int,
    val evaluatedDirectorModules: Int,
    val evaluatedCombatModules: Int,
    val evaluatedEconomyModules: Int,
    val evaluatedLootCatalogModules: Int,
    val evaluatedRelicModules: Int,
    val evaluatedNetSyncModules: Int,
    val totalTrackModulesEvaluated: Int,
    val combinedThreatScore: Double,
    val relicMoveSpeedMultiplier: Float,
    val relicCeremonialDaggers: Int,
    val netSyncSummary: LiveNetSyncTelemetrySummary,
    val deterministicChecksum: Long
) {
    val isHealthy: Boolean
        get() = totalTrackModulesEvaluated >= 147 &&
            !combinedThreatScore.isNaN() &&
            !relicMoveSpeedMultiplier.isNaN() &&
            deterministicChecksum != 0L
}

object Fnv1a64Hasher {
    private const val OFFSET_BASIS = -3750763034362895579L
    private const val PRIME = 1099511628211L

    fun initial(): Long = OFFSET_BASIS

    fun mixLong(hash: Long, value: Long): Long = (hash xor value) * PRIME

    fun mixInt(hash: Long, value: Int): Long = (hash xor value.toLong()) * PRIME

    fun mixDouble(hash: Long, value: Double): Long =
        (hash xor java.lang.Double.doubleToRawLongBits(value)) * PRIME

    fun mixString(hash: Long, value: String): Long {
        var h = hash
        for (ch in value) {
            h = (h xor ch.code.toLong()) * PRIME
        }
        return h
    }

    fun hashSectionComponentDef(def: SectionComponentDef): Long {
        var h = initial()
        h = mixString(h, def.componentId)
        h = mixString(h, def.biomeTag)
        h = mixInt(h, def.gridWidth)
        h = mixInt(h, def.gridHeight)
        for (row in def.rows) {
            h = mixString(h, row)
        }
        for (sock in def.sockets) {
            h = mixString(h, sock.direction.name)
            h = mixString(h, sock.kind.name)
            h = mixInt(h, sock.unlockCostCurrency)
        }
        return h
    }

    fun extractCoordinates(def: SectionComponentDef, symbol: Char): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>()
        for (y in def.rows.indices) {
            val row = def.rows[y]
            for (x in row.indices) {
                if (row[x] == symbol) {
                    out.add(x to y)
                }
            }
        }
        return out
    }
}
