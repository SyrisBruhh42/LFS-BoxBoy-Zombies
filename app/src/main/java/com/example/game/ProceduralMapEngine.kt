package com.example.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.example.data.PerkType
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

enum class StationKind {
    DEBRIS_DOOR,
    WALL_WEAPON,
    MYSTERY_BOX,
    PACK_A_PUNCH,
    PERK_MACHINE,
    BARRICADE_WINDOW,
    SIEGE_FABRICATOR,
    UPGRADE_ANVIL
}

enum class DistrictBiome(
    val title: String,
    val subtitle: String,
    val roadColorHex: Long,
    val accentColorHex: Long,
    val secondaryColorHex: Long
) {
    DOWNTOWN_NEON_PLAZA(
        "Oakridge Mill Town",
        "Historic Brick Shophouses & Mill Creek",
        0xFF141E24,
        0xFF00E5FF,
        0xFFE5B842
    ),
    OVERGROWTH_CRATER_PARK(
        "Silverlake Resort & Woods",
        "Alpine Pine Groves & Mirror Lake Cabins",
        0xFF122219,
        0xFF52FF88,
        0xFF2E6B45
    ),
    CRYO_HARBOR_PIER(
        "Mistharbor Fishing Village",
        "Coastal Boardwalks, Boathouses & Lagoon",
        0xFF0E1D28,
        0xFF40C4FF,
        0xFF234B61
    ),
    COLLAPSED_MALL_BAZAAR(
        "Sunvale Old Market & Gardens",
        "Cobblestone Piazzas, Villas & Botanical Pond",
        0xFF211B24,
        0xFFFFD166,
        0xFF5C4338
    ),
    INDUSTRIAL_RAILYARD(
        "Redwood Timber & Rail Town",
        "Sawmill Depots, Freight Tracks & Quarry Lake",
        0xFF1E1714,
        0xFFFF8A3D,
        0xFF54392D
    ),
    OBSIDIAN_CITADEL(
        "Ashenhollow Cathedral Citadel",
        "Gothic Sanctums, Void Lake & Bioluminescent Woods",
        0xFF161024,
        0xFFB388FF,
        0xFF432463
    )
}

/**
 * Pass 1: Organic Biome Terrain Surface Patches & Cross-Border Blending.
 */
enum class TerrainSurfaceKind(
    val fillColorHex: Long,
    val rimColorHex: Long,
    val detailColorHex: Long
) {
    LUSH_MEADOW_GRASS(0xFF1C3524, 0xFF284D34, 0xFF3E7A50),
    PINE_NEEDLE_FOREST_FLOOR(0xFF15291C, 0xFF213F2B, 0xFF2E593D),
    COBBLESTONE_TOWN_PLAZA(0xFF28252B, 0xFF3D3742, 0xFF544C5C),
    SANDY_COASTAL_DUNES(0xFF2E3830, 0xFF4A5442, 0xFF7D7459),
    GRAVEL_RAILYARD_BALLAST(0xFF29201B, 0xFF3E3028, 0xFF594438),
    ASHEN_OBSIDIAN_CRUST(0xFF211730, 0xFF36244F, 0xFF593A80),
    WOODEN_HARBOR_BOARDWALK(0xFF2B211B, 0xFF4A372D, 0xFF694E40)
}

data class TerrainPatch(
    val x: Float,
    val y: Float,
    val radiusX: Float,
    val radiusY: Float,
    val rotationDeg: Float,
    val kind: TerrainSurfaceKind,
    val roomIndex: Int,
    val isCrossBorderBlend: Boolean = false
)

/**
 * Pass 2: Winding County River / Creek Segments & Connected Town Lakes.
 */
data class RiverSegment(
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val width: Float,
    val shallowColorHex: Long = 0xFF1CA3EC,
    val deepColorHex: Long = 0xFF0B4F7C,
    val bankColorHex: Long = 0xFF8D7B68,
    val roomIndex: Int,
    val hasStoneBridgeAtMidpoint: Boolean = false
)

enum class LakeStyle(
    val shallowColorHex: Long,
    val deepColorHex: Long,
    val shoreColorHex: Long,
    val hasWoodenBridge: Boolean = true
) {
    CRYSTAL_RESORT_LAKE(0xFF1CA3EC, 0xFF0B4F7C, 0xFFC2B280, true),
    HARBOR_TIDAL_LAGOON(0xFF26C6DA, 0xFF005662, 0xFF90A4AE, true),
    BOTANICAL_GARDEN_POND(0xFF26A69A, 0xFF004D40, 0xFFA1887F, true),
    EMERALD_QUARRY_LAKE(0xFF00E676, 0xFF064E3B, 0xFF6D4C41, true),
    LUMINOUS_CITADEL_LAKE(0xFF7C4DFF, 0xFF260E59, 0xFF4A3B5C, true)
}

data class MapLake(
    val id: String,
    val name: String,
    val x: Float,
    val y: Float,
    val radiusX: Float,
    val radiusY: Float,
    val style: LakeStyle,
    val roomIndex: Int,
    val bridgeHorizontal: Boolean = true
)

/**
 * Pass 3: Hierarchical 3-Tier Road Network (Highways, Town Streets, Dirt Trails) & Roadside Story Props.
 */
enum class RoadTier(
    val defaultWidth: Float,
    val surfaceColorHex: Long,
    val curbColorHex: Long,
    val hasCenterline: Boolean
) {
    COUNTY_HIGHWAY(146f, 0xFF151E24, 0xFF455A64, true),
    TOWN_AVENUE_STREET(94f, 0xFF1E262B, 0xFF546E7A, true),
    SCENIC_DIRT_TRAIL(56f, 0xFF3B2D23, 0xFF594436, false)
}

data class WorldRoadSegment(
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val tier: RoadTier,
    val width: Float = tier.defaultWidth,
    val roomIndex: Int,
    val isBridgeDeck: Boolean = false,
    val roadName: String = ""
)

enum class RoadSidePropKind {
    HIGHWAY_DIRECTION_SIGNPOST,
    WOODEN_RANCH_FENCE,
    TOWN_BUS_SHELTER,
    ABANDONED_SUPPLY_TRUCK,
    VINTAGE_STREETLAMP
}

data class RoadSideProp(
    val kind: RoadSidePropKind,
    val x: Float,
    val y: Float,
    val rotationDeg: Float = 0f,
    val label: String = "",
    val roomIndex: Int,
    val accentColorHex: Long = 0xFFE5B842
)

/**
 * Pass 4: Parcel-Aligned Building Lots, Courtyards, Fences & Distinct 3D Town Architecture.
 */
enum class LotSurfaceKind(
    val fillColorHex: Long,
    val borderColorHex: Long
) {
    COBBLESTONE_PATIO(0xFF262D32, 0xFF455A64),
    FENCED_GRASS_YARD(0xFF1B3322, 0xFF5D4037),
    TIMBER_DECK_PORCH(0xFF3E2723, 0xFF6D4C41),
    SUNVALE_TERRACOTTA_COURTYARD(0xFF3B2822, 0xFF8D5B4C),
    INDUSTRIAL_CONCRETE_APRON(0xFF262423, 0xFF544C47),
    GOTHIC_FLAGSTONE_CLOISTER(0xFF221B30, 0xFF5E4382)
}

enum class TownBuildingStyle {
    BRICK_TOWN_SHOPHOUSE,
    LAKESIDE_TIMBER_CABIN,
    HARBOR_BOATHOUSE,
    SUNVALE_MARKET_VILLA,
    REDWOOD_SAWMILL_DEPOT,
    ASHEN_GOTHIC_SANCTUM
}

data class TownBuilding(
    val bounds: Rect,
    val lotBounds: Rect,
    val lotSurface: LotSurfaceKind,
    val roomIndex: Int,
    val style: TownBuildingStyle,
    val buildingName: String,
    val heightZ: Float,
    val facadeColorHex: Long,
    val roofColorHex: Long,
    val trimColorHex: Long,
    val hasAwning: Boolean,
    val hasChimney: Boolean,
    val hasPerimeterFence: Boolean = true,
    val hasLWingExtension: Boolean = false
)

/**
 * Pass 5: Poisson-Clustered Forest Groves, Hedgerows & Shoreline Vegetation.
 */
enum class FoliageKind {
    OAK_CANOPY_TREE,
    PINE_EVERGREEN_TREE,
    WEEPING_WILLOW_TREE,
    AUTUMN_MAPLE_TREE,
    DEADWOOD_BIO_TREE,
    LUSH_SHRUB_BUSH,
    FLOWERING_HEDGEROW,
    SHORELINE_REEDS_FERN
}

data class WorldFoliage(
    val x: Float,
    val y: Float,
    val radius: Float,
    val heightZ: Float,
    val kind: FoliageKind,
    val roomIndex: Int,
    val primaryColorHex: Long,
    val highlightColorHex: Long,
    val seedOffset: Float
)

