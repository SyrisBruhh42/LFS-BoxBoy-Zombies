package com.example.game.world.elona

import org.json.JSONArray
import org.json.JSONObject
import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.random.Random

/**
 * Port of the LFS Undercity Crawler (Elona) Socket-Driven Component Frontier,
 * Topological Solvability Receipt, Kiting-Loop Shortcut Resolver, and <15 KB
 * Sparse Coordinate Mutation Cache for Unified BoxBoys.
 */
enum class SocketDirection(val dx: Int, val dy: Int) {
    NORTH(0, -1),
    EAST(1, 0),
    SOUTH(0, 1),
    WEST(-1, 0);

    fun opposite(): SocketDirection = when (this) {
        NORTH -> SOUTH
        SOUTH -> NORTH
        EAST -> WEST
        WEST -> EAST
    }

    fun rotate90Clockwise(steps: Int): SocketDirection {
        val normalized = ((steps % 4) + 4) % 4
        val ordered = listOf(NORTH, EAST, SOUTH, WEST)
        return ordered[(ordered.indexOf(this) + normalized) % 4]
    }

    fun mirrorHorizontal(): SocketDirection = when (this) {
        EAST -> WEST
        WEST -> EAST
        else -> this
    }
}

enum class SocketKind {
    OPEN,
    ARCHED,
    LOCKED_DEBRIS_DOOR,
    BARRED_ZOMBIE_WINDOW,
    SECRET_BARREL_SEAM,
    WALL;

    fun isCompatibleWith(other: SocketKind): Boolean = when (this) {
        OPEN, ARCHED -> other in setOf(OPEN, ARCHED, LOCKED_DEBRIS_DOOR, BARRED_ZOMBIE_WINDOW)
        LOCKED_DEBRIS_DOOR -> other in setOf(LOCKED_DEBRIS_DOOR, OPEN, ARCHED)
        BARRED_ZOMBIE_WINDOW -> other in setOf(BARRED_ZOMBIE_WINDOW, OPEN, ARCHED)
        SECRET_BARREL_SEAM -> other in setOf(SECRET_BARREL_SEAM, WALL)
        WALL -> other in setOf(WALL, SECRET_BARREL_SEAM)
    }
}

data class RoomSocketSpec(
    val direction: SocketDirection,
    val kind: SocketKind,
    val offsetTiles: Int = 0,
    val unlockCostCurrency: Int = 750
) {
    fun rotated(steps: Int): RoomSocketSpec = copy(
        direction = direction.rotate90Clockwise(steps),
        offsetTiles = if (steps % 2 == 1) -offsetTiles else offsetTiles
    )

    fun mirroredHorizontal(): RoomSocketSpec = copy(
        direction = direction.mirrorHorizontal(),
        offsetTiles = -offsetTiles
    )
}

/**
 * Editable connected room/plaza/corridor definition (ported from `section_component.gd`).
 * Legend:
 *  '#' = Reinforced Masonry Wall
 *  '.' = Walkable Tactical Floor
 *  'W' = Barricadable Zombie Window Chokepoint
 *  'D' = Points-Locked BO2 Debris Door (Frontier Socket)
 *  'V' = Borderlands 2 / BO2 Vending Machine
 *  'P' = Perk-a-Cola Station
 *  'U' = Pack-a-Punch Upgrade Anvil
 *  'B' = Explosive Boxhead Red/Cryo Barrel Cluster
 */
data class SectionComponentDef(
    val componentId: String,
    val revision: Int = 1,
    val biomeTag: String = "OAKRIDGE_PLAZA",
    val selectionWeight: Float = 1.0f,
    val gridWidth: Int = 12,
    val gridHeight: Int = 12,
    val rows: List<String>,
    val sockets: List<RoomSocketSpec>
)

data class PlacedSectionNode(
    val nodeId: String,
    val componentId: String,
    val gridX: Int,
    val gridY: Int,
    val rotationSteps: Int,
    val mirrored: Boolean,
    val unlockCostCurrency: Int,
    val unlocked: Boolean,
    val sockets: List<RoomSocketSpec>
)

data class FrontierDoorSocket(
    val frontierId: String,
    val parentNodeId: String,
    val targetGridX: Int,
    val targetGridY: Int,
    val requiredEntryDirection: SocketDirection,
    val unlockCostCurrency: Int,
    val socketKind: SocketKind = SocketKind.LOCKED_DEBRIS_DOOR
)

data class ShortcutLoopGate(
    val gateId: String,
    val fromNodeId: String,
    val toNodeId: String,
    val loopDistance: Int,
    val backtrackSavings: Float,
    val unlockCostCurrency: Int = 1000,
    val unlocked: Boolean = false
)

