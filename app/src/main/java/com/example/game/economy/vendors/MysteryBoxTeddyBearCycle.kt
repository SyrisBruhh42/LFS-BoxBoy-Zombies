package com.example.game.economy.vendors

import androidx.annotation.Keep
import com.example.game.ModularWeaponGenerator
import com.example.game.ModularWeaponInstance
import kotlin.random.Random

@Keep
data class MysteryBoxState(
    val currentStationId: String,
    val x: Float,
    val y: Float,
    val cost: Int = 950,
    val rollInProgress: Boolean = false,
    val rollTimeRemainingMs: Float = 0f,
    val offeredWeapon: ModularWeaponInstance? = null,
    val weaponOfferTimeRemainingMs: Float = 0f,
    val isTeddyBearRoll: Boolean = false,
    val usesAtCurrentLocation: Int = 0,
    val maxUsesBeforeTeddy: Int = 5
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "currentStationId" to currentStationId,
        "x" to x,
        "y" to y,
        "cost" to cost,
        "rollInProgress" to rollInProgress,
        "rollTimeRemainingMs" to rollTimeRemainingMs,
        "offeredWeapon" to offeredWeapon?.instanceId, // Simplified for serialization
        "weaponOfferTimeRemainingMs" to weaponOfferTimeRemainingMs,
        "isTeddyBearRoll" to isTeddyBearRoll,
        "usesAtCurrentLocation" to usesAtCurrentLocation,
        "maxUsesBeforeTeddy" to maxUsesBeforeTeddy
    )

    companion object {
        fun fromMap(map: Map<String, Any?>, availableWeapons: Map<String, ModularWeaponInstance> = emptyMap()): MysteryBoxState {
            val weaponId = map["offeredWeapon"] as? String
            return MysteryBoxState(
                currentStationId = map["currentStationId"] as? String ?: "",
                x = (map["x"] as? Number)?.toFloat() ?: 0f,
                y = (map["y"] as? Number)?.toFloat() ?: 0f,
                cost = (map["cost"] as? Number)?.toInt() ?: 950,
                rollInProgress = map["rollInProgress"] as? Boolean ?: false,
                rollTimeRemainingMs = (map["rollTimeRemainingMs"] as? Number)?.toFloat() ?: 0f,
                offeredWeapon = if (weaponId != null) availableWeapons[weaponId] else null,
                weaponOfferTimeRemainingMs = (map["weaponOfferTimeRemainingMs"] as? Number)?.toFloat() ?: 0f,
                isTeddyBearRoll = map["isTeddyBearRoll"] as? Boolean ?: false,
                usesAtCurrentLocation = (map["usesAtCurrentLocation"] as? Number)?.toInt() ?: 0,
                maxUsesBeforeTeddy = (map["maxUsesBeforeTeddy"] as? Number)?.toInt() ?: 5
            )
        }
    }
}