enum class UrbanLandmarkKind {
    ROTARY_PLAZA_FOUNTAIN,
    EMBER_BLAST_CRATER,
    CRYO_COOLANT_VAT,
    BAZAAR_SKYLIGHT_DOME,
    RAILWAY_TURNTABLE,
    OBSIDIAN_VOID_ALTAR,
    ABANDONED_TRANSIT_CAR,
    OVERGROWN_MEDIAN_GROVE
}

data class UrbanLandmark(
    val kind: UrbanLandmarkKind,
    val x: Float,
    val y: Float,
    val radiusX: Float,
    val radiusY: Float,
    val rotationDeg: Float = 0f,
    val roomIndex: Int
)

data class JaggedOutcrop(
    val bounds: Rect,
    val edgeSide: Int, // 0 = North, 1 = South, 2 = West, 3 = East
    val roomIndex: Int,
    val heightZ: Float,
    val primaryColorHex: Long,
    val roofColorHex: Long,
    val notchOffsetRatio: Float
)

data class MapRoom(
    val index: Int,
    val name: String,
    val bounds: Rect,
    val unlockCost: Int,
    val isUnlocked: Boolean,
    val floorColorHex: Long,
    val biome: DistrictBiome = DistrictBiome.DOWNTOWN_NEON_PLAZA
)

data class InteractiveStation(
    val id: String,
    val kind: StationKind,
    val label: String,
    val x: Float,
    val y: Float,
    val radius: Float = 48f,
    val cost: Int,
    val roomIndex: Int,
    val targetRoomIndex: Int = -1,
    val weaponId: String = "",
    val perkType: PerkType? = null,
    val barricadePlanks: Int = 5,
    val maxPlanks: Int = 5
)

data class ExplosiveBarrel(
    val id: Int,
    val x: Float,
    val y: Float,
    val roomIndex: Int,
    val hp: Int = 40,
    val isDetonated: Boolean = false
)

data class ProceduralMap(
    val seed: Long,
    val worldWidth: Float = 4800f,
    val worldHeight: Float = 2760f,
    val rooms: List<MapRoom>,
    val terrainPatches: List<TerrainPatch> = emptyList(),
    val riverSegments: List<RiverSegment> = emptyList(),
    val roads: List<WorldRoadSegment> = emptyList(),
    val roadSideProps: List<RoadSideProp> = emptyList(),
    val wallObstacles: List<Rect>,
    val townBuildings: List<TownBuilding> = emptyList(),
    val lakes: List<MapLake> = emptyList(),
    val foliage: List<WorldFoliage> = emptyList(),
    val jaggedPerimeterOutcrops: List<JaggedOutcrop> = emptyList(),
    val landmarks: List<UrbanLandmark> = emptyList(),
    val stations: List<InteractiveStation>,
    val barrels: List<ExplosiveBarrel>
)

/**
 * 5-Pass Hierarchical Procedural World Map Engine inspired by Project Zomboid & ZERO Sievert:
 * - Pass 1: Regional Biome Zoning & Cross-Border Organic Terrain Blending (`TerrainPatch`)
 * - Pass 2: Connected Watershed — Winding Rivers, Creeks, Lakes & Bridges (`RiverSegment`, `MapLake`)
 * - Pass 3: Hierarchical 3-Tier Road Network (Highways, Town Streets, Dirt Trails) & Roadside Props (`WorldRoadSegment`)
 * - Pass 4: Parcel-Aligned Building Lots, Fenced Yards, L-Wing Compounds & 3D Architecture (`TownBuilding`)
 * - Pass 5: Poisson-Clustered Woodland Groves, Avenue Trees, Hedgerows & Shoreline Reeds (`WorldFoliage`)
 */
object ProceduralMapEngine {

    const val DEFAULT_SPAWN_X = 840f
    const val DEFAULT_SPAWN_Y = 1860f
    val DEFAULT_UNLOCKED_ROOMS: Set<Int> = setOf(0, 1, 2, 3, 4)

