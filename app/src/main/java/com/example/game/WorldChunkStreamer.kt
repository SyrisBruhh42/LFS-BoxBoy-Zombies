package com.example.game

import androidx.compose.ui.geometry.Rect
import com.example.network.CoopPeerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.max

/**
 * P.S. 3: Asynchronous 600px Spatial Grid Chunk-Based World Loading System with 3-Tier LOD Caching.
 *
 * Partitions the 4800 x 2760 tactical county into a deterministic 8 x 5 grid of 600x600px chunks
 * (`ChunkCoord(cx, cy)`). Dynamically streams and caches static props, trees, buildings, lakes,
 * outcrops, and wall obstacles around the player AND all 4 squad members (`coopPeers`) without
 * frame drops or full-world iteration stalls.
 */
data class ChunkCoord(
    val cx: Int,
    val cy: Int
)

enum class ChunkLodTier(val label: String, val badgeColorHex: Long) {
    ACTIVE_FULL_DETAIL("Full-Detail (<=1 Chunk)", 0xFF00FF66),
    MID_LOD_CACHED("Mid-LOD Cached (2 Chunks)", 0xFFFFD166),
    EVICTED_UNLOADED("Unloaded (>2 Chunks)", 0xFF64748B)
}

data class WorldChunkSlice(
    val coord: ChunkCoord,
    val worldBounds: Rect,
    val lodTier: ChunkLodTier,
    val townBuildings: List<TownBuilding>,
    val treesAndShrubs: List<WorldFoliage>,
    val roadProps: List<RoadSideProp>,
    val lakes: List<MapLake>,
    val wallObstacles: List<Rect>,
    val outcrops: List<JaggedOutcrop>,
    val stations: List<InteractiveStation>,
    val lastAccessedMs: Long
)

data class ChunkStreamSnapshot(
    val mapSeed: Long = 0L,
    val activeFullDetailChunks: List<WorldChunkSlice> = emptyList(),
    val midLodCachedChunks: List<WorldChunkSlice> = emptyList(),
    val activeChunkCount: Int = 0,
    val cachedMidLodCount: Int = 0,
    val totalChunksInWorld: Int = 40,
    val playerChunk: ChunkCoord = ChunkCoord(0, 0)
)

object WorldChunkStreamer {
    const val CHUNK_SIZE_PX: Float = 600f
    const val WORLD_COLS: Int = 8 // 8 * 600 = 4800
    const val WORLD_ROWS: Int = 5 // 5 * 600 = 3000 (covers 2760 height)

    private val streamerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val chunkCache = ConcurrentHashMap<ChunkCoord, WorldChunkSlice>()
    private var indexedMapSeed: Long = Long.MIN_VALUE
    private var indexedWallHash: Int = 0
    private var lastWallObstaclesRef: List<Rect>? = null

    // Memoization state for 60/120Hz queryAndStreamSquadChunks
    private var lastSquadChunkCoords: Set<ChunkCoord> = emptySet()
    private var lastPlayerCoord: ChunkCoord = ChunkCoord(-1, -1)
    private var lastFocalPeerCount: Int = -1
    private var lastTopologyRevision: Long = Long.MIN_VALUE
    private var cachedSnapshot: ChunkStreamSnapshot? = null
    private var manualInvalidationCounter: Long = 0L
    private var memoizationHitCount: Long = 0L
    private var rebuildCount: Long = 0L

    private val _snapshotFlow = MutableStateFlow(ChunkStreamSnapshot())
    val snapshotFlow: StateFlow<ChunkStreamSnapshot> = _snapshotFlow.asStateFlow()

    /**
     * Explicitly invalidates the memoized chunk snapshot when maze pillars shift,
     * doors unlock, or fortifications/props mutate chunk geometry.
     */
    fun invalidateChunks() {
        manualInvalidationCounter++
        cachedSnapshot = null
    }

    fun getMemoizationHitCountForTest(): Long = memoizationHitCount
    fun getRebuildCountForTest(): Long = rebuildCount

