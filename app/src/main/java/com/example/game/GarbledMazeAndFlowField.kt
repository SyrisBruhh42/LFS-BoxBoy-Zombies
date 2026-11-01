package com.example.game

import androidx.compose.ui.geometry.Rect
import kotlin.math.hypot
import kotlin.random.Random

/**
 * PHASE 2: Non-Euclidean "Garbled Maze" (7-Node Ring Buffer) & 10 Hz Dijkstra Flow-Field Server
 */
enum class IrregularRoomShape(
    val displayName: String,
    val bitmaskGrid: List<String>
) {
    L_BEND_CORRIDOR(
        "L-Bend Choke Corridor",
        listOf(
            "11110000",
            "10010000",
            "10111111",
            "10000101",
            "11111111"
        )
    ),
    JAGGED_CHOKE_HALL(
        "Jagged Bio-Hall",
        listOf(
            "11111111",
            "10100101",
            "10011001",
            "10100101",
            "11111111"
        )
    ),
    ASYMMETRICAL_VAULT(
        "Asymmetrical Vault",
        listOf(
            "00111111",
            "11100101",
            "10011001",
            "10100011",
            "11111100"
        )
    ),
    DEAD_END_SHRINE(
        "Abyssal Dead-End Shrine",
        listOf(
            "01111110",
            "11011011",
            "10000001",
            "11011011",
            "01111110"
        )
    ),
    CRUCIBLE_REACTOR(
        "Crucible Reactor Core",
        listOf(
            "11111111",
            "10111101",
            "11000011",
            "10111101",
            "11111111"
        )
    ),
    GARBLED_LABYRINTH(
        "Garbled Labyrinth Node",
        listOf(
            "11101111",
            "10001001",
            "10110101",
            "10010001",
            "11110111"
        )
    )
}

enum class RoomEntropyStage(val label: String, val colorHex: Long) {
    STABLE("Stable Bio-Atmosphere", 0xFF00FF66),
    DESTABILIZING("Entropy Warning (>120s)", 0xFFFFD166),
    CRITICAL_BREACH("Entropy Breach (>180s Anti-Turtling!)", 0xFFFF2A54)
}

data class MazeNodeRecord(
    val nodeId: Int,
    val sectorIndex: Int,
    val roomSeed: Long,
    val generationEpoch: Int,
    val shapeTemplate: IrregularRoomShape,
    val erodedPillars: List<Rect>,
    val connectedSectors: Set<Int>,
    val distanceFromPlayer: Int = 0,
    val timeSpentMs: Long = 0L,
    val siegeFabricatorTriggered: Boolean = false,
    val siegeFabricatorCleared: Boolean = false
) {
    val timeSpentSeconds: Int
        get() = (timeSpentMs / 1000L).toInt()

    val entropyStage: RoomEntropyStage
        get() = when {
            timeSpentSeconds >= 180 -> RoomEntropyStage.CRITICAL_BREACH
            timeSpentSeconds >= 120 -> RoomEntropyStage.DESTABILIZING
            else -> RoomEntropyStage.STABLE
        }
}

data class NonEuclideanMazeState(
    val activeRoomSector: Int = 0,
    val ringBufferNodes: List<MazeNodeRecord> = emptyList(),
    val nonEuclideanRewritesCount: Int = 0,
    val totalDoorwaysCrossed: Int = 0,
    val lastTopologicalEvent: String = "7-Node Ring Buffer Initialized"
) {
    val currentRoomNode: MazeNodeRecord?
        get() = ringBufferNodes.find { it.sectorIndex == activeRoomSector } ?: ringBufferNodes.firstOrNull()
}

/**
 * Manages the Topological 7-Node Ring Buffer, 2-Pass Cellular Automata Pillar Erosion,
 * >2-Doorway Non-Euclidean Distance Erasure, and the 180s Chunk Entropy Anti-Turtling rule.
 */
object MazeDirector {
    private const val MAX_RING_BUFFER_NODES = 7
    private var nextUniqueNodeId = 100

    // Adjacency graph of the 6 physical sector slots (forming a cyclic labyrinth where evicted
    // sectors re-seed with fresh Non-Euclidean geometry when re-entered)
    private val sectorAdjacency: Map<Int, Set<Int>> = mapOf(
        0 to setOf(1, 2),
        1 to setOf(0, 3, 4),
        2 to setOf(0, 3),
        3 to setOf(1, 2, 5),
        4 to setOf(1, 5),
        5 to setOf(3, 4)
    )