    fun generateMap(seed: Long, unlockedRoomIndices: Set<Int> = DEFAULT_UNLOCKED_ROOMS): ProceduralMap {
        val rng = Random(seed)

        // 6 expansive, character-rich Towns laid out across a 4680x2620 playable county (4800x2760 total world)
        val rooms = listOf(
            MapRoom(
                index = 0,
                name = "TOWN 01: OAKRIDGE MILL TOWN",
                bounds = Rect(60f, 1140f, 1620f, 2680f),
                unlockCost = 0,
                isUnlocked = true,
                floorColorHex = 0xFF192622,
                biome = DistrictBiome.DOWNTOWN_NEON_PLAZA
            ),
            MapRoom(
                index = 1,
                name = "TOWN 02: SILVERLAKE RESORT & WOODS",
                bounds = Rect(1620f, 1140f, 3180f, 2680f),
                unlockCost = 750,
                isUnlocked = 1 in unlockedRoomIndices,
                floorColorHex = 0xFF182A1E,
                biome = DistrictBiome.OVERGROWTH_CRATER_PARK
            ),
            MapRoom(
                index = 2,
                name = "TOWN 03: MISTHARBOR FISHING VILLAGE",
                bounds = Rect(60f, 60f, 1620f, 1140f),
                unlockCost = 1000,
                isUnlocked = 2 in unlockedRoomIndices,
                floorColorHex = 0xFF152530,
                biome = DistrictBiome.CRYO_HARBOR_PIER
            ),
            MapRoom(
                index = 3,
                name = "TOWN 04: SUNVALE OLD MARKET & GARDENS",
                bounds = Rect(1620f, 60f, 3180f, 1140f),
                unlockCost = 1250,
                isUnlocked = 3 in unlockedRoomIndices,
                floorColorHex = 0xFF231F26,
                biome = DistrictBiome.COLLAPSED_MALL_BAZAAR
            ),
            MapRoom(
                index = 4,
                name = "TOWN 05: REDWOOD TIMBER & RAIL TOWN",
                bounds = Rect(3180f, 1140f, 4740f, 2680f),
                unlockCost = 1500,
                isUnlocked = 4 in unlockedRoomIndices,
                floorColorHex = 0xFF241D18,
                biome = DistrictBiome.INDUSTRIAL_RAILYARD
            ),
            MapRoom(
                index = 5,
                name = "TOWN 06: ASHENHOLLOW CATHEDRAL CITADEL",
                bounds = Rect(3180f, 60f, 4740f, 1140f),
                unlockCost = 2250,
                isUnlocked = 5 in unlockedRoomIndices,
                floorColorHex = 0xFF1E162B,
                biome = DistrictBiome.OBSIDIAN_CITADEL
            )
        )

        // PASS 1: Generate Regional Biome Terrain Patches & Cross-Border Organic Blending
        val terrainPatches = generateTerrainPatches(rooms, seed)

        // PASS 2: Generate Scenic Lakes, Lagoons, Quarry Ponds & Connected Winding Rivers
        val lakes = generateTownLakes(rooms)
        val riverSegments = generateCountyRivers(lakes)

        // PASS 3: Generate Hierarchical 3-Tier Road Network (Highways, Town Streets, Trails) & Roadside Props
        val roads = generateHierarchicalRoadNetwork(rooms, lakes)
        val roadSideProps = generateRoadSideProps(rooms, seed)

        // PASS 4: Generate Parcel-Aligned Town Building Lots (avoiding lakes, rivers, roads & test corridors)
        val pillars = mutableListOf<Rect>()
        rooms.forEach { room ->
            val shape = IrregularRoomShape.entries[(room.index + (seed and 0xFFL).toInt()) % IrregularRoomShape.entries.size]
            val rawFootprints = MazeDirector.generateCellularAutomataPillars(
                roomBounds = room.bounds,
                seed = seed xor (room.index * 9973L),
                shape = shape
            ).filter { rect ->
                val clearOfLakes = lakes.none { lake ->
                    lake.roomIndex == room.index &&
                        hypot(rect.center.x - lake.x, rect.center.y - lake.y) < (lake.radiusX + 90f)
                }
                val clearOfRivers = riverSegments.none { seg ->
                    pointToSegmentDist(rect.center.x, rect.center.y, seg.startX, seg.startY, seg.endX, seg.endY) < (seg.width + 58f)
                }
                val clearOfRoads = roads.none { road ->
                    road.roomIndex == room.index &&
                        pointToSegmentDist(rect.center.x, rect.center.y, road.startX, road.startY, road.endX, road.endY) < (road.width * 0.5f + 56f)
                }
                clearOfLakes && clearOfRivers && clearOfRoads
            }
            pillars.addAll(rawFootprints)
        }
        val townBuildings = rebuildTownBuildingsForPillars(rooms, pillars, seed)

        // PASS 5: Generate Poisson-Clustered Woodland Groves, Avenue Trees, Hedgerows & Shoreline Reeds
        val foliage = generateWorldFoliage(rooms, pillars, lakes, riverSegments, roads, seed)

        // Generate Jagged Non-Rectangular Perimeter Borders (cliff crags, ruined town walls, timber bastions)
        val jaggedOutcrops = generateJaggedPerimeterOutcrops(rooms, seed)

        // Generate Town Plaza Landmarks & Props
        val landmarks = generateDistrictLandmarks(rooms, lakes, seed)

        val stations = mutableListOf<InteractiveStation>()

        // Debris Doors connecting towns when locked
        if (1 !in unlockedRoomIndices) {
            stations.add(
                InteractiveStation(
                    id = "door_0_1",
                    kind = StationKind.DEBRIS_DOOR,
                    label = "OPEN SILVERLAKE WOODS [750 PTS]",
                    x = 1620f,
                    y = 1910f,
                    cost = 750,
                    roomIndex = 0,
                    targetRoomIndex = 1
                )
            )
        }
        if (2 !in unlockedRoomIndices) {
            stations.add(
                InteractiveStation(
                    id = "door_0_2",
                    kind = StationKind.DEBRIS_DOOR,
                    label = "OPEN MISTHARBOR VILLAGE [1000 PTS]",
                    x = 840f,
                    y = 1140f,
                    cost = 1000,
                    roomIndex = 0,
                    targetRoomIndex = 2
                )
            )
        }
        if (3 !in unlockedRoomIndices) {
            stations.add(
                InteractiveStation(
                    id = "door_1_3",
                    kind = StationKind.DEBRIS_DOOR,
                    label = "OPEN SUNVALE MARKET [1250 PTS]",
                    x = 2400f,
                    y = 1140f,
                    cost = 1250,
                    roomIndex = 1,
                    targetRoomIndex = 3
                )
            )
        }
        if (4 !in unlockedRoomIndices) {
            stations.add(
                InteractiveStation(
                    id = "door_1_4",
                    kind = StationKind.DEBRIS_DOOR,
                    label = "OPEN REDWOOD RAIL TOWN [1500 PTS]",
                    x = 3180f,
                    y = 1910f,
                    cost = 1500,
                    roomIndex = 1,
                    targetRoomIndex = 4
                )
            )
        }
        if (5 !in unlockedRoomIndices) {
            stations.add(
                InteractiveStation(
                    id = "door_4_5",
                    kind = StationKind.DEBRIS_DOOR,
                    label = "OPEN ASHENHOLLOW CITADEL [2250 PTS]",
                    x = 3960f,
                    y = 1140f,
                    cost = 2250,
                    roomIndex = 4,
                    targetRoomIndex = 5
                )
            )
        }

        // Wall-Buy Weapons spread across the 6 Towns
        stations.add(
            InteractiveStation(
                id = "wall_uzi",
                kind = StationKind.WALL_WEAPON,
                label = "WALL-BUY: MICRO UZI [750 PTS]",
                x = 340f,
                y = 1380f,
                cost = 750,
                roomIndex = 0,
                weaponId = "uzi"
            )
        )
        stations.add(
            InteractiveStation(
                id = "wall_shotgun",
                kind = StationKind.WALL_WEAPON,
                label = "WALL-BUY: 12G TRENCH [1250 PTS]",
                x = 2400f,
                y = 2440f,
                cost = 1250,
                roomIndex = 1,
                weaponId = "shotgun"
            )
        )
        stations.add(
            InteractiveStation(
                id = "wall_barrels",
                kind = StationKind.WALL_WEAPON,
                label = "WALL-BUY: RED BARRELS [1500 PTS]",
                x = 360f,
                y = 280f,
                cost = 1500,
                roomIndex = 2,
                weaponId = "barrels"
            )
        )
        stations.add(
            InteractiveStation(
                id = "wall_ar",
                kind = StationKind.WALL_WEAPON,
                label = "WALL-BUY: COMMANDO GALIL [2000 PTS]",
                x = 4260f,
                y = 2440f,
                cost = 2000,
                roomIndex = 4,
                weaponId = "ar"
            )
        )

        // Mystery Box & Pack-a-Punch
        stations.add(
            InteractiveStation(
                id = "mystery_box",
                kind = StationKind.MYSTERY_BOX,
                label = "MYSTERY BOX ROLL [950 PTS]",
                x = 2400f,
                y = 320f,
                cost = 950,
                roomIndex = 3
            )
        )
        stations.add(
            InteractiveStation(
                id = "pack_a_punch",
                kind = StationKind.PACK_A_PUNCH,
                label = "PACK-A-PUNCH UPGRADE [2000 PTS]",
                x = 3960f,
                y = 320f,
                cost = 2000,
                roomIndex = 5
            )
        )

        // Phase 3 & Phase 5: Siege Fabricator Beacons & Upgrade Anvil Stations
        stations.add(
            InteractiveStation(
                id = "siege_fabricator_0",
                kind = StationKind.SIEGE_FABRICATOR,
                label = "START SIEGE FABRICATOR [500 PTS -> LOOT CACHE]",
                x = 1060f,
                y = 2380f,
                cost = 500,
                roomIndex = 0
            )
        )
        stations.add(
            InteractiveStation(
                id = "upgrade_anvil_0",
                kind = StationKind.UPGRADE_ANVIL,
                label = "OAKRIDGE UPGRADE ANVIL [TEMPER GEAR]",
                x = 1060f,
                y = 1420f,
                cost = 350,
                roomIndex = 0
            )
        )
        stations.add(
            InteractiveStation(
                id = "siege_fabricator_2",
                kind = StationKind.SIEGE_FABRICATOR,
                label = "MISTHARBOR SIEGE FABRICATOR [500 PTS -> EPIC CACHE]",
                x = 840f,
                y = 600f,
                cost = 500,
                roomIndex = 2
            )
        )
        stations.add(
            InteractiveStation(
                id = "upgrade_anvil_3",
                kind = StationKind.UPGRADE_ANVIL,
                label = "SUNVALE UPGRADE ANVIL [TEMPER GEAR]",
                x = 1980f,
                y = 600f,
                cost = 350,
                roomIndex = 3
            )
        )

        // Perk-a-Cola Machines
        stations.add(
            InteractiveStation(
                id = "perk_revive",
                kind = StationKind.PERK_MACHINE,
                label = "QUICK REVIVE [1000 PTS]",
                x = 320f,
                y = 2380f,
                cost = PerkType.QUICK_REVIVE.cost,
                roomIndex = 0,
                perkType = PerkType.QUICK_REVIVE
            )
        )
        stations.add(
            InteractiveStation(
                id = "perk_jugg",
                kind = StationKind.PERK_MACHINE,
                label = "JUGGERNOG [2500 PTS]",
                x = 1980f,
                y = 1420f,
                cost = PerkType.JUGGERNOG.cost,
                roomIndex = 1,
                perkType = PerkType.JUGGERNOG
            )
        )
        stations.add(
            InteractiveStation(
                id = "perk_speed",
                kind = StationKind.PERK_MACHINE,
                label = "SPEED COLA [1500 PTS]",
                x = 1220f,
                y = 280f,
                cost = PerkType.SPEED_COLA.cost,
                roomIndex = 2,
                perkType = PerkType.SPEED_COLA
            )
        )
        stations.add(
            InteractiveStation(
                id = "perk_double",
                kind = StationKind.PERK_MACHINE,
                label = "DOUBLE TAP II [2000 PTS]",
                x = 3540f,
                y = 1420f,
                cost = PerkType.DOUBLE_TAP.cost,
                roomIndex = 4,
                perkType = PerkType.DOUBLE_TAP
            )
        )
        stations.add(
            InteractiveStation(
                id = "perk_stamin",
                kind = StationKind.PERK_MACHINE,
                label = "STAMIN-UP [1500 PTS]",
                x = 2820f,
                y = 360f,
                cost = PerkType.STAMIN_UP.cost,
                roomIndex = 3,
                perkType = PerkType.STAMIN_UP
            )
        )

        // Barricade Spawn Breaches around the jagged outer perimeters of the 6 Towns
        val windowCoords = listOf(
            Triple(120f, 1910f, 0),
            Triple(840f, 2620f, 0),
            Triple(2400f, 2620f, 1),
            Triple(120f, 600f, 2),
            Triple(2400f, 120f, 3),
            Triple(4680f, 1910f, 4),
            Triple(3960f, 120f, 5)
        )
        windowCoords.forEachIndexed { idx, (wx, wy, rIdx) ->
            stations.add(
                InteractiveStation(
                    id = "window_$idx",
                    kind = StationKind.BARRICADE_WINDOW,
                    label = "REPAIR BARRICADE [+20 PTS]",
                    x = wx,
                    y = wy,
                    cost = 0,
                    roomIndex = rIdx,
                    barricadePlanks = 5,
                    maxPlanks = 5
                )
            )
        }

        // Explosive Boxhead Red Barrels (5 per town across the larger county)
        val barrels = mutableListOf<ExplosiveBarrel>()
        rooms.forEachIndexed { rIdx, room ->
            repeat(5) { b ->
                val bx = rng.nextInt((room.bounds.left + 180).toInt(), (room.bounds.right - 180).toInt()).toFloat()
                val by = rng.nextInt((room.bounds.top + 180).toInt(), (room.bounds.bottom - 180).toInt()).toFloat()
                barrels.add(
                    ExplosiveBarrel(
                        id = rIdx * 10 + b,
                        x = bx,
                        y = by,
                        roomIndex = rIdx
                    )
                )
            }
        }

        return ProceduralMap(
            seed = seed,
            rooms = rooms,
            terrainPatches = terrainPatches,
            riverSegments = riverSegments,
            roads = roads,
            roadSideProps = roadSideProps,
            wallObstacles = pillars,
            townBuildings = townBuildings,
            lakes = lakes,
            foliage = foliage,
            jaggedPerimeterOutcrops = jaggedOutcrops,
            landmarks = landmarks,
            stations = stations,
            barrels = barrels
        )
    }