    fun worldToChunkCoord(x: Float, y: Float): ChunkCoord {
        val cx = (x / CHUNK_SIZE_PX).toInt().coerceIn(0, WORLD_COLS - 1)
        val cy = (y / CHUNK_SIZE_PX).toInt().coerceIn(0, WORLD_ROWS - 1)
        return ChunkCoord(cx, cy)
    }

    fun chunkBounds(coord: ChunkCoord): Rect {
        val left = coord.cx * CHUNK_SIZE_PX
        val top = coord.cy * CHUNK_SIZE_PX
        return Rect(left, top, left + CHUNK_SIZE_PX, top + CHUNK_SIZE_PX)
    }

    private fun computeTopologyRevision(map: ProceduralMap): Long {
        ensureMapIndexed(map)
        var rev = map.seed
        rev = rev * 31L + indexedWallHash
        rev = rev * 31L + map.townBuildings.size
        rev = rev * 31L + map.stations.size
        rev = rev * 31L + map.barrels.size
        rev = rev * 31L + manualInvalidationCounter
        return rev
    }

    /**
     * Synchronously returns the current active chunks around the squad (for deterministic unit tests
     * and immediate first-frame rendering) while triggering async background pre-warming for adjacent chunks
     * ONLY when squad chunk coordinates or world topology change.
     */
    fun queryAndStreamSquadChunks(
        map: ProceduralMap,
        playerX: Float,
        playerY: Float,
        coopPeers: List<CoopPeerState> = emptyList(),
        nowMs: Long = System.currentTimeMillis()
    ): ChunkStreamSnapshot {
        val topologyRev = computeTopologyRevision(map)
        val playerCoord = worldToChunkCoord(playerX, playerY)

        // Fast zero-allocation memoization check for solo play
        if (coopPeers.isEmpty() &&
            lastFocalPeerCount == 0 &&
            playerCoord == lastPlayerCoord &&
            topologyRev == lastTopologyRevision
        ) {
            cachedSnapshot?.let {
                memoizationHitCount++
                return it
            }
        }

        val focalCoords = LinkedHashSet<ChunkCoord>(4)
        focalCoords.add(playerCoord)
        var activePeers = 0
        for (i in 0 until coopPeers.size) {
            val peer = coopPeers[i]
            if (peer.hp > 0 || peer.isDowned) {
                focalCoords.add(worldToChunkCoord(peer.x, peer.y))
                activePeers++
            }
        }

        if (topologyRev == lastTopologyRevision &&
            focalCoords == lastSquadChunkCoords
        ) {
            cachedSnapshot?.let {
                memoizationHitCount++
                return it
            }
        }

        rebuildCount++
        val activeList = ArrayList<WorldChunkSlice>(16)
        val midLodList = ArrayList<WorldChunkSlice>(24)

        for (cy in 0 until WORLD_ROWS) {
            for (cx in 0 until WORLD_COLS) {
                val coord = ChunkCoord(cx, cy)
                var minChebyshev = Int.MAX_VALUE
                for (focal in focalCoords) {
                    val dist = max(abs(cx - focal.cx), abs(cy - focal.cy))
                    if (dist < minChebyshev) minChebyshev = dist
                }

                when {
                    minChebyshev <= 1 -> {
                        val slice = getOrBuildChunkSlice(map, coord, ChunkLodTier.ACTIVE_FULL_DETAIL, nowMs)
                        activeList.add(slice)
                    }
                    minChebyshev == 2 -> {
                        val slice = getOrBuildChunkSlice(map, coord, ChunkLodTier.MID_LOD_CACHED, nowMs)
                        midLodList.add(slice)
                    }
                    else -> {
                        chunkCache.remove(coord)
                    }
                }
            }
        }

        val snapshot = ChunkStreamSnapshot(
            mapSeed = map.seed,
            activeFullDetailChunks = activeList,
            midLodCachedChunks = midLodList,
            activeChunkCount = activeList.size,
            cachedMidLodCount = midLodList.size,
            totalChunksInWorld = WORLD_COLS * WORLD_ROWS,
            playerChunk = playerCoord
        )
        lastPlayerCoord = playerCoord
        lastFocalPeerCount = activePeers
        lastSquadChunkCoords = focalCoords
        lastTopologyRevision = topologyRev
        cachedSnapshot = snapshot
        _snapshotFlow.value = snapshot

        // Pre-warm neighbor ring asynchronously on background dispatcher only when chunk set or topology changes
        streamerScope.launch {
            for (focal in focalCoords) {
                for (dy in -2..2) {
                    for (dx in -2..2) {
                        val ncx = focal.cx + dx
                        val ncy = focal.cy + dy
                        if (ncx in 0 until WORLD_COLS && ncy in 0 until WORLD_ROWS) {
                            val dist = max(abs(dx), abs(dy))
                            val tier = if (dist <= 1) ChunkLodTier.ACTIVE_FULL_DETAIL else ChunkLodTier.MID_LOD_CACHED
                            getOrBuildChunkSlice(map, ChunkCoord(ncx, ncy), tier, nowMs)
                        }
                    }
                }
            }
        }

        return snapshot
    }