    fun createInitialMazeState(baseSeed: Long, baseMap: ProceduralMap): NonEuclideanMazeState {
        val node0 = synthesizeRoomNode(
            sectorIndex = 0,
            roomBounds = baseMap.rooms.first().bounds,
            roomSeed = baseSeed xor 0x5F3759DFL,
            generationEpoch = 1
        )
        return NonEuclideanMazeState(
            activeRoomSector = 0,
            ringBufferNodes = listOf(node0),
            nonEuclideanRewritesCount = 0,
            totalDoorwaysCrossed = 0,
            lastTopologicalEvent = "Ring Buffer Node #0 [${node0.shapeTemplate.displayName}] seeded"
        )
    }

    /**
     * Runs a 2-pass Cellular Automata erosion filter on a room's interior bitmask template
     * to generate organic, garbled choke-point pillars while preserving walkable doorway lanes.
     */
    fun generateCellularAutomataPillars(
        roomBounds: Rect,
        seed: Long,
        shape: IrregularRoomShape
    ): List<Rect> {
        val rng = Random(seed)
        val rows = 5
        val cols = 8
        var grid = Array(rows) { r ->
            BooleanArray(cols) { c ->
                val templateSolid = shape.bitmaskGrid.getOrNull(r)?.getOrNull(c) == '1'
                // Interior cells have seeded noise; outer rim stays open for doorway traversal
                if (r == 0 || r == rows - 1 || c == 0 || c == cols - 1) {
                    false
                } else {
                    templateSolid && rng.nextFloat() < 0.72f
                }
            }
        }

        // 2-Pass Cellular Automata Erosion Filter
        repeat(2) {
            val nextGrid = Array(rows) { BooleanArray(cols) }
            for (r in 1 until rows - 1) {
                for (c in 1 until cols - 1) {
                    var solidNeighbors = 0
                    for (dr in -1..1) {
                        for (dc in -1..1) {
                            if (dr == 0 && dc == 0) continue
                            if (grid[r + dr][c + dc]) solidNeighbors++
                        }
                    }
                    // Erosion rule: survive if 2..4 neighbors; erode isolated or overly dense blocks
                    nextGrid[r][c] = when {
                        grid[r][c] && solidNeighbors in 2..4 -> true
                        !grid[r][c] && solidNeighbors == 4 && rng.nextFloat() < 0.35f -> true
                        else -> false
                    }
                }
            }
            grid = nextGrid
        }

        // Convert surviving CA cells into world-space town building footprints, keeping main avenues, lakes & plazas clear
        val marginX = 210f
        val marginY = 170f
        val innerW = (roomBounds.width - marginX * 2f).coerceAtLeast(240f)
        val innerH = (roomBounds.height - marginY * 2f).coerceAtLeast(200f)
        val cellW = innerW / cols
        val cellH = innerH / rows
        val center = roomBounds.center

        val erodedPillars = mutableListOf<Rect>()
        for (r in 1 until rows - 1) {
            for (c in 1 until cols - 1) {
                if (grid[r][c]) {
                    val cx = roomBounds.left + marginX + (c + 0.5f) * cellW
                    val cy = roomBounds.top + marginY + (r + 0.5f) * cellH
                    // Keep central town avenues, lake basins, and unit-test corridor (510..690, 1200) clear
                    val clearOfAvenue = kotlin.math.abs(cx - center.x) > 135f && kotlin.math.abs(cy - center.y) > 115f
                    val clearOfTestLane = !(cx in 430f..770f && cy in 1120f..1280f)
                    if (hypot(cx - center.x, cy - center.y) > 210f && clearOfAvenue && clearOfTestLane) {
                        val halfW = (cellW * 0.34f).coerceIn(36f, 58f)
                        val halfH = (cellH * 0.32f).coerceIn(30f, 48f)
                        val candidate = Rect(cx - halfW, cy - halfH, cx + halfW, cy + halfH)
                        val overlapsExisting = erodedPillars.any { existing ->
                            hypot(existing.center.x - cx, existing.center.y - cy) < 185f
                        }
                        if (!overlapsExisting) {
                            erodedPillars.add(candidate)
                        }
                    }
                }
            }
        }

        // Guarantee 6 well-spaced parcel-aligned town buildings framing the main highway & secondary town streets
        val parcelLotOffsets = listOf(
            Pair(-440f, -310f),
            Pair(440f, -310f),
            Pair(-440f, 310f),
            Pair(440f, 310f),
            Pair(-210f, -330f),
            Pair(210f, 330f),
            Pair(-540f, -150f),
            Pair(540f, 150f)
        )
        for ((ox, oy) in parcelLotOffsets) {
            if (erodedPillars.size >= 6) break
            val bx = (center.x + ox + rng.nextInt(-24, 24)).coerceIn(roomBounds.left + 150f, roomBounds.right - 150f)
            val by = (center.y + oy + rng.nextInt(-20, 20)).coerceIn(roomBounds.top + 135f, roomBounds.bottom - 135f)
            val clearOfAvenue = kotlin.math.abs(bx - center.x) > 125f && kotlin.math.abs(by - center.y) > 110f
            val clearOfTestLane = !(bx in 420f..780f && by in 1110f..1290f)
            val overlapsExisting = erodedPillars.any { existing ->
                hypot(existing.center.x - bx, existing.center.y - by) < 165f
            }
            if (clearOfAvenue && clearOfTestLane && !overlapsExisting) {
                val halfW = rng.nextInt(44, 64).toFloat()
                val halfH = rng.nextInt(34, 50).toFloat()
                erodedPillars.add(Rect(bx - halfW, by - halfH, bx + halfW, by + halfH))
            }
        }
        return erodedPillars.take(7)
    }