    /**
     * PASS 1: Generates Organic Biome Terrain Patches inside each town and overlapping
     * across town boundaries so regional biomes blend naturally instead of snapping on straight lines.
     */
    private fun generateTerrainPatches(rooms: List<MapRoom>, seed: Long): List<TerrainPatch> {
        val patches = mutableListOf<TerrainPatch>()
        val rng = Random(seed xor 0x1F2E3D4CL)

        rooms.forEach { room ->
            val b = room.bounds
            val surfaceKind = when (room.biome) {
                DistrictBiome.DOWNTOWN_NEON_PLAZA -> TerrainSurfaceKind.LUSH_MEADOW_GRASS
                DistrictBiome.OVERGROWTH_CRATER_PARK -> TerrainSurfaceKind.PINE_NEEDLE_FOREST_FLOOR
                DistrictBiome.CRYO_HARBOR_PIER -> TerrainSurfaceKind.WOODEN_HARBOR_BOARDWALK
                DistrictBiome.COLLAPSED_MALL_BAZAAR -> TerrainSurfaceKind.COBBLESTONE_TOWN_PLAZA
                DistrictBiome.INDUSTRIAL_RAILYARD -> TerrainSurfaceKind.GRAVEL_RAILYARD_BALLAST
                DistrictBiome.OBSIDIAN_CITADEL -> TerrainSurfaceKind.ASHEN_OBSIDIAN_CRUST
            }
            val secondarySurface = when (room.biome) {
                DistrictBiome.DOWNTOWN_NEON_PLAZA -> TerrainSurfaceKind.COBBLESTONE_TOWN_PLAZA
                DistrictBiome.OVERGROWTH_CRATER_PARK -> TerrainSurfaceKind.LUSH_MEADOW_GRASS
                DistrictBiome.CRYO_HARBOR_PIER -> TerrainSurfaceKind.SANDY_COASTAL_DUNES
                DistrictBiome.COLLAPSED_MALL_BAZAAR -> TerrainSurfaceKind.LUSH_MEADOW_GRASS
                DistrictBiome.INDUSTRIAL_RAILYARD -> TerrainSurfaceKind.PINE_NEEDLE_FOREST_FLOOR
                DistrictBiome.OBSIDIAN_CITADEL -> TerrainSurfaceKind.COBBLESTONE_TOWN_PLAZA
            }

            // 6 large organic terrain zones per town
            val zoneOffsets = listOf(
                Pair(-480f, -420f),
                Pair(480f, -420f),
                Pair(-480f, 420f),
                Pair(480f, 420f),
                Pair(-240f, 180f),
                Pair(260f, -210f)
            )
            zoneOffsets.forEachIndexed { zIdx, (ox, oy) ->
                val px = (b.center.x + ox + rng.nextInt(-60, 60)).coerceIn(b.left + 140f, b.right - 140f)
                val py = (b.center.y + oy + rng.nextInt(-50, 50)).coerceIn(b.top + 120f, b.bottom - 120f)
                patches.add(
                    TerrainPatch(
                        x = px,
                        y = py,
                        radiusX = rng.nextInt(210, 340).toFloat(),
                        radiusY = rng.nextInt(150, 240).toFloat(),
                        rotationDeg = rng.nextInt(-22, 22).toFloat(),
                        kind = if (zIdx % 3 == 0) secondarySurface else surfaceKind,
                        roomIndex = room.index
                    )
                )
            }
        }

        // Cross-border organic transition patches along vertical boundaries (x = 1620, x = 3180) and horizontal boundary (y = 1140)
        val verticalBorders = listOf(Pair(1620f, 0), Pair(3180f, 1))
        for ((bx, roomIdx) in verticalBorders) {
            var cy = 220f
            while (cy < 2540f) {
                patches.add(
                    TerrainPatch(
                        x = bx + rng.nextInt(-45, 45),
                        y = cy,
                        radiusX = rng.nextInt(150, 230).toFloat(),
                        radiusY = rng.nextInt(110, 175).toFloat(),
                        rotationDeg = rng.nextInt(-28, 28).toFloat(),
                        kind = if ((cy.toInt() / 300) % 2 == 0) {
                            TerrainSurfaceKind.LUSH_MEADOW_GRASS
                        } else {
                            TerrainSurfaceKind.PINE_NEEDLE_FOREST_FLOOR
                        },
                        roomIndex = roomIdx,
                        isCrossBorderBlend = true
                    )
                )
                cy += 340f
            }
        }

        var cx = 260f
        while (cx < 4540f) {
            val ownerRoom = when {
                cx < 1620f -> 0
                cx < 3180f -> 1
                else -> 4
            }
            patches.add(
                TerrainPatch(
                    x = cx,
                    y = 1140f + rng.nextInt(-40, 40),
                    radiusX = rng.nextInt(170, 250).toFloat(),
                    radiusY = rng.nextInt(110, 165).toFloat(),
                    rotationDeg = rng.nextInt(-20, 20).toFloat(),
                    kind = when (ownerRoom) {
                        0 -> TerrainSurfaceKind.SANDY_COASTAL_DUNES
                        1 -> TerrainSurfaceKind.COBBLESTONE_TOWN_PLAZA
                        else -> TerrainSurfaceKind.GRAVEL_RAILYARD_BALLAST
                    },
                    roomIndex = ownerRoom,
                    isCrossBorderBlend = true
                )
            )
            cx += 380f
        }

        return patches
    }

    /**
     * PASS 2A: Generates scenic Lakes, Coastal Lagoons, Koi Ponds, and Quarry Waters across the 6 Towns.
     */
    private fun generateTownLakes(rooms: List<MapRoom>): List<MapLake> {
        val lakes = mutableListOf<MapLake>()
        rooms.forEach { room ->
            val b = room.bounds
            val cx = b.center.x
            val cy = b.center.y
            when (room.index) {
                0 -> {
                    lakes.add(
                        MapLake(
                            id = "lake_oakridge_mill",
                            name = "Oakridge Mill Pond",
                            x = b.left + 370f,
                            y = b.bottom - 340f,
                            radiusX = 175f,
                            radiusY = 124f,
                            style = LakeStyle.CRYSTAL_RESORT_LAKE,
                            roomIndex = 0,
                            bridgeHorizontal = true
                        )
                    )
                }
                1 -> {
                    lakes.add(
                        MapLake(
                            id = "lake_silverlake_main",
                            name = "Silverlake Mirror Basin",
                            x = cx + 280f,
                            y = cy - 260f,
                            radiusX = 248f,
                            radiusY = 170f,
                            style = LakeStyle.CRYSTAL_RESORT_LAKE,
                            roomIndex = 1,
                            bridgeHorizontal = false
                        )
                    )
                    lakes.add(
                        MapLake(
                            id = "lake_silverlake_south",
                            name = "Pinewood Creek Pond",
                            x = cx - 340f,
                            y = cy + 310f,
                            radiusX = 162f,
                            radiusY = 114f,
                            style = LakeStyle.EMERALD_QUARRY_LAKE,
                            roomIndex = 1,
                            bridgeHorizontal = true
                        )
                    )
                }
                2 -> {
                    lakes.add(
                        MapLake(
                            id = "lake_mistharbor_lagoon",
                            name = "Mistharbor Tidal Lagoon",
                            x = cx - 320f,
                            y = cy + 210f,
                            radiusX = 232f,
                            radiusY = 144f,
                            style = LakeStyle.HARBOR_TIDAL_LAGOON,
                            roomIndex = 2,
                            bridgeHorizontal = true
                        )
                    )
                }
                3 -> {
                    lakes.add(
                        MapLake(
                            id = "lake_sunvale_botanical",
                            name = "Sunvale Botanical Pond",
                            x = cx + 310f,
                            y = cy + 220f,
                            radiusX = 195f,
                            radiusY = 135f,
                            style = LakeStyle.BOTANICAL_GARDEN_POND,
                            roomIndex = 3,
                            bridgeHorizontal = false
                        )
                    )
                }
                4 -> {
                    lakes.add(
                        MapLake(
                            id = "lake_redwood_quarry",
                            name = "Redwood Timber Quarry Lake",
                            x = cx - 330f,
                            y = cy - 280f,
                            radiusX = 220f,
                            radiusY = 154f,
                            style = LakeStyle.EMERALD_QUARRY_LAKE,
                            roomIndex = 4,
                            bridgeHorizontal = true
                        )
                    )
                }
                5 -> {
                    lakes.add(
                        MapLake(
                            id = "lake_ashenhollow_crater",
                            name = "Ashenhollow Void Lake",
                            x = cx + 320f,
                            y = cy + 210f,
                            radiusX = 210f,
                            radiusY = 140f,
                            style = LakeStyle.LUMINOUS_CITADEL_LAKE,
                            roomIndex = 5,
                            bridgeHorizontal = false
                        )
                    )
                }
            }
        }
        return lakes
    }

