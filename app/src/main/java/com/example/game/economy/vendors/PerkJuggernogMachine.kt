package com.example.game.economy.vendors

import androidx.annotation.Keep
import com.example.data.PerkType
import com.example.game.GameSessionState
import com.example.game.economy.VendingCatalogOffer
import com.example.game.economy.VendingMachineBrand
import kotlin.math.abs
import kotlin.math.min

@Keep
data class JuggernogMachineConfig(
    val interactionRadius: Float = 150f,
    val cost: Int = PerkType.JUGGERNOG.cost,
    val bonusMaxHpMultiplier: Int = 2,
    val bonusShieldRegenRate: Float = 5.0f
) {
    fun toMap(): Map<String, Any> = mapOf(
        "interactionRadius" to interactionRadius,
        "cost" to cost,
        "bonusMaxHpMultiplier" to bonusMaxHpMultiplier,
        "bonusShieldRegenRate" to bonusShieldRegenRate
    )

    companion object {
        fun fromMap(map: Map<String, Any>): JuggernogMachineConfig = JuggernogMachineConfig(
            interactionRadius = (map["interactionRadius"] as? Number)?.toFloat() ?: 150f,
            cost = (map["cost"] as? Number)?.toInt() ?: PerkType.JUGGERNOG.cost,
            bonusMaxHpMultiplier = (map["bonusMaxHpMultiplier"] as? Number)?.toInt() ?: 2,
            bonusShieldRegenRate = (map["bonusShieldRegenRate"] as? Number)?.toFloat() ?: 5.0f
        )
    }
}

@Keep
data class JuggernogMachineState(
    val machineId: String,
    val x: Float,
    val y: Float,
    val isActive: Boolean = true,
    val usesCount: Int = 0
) {
    fun computeChecksum(): Long {
        var hash = -2128831035157016569L
        for (char in machineId) {
            hash = hash xor char.code.toLong()
            hash = hash * 1099511628211L
        }
        hash = hash xor x.toBits().toLong()
        hash = hash * 1099511628211L
        hash = hash xor y.toBits().toLong()
        hash = hash * 1099511628211L
        hash = hash xor (if (isActive) 1L else 0L)
        hash = hash * 1099511628211L
        hash = hash xor usesCount.toLong()
        hash = hash * 1099511628211L
        return hash
    }

    fun toMap(): Map<String, Any> = mapOf(
        "machineId" to machineId,
        "x" to x,
        "y" to y,
        "isActive" to isActive,
        "usesCount" to usesCount
    )

    companion object {
        fun fromMap(map: Map<String, Any>): JuggernogMachineState = JuggernogMachineState(
            machineId = map["machineId"] as? String ?: "jugg_default",
            x = (map["x"] as? Number)?.toFloat() ?: 0f,
            y = (map["y"] as? Number)?.toFloat() ?: 0f,
            isActive = map["isActive"] as? Boolean ?: true,
            usesCount = (map["usesCount"] as? Number)?.toInt() ?: 0
        )
    }
}

sealed class JuggernogPurchaseResult {
    data class Success(val newState: GameSessionState, val updatedMachine: JuggernogMachineState, val message: String) : JuggernogPurchaseResult()
    data class Error(val reason: String) : JuggernogPurchaseResult()
}

object PerkJuggernogMachine {

    fun processPurchase(
        state: GameSessionState,
        machineState: JuggernogMachineState,
        config: JuggernogMachineConfig = JuggernogMachineConfig(),
        offer: VendingCatalogOffer? = null
    ): JuggernogPurchaseResult {
        if (!machineState.isActive) {
            return JuggernogPurchaseResult.Error("Juggernog Machine is offline or unpowered.")
        }
        
        val dx = state.playerX - machineState.x
        val dy = state.playerY - machineState.y
        val distSq = dx * dx + dy * dy
        if (!distSq.isFinite() || distSq > config.interactionRadius * config.interactionRadius) {
            return JuggernogPurchaseResult.Error("Player is out of interaction range.")
        }

        val cost = offer?.costCurrency ?: config.cost
        
        if (offer != null && offer.brand != VendingMachineBrand.PERK_A_COLA_FOUNTAIN) {
            return JuggernogPurchaseResult.Error("Vendor Brand Mismatch: Required PERK_A_COLA_FOUNTAIN")
        }

        if (offer != null && offer.perkPayload != PerkType.JUGGERNOG) {
            return JuggernogPurchaseResult.Error("Perk Payload Mismatch: Required JUGGERNOG")
        }

        if (PerkType.JUGGERNOG in state.activePerks) {
            return JuggernogPurchaseResult.Error("Juggernog is already active.")
        }

        if (state.points < cost) {
            return JuggernogPurchaseResult.Error("Insufficient Currency Points")
        }

        val newPoints = state.points - cost
        val newActivePerks = state.activePerks + PerkType.JUGGERNOG
        
        val newMaxHp = state.maxHp * config.bonusMaxHpMultiplier
        val hpIncrease = newMaxHp - state.maxHp
        val newHp = min(state.hp + hpIncrease, newMaxHp)

        val newState = state.copy(
            points = newPoints,
            activePerks = newActivePerks,
            maxHp = newMaxHp,
            hp = newHp
        )
        
        val newMachineState = machineState.copy(usesCount = machineState.usesCount + 1)

        return JuggernogPurchaseResult.Success(
            newState = newState,
            updatedMachine = newMachineState,
            message = "Purchased Juggernog! Max HP doubled."
        )
    }
    
    fun tickShieldRegen(
        state: GameSessionState,
        dtSeconds: Float,
        seed: Long,
        config: JuggernogMachineConfig = JuggernogMachineConfig()
    ): GameSessionState {
        val dt = if (dtSeconds.isFinite()) dtSeconds.coerceIn(0.001f, 0.100f) else 0.016f
        
        if (PerkType.JUGGERNOG !in state.activePerks) {
            return state
        }
        
        if (state.hp <= 0 || state.isPlayerDowned) {
            return state
        }

        if (state.shield < state.maxShield) {
            val regenAmount = config.bonusShieldRegenRate * dt
            val wholeRegen = regenAmount.toInt()
            val fractionalPart = regenAmount - wholeRegen
            
            // FNV-1a seeded deterministic fraction check
            var hash = -2128831035157016569L
            hash = hash xor seed
            hash = hash * 1099511628211L
            hash = hash xor state.clockMs
            hash = hash * 1099511628211L
            
            val randFraction = (abs(hash % 1000) / 1000f)
            
            val bonusRegen = wholeRegen + if (randFraction < fractionalPart) 1 else 0
            
            if (bonusRegen > 0) {
                return state.copy(
                    shield = min(state.shield + bonusRegen, state.maxShield)
                )
            }
        }
        return state
    }
}
