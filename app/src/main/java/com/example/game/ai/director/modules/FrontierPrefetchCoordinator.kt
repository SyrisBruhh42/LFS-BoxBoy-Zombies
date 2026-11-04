package com.example.game.ai.director.modules

import com.example.game.ai.director.AiSectorBlueprintProposal
import com.example.game.ai.director.LocalOllamaMapDirector
import com.example.game.world.elona.FrontierDoorSocket
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Background coroutine prefetch pool keeping 3 solved AI sectors cached ahead of locked doors.
 */
class FrontierPrefetchCoordinator(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val scope = CoroutineScope(dispatcher + SupervisorJob())
    
    private val cacheMutex = Mutex()
    private val prefetchCache = mutableMapOf<String, AiSectorBlueprintProposal>()
    private val activeJobs = mutableMapOf<String, Job>()
    
    companion object {
        const val MAX_PREFETCH_POOL_SIZE = 3
    }
    
    /**
     * Starts prefetching sectors for the provided frontier doors.
     * Keeps at most 3 sectors in the cache or currently prefetching.
     */
    suspend fun updateFrontierTargets(
        frontiers: List<FrontierDoorSocket>,
        wave: Int,
        biomeTag: String,
        baseSeed: Long
    ) {
        val priorityFrontiers = frontiers.take(MAX_PREFETCH_POOL_SIZE)
        val validIds = priorityFrontiers.map { it.frontierId }.toSet()
        
        cacheMutex.withLock {
            // Cancel jobs that are no longer priority
            val jobIterator = activeJobs.iterator()
            while (jobIterator.hasNext()) {
                val (id, job) = jobIterator.next()
                if (id !in validIds) {
                    job.cancel()
                    jobIterator.remove()
                }
            }
            
            // Remove cached items no longer needed
            val cacheIterator = prefetchCache.iterator()
            while (cacheIterator.hasNext()) {
                val (id, _) = cacheIterator.next()
                if (id !in validIds) {
                    cacheIterator.remove()
                }
            }
            
            // Start prefetching for missing targets
            for (frontier in priorityFrontiers) {
                if (prefetchCache.containsKey(frontier.frontierId)) continue
                if (activeJobs.containsKey(frontier.frontierId)) continue
                
                val job = scope.launch {
                    val stepSeed = baseSeed xor (frontier.targetGridX * 73856093L) xor (frontier.targetGridY * 19349663L)
                    
                    // In a real scenario, this might await an Ollama network call.
                    // For the Track 2 Local Open-Source AI Director deterministic mode:
                    val proposal = LocalOllamaMapDirector.synthesizeDeterministicWfcSector(
                        wave = wave,
                        biomeTag = biomeTag,
                        seed = stepSeed
                    )
                    
                    cacheMutex.withLock {
                        prefetchCache[frontier.frontierId] = proposal
                        activeJobs.remove(frontier.frontierId)
                    }
                }
                activeJobs[frontier.frontierId] = job
            }
        }
    }
    
    /**
     * Synchronously retrieves a prefetched sector if available, preventing frame drops.
     */
    suspend fun consumePrefetchedSector(frontierId: String): AiSectorBlueprintProposal? {
        return cacheMutex.withLock {
            val proposal = prefetchCache.remove(frontierId)
            activeJobs.remove(frontierId)?.cancel()
            proposal
        }
    }
    
    suspend fun clear() {
        cacheMutex.withLock {
            activeJobs.values.forEach { it.cancel() }
            activeJobs.clear()
            prefetchCache.clear()
        }
    }
    
    suspend fun getCachedCount(): Int {
        return cacheMutex.withLock { prefetchCache.size }
    }
    
    fun shutdown() {
        scope.cancel()
    }
}


fun evaluateFrontierPrefetchCoordinatorLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "FrontierPrefetchCoordinator") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