    /**
     * PASS 2B: Connects the town lakes with meandering river/stream channels & stone highway bridges.
     * Note: These streams are visual shallow creeks crossed by stone bridges at road intersections so
     * unit test lanes and highway travel remain 100% unobstructed.
     */
    private fun generateCountyRivers(lakes: List<MapLake>): List<RiverSegment> {
        val byId = lakes.associateBy { it.id }
        val list = mutableListOf<RiverSegment>()

        val sunvale = byId["lake_sunvale_botanical"]
        val silverMain = byId["lake_silverlake_main"]
        val silverSouth = byId["lake_silverlake_south"]
        val oakridge = byId["lake_oakridge_mill"]
        val mistharbor = byId["lake_mistharbor_lagoon"]
        val ashenhollow = byId["lake_ashenhollow_crater"]
        val redwood = byId["lake_redwood_quarry"]

        if (sunvale != null && silverMain != null) {
            list.add(
                RiverSegment(
                    startX = sunvale.x,
                    startY = sunvale.y + sunvale.radiusY * 0.7f,
                    endX = silverMain.x,
                    endY = silverMain.y - silverMain.radiusY * 0.7f,
                    width = 54f,
                    shallowColorHex = 0xFF26A69A,
                    deepColorHex = 0xFF0B4F7C,
                    roomIndex = 1,
                    hasStoneBridgeAtMidpoint = true
                )
            )
        }
        if (silverMain != null && silverSouth != null) {
            list.add(
                RiverSegment(
                    startX = silverMain.x - 90f,
                    startY = silverMain.y + 70f,
                    endX = silverSouth.x + 80f,
                    endY = silverSouth.y - 50f,
                    width = 48f,
                    shallowColorHex = 0xFF1CA3EC,
                    deepColorHex = 0xFF064E3B,
                    roomIndex = 1,
                    hasStoneBridgeAtMidpoint = true
                )
            )
        }
        if (silverSouth != null && oakridge != null) {
            list.add(
                RiverSegment(
                    startX = silverSouth.x - silverSouth.radiusX * 0.8f,
                    startY = silverSouth.y + 20f,
                    endX = oakridge.x + oakridge.radiusX * 0.8f,
                    endY = oakridge.y + 10f,
                    width = 50f,
                    shallowColorHex = 0xFF1CA3EC,
                    deepColorHex = 0xFF0B4F7C,
                    roomIndex = 0,
                    hasStoneBridgeAtMidpoint = true
                )
            )
        }
        if (oakridge != null && mistharbor != null) {
            list.add(
                RiverSegment(
                    startX = oakridge.x - 40f,
                    startY = oakridge.y - oakridge.radiusY * 0.8f,
                    endX = mistharbor.x - 30f,
                    endY = mistharbor.y + mistharbor.radiusY * 0.8f,
                    width = 52f,
                    shallowColorHex = 0xFF26C6DA,
                    deepColorHex = 0xFF005662,
                    roomIndex = 0,
                    hasStoneBridgeAtMidpoint = true
                )
            )
        }
        if (ashenhollow != null && redwood != null) {
            list.add(
                RiverSegment(
                    startX = ashenhollow.x - 60f,
                    startY = ashenhollow.y + ashenhollow.radiusY * 0.75f,
                    endX = redwood.x + 60f,
                    endY = redwood.y - redwood.radiusY * 0.75f,
                    width = 50f,
                    shallowColorHex = 0xFF7C4DFF,
                    deepColorHex = 0xFF064E3B,
                    roomIndex = 4,
                    hasStoneBridgeAtMidpoint = true
                )
            )
        }
        return list
    }

    /**
     * PASS 3A: Generates the 3-Tier Hierarchical Road Network:
     * - Tier 1: County Highways connecting all 6 Town Plazas
     * - Tier 2: Secondary Town Avenue & Neighborhood Streets forming urban city blocks
     * - Tier 3: Scenic Dirt & Gravel Trails connecting streets to lakes, cabins, and landmarks
     */
    private fun generateHierarchicalRoadNetwork(
        rooms: List<MapRoom>,
        lakes: List<MapLake>
    ): List<WorldRoadSegment> {
        val roads = mutableListOf<WorldRoadSegment>()
        rooms.forEach { room ->
            val b = room.bounds
            val cx = b.center.x
            val cy = b.center.y

            // Tier 1: Primary East-West & North-South County Highways through the Town Center
            roads.add(
                WorldRoadSegment(
                    startX = b.left,
                    startY = cy,
                    endX = b.right,
                    endY = cy,
                    tier = RoadTier.COUNTY_HIGHWAY,
                    roomIndex = room.index,
                    roadName = "${room.biome.title} Hwy"
                )
            )
            roads.add(
                WorldRoadSegment(
                    startX = cx,
                    startY = b.top,
                    endX = cx,
                    endY = b.bottom,
                    tier = RoadTier.COUNTY_HIGHWAY,
                    roomIndex = room.index,
                    roadName = "Route 0${room.index + 1}"
                )
            )

            // Tier 2: Secondary Town Block Perimeter Streets (creates realistic urban lots & blocks)
            val northStreetY = (cy - 390f).coerceAtLeast(b.top + 130f)
            val southStreetY = (cy + 390f).coerceAtMost(b.bottom - 130f)
            val westStreetX = (cx - 510f).coerceAtLeast(b.left + 150f)
            val eastStreetX = (cx + 510f).coerceAtMost(b.right - 150f)

            // North & South neighborhood cross-streets
            roads.add(
                WorldRoadSegment(
                    startX = westStreetX,
                    startY = northStreetY,
                    endX = eastStreetX,
                    endY = northStreetY,
                    tier = RoadTier.TOWN_AVENUE_STREET,
                    roomIndex = room.index,
                    roadName = "North Market St"
                )
            )
            roads.add(
                WorldRoadSegment(
                    startX = westStreetX,
                    startY = southStreetY,
                    endX = eastStreetX,
                    endY = southStreetY,
                    tier = RoadTier.TOWN_AVENUE_STREET,
                    roomIndex = room.index,
                    roadName = "South Mill Ave"
                )
            )
            // West & East connector streets
            roads.add(
                WorldRoadSegment(
                    startX = westStreetX,
                    startY = northStreetY,
                    endX = westStreetX,
                    endY = southStreetY,
                    tier = RoadTier.TOWN_AVENUE_STREET,
                    roomIndex = room.index,
                    roadName = "West Quarter Ln"
                )
            )
            roads.add(
                WorldRoadSegment(
                    startX = eastStreetX,
                    startY = northStreetY,
                    endX = eastStreetX,
                    endY = southStreetY,
                    tier = RoadTier.TOWN_AVENUE_STREET,
                    roomIndex = room.index,
                    roadName = "East Guild Way"
                )
            )

            // Tier 3: Scenic Dirt & Cobble Trails leading from the main highway directly to each Town Lake's bridge
            lakes.filter { it.roomIndex == room.index }.forEach { lake ->
                roads.add(
                    WorldRoadSegment(
                        startX = cx,
                        startY = lake.y,
                        endX = lake.x,
                        endY = lake.y,
                        tier = RoadTier.SCENIC_DIRT_TRAIL,
                        roomIndex = room.index,
                        roadName = "${lake.name} Trail"
                    )
                )
            }
        }
        return roads
    }