    private fun ensureMapIndexed(map: ProceduralMap) {
        if (map.seed == indexedMapSeed && map.wallObstacles === lastWallObstaclesRef) {
            return
        }
        val wallHash = map.wallObstacles.hashCode()
        if (map.seed != indexedMapSeed || wallHash != indexedWallHash) {
            chunkCache.clear()
            cachedSnapshot = null
            indexedMapSeed = map.seed
            indexedWallHash = wallHash
        }
        lastWallObstaclesRef = map.wallObstacles
    }

    private fun getOrBuildChunkSlice(
        map: ProceduralMap,
        coord: ChunkCoord,
        tier: ChunkLodTier,
        nowMs: Long
    ): WorldChunkSlice {
        val existing = chunkCache[coord]
        if (existing != null && existing.lodTier == tier) {
            return existing
        }
        val bounds = chunkBounds(coord)
        val paddedBounds = Rect(
            bounds.left - 80f,
            bounds.top - 80f,
            bounds.right + 80f,
            bounds.bottom + 80f
        )

        val buildings = map.townBuildings.filter { b ->
            val cx = b.bounds.center.x
            val cy = b.bounds.center.y
            cx >= bounds.left && cx < bounds.right && cy >= bounds.top && cy < bounds.bottom
        }

        val trees = ArrayList<WorldFoliage>()
        for (i in 0 until map.foliage.size) {
            val f = map.foliage[i]
            if (f.x >= bounds.left && f.x < bounds.right && f.y >= bounds.top && f.y < bounds.bottom) {
                if (tier == ChunkLodTier.ACTIVE_FULL_DETAIL || f.heightZ > 20f || (i % 2 == 0)) {
                    trees.add(f)
                }
            }
        }

        val roadProps = ArrayList<RoadSideProp>()
        if (tier == ChunkLodTier.ACTIVE_FULL_DETAIL) {
            for (i in 0 until map.roadSideProps.size) {
                val rp = map.roadSideProps[i]
                if (rp.x >= bounds.left && rp.x < bounds.right && rp.y >= bounds.top && rp.y < bounds.bottom) {
                    roadProps.add(rp)
                }
            }
        }

        val lakes = map.lakes.filter { lake ->
            lake.x >= paddedBounds.left && lake.x <= paddedBounds.right &&
                lake.y >= paddedBounds.top && lake.y <= paddedBounds.bottom
        }

        val walls = map.wallObstacles.filter { rect ->
            val cx = rect.center.x
            val cy = rect.center.y
            cx >= bounds.left && cx < bounds.right && cy >= bounds.top && cy < bounds.bottom
        }

        val outcrops = map.jaggedPerimeterOutcrops.filter { oc ->
            val cx = oc.bounds.center.x
            val cy = oc.bounds.center.y
            cx >= bounds.left && cx < bounds.right && cy >= bounds.top && cy < bounds.bottom
        }

        val stations = map.stations.filter { st ->
            st.x >= bounds.left && st.x < bounds.right && st.y >= bounds.top && st.y < bounds.bottom
        }

        val slice = WorldChunkSlice(
            coord = coord,
            worldBounds = bounds,
            lodTier = tier,
            townBuildings = buildings,
            treesAndShrubs = trees,
            roadProps = roadProps,
            lakes = lakes,
            wallObstacles = walls,
            outcrops = outcrops,
            stations = stations,
            lastAccessedMs = nowMs
        )
        chunkCache[coord] = slice
        return slice
    }