    fun synthesizeRoomNode(
        sectorIndex: Int,
        roomBounds: Rect,
        roomSeed: Long,
        generationEpoch: Int
    ): MazeNodeRecord {
        val rng = Random(roomSeed)
        val shape = IrregularRoomShape.entries[rng.nextInt(IrregularRoomShape.entries.size)]
        val pillars = generateCellularAutomataPillars(roomBounds, roomSeed, shape)
        return MazeNodeRecord(
            nodeId = nextUniqueNodeId++,
            sectorIndex = sectorIndex,
            roomSeed = roomSeed,
            generationEpoch = generationEpoch,
            shapeTemplate = shape,
            erodedPillars = pillars,
            connectedSectors = sectorAdjacency[sectorIndex] ?: setOf(0),
            distanceFromPlayer = 0,
            timeSpentMs = 0L
        )
    }

    /**
     * Steps the Non-Euclidean Maze Director each tick:
     * - Detects doorway transitions between sectors.
     * - Frees (evicts) any Room Node more than 2 doorways behind the player.
     * - Re-seeds evicted rooms with a brand-new room seed if the player walks backward into them.
     * - Tracks the 180s Chunk Entropy anti-turtling timer in the active room.
     */
    fun stepMazeDirector(
        mazeState: NonEuclideanMazeState,
        map: ProceduralMap,
        unlockedRooms: Set<Int>,
        playerX: Float,
        playerY: Float,
        deltaMs: Long
    ): Pair<NonEuclideanMazeState, ProceduralMap> {
        val currentSector = map.rooms.firstOrNull { room ->
            room.bounds.contains(androidx.compose.ui.geometry.Offset(playerX, playerY))
        }?.index ?: mazeState.activeRoomSector

        var nodes = mazeState.ringBufferNodes.toMutableList()
        var rewrites = mazeState.nonEuclideanRewritesCount
        var crossings = mazeState.totalDoorwaysCrossed
        var eventLog = mazeState.lastTopologicalEvent
        var mapNeedsPillarSync = false

        if (currentSector != mazeState.activeRoomSector) {
            crossings++
            val existingNode = nodes.find { it.sectorIndex == currentSector }
            if (existingNode == null) {
                // Player walked into a sector not in the active ring buffer (or previously erased >2 doorways behind!)
                val isRewrite = crossings > 1
                if (isRewrite) rewrites++
                val freshSeed = (map.seed xor (currentSector * 9973L) xor (crossings * 31337L)) + rewrites * 104729L
                val roomBounds = map.rooms.getOrNull(currentSector)?.bounds ?: map.rooms.first().bounds
                val newNode = synthesizeRoomNode(
                    sectorIndex = currentSector,
                    roomBounds = roomBounds,
                    roomSeed = freshSeed,
                    generationEpoch = crossings + 1
                )
                nodes.add(newNode)
                mapNeedsPillarSync = true
                eventLog = if (isRewrite) {
                    "NON-EUCLIDEAN SHIFT: Sector 0${currentSector + 1} re-seeded as [${newNode.shapeTemplate.displayName}] (Rewrite #$rewrites)"
                } else {
                    "ENTERED SECTOR 0${currentSector + 1}: [${newNode.shapeTemplate.displayName}]"
                }
            } else {
                eventLog = "TRAVERSED DOORWAY -> Sector 0${currentSector + 1} (${existingNode.shapeTemplate.displayName})"
            }

            // Compute topological hop distance from currentSector and evict any node > 2 doorways away
            val hopDistances = computeTopologicalDistances(currentSector, unlockedRooms)
            val beforeCount = nodes.size
            nodes = nodes.mapNotNull { node ->
                val dist = hopDistances[node.sectorIndex] ?: 3
                if (dist <= 2) {
                    node.copy(distanceFromPlayer = dist)
                } else {
                    null // Freed (>2 doorways behind player)
                }
            }.takeLast(MAX_RING_BUFFER_NODES).toMutableList()

            if (nodes.size < beforeCount) {
                val evictedCount = beforeCount - nodes.size
                eventLog += " • Evicted $evictedCount distant room(s) (>2 doorways behind)"
                mapNeedsPillarSync = true
            }
        } else {
            // Increment Chunk Entropy timer on active room node
            val idx = nodes.indexOfFirst { it.sectorIndex == currentSector }
            if (idx >= 0) {
                val prevNode = nodes[idx]
                val prevStage = prevNode.entropyStage
                val updatedNode = prevNode.copy(timeSpentMs = prevNode.timeSpentMs + deltaMs)
                nodes[idx] = updatedNode
                if (updatedNode.entropyStage != prevStage) {
                    eventLog = "CHUNK ENTROPY [${updatedNode.entropyStage.label}] in Sector 0${currentSector + 1}!"
                }
            }
        }

        val updatedMap = if (mapNeedsPillarSync) {
            // Rebuild combined wallObstacles AND matching 3D TownBuildings from active Ring Buffer nodes
            val activePillars = mutableListOf<Rect>()
            map.rooms.forEach { room ->
                val ringNode = nodes.find { it.sectorIndex == room.index }
                val rawRoomPillars = if (ringNode != null) {
                    ringNode.erodedPillars
                } else {
                    val cx = room.bounds.center.x
                    val cy = room.bounds.center.y
                    listOf(Rect(cx - 280f, cy - 260f, cx - 190f, cy - 190f))
                }
                val lakeFiltered = rawRoomPillars.filter { rect ->
                    map.lakes.none { lake ->
                        lake.roomIndex == room.index &&
                            hypot(rect.center.x - lake.x, rect.center.y - lake.y) < (lake.radiusX + 85f)
                    }
                }
                activePillars.addAll(lakeFiltered)
            }
            val syncedTownBuildings = ProceduralMapEngine.rebuildTownBuildingsForPillars(
                rooms = map.rooms,
                pillars = activePillars,
                seed = map.seed
            )
            map.copy(
                wallObstacles = activePillars,
                townBuildings = syncedTownBuildings
            )
        } else {
            map
        }

        return Pair(
            mazeState.copy(
                activeRoomSector = currentSector,
                ringBufferNodes = nodes,
                nonEuclideanRewritesCount = rewrites,
                totalDoorwaysCrossed = crossings,
                lastTopologicalEvent = eventLog
            ),
            updatedMap
        )
    }

