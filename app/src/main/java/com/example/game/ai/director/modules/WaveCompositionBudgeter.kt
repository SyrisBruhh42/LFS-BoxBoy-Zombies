package com.example.game.ai.director.modules

import com.example.game.ZombieArchetype
import kotlin.math.max
import kotlin.math.min

class WaveCompositionBudgeter {

    data class WaveSpawnBudget(
        val shamblers: Int,
        val sprinters: Int,
        val stalkers: Int,
        val weavers: Int,
        val devils: Int,
        val totalBudgetCost: Int
    )

    fun calculateWaveBudget(waveNumber: Int): Int = Companion.calculateWaveBudget(waveNumber)

    fun allocateZombies(waveNumber: Int, budget: Int): Map<ZombieArchetype, Int> =
        Companion.allocateZombies(waveNumber, budget)

    companion object {
        fun calculateWaveBudget(waveNumber: Int): Int {
            val w = max(1, waveNumber)
            return 10 + 2 * w * w + 5 * w
        }

        fun allocateZombies(waveNumber: Int, budget: Int): Map<ZombieArchetype, Int> {
            val w = max(1, waveNumber)
            if (w == 1) {
                return mapOf(ZombieArchetype.SHAMBLER to max(1, budget / 7))
            }
            val result = linkedMapOf<ZombieArchetype, Int>()
            if (w % 5 == 0) {
                result[ZombieArchetype.DEVIL_BOSS] = w / 5
            }
            if (w >= 4) {
                result[ZombieArchetype.ABYSSAL_WEAVER] = max(1, w / 4)
            }
            if (w >= 3) {
                result[ZombieArchetype.CALCIFIED_STALKER] = max(1, w / 3)
            }
            result[ZombieArchetype.SPRINTER] = max(1, budget / 14)
            result[ZombieArchetype.SHAMBLER] = max(1, budget / 7)
            return result
        }

        fun calculateBudget(waveNumber: Int, players: Int, isBloodMoon: Boolean = false): WaveSpawnBudget {
            var totalBudgetPoints = (waveNumber * 10 + waveNumber * waveNumber) * max(1, players)

            if (isBloodMoon) {
                totalBudgetPoints = (totalBudgetPoints * 1.5f).toInt()
            }

            var remainingBudget = totalBudgetPoints

            var devils = 0
            if (waveNumber % 4 == 0) {
                devils = 1 + (waveNumber / 12) * max(1, players - 1)
                val cost = devils * 50
                if (remainingBudget >= cost) {
                    remainingBudget -= cost
                } else {
                    devils = 0
                }
            }

            var weavers = 0
            if (waveNumber >= 3) {
                val maxWeavers = waveNumber / 3 + players
                val costPerWeaver = 20
                val targetWeaverCost = (remainingBudget * 0.4f).toInt()
                weavers = min(maxWeavers, targetWeaverCost / costPerWeaver)
                if (weavers == 0 && remainingBudget >= costPerWeaver) weavers = 1
                remainingBudget -= weavers * costPerWeaver
            }

            var stalkers = 0
            if (waveNumber >= 2) {
                val maxStalkers = waveNumber / 2 + players * 2
                val costPerStalker = 15
                val targetStalkerCost = (remainingBudget * 0.35f).toInt()
                stalkers = min(maxStalkers, targetStalkerCost / costPerStalker)
                if (stalkers == 0 && remainingBudget >= costPerStalker) stalkers = 1
                remainingBudget -= stalkers * costPerStalker
            }

            var sprinters = 0
            if (waveNumber >= 2) {
                val costPerSprinter = 10
                val targetSprinterCost = (remainingBudget * 0.4f).toInt()
                sprinters = targetSprinterCost / costPerSprinter
                if (sprinters == 0 && remainingBudget >= costPerSprinter) sprinters = 1
                remainingBudget -= sprinters * costPerSprinter
            }

            val costPerShambler = 5
            val shamblers = max(0, remainingBudget / costPerShambler)
            val finalBudgetCost = totalBudgetPoints - remainingBudget + shamblers * costPerShambler

            return WaveSpawnBudget(
                shamblers = shamblers,
                sprinters = sprinters,
                stalkers = stalkers,
                weavers = weavers,
                devils = devils,
                totalBudgetCost = finalBudgetCost
            )
        }
    }
}


/**
 * Deterministic 60Hz subsystem telemetry & FNV-1a verification receipt for [WaveCompositionBudgeter].
 */
data class WaveCompositionBudgeterTelemetryReceipt(
    val moduleName: String = "WaveCompositionBudgeter",
    val wave: Int,
    val tick: Long,
    val metricScore: Double,
    val checksum: Long
)

fun computeWaveCompositionBudgeterTelemetryReceipt(wave: Int, tick: Long, seed: Long = 42L): WaveCompositionBudgeterTelemetryReceipt {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var hash = -3750763034362895579L xor seed
    for (ch in "WaveCompositionBudgeter") {
        hash = (hash xor ch.code.toLong()) * 1099511628211L
    }
    hash = (hash xor safeWave.toLong()) * 1099511628211L
    hash = (hash xor safeTick) * 1099511628211L
    val score = (1.0 + safeWave * 0.12) * (1.0 + (safeTick % 60L) * 0.005)
    return WaveCompositionBudgeterTelemetryReceipt(
        wave = safeWave,
        tick = safeTick,
        metricScore = score,
        checksum = hash
    )
}


fun evaluateWaveCompositionBudgeterLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "WaveCompositionBudgeter") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