    /**
     * PASS 3B: Generates Roadside Story & Infrastructure Props (Highway Direction Signposts,
     * Wooden Ranch Fences, Town Bus Shelters, Abandoned Supply Trucks, Vintage Streetlamps).
     */
    private fun generateRoadSideProps(rooms: List<MapRoom>, seed: Long): List<RoadSideProp> {
        val props = mutableListOf<RoadSideProp>()
        val rng = Random(seed xor 0x7A8B9C0DL)

        rooms.forEach { room ->
            val b = room.bounds
            val cx = b.center.x
            val cy = b.center.y
            val accent = room.biome.accentColorHex

            // 1. Town Highway Direction Signpost at the main crossroads
            val signLabel = when (room.index) {
                0 -> "OAKRIDGE MILL • SILVERLAKE E ->"
                1 -> "<- OAKRIDGE W • SILVERLAKE • REDWOOD E ->"
                2 -> "MISTHARBOR PIER • SUNVALE E ->"
                3 -> "<- MISTHARBOR W • SUNVALE MARKET"
                4 -> "<- SILVERLAKE W • REDWOOD RAIL DEPOT"
                else -> "ASHENHOLLOW CATHEDRAL CITADEL"
            }
            props.add(
                RoadSideProp(
                    kind = RoadSidePropKind.HIGHWAY_DIRECTION_SIGNPOST,
                    x = cx + 104f,
                    y = cy - 104f,
                    label = signLabel,
                    roomIndex = room.index,
                    accentColorHex = accent
                )
            )

            // 2. Town Bus Shelter along the East-West Highway sidewalk
            props.add(
                RoadSideProp(
                    kind = RoadSidePropKind.TOWN_BUS_SHELTER,
                    x = cx - 260f,
                    y = cy - 96f,
                    label = "${room.biome.title.take(12).uppercase()} STOP",
                    roomIndex = room.index,
                    accentColorHex = accent
                )
            )

            // 3. Abandoned Supply Truck along the highway shoulder
            props.add(
                RoadSideProp(
                    kind = RoadSidePropKind.ABANDONED_SUPPLY_TRUCK,
                    x = cx + 290f,
                    y = cy + 52f,
                    rotationDeg = rng.nextInt(-10, 12).toFloat(),
                    label = "SUPPLY CONVOY",
                    roomIndex = room.index,
                    accentColorHex = accent
                )
            )

            // 4. Wooden Ranch Fences lining rural highway approaches
            val fenceOffsets = listOf(-620f, -540f, 540f, 620f)
            fenceOffsets.forEach { fox ->
                val fx = (cx + fox).coerceIn(b.left + 90f, b.right - 90f)
                props.add(
                    RoadSideProp(
                        kind = RoadSidePropKind.WOODEN_RANCH_FENCE,
                        x = fx,
                        y = cy - 92f,
                        roomIndex = room.index,
                        accentColorHex = room.biome.secondaryColorHex
                    )
                )
                props.add(
                    RoadSideProp(
                        kind = RoadSidePropKind.WOODEN_RANCH_FENCE,
                        x = fx,
                        y = cy + 92f,
                        roomIndex = room.index,
                        accentColorHex = room.biome.secondaryColorHex
                    )
                )
            }
        }
        return props
    }

    /**
     * PASS 4: Rebuilds parcel-aligned 3D `TownBuilding` instances (with Lot Parcels, Courtyards,
     * Perimeter Fences, L-Wing extensions, Chimneys, and Storefront Awnings) for all building footprints.
     */
    fun rebuildTownBuildingsForPillars(
        rooms: List<MapRoom>,
        pillars: List<Rect>,
        seed: Long
    ): List<TownBuilding> {
        val result = mutableListOf<TownBuilding>()
        pillars.forEachIndexed { idx, footprint ->
            val ownerRoom = rooms.firstOrNull { room ->
                room.bounds.contains(Offset(footprint.center.x, footprint.center.y))
            } ?: rooms.first()
            result.add(createTownBuildingForRoom(ownerRoom, footprint, idx, seed))
        }
        return result
    }

    private fun createTownBuildingForRoom(
        room: MapRoom,
        footprint: Rect,
        buildingIdx: Int,
        seed: Long
    ): TownBuilding {
        val rng = Random(seed xor (room.index * 4099L + buildingIdx * 131L))
        // Lot Parcel setback around the building footprint (front yard / patio / courtyard)
        val lotBounds = Rect(
            left = footprint.left - 22f,
            top = footprint.top - 18f,
            right = footprint.right + 22f,
            bottom = footprint.bottom + 26f
        )
        val hasLWing = buildingIdx % 2 == 0
        return when (room.biome) {
            DistrictBiome.DOWNTOWN_NEON_PLAZA -> {
                val names = listOf("MILL GENERAL STORE", "OAKRIDGE APOTHECARY", "CLOCKWORK BAKERY", "RIVERSIDE TAVERN", "COUNTY HARDWARE", "UNION TELEGRAPH")
                TownBuilding(
                    bounds = footprint,
                    lotBounds = lotBounds,
                    lotSurface = LotSurfaceKind.COBBLESTONE_PATIO,
                    roomIndex = room.index,
                    style = TownBuildingStyle.BRICK_TOWN_SHOPHOUSE,
                    buildingName = names[buildingIdx % names.size],
                    heightZ = rng.nextInt(40, 58).toFloat(),
                    facadeColorHex = if (buildingIdx % 2 == 0) 0xFF4A2C2A else 0xFF37474F,
                    roofColorHex = if (buildingIdx % 2 == 0) 0xFF6D3B36 else 0xFF455A64,
                    trimColorHex = 0xFFE5B842,
                    hasAwning = true,
                    hasChimney = buildingIdx % 2 == 0,
                    hasPerimeterFence = true,
                    hasLWingExtension = hasLWing
                )
            }
            DistrictBiome.OVERGROWTH_CRATER_PARK -> {
                val names = listOf("SILVERLAKE LODGE", "PINEWOOD RANGER CABIN", "ANGLER BOATSHED", "WHISPERING PINES INN", "TIMBERLAND OUTFITTER", "CEDAR RIDGE CHALET")
                TownBuilding(
                    bounds = footprint,
                    lotBounds = lotBounds,
                    lotSurface = LotSurfaceKind.FENCED_GRASS_YARD,
                    roomIndex = room.index,
                    style = TownBuildingStyle.LAKESIDE_TIMBER_CABIN,
                    buildingName = names[buildingIdx % names.size],
                    heightZ = rng.nextInt(36, 50).toFloat(),
                    facadeColorHex = 0xFF4E342E,
                    roofColorHex = 0xFF2E4E38,
                    trimColorHex = 0xFF8D6E63,
                    hasAwning = buildingIdx % 2 == 0,
                    hasChimney = true,
                    hasPerimeterFence = true,
                    hasLWingExtension = hasLWing
                )
            }
            DistrictBiome.CRYO_HARBOR_PIER -> {
                val names = listOf("MISTHARBOR CANNERY", "OLD WHARF BOATHOUSE", "HARBORMASTER OFFICE", "NORTHERN TRAWLER CO.", "SALTWORKS DEPOT", "LIGHTHOUSE KEEP")
                TownBuilding(
                    bounds = footprint,
                    lotBounds = lotBounds,
                    lotSurface = LotSurfaceKind.TIMBER_DECK_PORCH,
                    roomIndex = room.index,
                    style = TownBuildingStyle.HARBOR_BOATHOUSE,
                    buildingName = names[buildingIdx % names.size],
                    heightZ = rng.nextInt(38, 54).toFloat(),
                    facadeColorHex = 0xFF223A4A,
                    roofColorHex = 0xFF33566E,
                    trimColorHex = 0xFF40C4FF,
                    hasAwning = true,
                    hasChimney = false,
                    hasPerimeterFence = true,
                    hasLWingExtension = hasLWing
                )
            }
            DistrictBiome.COLLAPSED_MALL_BAZAAR -> {
                val names = listOf("SUNVALE SPICE BAZAAR", "VILLA MERCATO", "BOTANICAL CONSERVATORY", "ARTISAN SILK GUILD", "OLD TOWN EMPORIUM", "GOLDEN LANTERN BISTRO")
                TownBuilding(
                    bounds = footprint,
                    lotBounds = lotBounds,
                    lotSurface = LotSurfaceKind.SUNVALE_TERRACOTTA_COURTYARD,
                    roomIndex = room.index,
                    style = TownBuildingStyle.SUNVALE_MARKET_VILLA,
                    buildingName = names[buildingIdx % names.size],
                    heightZ = rng.nextInt(42, 60).toFloat(),
                    facadeColorHex = 0xFF5D4037,
                    roofColorHex = 0xFF8D4E35,
                    trimColorHex = 0xFFFFD166,
                    hasAwning = true,
                    hasChimney = buildingIdx % 2 == 1,
                    hasPerimeterFence = true,
                    hasLWingExtension = hasLWing
                )
            }
            DistrictBiome.INDUSTRIAL_RAILYARD -> {
                val names = listOf("REDWOOD LUMBER MILL", "PACIFIC FREIGHT DEPOT", "LOCOMOTIVE FOUNDRY", "QUARRY BLAST WORKS", "IRONHORSE SWITCHYARD", "TIMBER CARPENTRY")
                TownBuilding(
                    bounds = footprint,
                    lotBounds = lotBounds,
                    lotSurface = LotSurfaceKind.INDUSTRIAL_CONCRETE_APRON,
                    roomIndex = room.index,
                    style = TownBuildingStyle.REDWOOD_SAWMILL_DEPOT,
                    buildingName = names[buildingIdx % names.size],
                    heightZ = rng.nextInt(44, 58).toFloat(),
                    facadeColorHex = 0xFF422A22,
                    roofColorHex = 0xFF5C3A2E,
                    trimColorHex = 0xFFFF8A3D,
                    hasAwning = false,
                    hasChimney = true,
                    hasPerimeterFence = true,
                    hasLWingExtension = hasLWing
                )
            }
            DistrictBiome.OBSIDIAN_CITADEL -> {
                val names = listOf("ASHEN CATHEDRAL NAVE", "VOID ALCHEMIST ABBEY", "OBSIDIAN RELIQUARY", "BELLTOWER SANCTUM", "ELDRITCH ARCHIVES", "CHORUS CHAPEL")
                TownBuilding(
                    bounds = footprint,
                    lotBounds = lotBounds,
                    lotSurface = LotSurfaceKind.GOTHIC_FLAGSTONE_CLOISTER,
                    roomIndex = room.index,
                    style = TownBuildingStyle.ASHEN_GOTHIC_SANCTUM,
                    buildingName = names[buildingIdx % names.size],
                    heightZ = rng.nextInt(48, 66).toFloat(),
                    facadeColorHex = 0xFF281E38,
                    roofColorHex = 0xFF3E2C59,
                    trimColorHex = 0xFFB388FF,
                    hasAwning = false,
                    hasChimney = true,
                    hasPerimeterFence = true,
                    hasLWingExtension = hasLWing
                )
            }
        }
    }