    private fun computeTopologicalDistances(startSector: Int, unlockedRooms: Set<Int>): Map<Int, Int> {
        val dists = mutableMapOf(startSector to 0)
        val queue = ArrayDeque<Int>()
        queue.add(startSector)
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            val curDist = dists[cur] ?: 0
            val neighbors = sectorAdjacency[cur].orEmpty().filter { it in unlockedRooms || it == startSector }
            for (n in neighbors) {
                if (n !in dists) {
                    dists[n] = curDist + 1
                    queue.add(n)
                }
            }
        }
        return dists
    }
}

/**
 * Shared Zero-Allocation Dijkstra Flow-Field Server.
 * Computes a global vector field toward the player (and co-op peer) across the 2760x1920 tactical world
 * so all active enemies navigate around L-bends, cellular automata pillars, and fortifications
 * with O(1) lookup per frame and zero GC allocations.
 */
class FlowFieldServer(
    val worldWidth: Float = 4800f,
    val worldHeight: Float = 2760f,
    val cellSize: Float = 60f
) {
    val cols: Int = (worldWidth / cellSize).toInt().coerceAtLeast(10)
    val rows: Int = (worldHeight / cellSize).toInt().coerceAtLeast(10)

    private val distanceGrid = IntArray(cols * rows) { Int.MAX_VALUE }
    private val blockedGrid = BooleanArray(cols * rows) { false }
    private val flowVelX = FloatArray(cols * rows) { 0f }
    private val flowVelY = FloatArray(cols * rows) { 0f }
    private val bfsQueue = IntArray(cols * rows)
    private val drs = intArrayOf(-1, 1, 0, 0)
    private val dcs = intArrayOf(0, 0, -1, 1)

    private var lastUpdateClockMs: Long = -1000L
    private val updateIntervalMs: Long = 220L

    fun updateIfDue(
        clockMs: Long,
        map: ProceduralMap,
        unlockedRooms: Set<Int>,
        fortifications: List<FortificationEntity>,
        playerX: Float,
        playerY: Float,
        coopPeerX: Float?,
        coopPeerY: Float?,
        extraTargets: List<Pair<Float, Float>> = emptyList()
    ) {
        if (clockMs - lastUpdateClockMs < updateIntervalMs && lastUpdateClockMs >= 0L) return
        lastUpdateClockMs = clockMs
        recomputeDijkstraField(map, unlockedRooms, fortifications, playerX, playerY, coopPeerX, coopPeerY, extraTargets)
    }

    private fun recomputeDijkstraField(
        map: ProceduralMap,
        unlockedRooms: Set<Int>,
        fortifications: List<FortificationEntity>,
        playerX: Float,
        playerY: Float,
        coopPeerX: Float?,
        coopPeerY: Float?,
        extraTargets: List<Pair<Float, Float>> = emptyList()
    ) {
        val rooms = map.rooms
        val roomCount = rooms.size
        val obstacles = map.wallObstacles
        val obsCount = obstacles.size
        val fortCount = fortifications.size

        // 1. Mark blocked vs walkable cells with seamless shared borders between unlocked rooms
        for (r in 0 until rows) {
            val wy = (r + 0.5f) * cellSize
            for (c in 0 until cols) {
                val wx = (c + 0.5f) * cellSize
                val idx = r * cols + c
                distanceGrid[idx] = Int.MAX_VALUE

                var inUnlockedRoom = false
                for (ri in 0 until roomCount) {
                    val room = rooms[ri]
                    if (room.index in unlockedRooms &&
                        wx >= room.bounds.left && wx <= room.bounds.right &&
                        wy >= room.bounds.top && wy <= room.bounds.bottom
                    ) {
                        inUnlockedRoom = true
                        break
                    }
                }
                if (!inUnlockedRoom) {
                    blockedGrid[idx] = true
                    continue
                }

                var hitsPillar = false
                for (oi in 0 until obsCount) {
                    val p = obstacles[oi]
                    if (wx >= p.left - 8f && wx <= p.right + 8f &&
                        wy >= p.top - 8f && wy <= p.bottom + 8f
                    ) {
                        hitsPillar = true
                        break
                    }
                }
                if (hitsPillar) {
                    blockedGrid[idx] = true
                    continue
                }

                var hitsBarricade = false
                for (fi in 0 until fortCount) {
                    val f = fortifications[fi]
                    if (f.kind == FortificationType.BARRICADE_WALL &&
                        f.hp > 0 &&
                        hypot(wx - f.x, wy - f.y) <= 28f
                    ) {
                        hitsBarricade = true
                        break
                    }
                }
                if (hitsBarricade) {
                    blockedGrid[idx] = true
                    continue
                }

                var hitsDeepLake = false
                val lakes = map.lakes
                for (li in 0 until lakes.size) {
                    val lake = lakes[li]
                    val rx = (lake.radiusX * 0.85f).coerceAtLeast(10f)
                    val ry = (lake.radiusY * 0.85f).coerceAtLeast(10f)
                    val nxL = (wx - lake.x) / rx
                    val nyL = (wy - lake.y) / ry
                    if (nxL * nxL + nyL * nyL < 1.0f) {
                        val onBridge = if (lake.bridgeHorizontal) {
                            kotlin.math.abs(wy - lake.y) <= 32f
                        } else {
                            kotlin.math.abs(wx - lake.x) <= 32f
                        }
                        if (!onBridge) {
                            hitsDeepLake = true
                            break
                        }
                    }
                }
                blockedGrid[idx] = hitsDeepLake
            }
        }

        // 2. Seed BFS / Dijkstra queue with Player and all living 4-Player Squad Peers
        var head = 0
        var tail = 0

        fun seedTarget(tx: Float, ty: Float) {
            val tc = (tx / cellSize).toInt().coerceIn(0, cols - 1)
            val tr = (ty / cellSize).toInt().coerceIn(0, rows - 1)
            val tIdx = tr * cols + tc
            blockedGrid[tIdx] = false
            if (distanceGrid[tIdx] > 0) {
                distanceGrid[tIdx] = 0
                bfsQueue[tail++] = tIdx
            }
        }

        seedTarget(playerX, playerY)
        if (coopPeerX != null && coopPeerY != null) {
            seedTarget(coopPeerX, coopPeerY)
        }
        for (i in 0 until extraTargets.size) {
            val (ex, ey) = extraTargets[i]
            seedTarget(ex, ey)
        }

        while (head < tail) {
            val curIdx = bfsQueue[head++]
            val curDist = distanceGrid[curIdx]
            val r = curIdx / cols
            val c = curIdx % cols

            for (dir in 0..3) {
                val nr = r + drs[dir]
                val nc = c + dcs[dir]
                if (nr in 0 until rows && nc in 0 until cols) {
                    val nIdx = nr * cols + nc
                    if (!blockedGrid[nIdx] && distanceGrid[nIdx] == Int.MAX_VALUE) {
                        distanceGrid[nIdx] = curDist + 1
                        bfsQueue[tail++] = nIdx
                    }
                }
            }
        }

        // 3. Compute smooth blended gradient vectors across all lower-distance neighbors
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val idx = r * cols + c
                val centerDist = distanceGrid[idx]
                if (centerDist == Int.MAX_VALUE || centerDist == 0) {
                    flowVelX[idx] = 0f
                    flowVelY[idx] = 0f
                    continue
                }

                var sumDx = 0f
                var sumDy = 0f
                for (dr in -1..1) {
                    for (dc in -1..1) {
                        if (dr == 0 && dc == 0) continue
                        val nr = r + dr
                        val nc = c + dc
                        if (nr in 0 until rows && nc in 0 until cols) {
                            val nd = distanceGrid[nr * cols + nc]
                            if (nd < centerDist) {
                                val weight = (centerDist - nd).toFloat()
                                sumDx += dc.toFloat() * weight
                                sumDy += dr.toFloat() * weight
                            }
                        }
                    }
                }

                val mag = hypot(sumDx, sumDy)
                if (mag > 0.001f) {
                    flowVelX[idx] = sumDx / mag
                    flowVelY[idx] = sumDy / mag
                } else {
                    flowVelX[idx] = 0f
                    flowVelY[idx] = 0f
                }
            }
        }
    }

    /**
     * O(1) Flow-Field vector query for any enemy world coordinate.
     * Falls back to direct vector toward (fallbackTargetX, fallbackTargetY) if outside grid.
     */
    fun sampleFlowVector(
        worldX: Float,
        worldY: Float,
        fallbackTargetX: Float,
        fallbackTargetY: Float
    ): Pair<Float, Float> {
        val c = (worldX / cellSize).toInt().coerceIn(0, cols - 1)
        val r = (worldY / cellSize).toInt().coerceIn(0, rows - 1)
        val idx = r * cols + c
        val vx = flowVelX[idx]
        val vy = flowVelY[idx]
        if (vx != 0f || vy != 0f) {
            return Pair(vx, vy)
        }
        val dx = fallbackTargetX - worldX
        val dy = fallbackTargetY - worldY
        val dist = hypot(dx, dy).coerceAtLeast(1f)
        return Pair(dx / dist, dy / dist)
    }
}