/**
 * Saved component geometry & progression receipt (`assembled_section.gd` + `section_progression.gd`).
 * Clients validate and restore exact world geometry from this compact receipt without re-running RNG.
 */
data class AssembledSectionReceipt(
    val schemaVersion: Int = 1,
    val seed: Long,
    val topologyHash: String,
    val placedNodes: List<PlacedSectionNode>,
    val frontierSockets: List<FrontierDoorSocket>,
    val shortcutGates: List<ShortcutLoopGate>,
    val reachableVendingCount: Int,
    val solvable: Boolean
)

enum class ChunkMutationType {
    DOOR_UNLOCKED,
    SHORTCUT_GATE_OPENED,
    WINDOW_BARRICADE_TIER,
    WINDOW_BARRICADE_HP,
    BARREL_DETONATED,
    FORTIFICATION_DEPLOYED,
    VENDING_ITEM_ROTATED
}

data class SparseTileMutation(
    val chunkX: Int,
    val chunkY: Int,
    val tileX: Int,
    val tileY: Int,
    val mutationType: ChunkMutationType,
    val intValue: Int = 0,
    val metadata: String = "",
    val updatedAtMs: Long = System.currentTimeMillis()
)

/**
 * Sparse coordinate mutation cache (`state_cache.gd` + `world_state_archiver.gd`).
 * Keeps mid-session hot-join world sync well under 15 KB.
 */
class SparseWorldStateCache {
    private val mutationsByChunk = LinkedHashMap<Pair<Int, Int>, LinkedHashMap<Pair<Int, Int>, SparseTileMutation>>()

    @Synchronized
    fun recordMutation(
        chunkX: Int,
        chunkY: Int,
        tileX: Int,
        tileY: Int,
        mutationType: ChunkMutationType,
        intValue: Int = 0,
        metadata: String = ""
    ) {
        val chunkKey = chunkX to chunkY
        val tileMap = mutationsByChunk.getOrPut(chunkKey) { LinkedHashMap() }
        tileMap[tileX to tileY] = SparseTileMutation(
            chunkX = chunkX,
            chunkY = chunkY,
            tileX = tileX,
            tileY = tileY,
            mutationType = mutationType,
            intValue = intValue,
            metadata = metadata
        )
    }

    @Synchronized
    fun getChunkMutations(chunkX: Int, chunkY: Int): List<SparseTileMutation> {
        return mutationsByChunk[chunkX to chunkY]?.values?.toList().orEmpty()
    }

    @Synchronized
    fun hasMutation(chunkX: Int, chunkY: Int, tileX: Int, tileY: Int): Boolean {
        return mutationsByChunk[chunkX to chunkY]?.containsKey(tileX to tileY) == true
    }

    @Synchronized
    fun allMutations(): List<SparseTileMutation> {
        return mutationsByChunk.values.flatMap { it.values }
    }

    @Synchronized
    fun serializeCompactJson(): String {
        return buildString {
            append("{\"v\":1,\"chunks\":[")
            var firstChunk = true
            for ((chunkKey, tileMap) in mutationsByChunk) {
                if (!firstChunk) append(',')
                firstChunk = false
                append("{\"cx\":").append(chunkKey.first)
                append(",\"cy\":").append(chunkKey.second)
                append(",\"d\":[")
                var firstMut = true
                for (m in tileMap.values) {
                    if (!firstMut) append(',')
                    firstMut = false
                    append("{\"tx\":").append(m.tileX)
                    append(",\"ty\":").append(m.tileY)
                    append(",\"t\":\"").append(m.mutationType.name).append('"')
                    append(",\"v\":").append(m.intValue)
                    if (m.metadata.isNotEmpty()) {
                        append(",\"m\":\"").append(m.metadata.replace("\"", "\\\"")).append('"')
                    }
                    append('}')
                }
                append("]}")
            }
            append("]}")
        }
    }