    /**
     * PASS 5: Generates ~260 Poisson-Clustered 3D Trees, Woodland Groves, Avenue Trees,
     * Flowering Hedgerows, and Shoreline Cattails/Ferns across the 6 Towns.
     */
    private fun generateWorldFoliage(
        rooms: List<MapRoom>,
        buildings: List<Rect>,
        lakes: List<MapLake>,
        rivers: List<RiverSegment>,
        roads: List<WorldRoadSegment>,
        seed: Long
    ): List<WorldFoliage> {
        val foliage = mutableListOf<WorldFoliage>()
        val rng = Random(seed xor 0x3C6EF372L)

        rooms.forEach { room ->
            val b = room.bounds
            val cx = b.center.x
            val cy = b.center.y

            // 1. Ring of shoreline willow/pine trees & cattail reeds around every lake in this town
            lakes.filter { it.roomIndex == room.index }.forEach { lake ->
                val ringCount = 14
                for (i in 0 until ringCount) {
                    val ang = (i.toFloat() / ringCount) * (Math.PI.toFloat() * 2f) + rng.nextFloat() * 0.22f
                    val isReed = i % 2 == 0
                    val distScale = if (isReed) 0.96f else 1.18f
                    val fx = (lake.x + cos(ang) * lake.radiusX * distScale)
                        .coerceIn(b.left + 85f, b.right - 85f)
                    val fy = (lake.y + sin(ang) * lake.radiusY * distScale)
                        .coerceIn(b.top + 85f, b.bottom - 85f)

                    if (isReed) {
                        foliage.add(
                            WorldFoliage(
                                x = fx,
                                y = fy,
                                radius = rng.nextInt(14, 22).toFloat(),
                                heightZ = 14f,
                                kind = FoliageKind.SHORELINE_REEDS_FERN,
                                roomIndex = room.index,
                                primaryColorHex = 0xFF2E7D32,
                                highlightColorHex = 0xFF66BB6A,
                                seedOffset = rng.nextFloat()
                            )
                        )
                    } else {
                        val treeKind = if (room.biome == DistrictBiome.CRYO_HARBOR_PIER || i % 3 == 0) {
                            FoliageKind.WEEPING_WILLOW_TREE
                        } else {
                            FoliageKind.PINE_EVERGREEN_TREE
                        }
                        foliage.add(
                            WorldFoliage(
                                x = fx,
                                y = fy,
                                radius = rng.nextInt(26, 36).toFloat(),
                                heightZ = rng.nextInt(36, 50).toFloat(),
                                kind = treeKind,
                                roomIndex = room.index,
                                primaryColorHex = if (treeKind == FoliageKind.WEEPING_WILLOW_TREE) 0xFF26735B else 0xFF1B4D32,
                                highlightColorHex = if (treeKind == FoliageKind.WEEPING_WILLOW_TREE) 0xFF4DB6AC else 0xFF43A047,
                                seedOffset = rng.nextFloat()
                            )
                        )
                    }
                }
            }

            // 2. Poisson-Clustered Woodland Groves & Boulevard Hedgerows (30 per town)
            val targetCount = 30
            var attempts = 0
            var placed = 0
            while (placed < targetCount && attempts < 120) {
                attempts++
                val fx = rng.nextInt((b.left + 90).toInt(), (b.right - 90).toInt()).toFloat()
                val fy = rng.nextInt((b.top + 90).toInt(), (b.bottom - 90).toInt()).toFloat()

                // Keep the main highway & street asphalt lanes clear (allow trees along sidewalks & groves!)
                val onRoadAsphalt = abs(fx - cx) < 66f || abs(fy - cy) < 66f ||
                    roads.any { rd ->
                        rd.roomIndex == room.index &&
                            pointToSegmentDist(fx, fy, rd.startX, rd.startY, rd.endX, rd.endY) < (rd.width * 0.5f + 10f)
                    }
                if (onRoadAsphalt) continue

                // Keep out of deep lake water & rivers
                val insideWater = lakes.any { lake ->
                    lake.roomIndex == room.index &&
                        hypot((fx - lake.x) / lake.radiusX, (fy - lake.y) / lake.radiusY) < 1.05f
                } || rivers.any { seg ->
                    pointToSegmentDist(fx, fy, seg.startX, seg.startY, seg.endX, seg.endY) < seg.width * 0.65f
                }
                if (insideWater) continue

                // Keep out of building footprints
                val insideBuilding = buildings.any { rect ->
                    fx in (rect.left - 24f)..(rect.right + 24f) &&
                        fy in (rect.top - 24f)..(rect.bottom + 24f)
                }
                if (insideBuilding) continue

                val isShrub = placed % 3 == 0
                if (isShrub) {
                    val shrubKind = if (room.biome == DistrictBiome.COLLAPSED_MALL_BAZAAR || room.biome == DistrictBiome.DOWNTOWN_NEON_PLAZA) {
                        FoliageKind.FLOWERING_HEDGEROW
                    } else {
                        FoliageKind.LUSH_SHRUB_BUSH
                    }
                    foliage.add(
                        WorldFoliage(
                            x = fx,
                            y = fy,
                            radius = rng.nextInt(16, 26).toFloat(),
                            heightZ = rng.nextInt(12, 18).toFloat(),
                            kind = shrubKind,
                            roomIndex = room.index,
                            primaryColorHex = 0xFF215A32,
                            highlightColorHex = if (shrubKind == FoliageKind.FLOWERING_HEDGEROW) 0xFFFF80AB else 0xFF52FF88,
                            seedOffset = rng.nextFloat()
                        )
                    )
                } else {
                    val (treeKind, baseCol, hiCol) = when (room.biome) {
                        DistrictBiome.DOWNTOWN_NEON_PLAZA -> if (placed % 4 == 0) {
                            Triple(FoliageKind.AUTUMN_MAPLE_TREE, 0xFF9E3D1C, 0xFFFF8A3D)
                        } else {
                            Triple(FoliageKind.OAK_CANOPY_TREE, 0xFF1E5631, 0xFF4CAF50)
                        }
                        DistrictBiome.OVERGROWTH_CRATER_PARK -> if (placed % 2 == 0) {
                            Triple(FoliageKind.PINE_EVERGREEN_TREE, 0xFF14452F, 0xFF388E3C)
                        } else {
                            Triple(FoliageKind.OAK_CANOPY_TREE, 0xFF1E5E36, 0xFF52FF88)
                        }
                        DistrictBiome.CRYO_HARBOR_PIER -> Triple(FoliageKind.WEEPING_WILLOW_TREE, 0xFF1A4F59, 0xFF4DD0E1)
                        DistrictBiome.COLLAPSED_MALL_BAZAAR -> Triple(FoliageKind.AUTUMN_MAPLE_TREE, 0xFFA84320, 0xFFFFB74D)
                        DistrictBiome.INDUSTRIAL_RAILYARD -> Triple(FoliageKind.PINE_EVERGREEN_TREE, 0xFF1B4332, 0xFF40916C)
                        DistrictBiome.OBSIDIAN_CITADEL -> Triple(FoliageKind.DEADWOOD_BIO_TREE, 0xFF311B47, 0xFFB388FF)
                    }
                    foliage.add(
                        WorldFoliage(
                            x = fx,
                            y = fy,
                            radius = rng.nextInt(26, 38).toFloat(),
                            heightZ = rng.nextInt(36, 54).toFloat(),
                            kind = treeKind,
                            roomIndex = room.index,
                            primaryColorHex = baseCol,
                            highlightColorHex = hiCol,
                            seedOffset = rng.nextFloat()
                        )
                    )
                }
                placed++
            }
        }
        return foliage
    }