    /**
     * Fast chunk-localized collision lookup for any entity at (x, y) so physics checks only inspect
     * the 3x3 chunk neighborhood around (x, y).
     */
    fun isWalkableInChunkedWorld(
        x: Float,
        y: Float,
        map: ProceduralMap,
        unlockedRooms: Set<Int>
    ): Boolean {
        val rooms = map.rooms
        var inUnlockedArea = false
        for (i in 0 until rooms.size) {
            val room = rooms[i]
            val idx = room.index
            if (idx !in unlockedRooms) continue
            val b = room.bounds

            val openLeft = (idx == 1 && 0 in unlockedRooms) || (idx == 4 && 1 in unlockedRooms) ||
                (idx == 3 && 2 in unlockedRooms) || (idx == 5 && 3 in unlockedRooms)
            val openRight = (idx == 0 && 1 in unlockedRooms) || (idx == 1 && 4 in unlockedRooms) ||
                (idx == 2 && 3 in unlockedRooms) || (idx == 3 && 5 in unlockedRooms)
            val openTop = (idx == 0 && 2 in unlockedRooms) || (idx == 1 && 3 in unlockedRooms) ||
                (idx == 4 && 5 in unlockedRooms)
            val openBottom = (idx == 2 && 0 in unlockedRooms) || (idx == 3 && 1 in unlockedRooms) ||
                (idx == 5 && 4 in unlockedRooms)

            val minX = if (openLeft) b.left - 4f else b.left + 22f
            val maxX = if (openRight) b.right + 4f else b.right - 22f
            val minY = if (openTop) b.top - 4f else b.top + 22f
            val maxY = if (openBottom) b.bottom + 4f else b.bottom - 22f

            if (x >= minX && x <= maxX && y >= minY && y <= maxY) {
                inUnlockedArea = true
                break
            }
        }
        if (!inUnlockedArea) return false

        ensureMapIndexed(map)
        val centerCoord = worldToChunkCoord(x, y)
        for (dy in -1..1) {
            for (dx in -1..1) {
                val cx = centerCoord.cx + dx
                val cy = centerCoord.cy + dy
                if (cx !in 0 until WORLD_COLS || cy !in 0 until WORLD_ROWS) continue
                val coord = ChunkCoord(cx, cy)
                val slice = chunkCache[coord]
                if (slice == null) {
                    if (dx == 0 && dy == 0) return false
                    continue
                }

                for (i in 0 until slice.outcrops.size) {
                    val oc = slice.outcrops[i].bounds
                    if (x >= oc.left - 6f && x <= oc.right + 6f && y >= oc.top - 6f && y <= oc.bottom + 6f) {
                        return false
                    }
                }
                for (i in 0 until slice.wallObstacles.size) {
                    val p = slice.wallObstacles[i]
                    if (x >= p.left - 12f && x <= p.right + 12f && y >= p.top - 12f && y <= p.bottom + 12f) {
                        return false
                    }
                }
                for (i in 0 until slice.lakes.size) {
                    val lake = slice.lakes[i]
                    val rx = (lake.radiusX * 0.85f).coerceAtLeast(10f)
                    val ry = (lake.radiusY * 0.85f).coerceAtLeast(10f)
                    val nxLake = (x - lake.x) / rx
                    val nyLake = (y - lake.y) / ry
                    if (nxLake * nxLake + nyLake * nyLake < 1.0f) {
                        val onFootbridge = if (lake.bridgeHorizontal) {
                            abs(y - lake.y) <= 28f
                        } else {
                            abs(x - lake.x) <= 28f
                        }
                        if (!onFootbridge) return false
                    }
                }
            }
        }
        return true
    }
}
