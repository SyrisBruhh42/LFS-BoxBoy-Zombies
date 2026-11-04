package com.example.game.loot.borderlands.catalog

import androidx.annotation.Keep

@Keep
data class JakobsEnemy(
    val id: Int,
    val x: Float,
    val y: Float,
    val hp: Int
) {
    fun toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "x" to x,
        "y" to y,
        "hp" to hp
    )

    companion object {
        fun fromMap(map: Map<String, Any>): JakobsEnemy {
            return JakobsEnemy(
                id = (map["id"] as? Number)?.toInt() ?: 0,
                x = (map["x"] as? Number)?.toFloat() ?: 0f,
                y = (map["y"] as? Number)?.toFloat() ?: 0f,
                hp = (map["hp"] as? Number)?.toInt() ?: 0
            )
        }
    }
}

@Keep
data class JakobsAttackState(
    val baseDamage: Int,
    val isCritical: Boolean,
    val targetEnemyId: Int,
    val ricochetRadius: Float
) {
    fun toMap(): Map<String, Any> = mapOf(
        "baseDamage" to baseDamage,
        "isCritical" to isCritical,
        "targetEnemyId" to targetEnemyId,
        "ricochetRadius" to ricochetRadius
    )

    companion object {
        fun fromMap(map: Map<String, Any>): JakobsAttackState {
            return JakobsAttackState(
                baseDamage = (map["baseDamage"] as? Number)?.toInt() ?: 0,
                isCritical = map["isCritical"] as? Boolean ?: false,
                targetEnemyId = (map["targetEnemyId"] as? Number)?.toInt() ?: -1,
                ricochetRadius = (map["ricochetRadius"] as? Number)?.toFloat() ?: 0f
            )
        }
    }
}

@Keep
data class JakobsRicochetResult(
    val didRicochet: Boolean,
    val ricochetTargetId: Int,
    val ricochetDamage: Int
) {
    fun toMap(): Map<String, Any> = mapOf(
        "didRicochet" to didRicochet,
        "ricochetTargetId" to ricochetTargetId,
        "ricochetDamage" to ricochetDamage
    )

    companion object {
        fun fromMap(map: Map<String, Any>): JakobsRicochetResult {
            return JakobsRicochetResult(
                didRicochet = map["didRicochet"] as? Boolean ?: false,
                ricochetTargetId = (map["ricochetTargetId"] as? Number)?.toInt() ?: -1,
                ricochetDamage = (map["ricochetDamage"] as? Number)?.toInt() ?: 0
            )
        }
    }
}

@Keep
data class JakobsAttackResult(
    val finalDamage: Int,
    val wasLethal: Boolean,
    val ricochetResult: JakobsRicochetResult,
    val updatedEnemies: List<JakobsEnemy>
) {
    fun toMap(): Map<String, Any> = mapOf(
        "finalDamage" to finalDamage,
        "wasLethal" to wasLethal,
        "ricochetResult" to ricochetResult.toMap(),
        "updatedEnemies" to updatedEnemies.map { it.toMap() }
    )

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(map: Map<String, Any>): JakobsAttackResult {
            val ricochetMap = map["ricochetResult"] as? Map<String, Any> ?: emptyMap()
            val enemiesList = map["updatedEnemies"] as? List<Map<String, Any>> ?: emptyList()
            return JakobsAttackResult(
                finalDamage = (map["finalDamage"] as? Number)?.toInt() ?: 0,
                wasLethal = map["wasLethal"] as? Boolean ?: false,
                ricochetResult = JakobsRicochetResult.fromMap(ricochetMap),
                updatedEnemies = enemiesList.map { JakobsEnemy.fromMap(it) }
            )
        }
    }
}

object JakobsAbyssManufacturerTraitEngine {
    const val JAKOBS_CRIT_BONUS = 0.45f
    const val BASE_CRIT_MULTIPLIER = 2.0f

    fun processAttack(
        state: JakobsAttackState,
        enemies: List<JakobsEnemy>
    ): JakobsAttackResult {
        val safeBaseDamage = if (state.baseDamage > 0) state.baseDamage else 0
        val safeRadius = if (state.ricochetRadius.isFinite() && state.ricochetRadius > 0f) state.ricochetRadius else 0f

        val finalMultiplier = if (state.isCritical) (BASE_CRIT_MULTIPLIER + JAKOBS_CRIT_BONUS) else 1.0f
        val finalDamage = (safeBaseDamage * finalMultiplier).toInt()

        // We return a new list with updated hp, we must not mutate the original list elements
        val updatedEnemies = enemies.toMutableList()
        val targetIdx = updatedEnemies.indexOfFirst { it.id == state.targetEnemyId }

        var wasLethal = false
        var ricochetResult = JakobsRicochetResult(false, -1, 0)

        if (targetIdx != -1) {
            val target = updatedEnemies[targetIdx]
            val newHp = target.hp - finalDamage
            updatedEnemies[targetIdx] = target.copy(hp = newHp)

            if (newHp <= 0 && target.hp > 0) {
                wasLethal = true
            }

            if (state.isCritical && wasLethal && safeRadius > 0f) {
                var closestEnemy: JakobsEnemy? = null
                var closestDistSq = Float.MAX_VALUE

                val targetX = if (target.x.isFinite()) target.x else 0f
                val targetY = if (target.y.isFinite()) target.y else 0f
                val radiusSq = safeRadius * safeRadius

                for (enemy in updatedEnemies) {
                    if (enemy.id != target.id && enemy.hp > 0) {
                        val ex = if (enemy.x.isFinite()) enemy.x else 0f
                        val ey = if (enemy.y.isFinite()) enemy.y else 0f

                        val dx = ex - targetX
                        val dy = ey - targetY
                        val distSq = dx * dx + dy * dy

                        if (distSq <= radiusSq && distSq < closestDistSq) {
                            closestDistSq = distSq
                            closestEnemy = enemy
                        }
                    }
                }

                if (closestEnemy != null) {
                    val ricochetDamage = finalDamage
                    val ricochetTargetIdx = updatedEnemies.indexOfFirst { it.id == closestEnemy!!.id }
                    if (ricochetTargetIdx != -1) {
                        val rTarget = updatedEnemies[ricochetTargetIdx]
                        updatedEnemies[ricochetTargetIdx] = rTarget.copy(hp = rTarget.hp - ricochetDamage)
                        ricochetResult = JakobsRicochetResult(true, closestEnemy.id, ricochetDamage)
                    }
                }
            }
        }

        return JakobsAttackResult(
            finalDamage = finalDamage,
            wasLethal = wasLethal,
            ricochetResult = ricochetResult,
            updatedEnemies = updatedEnemies
        )
    }
}
