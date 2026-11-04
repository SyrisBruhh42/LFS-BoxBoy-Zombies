package com.example.game.economy.vendors

import androidx.annotation.Keep
import com.example.game.ModularWeaponInstance
import kotlin.math.roundToInt
import kotlin.math.hypot

@Keep
data class PendingSalvageTask(
    val taskId: String,
    val weapon: ModularWeaponInstance,
    val originX: Float,
    val originY: Float,
    val remainingDelayMs: Long,
    val rebateValue: Int
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "taskId" to taskId,
            // Since we need deterministic serialization, we simplify the weapon's footprint
            // by just storing its id and relevant stats for reconstruction if necessary,
            // but in practice, for the task queue, we just need to keep the object alive.
            // For true 100% robust networking, a full weapon `toMap` would exist in ModularWeaponInstance.
            // We use instanceId as a proxy.
            "weaponId" to weapon.instanceId,
            "originX" to originX,
            "originY" to originY,
            "remainingDelayMs" to remainingDelayMs,
            "rebateValue" to rebateValue
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any>, weaponCache: Map<String, ModularWeaponInstance>): PendingSalvageTask? {
            val taskId = map["taskId"] as? String ?: return null
            val weaponId = map["weaponId"] as? String ?: return null
            val weapon = weaponCache[weaponId] ?: return null
            val originX = (map["originX"] as? Number)?.toFloat() ?: return null
            val originY = (map["originY"] as? Number)?.toFloat() ?: return null
            val remainingDelayMs = (map["remainingDelayMs"] as? Number)?.toLong() ?: return null
            val rebateValue = (map["rebateValue"] as? Number)?.toInt() ?: return null

            return PendingSalvageTask(taskId, weapon, originX, originY, remainingDelayMs, rebateValue)
        }
    }
}

@Keep
data class SalvageDisplacedGearState(
    val pendingTasks: List<PendingSalvageTask> = emptyList(),
    val totalRebateAccrued: Int = 0,
    val maxProcessingCapacity: Int = 50
) {
    fun calculateChecksum(): Long {
        var hash = 2166136261L
        hash = (hash xor totalRebateAccrued.toLong()) * 16777619L
        hash = (hash xor maxProcessingCapacity.toLong()) * 16777619L
        for (task in pendingTasks) {
            hash = (hash xor task.taskId.hashCode().toLong()) * 16777619L
            hash = (hash xor task.rebateValue.toLong()) * 16777619L
            hash = (hash xor task.remainingDelayMs) * 16777619L
        }
        return hash
    }

    fun toMap(): Map<String, Any> {
        return mapOf(
            "pendingTasks" to pendingTasks.map { it.toMap() },
            "totalRebateAccrued" to totalRebateAccrued,
            "maxProcessingCapacity" to maxProcessingCapacity
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any>, weaponCache: Map<String, ModularWeaponInstance>): SalvageDisplacedGearState {
            val pendingTasksList = (map["pendingTasks"] as? List<*>)?.mapNotNull { item ->
                (item as? Map<String, Any>)?.let { PendingSalvageTask.fromMap(it, weaponCache) }
            } ?: emptyList()

            val totalRebateAccrued = (map["totalRebateAccrued"] as? Number)?.toInt() ?: 0
            val maxProcessingCapacity = (map["maxProcessingCapacity"] as? Number)?.toInt() ?: 50

            return SalvageDisplacedGearState(pendingTasksList, totalRebateAccrued, maxProcessingCapacity)
        }
    }
}

@Keep
data class SalvageTickResult(
    val newState: SalvageDisplacedGearState,
    val completedRebates: Int,
    val tasksProcessed: Int
)

object SalvageDisplacedGearBonusEngine {

    const val MAX_SALVAGE_DISTANCE = 600f

    /**
     * Calculates the deterministic salvage value of a given weapon.
     */
    fun calculateRebateValue(weapon: ModularWeaponInstance): Int {
        val baseValue = 150f
        val rarityMultiplier = weapon.rarity.statMultiplier
        val levelFactor = 1f + (weapon.itemLevel - 1) * 0.1f
        val anvilFactor = 1f + weapon.anvilUpgradeTier * 0.25f

        val rawValue = baseValue * rarityMultiplier * levelFactor * anvilFactor

        // Graceful degradation: ensure finite bounds
        if (!rawValue.isFinite() || rawValue < 0f) {
            return 10 // Fallback nominal value
        }

        return rawValue.roundToInt().coerceIn(10, 5000)
    }

    /**
     * Enqueues a displaced weapon to be salvaged.
     */
    fun enqueueSalvage(
        state: SalvageDisplacedGearState,
        weapon: ModularWeaponInstance,
        playerX: Float,
        playerY: Float,
        taskId: String,
        delayMs: Long = 1500L
    ): SalvageDisplacedGearState {
        // Enforce valid coordinates
        val validX = if (playerX.isFinite()) playerX else 0f
        val validY = if (playerY.isFinite()) playerY else 0f

        val rebate = calculateRebateValue(weapon)
        val validDelay = delayMs.coerceAtLeast(0L)

        val newTask = PendingSalvageTask(
            taskId = taskId,
            weapon = weapon,
            originX = validX,
            originY = validY,
            remainingDelayMs = validDelay,
            rebateValue = rebate
        )

        val newList = state.pendingTasks.toMutableList()
        // Prevent infinite memory growth if tasks are not processed
        if (newList.size >= state.maxProcessingCapacity * 2) {
            newList.removeAt(0) // Drop oldest
        }
        newList.add(newTask)

        return state.copy(pendingTasks = newList)
    }

    /**
     * Steps the engine, ticking down delays and processing completed salvage operations.
     */
    fun step(
        state: SalvageDisplacedGearState,
        dtSeconds: Float,
        playerX: Float,
        playerY: Float
    ): SalvageTickResult {
        if (state.pendingTasks.isEmpty()) {
            return SalvageTickResult(state, 0, 0)
        }

        // Clamp dtSeconds to valid bounds [0.001, 0.100] per requirements
        val safeDt = if (dtSeconds.isFinite()) dtSeconds.coerceIn(0.001f, 0.100f) else 0.016f
        val dtMs = (safeDt * 1000f).toLong()

        val safePlayerX = if (playerX.isFinite()) playerX else 0f
        val safePlayerY = if (playerY.isFinite()) playerY else 0f

        val newPending = mutableListOf<PendingSalvageTask>()
        var completedRebateSum = 0
        var processedCount = 0

        // Process only up to capacity to prevent spike frame drops
        val tasksToProcess = state.pendingTasks.take(state.maxProcessingCapacity)
        val delayedTasks = state.pendingTasks.drop(state.maxProcessingCapacity)

        for (task in tasksToProcess) {
            val newDelay = task.remainingDelayMs - dtMs

            if (newDelay <= 0L) {
                // Task matured, validate distance
                val dx = safePlayerX - task.originX
                val dy = safePlayerY - task.originY
                val dist = hypot(dx, dy)

                if (dist.isFinite() && dist <= MAX_SALVAGE_DISTANCE) {
                    completedRebateSum += task.rebateValue
                }
                // If out of range, the task is dropped and yields no rebate
                processedCount++
            } else {
                newPending.add(task.copy(remainingDelayMs = newDelay))
            }
        }

        // Keep unprocessed tasks
        newPending.addAll(delayedTasks)

        val newState = state.copy(
            pendingTasks = newPending,
            totalRebateAccrued = state.totalRebateAccrued + completedRebateSum
        )

        return SalvageTickResult(
            newState = newState,
            completedRebates = completedRebateSum,
            tasksProcessed = processedCount
        )
    }
}
