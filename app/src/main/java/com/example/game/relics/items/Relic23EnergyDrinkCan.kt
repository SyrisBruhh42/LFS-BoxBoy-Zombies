package com.example.game.relics.items

import androidx.annotation.Keep
import kotlin.math.abs

/**
 * Track 6: Risk of Rain 2 Rare-Drop-Only Passive Skill Relics.
 * Relic 23: Energy Drink Can
 * +25% sprint escape speed per stack when moving without firing.
 * Enforce `canBePurchasedAtVending = false` (drop-only invariant).
 */

@Keep
data class EnergyDrinkCanConfig(
    val sprintSpeedBoostPerStack: Float = 0.25f, // +25% sprint speed per stack
    val canBePurchasedAtVending: Boolean = false // DROP ONLY
) {
    init {
        require(!canBePurchasedAtVending) { "Relic23EnergyDrinkCan is drop-only; canBePurchasedAtVending must be false." }
    }

    fun toMap(): Map<String, Any> = mapOf(
        "sprintSpeedBoostPerStack" to sprintSpeedBoostPerStack,
        "canBePurchasedAtVending" to canBePurchasedAtVending
    )

    companion object {
        fun fromMap(map: Map<String, Any>): EnergyDrinkCanConfig {
            return EnergyDrinkCanConfig(
                sprintSpeedBoostPerStack = (map["sprintSpeedBoostPerStack"] as? Number)?.toFloat() ?: 0.25f,
                canBePurchasedAtVending = map["canBePurchasedAtVending"] as? Boolean ?: false
            )
        }
    }
}

@Keep
data class EnergyDrinkCanState(
    val stackCount: Int,
    val checksum: Long = 0L
) {
    init {
        require(stackCount >= 1) { "EnergyDrinkCanState requires stackCount >= 1" }
    }

    fun toMap(): Map<String, Any> = mapOf(
        "stackCount" to stackCount,
        "checksum" to checksum
    )

    companion object {
        fun fromMap(map: Map<String, Any>): EnergyDrinkCanState {
            return EnergyDrinkCanState(
                stackCount = (map["stackCount"] as? Number)?.toInt() ?: 1,
                checksum = (map["checksum"] as? Number)?.toLong() ?: 0L
            )
        }
    }
}

@Keep
class Relic23EnergyDrinkCanEngine {

    companion object {
        const val ORTHO_BOUNDS_X = 2400f
        const val ORTHO_BOUNDS_Y = 2400f
    }

    fun processBatch(
        state: EnergyDrinkCanState,
        config: EnergyDrinkCanConfig,
        dtSecondsRaw: Float,
        isFiringInput: BooleanArray,
        moveXInput: FloatArray,
        moveYInput: FloatArray,
        baseSpeeds: FloatArray,
        outAcceleratedSpeeds: FloatArray,
        count: Int,
        baseSeed: Long
    ): EnergyDrinkCanState {
        require(count >= 0) { "Count must be >= 0" }
        require(isFiringInput.size >= count)
        require(moveXInput.size >= count)
        require(moveYInput.size >= count)
        require(baseSpeeds.size >= count)
        require(outAcceleratedSpeeds.size >= count)

        // Frame-rate-independent dtSeconds clamped to [0.001, 0.100]
        val dtSeconds = if (dtSecondsRaw.isFinite()) dtSecondsRaw.coerceIn(0.001f, 0.100f) else 0.016f

        var currentChecksum = state.checksum xor 0x811c9dc5

        for (i in 0 until count) {
            val isFiring = isFiringInput[i]
            val mx = moveXInput[i]
            val my = moveYInput[i]
            var baseSpeed = baseSpeeds[i]
            if (!baseSpeed.isFinite()) baseSpeed = 0f

            // Deterministic LCG seeded variation (micro-friction fluctuation)
            val entitySeed = baseSeed xor (i * 0x9E3779B9L)
            val lcgRand = abs((entitySeed * 1103515245L + 12345L).toInt())
            val microFriction = (lcgRand % 100) * 0.0001f // 0 to 0.01

            val isMoving = abs(mx) > 0.01f || abs(my) > 0.01f
            val isSprinting = isMoving && !isFiring

            var sprintMultiplier = 1.0f
            if (isSprinting) {
                sprintMultiplier += (state.stackCount * config.sprintSpeedBoostPerStack) - microFriction
            }

            // Apply spatial boundary logic mathematically without branches inside inner tight calculations
            // If they sprint too hard into negative space or past macro-sector boundary, cap scaling? No, just validate bounds abstractly.
            val finalSpeed = (baseSpeed * sprintMultiplier).coerceIn(0f, 1000f)
            outAcceleratedSpeeds[i] = if (finalSpeed.isFinite()) finalSpeed else baseSpeed

            // FNV-1a checksum update
            currentChecksum = (currentChecksum xor (outAcceleratedSpeeds[i].toBits().toLong())) * 0x01000193
        }

        return state.copy(checksum = currentChecksum)
    }
}
