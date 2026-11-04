package com.example.game.economy.vendors

import androidx.annotation.Keep

@Keep
data class CoopDoorState(
    val doorId: String,
    val targetRoomIndex: Int,
    val totalCost: Int,
    val currentContribution: Int,
    val isUnlocked: Boolean,
    val contributionsByPeer: Map<String, Int>
) {
    init {
        require(totalCost > 0) { "totalCost must be > 0" }
        require(currentContribution in 0..totalCost) { "currentContribution must be between 0 and totalCost" }
        require(targetRoomIndex >= 0) { "targetRoomIndex must be >= 0" }
    }
    
    fun toMap(): Map<String, Any> {
        return mapOf(
            "doorId" to doorId,
            "targetRoomIndex" to targetRoomIndex,
            "totalCost" to totalCost,
            "currentContribution" to currentContribution,
            "isUnlocked" to isUnlocked,
            "contributionsByPeer" to contributionsByPeer
        )
    }
    
    companion object {
        fun fromMap(map: Map<String, Any>): CoopDoorState {
            val doorId = map["doorId"] as? String ?: ""
            val targetRoomIndex = (map["targetRoomIndex"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0
            val totalCost = (map["totalCost"] as? Number)?.toInt()?.coerceAtLeast(1) ?: 1
            val rawCurrentContribution = (map["currentContribution"] as? Number)?.toInt() ?: 0
            val isUnlocked = map["isUnlocked"] as? Boolean ?: false
            
            val rawContributions = map["contributionsByPeer"] as? Map<*, *> ?: emptyMap<Any, Any>()
            val contributionsByPeer = rawContributions.mapNotNull { 
                val key = it.key as? String
                val value = (it.value as? Number)?.toInt()
                if (key != null && value != null) key to value else null
            }.toMap()
            
            val currentContribution = rawCurrentContribution.coerceIn(0, totalCost)
            
            return CoopDoorState(
                doorId = doorId,
                targetRoomIndex = targetRoomIndex,
                totalCost = totalCost,
                currentContribution = currentContribution,
                isUnlocked = isUnlocked,
                contributionsByPeer = contributionsByPeer
            )
        }
    }
}

@Keep
data class ContributionAttemptResult(
    val success: Boolean,
    val amountContributed: Int,
    val updatedState: CoopDoorState,
    val remainingPlayerPoints: Int,
    val isNewlyUnlocked: Boolean,
    val message: String
)

object CoopDoorSplitContributionEngine {
    
    fun contribute(
        state: CoopDoorState,
        peerId: String,
        amount: Int,
        playerPoints: Int
    ): ContributionAttemptResult {
        if (state.isUnlocked) {
            return ContributionAttemptResult(
                success = false,
                amountContributed = 0,
                updatedState = state,
                remainingPlayerPoints = playerPoints,
                isNewlyUnlocked = false,
                message = "Door is already unlocked."
            )
        }
        
        if (amount <= 0) {
            return ContributionAttemptResult(
                success = false,
                amountContributed = 0,
                updatedState = state,
                remainingPlayerPoints = playerPoints,
                isNewlyUnlocked = false,
                message = "Contribution amount must be positive."
            )
        }
        
        if (playerPoints <= 0) {
            return ContributionAttemptResult(
                success = false,
                amountContributed = 0,
                updatedState = state,
                remainingPlayerPoints = playerPoints,
                isNewlyUnlocked = false,
                message = "Insufficient points."
            )
        }
        
        val actualAmount = minOf(amount, playerPoints, state.totalCost - state.currentContribution)
        if (actualAmount <= 0) {
             return ContributionAttemptResult(
                success = false,
                amountContributed = 0,
                updatedState = state,
                remainingPlayerPoints = playerPoints,
                isNewlyUnlocked = false,
                message = "Contribution not possible."
            )
        }
        
        val newContribution = state.currentContribution + actualAmount
        val newIsUnlocked = newContribution >= state.totalCost
        
        val newContributionsByPeer = state.contributionsByPeer.toMutableMap()
        newContributionsByPeer[peerId] = (newContributionsByPeer[peerId] ?: 0) + actualAmount
        
        val newState = state.copy(
            currentContribution = newContribution,
            isUnlocked = newIsUnlocked,
            contributionsByPeer = newContributionsByPeer
        )
        
        return ContributionAttemptResult(
            success = true,
            amountContributed = actualAmount,
            updatedState = newState,
            remainingPlayerPoints = playerPoints - actualAmount,
            isNewlyUnlocked = newIsUnlocked,
            message = if (newIsUnlocked) "Door unlocked!" else "Contributed \$actualAmount points."
        )
    }

    fun contributeMaxPossible(
        state: CoopDoorState,
        peerId: String,
        playerPoints: Int
    ): ContributionAttemptResult {
        return contribute(state, peerId, playerPoints, playerPoints)
    }
    
    fun computeChecksum(state: CoopDoorState): Int {
        // FNV-1a 32-bit checksum
        var hash = -2128831035 // 0x811C9DC5
        val prime = 16777619 // 0x01000193
        
        fun updateHash(str: String) {
            for (char in str) {
                hash = hash xor char.code
                hash *= prime
            }
        }
        
        fun updateHash(intVal: Int) {
            var v = intVal
            for (i in 0..3) {
                hash = hash xor (v and 0xFF)
                hash *= prime
                v = v ushr 8
            }
        }
        
        fun updateHash(boolVal: Boolean) {
            hash = hash xor (if (boolVal) 1 else 0)
            hash *= prime
        }
        
        updateHash(state.doorId)
        updateHash(state.targetRoomIndex)
        updateHash(state.totalCost)
        updateHash(state.currentContribution)
        updateHash(state.isUnlocked)
        
        // Sort keys to ensure deterministic checksum across distributed peers
        val sortedKeys = state.contributionsByPeer.keys.sorted()
        for (key in sortedKeys) {
            updateHash(key)
            updateHash(state.contributionsByPeer[key] ?: 0)
        }
        
        return hash
    }
}
