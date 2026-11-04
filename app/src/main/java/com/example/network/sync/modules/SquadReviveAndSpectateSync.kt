package com.example.network.sync.modules

import androidx.annotation.Keep

@Keep
data class SquadReviveConfig(
    val maxBleedoutTimeSeconds: Float = 45.0f,
    val baseReviveTimeSeconds: Float = 3.0f,
    val quickReviveTimeSeconds: Float = 1.5f,
    val maxReviveTetherDistancePx: Float = 120.0f,
    val maxSoloQuickRevives: Int = 3,
    val isSoloMode: Boolean = false
) {
    fun toMap(): Map<String, Any> = mapOf(
        "maxBleedoutTimeSeconds" to maxBleedoutTimeSeconds,
        "baseReviveTimeSeconds" to baseReviveTimeSeconds,
        "quickReviveTimeSeconds" to quickReviveTimeSeconds,
        "maxReviveTetherDistancePx" to maxReviveTetherDistancePx,
        "maxSoloQuickRevives" to maxSoloQuickRevives,
        "isSoloMode" to isSoloMode
    )

    companion object {
        fun fromMap(map: Map<String, Any>): SquadReviveConfig {
            return SquadReviveConfig(
                maxBleedoutTimeSeconds = (map["maxBleedoutTimeSeconds"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 45.0f,
                baseReviveTimeSeconds = (map["baseReviveTimeSeconds"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 3.0f,
                quickReviveTimeSeconds = (map["quickReviveTimeSeconds"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 1.5f,
                maxReviveTetherDistancePx = (map["maxReviveTetherDistancePx"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 120.0f,
                maxSoloQuickRevives = (map["maxSoloQuickRevives"] as? Number)?.toInt() ?: 3,
                isSoloMode = map["isSoloMode"] as? Boolean ?: false
            )
        }
    }
}

@Keep
data class PlayerReviveState(
    var playerId: String = "",
    var isDowned: Boolean = false,
    var isDeadSpectating: Boolean = false,
    var bleedoutTimer: Float = 0.0f,
    var reviveTimer: Float = 0.0f,
    var reviverPlayerId: String = "",
    var hasQuickRevivePerk: Boolean = false,
    var soloQuickRevivesUsed: Int = 0,
    var posX: Float = 0.0f,
    var posY: Float = 0.0f,
    var queuedForNextWaveRespawn: Boolean = false
) {
    fun toMap(): Map<String, Any> = mapOf(
        "playerId" to playerId,
        "isDowned" to isDowned,
        "isDeadSpectating" to isDeadSpectating,
        "bleedoutTimer" to bleedoutTimer,
        "reviveTimer" to reviveTimer,
        "reviverPlayerId" to reviverPlayerId,
        "hasQuickRevivePerk" to hasQuickRevivePerk,
        "soloQuickRevivesUsed" to soloQuickRevivesUsed,
        "posX" to posX,
        "posY" to posY,
        "queuedForNextWaveRespawn" to queuedForNextWaveRespawn
    )
    
    fun computeChecksum(): Long {
        var hash = -2128831035142426879L // FNV-1a offset basis
        val prime = 1099511628211L
        
        fun addString(s: String) {
            for (i in s.indices) {
                hash = hash xor s[i].code.toLong()
                hash *= prime
            }
        }
        fun addFloat(f: Float) {
            val bits = if (f.isFinite()) f.toRawBits().toLong() else 0L
            hash = hash xor (bits and 0xFFFFFFFFL)
            hash *= prime
        }
        fun addBoolean(b: Boolean) {
            hash = hash xor (if (b) 1L else 0L)
            hash *= prime
        }
        fun addInt(i: Int) {
            hash = hash xor (i.toLong() and 0xFFFFFFFFL)
            hash *= prime
        }

        addString(playerId)
        addBoolean(isDowned)
        addBoolean(isDeadSpectating)
        addFloat(bleedoutTimer)
        addFloat(reviveTimer)
        addString(reviverPlayerId)
        addBoolean(hasQuickRevivePerk)
        addInt(soloQuickRevivesUsed)
        addFloat(posX)
        addFloat(posY)
        addBoolean(queuedForNextWaveRespawn)
        
        return hash
    }

    companion object {
        fun fromMap(map: Map<String, Any>): PlayerReviveState {
            return PlayerReviveState(
                playerId = map["playerId"] as? String ?: "",
                isDowned = map["isDowned"] as? Boolean ?: false,
                isDeadSpectating = map["isDeadSpectating"] as? Boolean ?: false,
                bleedoutTimer = (map["bleedoutTimer"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 0.0f,
                reviveTimer = (map["reviveTimer"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 0.0f,
                reviverPlayerId = map["reviverPlayerId"] as? String ?: "",
                hasQuickRevivePerk = map["hasQuickRevivePerk"] as? Boolean ?: false,
                soloQuickRevivesUsed = (map["soloQuickRevivesUsed"] as? Number)?.toInt() ?: 0,
                posX = (map["posX"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 0.0f,
                posY = (map["posY"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 0.0f,
                queuedForNextWaveRespawn = map["queuedForNextWaveRespawn"] as? Boolean ?: false
            )
        }
    }
}

@Keep
data class SquadReviveSnapshot(
    val players: Array<PlayerReviveState>
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "players" to players.map { it.toMap() }
        )
    }

    fun computeChecksum(): Long {
        var hash = -2128831035142426879L
        for (i in players.indices) {
            hash = hash xor players[i].computeChecksum()
            hash *= 1099511628211L
        }
        return hash
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SquadReviveSnapshot) return false
        return players.contentEquals(other.players)
    }

    override fun hashCode(): Int {
        return players.contentHashCode()
    }

    companion object {
        fun fromMap(map: Map<String, Any>): SquadReviveSnapshot {
            val playersList = map["players"] as? List<*> ?: emptyList<Any>()
            val playersArray = Array(playersList.size) { i -> 
                val playerMap = playersList[i] as? Map<String, Any> ?: emptyMap()
                PlayerReviveState.fromMap(playerMap) 
            }
            return SquadReviveSnapshot(playersArray)
        }
    }
}

class SquadReviveEngine(
    val config: SquadReviveConfig
) {
    fun processDownedPlayer(state: PlayerReviveState) {
        if (state.isDeadSpectating) return
        
        if (!state.isDowned) {
            state.isDowned = true
            state.bleedoutTimer = config.maxBleedoutTimeSeconds
            state.reviveTimer = 0.0f
            state.reviverPlayerId = ""
            
            // Solo Quick Revive is initiated inside tick() if conditions are met.
        }
    }

    fun startReviveTether(downedState: PlayerReviveState, reviverState: PlayerReviveState) {
        if (!downedState.isDowned || downedState.isDeadSpectating) return
        if (reviverState.isDowned || reviverState.isDeadSpectating) return
        
        val p1x = if (downedState.posX.isFinite()) downedState.posX else 0.0f
        val p1y = if (downedState.posY.isFinite()) downedState.posY else 0.0f
        val p2x = if (reviverState.posX.isFinite()) reviverState.posX else 0.0f
        val p2y = if (reviverState.posY.isFinite()) reviverState.posY else 0.0f
        
        val dx = p1x - p2x
        val dy = p1y - p2y
        
        val distSq = dx * dx + dy * dy
        val maxDistSq = config.maxReviveTetherDistancePx * config.maxReviveTetherDistancePx
        
        if (distSq <= maxDistSq) {
            if (downedState.reviverPlayerId.isEmpty() || downedState.reviverPlayerId == reviverState.playerId) {
                downedState.reviverPlayerId = reviverState.playerId
            }
        }
    }

    fun tick(snapshot: SquadReviveSnapshot, dtSecondsRaw: Float) {
        val dt = if (!dtSecondsRaw.isFinite()) {
            0.016f
        } else if (dtSecondsRaw > 10.0f) {
            0.100f
        } else {
            Math.max(0.001f, dtSecondsRaw)
        }
        
        val maxDistSq = config.maxReviveTetherDistancePx * config.maxReviveTetherDistancePx
        
        for (i in snapshot.players.indices) {
            val player = snapshot.players[i]
            if (player.isDowned && !player.isDeadSpectating) {
                
                if (config.isSoloMode && player.hasQuickRevivePerk && player.soloQuickRevivesUsed < config.maxSoloQuickRevives) {
                    player.reviverPlayerId = player.playerId
                }
                
                var isBeingRevivedNow = false
                
                if (player.reviverPlayerId.isNotEmpty()) {
                    val reviver = if (player.reviverPlayerId == player.playerId) {
                        player
                    } else {
                        findReviver(snapshot, player.reviverPlayerId)
                    }
                    
                    if (reviver != null && (!reviver.isDowned || reviver === player) && !reviver.isDeadSpectating) {
                        val p1x = if (player.posX.isFinite()) player.posX else 0.0f
                        val p1y = if (player.posY.isFinite()) player.posY else 0.0f
                        val p2x = if (reviver.posX.isFinite()) reviver.posX else 0.0f
                        val p2y = if (reviver.posY.isFinite()) reviver.posY else 0.0f
                        
                        val dx = p1x - p2x
                        val dy = p1y - p2y
                        val distSq = dx * dx + dy * dy
                        
                        if (distSq <= maxDistSq) {
                            isBeingRevivedNow = true
                            
                            val requiredReviveTime = if (reviver.hasQuickRevivePerk) config.quickReviveTimeSeconds else config.baseReviveTimeSeconds
                            
                            player.reviveTimer += dt
                            
                            if (player.reviveTimer >= requiredReviveTime) {
                                val wasSelfRevive = player.reviverPlayerId == player.playerId
                                
                                player.isDowned = false
                                player.bleedoutTimer = 0.0f
                                player.reviveTimer = 0.0f
                                player.reviverPlayerId = ""
                                
                                if (wasSelfRevive && config.isSoloMode) {
                                    player.soloQuickRevivesUsed += 1
                                    player.hasQuickRevivePerk = false
                                }
                                continue
                            }
                        }
                    }
                }
                
                if (!isBeingRevivedNow) {
                    player.reviverPlayerId = ""
                    player.reviveTimer = 0.0f 
                    
                    player.bleedoutTimer -= dt
                    if (player.bleedoutTimer <= 0.0f) {
                        player.bleedoutTimer = 0.0f
                        player.isDowned = false
                        player.isDeadSpectating = true
                        player.queuedForNextWaveRespawn = true
                    }
                }
            }
        }
    }

    fun applyPostBleedoutNextWaveRespawn(snapshot: SquadReviveSnapshot) {
        for (i in snapshot.players.indices) {
            val player = snapshot.players[i]
            if (player.isDeadSpectating && player.queuedForNextWaveRespawn) {
                player.isDeadSpectating = false
                player.isDowned = false
                player.queuedForNextWaveRespawn = false
                player.bleedoutTimer = 0.0f
                player.reviveTimer = 0.0f
                player.reviverPlayerId = ""
            }
        }
    }
    
    private fun findReviver(snapshot: SquadReviveSnapshot, reviverId: String): PlayerReviveState? {
        for (i in snapshot.players.indices) {
            if (snapshot.players[i].playerId == reviverId) {
                return snapshot.players[i]
            }
        }
        return null
    }
}


fun evaluateSquadReviveAndSpectateSyncLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "SquadReviveAndSpectateSync") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