    private fun generateJaggedPerimeterOutcrops(rooms: List<MapRoom>, seed: Long): List<JaggedOutcrop> {
        val outcrops = mutableListOf<JaggedOutcrop>()
        val rng = Random(seed xor 0x6A09E667L)

        rooms.forEach { room ->
            val b = room.bounds
            val idx = room.index
            val isNorthExterior = idx in setOf(2, 3, 5)
            val isSouthExterior = idx in setOf(0, 1, 4)
            val isWestExterior = idx in setOf(0, 2)
            val isEastExterior = idx in setOf(4, 5)

            val facadeColor = when (room.biome) {
                DistrictBiome.DOWNTOWN_NEON_PLAZA -> 0xFF1E2D36
                DistrictBiome.OVERGROWTH_CRATER_PARK -> 0xFF1E3326
                DistrictBiome.CRYO_HARBOR_PIER -> 0xFF1A3040
                DistrictBiome.COLLAPSED_MALL_BAZAAR -> 0xFF2D2338
                DistrictBiome.INDUSTRIAL_RAILYARD -> 0xFF33221C
                DistrictBiome.OBSIDIAN_CITADEL -> 0xFF281A3D
            }
            val roofColor = when (room.biome) {
                DistrictBiome.DOWNTOWN_NEON_PLAZA -> 0xFF2E4452
                DistrictBiome.OVERGROWTH_CRATER_PARK -> 0xFF2D4D39
                DistrictBiome.CRYO_HARBOR_PIER -> 0xFF294B63
                DistrictBiome.COLLAPSED_MALL_BAZAAR -> 0xFF453657
                DistrictBiome.INDUSTRIAL_RAILYARD -> 0xFF4D342B
                DistrictBiome.OBSIDIAN_CITADEL -> 0xFF3E285E
            }

            if (isNorthExterior) {
                var curX = b.left + 14f
                while (curX < b.right - 70f) {
                    val segW = rng.nextInt(88, 175).toFloat().coerceAtMost(b.right - curX - 10f)
                    val distFromCenter = abs((curX + segW * 0.5f) - b.center.x)
                    val jutInward = if (distFromCenter < 95f) 18f else rng.nextInt(28, 84).toFloat()
                    val zH = rng.nextInt(34, 60).toFloat()
                    outcrops.add(
                        JaggedOutcrop(
                            bounds = Rect(curX, b.top - 20f, curX + segW, b.top + jutInward),
                            edgeSide = 0,
                            roomIndex = idx,
                            heightZ = zH,
                            primaryColorHex = facadeColor,
                            roofColorHex = roofColor,
                            notchOffsetRatio = rng.nextFloat()
                        )
                    )
                    curX += segW + rng.nextInt(8, 28).toFloat()
                }
            }

            if (isSouthExterior) {
                var curX = b.left + 14f
                while (curX < b.right - 70f) {
                    val segW = rng.nextInt(88, 175).toFloat().coerceAtMost(b.right - curX - 10f)
                    val distFromCenter = abs((curX + segW * 0.5f) - b.center.x)
                    val jutInward = if (distFromCenter < 95f) 18f else rng.nextInt(28, 82).toFloat()
                    val zH = rng.nextInt(30, 54).toFloat()
                    outcrops.add(
                        JaggedOutcrop(
                            bounds = Rect(curX, b.bottom - jutInward, curX + segW, b.bottom + 20f),
                            edgeSide = 1,
                            roomIndex = idx,
                            heightZ = zH,
                            primaryColorHex = facadeColor,
                            roofColorHex = roofColor,
                            notchOffsetRatio = rng.nextFloat()
                        )
                    )
                    curX += segW + rng.nextInt(8, 28).toFloat()
                }
            }

            if (isWestExterior) {
                var curY = b.top + 32f
                while (curY < b.bottom - 70f) {
                    val segH = rng.nextInt(82, 165).toFloat().coerceAtMost(b.bottom - curY - 14f)
                    val distFromCenter = abs((curY + segH * 0.5f) - b.center.y)
                    val jutInward = if (distFromCenter < 95f) 18f else rng.nextInt(26, 78).toFloat()
                    val zH = rng.nextInt(34, 58).toFloat()
                    outcrops.add(
                        JaggedOutcrop(
                            bounds = Rect(b.left - 20f, curY, b.left + jutInward, curY + segH),
                            edgeSide = 2,
                            roomIndex = idx,
                            heightZ = zH,
                            primaryColorHex = facadeColor,
                            roofColorHex = roofColor,
                            notchOffsetRatio = rng.nextFloat()
                        )
                    )
                    curY += segH + rng.nextInt(8, 26).toFloat()
                }
            }

            if (isEastExterior) {
                var curY = b.top + 32f
                while (curY < b.bottom - 70f) {
                    val segH = rng.nextInt(82, 165).toFloat().coerceAtMost(b.bottom - curY - 14f)
                    val distFromCenter = abs((curY + segH * 0.5f) - b.center.y)
                    val jutInward = if (distFromCenter < 95f) 18f else rng.nextInt(26, 78).toFloat()
                    val zH = rng.nextInt(34, 58).toFloat()
                    outcrops.add(
                        JaggedOutcrop(
                            bounds = Rect(b.right - jutInward, curY, b.right + 20f, curY + segH),
                            edgeSide = 3,
                            roomIndex = idx,
                            heightZ = zH,
                            primaryColorHex = facadeColor,
                            roofColorHex = roofColor,
                            notchOffsetRatio = rng.nextFloat()
                        )
                    )
                    curY += segH + rng.nextInt(8, 26).toFloat()
                }
            }
        }
        return outcrops
    }

    private fun generateDistrictLandmarks(
        rooms: List<MapRoom>,
        lakes: List<MapLake>,
        seed: Long
    ): List<UrbanLandmark> {
        val list = mutableListOf<UrbanLandmark>()
        val rng = Random(seed xor 0xBB67AE85L)
        rooms.forEach { room ->
            val cx = room.bounds.center.x
            val cy = room.bounds.center.y
            val primaryKind = when (room.biome) {
                DistrictBiome.DOWNTOWN_NEON_PLAZA -> UrbanLandmarkKind.ROTARY_PLAZA_FOUNTAIN
                DistrictBiome.OVERGROWTH_CRATER_PARK -> UrbanLandmarkKind.EMBER_BLAST_CRATER
                DistrictBiome.CRYO_HARBOR_PIER -> UrbanLandmarkKind.CRYO_COOLANT_VAT
                DistrictBiome.COLLAPSED_MALL_BAZAAR -> UrbanLandmarkKind.BAZAAR_SKYLIGHT_DOME
                DistrictBiome.INDUSTRIAL_RAILYARD -> UrbanLandmarkKind.RAILWAY_TURNTABLE
                DistrictBiome.OBSIDIAN_CITADEL -> UrbanLandmarkKind.OBSIDIAN_VOID_ALTAR
            }
            list.add(
                UrbanLandmark(
                    kind = primaryKind,
                    x = cx,
                    y = cy,
                    radiusX = 82f,
                    radiusY = 66f,
                    roomIndex = room.index
                )
            )
            val quadOffsets = listOf(
                Pair(-420f, -340f),
                Pair(420f, -340f),
                Pair(-420f, 340f),
                Pair(420f, 340f)
            )
            quadOffsets.forEachIndexed { qIdx, (ox, oy) ->
                val lx = cx + ox + rng.nextInt(-30, 30)
                val ly = cy + oy + rng.nextInt(-25, 25)
                val nearLake = lakes.any {
                    it.roomIndex == room.index && hypot(lx - it.x, ly - it.y) < it.radiusX + 55f
                }
                if (!nearLake) {
                    val subKind = if ((qIdx + room.index) % 2 == 0) {
                        UrbanLandmarkKind.ABANDONED_TRANSIT_CAR
                    } else {
                        UrbanLandmarkKind.OVERGROWN_MEDIAN_GROVE
                    }
                    list.add(
                        UrbanLandmark(
                            kind = subKind,
                            x = lx,
                            y = ly,
                            radiusX = 48f,
                            radiusY = 28f,
                            rotationDeg = rng.nextInt(-25, 25).toFloat(),
                            roomIndex = room.index
                        )
                    )
                }
            }
        }
        return list
    }

    private fun pointToSegmentDist(px: Float, py: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        val lenSq = dx * dx + dy * dy
        if (lenSq <= 0.0001f) return hypot(px - x1, py - y1)
        val t = (((px - x1) * dx + (py - y1) * dy) / lenSq).coerceIn(0f, 1f)
        return hypot(px - (x1 + t * dx), py - (y1 + t * dy))
    }
}