class MysteryBoxTeddyBearCycle(
    private val boxLocations: List<Pair<String, Pair<Float, Float>>> // List of stationId to (x, y)
) {
    companion object {
        const val ROLL_DURATION_MS = 3000f
        const val OFFER_DURATION_MS = 10000f
    }

    init {
        require(boxLocations.isNotEmpty()) { "Must provide at least one Mystery Box location" }
    }

    fun initialize(seed: Long): MysteryBoxState {
        val rng = Random(seed)
        val initialLoc = boxLocations.random(rng)
        return MysteryBoxState(
            currentStationId = initialLoc.first,
            x = initialLoc.second.first,
            y = initialLoc.second.second,
            maxUsesBeforeTeddy = 3 + rng.nextInt(5) // Random uses between 3 and 7
        )
    }

    fun step(state: MysteryBoxState, dtSeconds: Float): MysteryBoxState {
        require(dtSeconds.isFinite() && !dtSeconds.isNaN()) { "dtSeconds must be finite and not NaN" }
        val clampedDtMs = dtSeconds.coerceIn(0.001f, 0.100f) * 1000f
        
        var newState = state

        if (newState.rollInProgress) {
            val newRollTime = newState.rollTimeRemainingMs - clampedDtMs
            if (newRollTime <= 0f) {
                // Roll finished
                if (newState.isTeddyBearRoll) {
                    newState = relocateBox(newState)
                } else {
                    newState = newState.copy(
                        rollInProgress = false,
                        rollTimeRemainingMs = 0f,
                        weaponOfferTimeRemainingMs = OFFER_DURATION_MS
                    )
                }
            } else {
                newState = newState.copy(rollTimeRemainingMs = newRollTime)
            }
        } else if (newState.weaponOfferTimeRemainingMs > 0f) {
            val newOfferTime = newState.weaponOfferTimeRemainingMs - clampedDtMs
            if (newOfferTime <= 0f) {
                // Offer expired
                newState = newState.copy(
                    weaponOfferTimeRemainingMs = 0f,
                    offeredWeapon = null
                )
            } else {
                newState = newState.copy(weaponOfferTimeRemainingMs = newOfferTime)
            }
        }

        return newState
    }

    fun attemptPurchase(
        state: MysteryBoxState,
        playerCurrency: Int,
        wave: Int,
        seed: Long
    ): Pair<MysteryBoxState, Boolean> {
        if (state.rollInProgress || state.weaponOfferTimeRemainingMs > 0f) {
            return Pair(state, false) // Box is busy
        }

        if (playerCurrency < state.cost) {
            return Pair(state, false) // Not enough money
        }

        val rng = Random(seed xor (state.usesAtCurrentLocation.toLong() * 31L))
        
        val uses = state.usesAtCurrentLocation + 1
        val willBeTeddy = uses >= state.maxUsesBeforeTeddy || (uses > 1 && rng.nextFloat() < (uses - 1) * 0.15f)

        if (willBeTeddy) {
            return Pair(
                state.copy(
                    rollInProgress = true,
                    rollTimeRemainingMs = ROLL_DURATION_MS,
                    isTeddyBearRoll = true,
                    usesAtCurrentLocation = uses
                ),
                true
            )
        } else {
            val rolledWeapon = ModularWeaponGenerator.generateProceduralWeapon(
                seed = seed xor 0x12345678L,
                wave = wave
            )
            return Pair(
                state.copy(
                    rollInProgress = true,
                    rollTimeRemainingMs = ROLL_DURATION_MS,
                    offeredWeapon = rolledWeapon,
                    isTeddyBearRoll = false,
                    usesAtCurrentLocation = uses
                ),
                true
            )
        }
    }
    
    fun grabWeapon(state: MysteryBoxState): Pair<MysteryBoxState, ModularWeaponInstance?> {
        if (state.offeredWeapon != null && state.weaponOfferTimeRemainingMs > 0f) {
            val weapon = state.offeredWeapon
            return Pair(
                state.copy(
                    offeredWeapon = null,
                    weaponOfferTimeRemainingMs = 0f
                ),
                weapon
            )
        }
        return Pair(state, null)
    }

    private fun relocateBox(state: MysteryBoxState): MysteryBoxState {
        val currentIndex = boxLocations.indexOfFirst { it.first == state.currentStationId }
        val nextIndex = if (currentIndex == -1 || currentIndex == boxLocations.size - 1) 0 else currentIndex + 1
        val nextLoc = boxLocations[nextIndex]
        
        val rng = Random(state.currentStationId.hashCode().toLong() xor 0x98765432L) 
        val nextMaxUses = 3 + rng.nextInt(5)

        return state.copy(
            currentStationId = nextLoc.first,
            x = nextLoc.second.first,
            y = nextLoc.second.second,
            rollInProgress = false,
            rollTimeRemainingMs = 0f,
            isTeddyBearRoll = false,
            offeredWeapon = null,
            weaponOfferTimeRemainingMs = 0f,
            usesAtCurrentLocation = 0,
            maxUsesBeforeTeddy = nextMaxUses
        )
    }
}


fun evaluateMysteryBoxTeddyBearCycleLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "MysteryBoxTeddyBearCycle") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