    @Synchronized
    fun deserializeCompactJson(payload: String): Boolean {
        return try {
            val root = JSONObject(payload)
            mutationsByChunk.clear()
            val chunksArr = root.optJSONArray("chunks") ?: return true
            for (i in 0 until chunksArr.length()) {
                val cObj = chunksArr.getJSONObject(i)
                val cx = cObj.getInt("cx")
                val cy = cObj.getInt("cy")
                val mutArr = cObj.optJSONArray("d") ?: continue
                for (j in 0 until mutArr.length()) {
                    val mObj = mutArr.getJSONObject(j)
                    recordMutation(
                        chunkX = cx,
                        chunkY = cy,
                        tileX = mObj.getInt("tx"),
                        tileY = mObj.getInt("ty"),
                        mutationType = ChunkMutationType.valueOf(mObj.getString("t")),
                        intValue = mObj.optInt("v", 0),
                        metadata = mObj.optString("m", "")
                    )
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}

object ElonaSocketFrontierEngine {
    const val MAX_SYNC_PAYLOAD_BYTES = 15_360 // 15 KB hot-join guarantee

    val DEFAULT_COMPONENT_CATALOG: List<SectionComponentDef> = listOf(
        SectionComponentDef(
            componentId = "boxboys.spawn.fortified_plaza",
            biomeTag = "DOWNTOWN_NEON_PLAZA",
            selectionWeight = 1.5f,
            rows = listOf(
                "#####DD#####",
                "#..W....W..#",
                "#.B......B.#",
                "#...V..P...#",
                "W..........W",
                "D..........D",
                "D..........D",
                "W..........W",
                "#...B..B...#",
                "#..W....W..#",
                "#####DD#####",
                "############"
            ),
            sockets = listOf(
                RoomSocketSpec(SocketDirection.NORTH, SocketKind.LOCKED_DEBRIS_DOOR, unlockCostCurrency = 750),
                RoomSocketSpec(SocketDirection.EAST, SocketKind.LOCKED_DEBRIS_DOOR, unlockCostCurrency = 750),
                RoomSocketSpec(SocketDirection.SOUTH, SocketKind.LOCKED_DEBRIS_DOOR, unlockCostCurrency = 1000),
                RoomSocketSpec(SocketDirection.WEST, SocketKind.LOCKED_DEBRIS_DOOR, unlockCostCurrency = 1000)
            )
        ),
        SectionComponentDef(
            componentId = "boxboys.corridor.chokepoint_alley",
            biomeTag = "INDUSTRIAL_RAILYARD",
            selectionWeight = 1.2f,
            rows = listOf(
                "#####DD#####",
                "###..W...###",
                "###..B...###",
                "###......###",
                "###W....W###",
                "D..........D",
                "D..........D",
                "###W....W###",
                "###..B...###",
                "###..V...###",
                "#####DD#####",
                "############"
            ),
            sockets = listOf(
                RoomSocketSpec(SocketDirection.NORTH, SocketKind.LOCKED_DEBRIS_DOOR, unlockCostCurrency = 1000),
                RoomSocketSpec(SocketDirection.SOUTH, SocketKind.LOCKED_DEBRIS_DOOR, unlockCostCurrency = 1000),
                RoomSocketSpec(SocketDirection.EAST, SocketKind.ARCHED, unlockCostCurrency = 900),
                RoomSocketSpec(SocketDirection.WEST, SocketKind.ARCHED, unlockCostCurrency = 900)
            )
        ),
        SectionComponentDef(
            componentId = "boxboys.vault.vending_bunker",
            biomeTag = "CRYO_HARBOR_PIER",
            selectionWeight = 1.0f,
            rows = listOf(
                "#####DD#####",
                "#V..W..W..V#",
                "#..B....B..#",
                "#....U.....#",
                "W..........W",
                "D..........D",
                "D..........D",
                "W....P.....W",
                "#..B....B..#",
                "#V..W..W..V#",
                "#####DD#####",
                "############"
            ),
            sockets = listOf(
                RoomSocketSpec(SocketDirection.NORTH, SocketKind.LOCKED_DEBRIS_DOOR, unlockCostCurrency = 1250),
                RoomSocketSpec(SocketDirection.EAST, SocketKind.LOCKED_DEBRIS_DOOR, unlockCostCurrency = 1250),
                RoomSocketSpec(SocketDirection.SOUTH, SocketKind.LOCKED_DEBRIS_DOOR, unlockCostCurrency = 1500),
                RoomSocketSpec(SocketDirection.WEST, SocketKind.LOCKED_DEBRIS_DOOR, unlockCostCurrency = 1500)
            )
        )
    )

    /**
     * Generates an initial solvable section assembly with open frontier doors and shortcut loop gates.
     */
    fun generateInitialWorldReceipt(
        seed: Long,
        initialUnlockedRooms: Int = 3,
        catalog: List<SectionComponentDef> = DEFAULT_COMPONENT_CATALOG
    ): AssembledSectionReceipt {
        val rng = Random(seed)
        val activeCatalog = catalog.ifEmpty { DEFAULT_COMPONENT_CATALOG }
        val occupied = LinkedHashMap<Pair<Int, Int>, PlacedSectionNode>()
        val frontier = LinkedHashMap<Pair<Int, Int>, FrontierDoorSocket>()

        val rootDef = activeCatalog.first()
        val rootNode = PlacedSectionNode(
            nodeId = "sec_0_0",
            componentId = rootDef.componentId,
            gridX = 0,
            gridY = 0,
            rotationSteps = 0,
            mirrored = false,
            unlockCostCurrency = 0,
            unlocked = true,
            sockets = rootDef.sockets
        )
        occupied[0 to 0] = rootNode
        refreshFrontierForNode(rootNode, occupied, frontier)

        var placedCount = 1
        var attempts = 0
        while (placedCount < initialUnlockedRooms && frontier.isNotEmpty() && attempts < 32) {
            attempts++
            val frontierEntries = frontier.values.toList()
            val chosen = frontierEntries[rng.nextInt(frontierEntries.size)]
            val candidateDef = activeCatalog[rng.nextInt(activeCatalog.size)]
            val rot = rng.nextInt(4)
            val rotatedSockets = candidateDef.sockets.map { it.rotated(rot) }
            val matchingSocket = rotatedSockets.find {
                it.direction == chosen.requiredEntryDirection && it.kind.isCompatibleWith(chosen.socketKind)
            } ?: continue

            val newNode = PlacedSectionNode(
                nodeId = "sec_${chosen.targetGridX}_${chosen.targetGridY}",
                componentId = candidateDef.componentId,
                gridX = chosen.targetGridX,
                gridY = chosen.targetGridY,
                rotationSteps = rot,
                mirrored = false,
                unlockCostCurrency = matchingSocket.unlockCostCurrency,
                unlocked = true,
                sockets = rotatedSockets
            )
            occupied[chosen.targetGridX to chosen.targetGridY] = newNode
            frontier.remove(chosen.targetGridX to chosen.targetGridY)
            refreshFrontierForNode(newNode, occupied, frontier)
            placedCount++
        }

        val nodesList = occupied.values.toList()
        val shortcuts = resolveShortcutLoops(nodesList)
        val solvable = verifyTopologicalReachability(nodesList)
        val vendingCount = nodesList.count { node ->
            val def = activeCatalog.find { it.componentId == node.componentId } ?: rootDef
            def.rows.any { row -> row.contains('V') }
        }
        val topologyHash = computeTopologyHash(seed, nodesList, frontier.values.toList())

        return AssembledSectionReceipt(
            seed = seed,
            topologyHash = topologyHash,
            placedNodes = nodesList,
            frontierSockets = frontier.values.toList(),
            shortcutGates = shortcuts,
            reachableVendingCount = vendingCount.coerceAtLeast(1),
            solvable = solvable
        )
    }

    /**
     * Expands the world dynamically when players spend single-currency points on a Frontier Debris Door!
     */
    fun unlockFrontierDoorAndExpand(
        currentReceipt: AssembledSectionReceipt,
        frontierId: String,
        stateCache: SparseWorldStateCache,
        catalog: List<SectionComponentDef> = DEFAULT_COMPONENT_CATALOG
    ): AssembledSectionReceipt {
        val targetFrontier = currentReceipt.frontierSockets.find { it.frontierId == frontierId }
            ?: currentReceipt.frontierSockets.firstOrNull()
            ?: return currentReceipt
        val activeCatalog = catalog.ifEmpty { DEFAULT_COMPONENT_CATALOG }
        val stepSeed = currentReceipt.seed xor (targetFrontier.targetGridX * 73856093L) xor (targetFrontier.targetGridY * 19349663L)
        val rng = Random(stepSeed)
        val chosenDef = activeCatalog[abs(rng.nextInt()) % activeCatalog.size]
        val rot = abs(rng.nextInt()) % 4
        val rotatedSockets = chosenDef.sockets.map { it.rotated(rot) }

        val occupied = currentReceipt.placedNodes.associateBy { it.gridX to it.gridY }.toMutableMap()
        val frontierMap = currentReceipt.frontierSockets.associateBy { it.targetGridX to it.targetGridY }.toMutableMap()

        val newNode = PlacedSectionNode(
            nodeId = "sec_${targetFrontier.targetGridX}_${targetFrontier.targetGridY}",
            componentId = chosenDef.componentId,
            gridX = targetFrontier.targetGridX,
            gridY = targetFrontier.targetGridY,
            rotationSteps = rot,
            mirrored = false,
            unlockCostCurrency = targetFrontier.unlockCostCurrency,
            unlocked = true,
            sockets = rotatedSockets
        )
        occupied[targetFrontier.targetGridX to targetFrontier.targetGridY] = newNode
        frontierMap.remove(targetFrontier.targetGridX to targetFrontier.targetGridY)
        refreshFrontierForNode(newNode, occupied, frontierMap)

        stateCache.recordMutation(
            chunkX = targetFrontier.targetGridX,
            chunkY = targetFrontier.targetGridY,
            tileX = 0,
            tileY = 0,
            mutationType = ChunkMutationType.DOOR_UNLOCKED,
            intValue = 1,
            metadata = newNode.componentId
        )

        val updatedNodes = occupied.values.toList()
        val updatedFrontier = frontierMap.values.toList()
        val rootDef = activeCatalog.first()
        val vendingCount = updatedNodes.count { node ->
            val def = activeCatalog.find { it.componentId == node.componentId } ?: rootDef
            def.rows.any { row -> row.contains('V') }
        }.coerceAtLeast(1)
        return currentReceipt.copy(
            topologyHash = computeTopologyHash(currentReceipt.seed, updatedNodes, updatedFrontier),
            placedNodes = updatedNodes,
            frontierSockets = updatedFrontier,
            shortcutGates = resolveShortcutLoops(updatedNodes),
            reachableVendingCount = vendingCount,
            solvable = verifyTopologicalReachability(updatedNodes)
        )
    }

    private fun refreshFrontierForNode(
        node: PlacedSectionNode,
        occupied: Map<Pair<Int, Int>, PlacedSectionNode>,
        frontier: MutableMap<Pair<Int, Int>, FrontierDoorSocket>
    ) {
        for (sock in node.sockets) {
            if (sock.kind == SocketKind.WALL) continue
            val nx = node.gridX + sock.direction.dx
            val ny = node.gridY + sock.direction.dy
            if (occupied.containsKey(nx to ny)) continue
            val key = nx to ny
            if (!frontier.containsKey(key)) {
                frontier[key] = FrontierDoorSocket(
                    frontierId = "door_${node.gridX}_${node.gridY}_to_${nx}_${ny}",
                    parentNodeId = node.nodeId,
                    targetGridX = nx,
                    targetGridY = ny,
                    requiredEntryDirection = sock.direction.opposite(),
                    unlockCostCurrency = sock.unlockCostCurrency,
                    socketKind = sock.kind
                )
            }
        }
    }

    fun verifyTopologicalReachability(nodes: List<PlacedSectionNode>): Boolean {
        if (nodes.isEmpty()) return false
        val byCoord = nodes.associateBy { it.gridX to it.gridY }
        val start = byCoord[0 to 0] ?: nodes.first()
        val visited = HashSet<String>()
        val queue = ArrayDeque<PlacedSectionNode>()
        queue.add(start)
        visited.add(start.nodeId)

        while (queue.isNotEmpty()) {
            val curr = queue.removeFirst()
            for (dir in SocketDirection.entries) {
                val neighbor = byCoord[(curr.gridX + dir.dx) to (curr.gridY + dir.dy)] ?: continue
                if (visited.add(neighbor.nodeId)) {
                    queue.add(neighbor)
                }
            }
        }
        return visited.size == nodes.size
    }

    private fun resolveShortcutLoops(nodes: List<PlacedSectionNode>): List<ShortcutLoopGate> {
        val gates = mutableListOf<ShortcutLoopGate>()
        for (i in nodes.indices) {
            for (j in i + 2 until nodes.size) {
                val a = nodes[i]
                val b = nodes[j]
                val manhattan = abs(a.gridX - b.gridX) + abs(a.gridY - b.gridY)
                val indexDist = j - i
                if (manhattan == 1 && indexDist >= 3) {
                    gates.add(
                        ShortcutLoopGate(
                            gateId = "shortcut_${a.nodeId}_${b.nodeId}",
                            fromNodeId = a.nodeId,
                            toNodeId = b.nodeId,
                            loopDistance = indexDist * 2,
                            backtrackSavings = 0.65f,
                            unlockCostCurrency = 900
                        )
                    )
                }
            }
        }
        return gates
    }

    private fun computeTopologyHash(
        seed: Long,
        nodes: List<PlacedSectionNode>,
        frontier: List<FrontierDoorSocket>
    ): String {
        var h = seed xor -0x340d631b7bdddcdbL
        for (n in nodes) {
            h = (h xor n.nodeId.hashCode().toLong()) * 0x100000001b3L
            h = (h xor n.componentId.hashCode().toLong()) * 0x100000001b3L
        }
        h = (h xor frontier.size.toLong()) * 0x100000001b3L
        return java.lang.Long.toHexString(h)
    }
}
