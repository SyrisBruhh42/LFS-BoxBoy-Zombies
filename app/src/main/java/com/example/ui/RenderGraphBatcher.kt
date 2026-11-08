package com.example.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.example.game.ChunkStreamSnapshot
import com.example.game.FoliageKind
import com.example.game.MapLake
import com.example.game.TownBuilding
import com.example.game.WorldChunkSlice
import com.example.game.WorldFoliage
import com.example.network.CoopPeerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * P.S. 1 & P.S. 2: Multi-Threaded Compose DrawScope RenderGraph Batching Pipeline
 * + Dark Bio-Noir 3/4 Orthographic Overlapping Sprite Sheet Atlas Registry.
 */
enum class RenderPassStage(val order: Int, val label: String) {
    PASS_0_GROUND_WATER(0, "Ground & Bio-Noir Lakes"),
    PASS_1_SHADOW_CASTERS(1, "Directional Ground Shadows"),
    PASS_2_YSORTED_OVERLAPPING_SPRITES(2, "Y-Sorted 3/4 Buildings, Trees & Props"),
    PASS_3_SQUAD_AND_ENTITIES(3, "4-Player Squad & Hostiles"),
    PASS_4_CANOPY_OVERHANGS_AND_HUD(4, "Overlapping Roof/Tree Canopies & Tactical HUD")
}

sealed class BatchedSpriteNode(val sortY: Float, val pass: RenderPassStage) {
    class LakeSpriteNode(
        val lake: MapLake
    ) : BatchedSpriteNode(lake.y, RenderPassStage.PASS_0_GROUND_WATER)

    class BuildingSpriteNode(
        val building: TownBuilding,
        val isMidLod: Boolean
    ) : BatchedSpriteNode(building.bounds.bottom, RenderPassStage.PASS_2_YSORTED_OVERLAPPING_SPRITES)

    class FoliageSpriteNode(
        val foliage: WorldFoliage,
        val isMidLod: Boolean
    ) : BatchedSpriteNode(foliage.y + foliage.radius * 0.45f, RenderPassStage.PASS_2_YSORTED_OVERLAPPING_SPRITES)

    class CanopyOverhangNode(
        val x: Float,
        val y: Float,
        val radius: Float,
        val variantIndex: Int
    ) : BatchedSpriteNode(y + radius, RenderPassStage.PASS_4_CANOPY_OVERHANGS_AND_HUD)
}

data class RenderGraphBatchFrame(
    val mapSeed: Long = 0L,
    val lakeNodes: List<BatchedSpriteNode.LakeSpriteNode> = emptyList(),
    val ySortedStructureAndFoliageNodes: List<BatchedSpriteNode> = emptyList(),
    val canopyOverhangNodes: List<BatchedSpriteNode.CanopyOverhangNode> = emptyList(),
    val totalBatchedNodes: Int = 0,
    val culledNodesCount: Int = 0,
    val activeChunkCount: Int = 0,
    val midLodChunkCount: Int = 0
)

object RenderGraphBatcher {
    private val batchScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val latestBatchRef = AtomicReference(RenderGraphBatchFrame())
    private var lastPreparedKey: Long = Long.MIN_VALUE

    /**
     * Builds or retrieves a pre-sorted RenderGraphBatchFrame for the current camera viewport and
     * active 600px spatial chunks, scheduling background updates on Dispatchers.Default when the camera moves.
     */
    fun prepareRenderGraphFrame(
        chunkSnapshot: ChunkStreamSnapshot,
        camMinX: Float,
        camMaxX: Float,
        camMinY: Float,
        camMaxY: Float
    ): RenderGraphBatchFrame {
        val quantCamX = (camMinX / 45f).toInt()
        val quantCamY = (camMinY / 45f).toInt()
        val stateKey = (chunkSnapshot.mapSeed xor (quantCamX.toLong() shl 20) xor (quantCamY.toLong() shl 4)) +
            chunkSnapshot.activeChunkCount * 31L + chunkSnapshot.cachedMidLodCount * 7L

        val cached = latestBatchRef.get()
        if (cached.totalBatchedNodes == 0 || cached.mapSeed != chunkSnapshot.mapSeed) {
            val immediate = buildBatchFrameInternal(chunkSnapshot, camMinX, camMaxX, camMinY, camMaxY)
            latestBatchRef.set(immediate)
            lastPreparedKey = stateKey
            return immediate
        }

        if (stateKey != lastPreparedKey) {
            lastPreparedKey = stateKey
            batchScope.launch {
                val updated = buildBatchFrameInternal(chunkSnapshot, camMinX, camMaxX, camMinY, camMaxY)
                latestBatchRef.set(updated)
            }
        }
        return latestBatchRef.get()
    }

    /**
     * Deterministic synchronous builder (also directly callable from unit tests).
     */
    fun buildBatchFrameInternal(
        chunkSnapshot: ChunkStreamSnapshot,
        camMinX: Float,
        camMaxX: Float,
        camMinY: Float,
        camMaxY: Float
    ): RenderGraphBatchFrame {
        val lakes = ArrayList<BatchedSpriteNode.LakeSpriteNode>(8)
        val ySorted = ArrayList<BatchedSpriteNode>(192)
        val overhangs = ArrayList<BatchedSpriteNode.CanopyOverhangNode>(64)
        var culled = 0

        val seenLakes = HashSet<String>()
        val seenBuildings = HashSet<String>()

        fun collectSlice(slice: WorldChunkSlice, isMidLod: Boolean) {
            for (i in 0 until slice.lakes.size) {
                val lake = slice.lakes[i]
                if (seenLakes.add(lake.id)) {
                    if (lake.x + lake.radiusX >= camMinX && lake.x - lake.radiusX <= camMaxX &&
                        lake.y + lake.radiusY >= camMinY && lake.y - lake.radiusY <= camMaxY
                    ) {
                        lakes.add(BatchedSpriteNode.LakeSpriteNode(lake))
                    } else {
                        culled++
                    }
                }
            }

            for (i in 0 until slice.townBuildings.size) {
                val b = slice.townBuildings[i]
                val bKey = "${b.roomIndex}_${b.bounds.left.toInt()}_${b.bounds.top.toInt()}"
                if (seenBuildings.add(bKey)) {
                    val fp = b.bounds
                    if (fp.right + 48f >= camMinX && fp.left - 48f <= camMaxX &&
                        fp.bottom + 48f >= camMinY && fp.top - 72f <= camMaxY
                    ) {
                        ySorted.add(BatchedSpriteNode.BuildingSpriteNode(b, isMidLod))
                    } else {
                        culled++
                    }
                }
            }

            for (i in 0 until slice.treesAndShrubs.size) {
                val f = slice.treesAndShrubs[i]
                val pad = f.radius * 1.6f
                if (f.x + pad >= camMinX && f.x - pad <= camMaxX &&
                    f.y + pad >= camMinY && f.y - pad <= camMaxY
                ) {
                    ySorted.add(BatchedSpriteNode.FoliageSpriteNode(f, isMidLod))
                    if (!isMidLod && f.heightZ > 24f) {
                        overhangs.add(
                            BatchedSpriteNode.CanopyOverhangNode(
                                x = f.x,
                                y = f.y - f.heightZ * 0.45f,
                                radius = f.radius * 1.28f,
                                variantIndex = if (f.kind == FoliageKind.PINE_EVERGREEN_TREE) 0 else 1
                            )
                        )
                    }
                } else {
                    culled++
                }
            }
        }

        for (i in 0 until chunkSnapshot.activeFullDetailChunks.size) {
            collectSlice(chunkSnapshot.activeFullDetailChunks[i], isMidLod = false)
        }
        for (i in 0 until chunkSnapshot.midLodCachedChunks.size) {
            collectSlice(chunkSnapshot.midLodCachedChunks[i], isMidLod = true)
        }

        ySorted.sortBy { it.sortY }

        val totalNodes = lakes.size + ySorted.size + overhangs.size
        return RenderGraphBatchFrame(
            mapSeed = chunkSnapshot.mapSeed,
            lakeNodes = lakes,
            ySortedStructureAndFoliageNodes = ySorted,
            canopyOverhangNodes = overhangs,
            totalBatchedNodes = totalNodes,
            culledNodesCount = culled,
            activeChunkCount = chunkSnapshot.activeChunkCount,
            midLodChunkCount = chunkSnapshot.cachedMidLodCount
        )
    }
}

/**
 * P.S. 2: Pre-baked GPU ImageBitmap Sprite Atlas Registry for Dark Bio-Noir 3/4 Orthographic
 * Overlapping Sprites (Pine/Oak Trees, Shrubs, Bio-Noir Lakes, Tactical Building Roofs, and 4K Inked Atlases).
 */
object BioNoirSpriteAtlasRegistry {
    const val TREE_CELL_PX = 96
    const val LAKE_STAMP_PX = 160
    const val BUILDING_ROOF_STAMP_PX = 128

    const val HERO_TUXEDO_ATLAS_ASSET = "inked-4k/hero_tuxedo_atlas.png"
    const val ZOMBIE_SUIT_ATLAS_ASSET = "inked-4k/zombie_suit_atlas.png"
    const val WOLF_DIRE_ATLAS_ASSET = "inked-4k/wolf_dire_atlas.png"
    const val PROPS_WASTELAND_ATLAS_ASSET = "inked-4k/props_wasteland_atlas.png"

    val CANONICAL_INKED_4K_ATLAS_ASSETS = listOf(
        HERO_TUXEDO_ATLAS_ASSET,
        ZOMBIE_SUIT_ATLAS_ASSET,
        WOLF_DIRE_ATLAS_ASSET,
        PROPS_WASTELAND_ATLAS_ASSET
    )

    val CANONICAL_BUNDLED_MAP_ASSETS = listOf(
        "maps/boxhead_2play_classic_rooms.boxboys.json",
        "maps/elona_12x12_vault_template.boxboys.json",
        "maps/sunvale_desert_bazaar_25d.boxboys.json"
    )

    val foliageSpriteSheet: ImageBitmap by lazy { buildFoliageSpriteSheet() }
    val lakeBioNoirStamp: ImageBitmap by lazy { buildLakeBioNoirStamp() }
    val buildingRoofStamp: ImageBitmap by lazy { buildBuildingRoofStamp() }

    @Volatile
    var heroTuxedoAtlas: ImageBitmap? = null
        private set

    @Volatile
    var zombieSuitAtlas: ImageBitmap? = null
        private set

    @Volatile
    var wolfDireAtlas: ImageBitmap? = null
        private set

    @Volatile
    var propsWastelandAtlas: ImageBitmap? = null
        private set

    @Volatile
    var isInked4kLoadedFromAssets: Boolean = false
        private set

    /**
     * Loads the 4K inked sprite atlases (`assets/inked-4k/<atlas>.png`) via `BitmapFactory.decodeStream`
     * when a valid Android `Context` is provided, and falls back safely to the procedural bitmap
     * stamps when `context == null` (e.g., in headless JVM unit tests).
     */
    @Synchronized
    fun ensureInitialized(context: Context? = null): Boolean {
        if (isInked4kLoadedFromAssets && heroTuxedoAtlas != null && propsWastelandAtlas != null) {
            return true
        }
        if (context != null) {
            try {
                val assets = context.assets
                val hero = assets.open(HERO_TUXEDO_ATLAS_ASSET).use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
                val zombie = assets.open(ZOMBIE_SUIT_ATLAS_ASSET).use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
                val wolf = assets.open(WOLF_DIRE_ATLAS_ASSET).use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
                val props = assets.open(PROPS_WASTELAND_ATLAS_ASSET).use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
                if (hero != null && zombie != null && wolf != null && props != null) {
                    heroTuxedoAtlas = hero
                    zombieSuitAtlas = zombie
                    wolfDireAtlas = wolf
                    propsWastelandAtlas = props
                    isInked4kLoadedFromAssets = true
                    return true
                }
            } catch (_: Throwable) {
                // Fall back to procedural stamps below
            }
        }
        // Safe fallback to procedural bitmap stamps when context == null or in headless unit tests
        runCatching {
            if (heroTuxedoAtlas == null) heroTuxedoAtlas = buildingRoofStamp
            if (zombieSuitAtlas == null) zombieSuitAtlas = buildingRoofStamp
            if (wolfDireAtlas == null) wolfDireAtlas = foliageSpriteSheet
            if (propsWastelandAtlas == null) propsWastelandAtlas = lakeBioNoirStamp
        }
        return isInked4kLoadedFromAssets
    }

    private fun buildFoliageSpriteSheet(): ImageBitmap {
        val bmp = ImageBitmap(TREE_CELL_PX * 4, TREE_CELL_PX, ImageBitmapConfig.Argb8888)
        val canvas = Canvas(bmp)
        val paint = Paint()

        // --- CELL 0: PINE TREE (x: 0..96) ---
        val cx0 = 48f
        val cy0 = 52f
        paint.style = PaintingStyle.Fill
        paint.color = Color(0x66020509)
        canvas.drawOval(cx0 - 28f, cy0 + 12f, cx0 + 34f, cy0 + 36f, paint)
        paint.color = Color(0xFF3E2723)
        canvas.drawRect(cx0 - 6f, cy0 + 2f, cx0 + 6f, cy0 + 26f, paint)
        val tiers = listOf(
            Triple(cy0 + 6f, 34f, Color(0xFF0F291E)),
            Triple(cy0 - 8f, 27f, Color(0xFF163A2B)),
            Triple(cy0 - 22f, 19f, Color(0xFF20503C))
        )
        for ((ty, rad, col) in tiers) {
            val path = Path().apply {
                moveTo(cx0, ty - rad * 1.15f)
                lineTo(cx0 + rad, ty + rad * 0.65f)
                lineTo(cx0, ty + rad * 0.42f)
                lineTo(cx0 - rad, ty + rad * 0.65f)
                close()
            }
            paint.style = PaintingStyle.Fill
            paint.color = col
            canvas.drawPath(path, paint)
            paint.style = PaintingStyle.Stroke
            paint.strokeWidth = 1.6f
            paint.color = Color(0x8838BDF8)
            canvas.drawPath(path, paint)
        }

        // --- CELL 1: OAK CANOPY (x: 96..192) ---
        val cx1 = 96f + 48f
        val cy1 = 50f
        paint.style = PaintingStyle.Fill
        paint.color = Color(0x66020509)
        canvas.drawOval(cx1 - 34f, cy1 + 10f, cx1 + 36f, cy1 + 38f, paint)
        paint.color = Color(0xFF4A2E1B)
        canvas.drawRect(cx1 - 7f, cy1 + 4f, cx1 + 7f, cy1 + 28f, paint)
        val lobes = listOf(
            Triple(-14f, 2f, 22f),
            Triple(14f, 2f, 21f),
            Triple(0f, -12f, 24f),
            Triple(-8f, -6f, 18f),
            Triple(10f, -8f, 17f)
        )
        for ((idx, lobe) in lobes.withIndex()) {
            paint.color = when (idx % 3) {
                0 -> Color(0xFF143325)
                1 -> Color(0xFF1D4734)
                else -> Color(0xFF285E45)
            }
            canvas.drawCircle(Offset(cx1 + lobe.first, cy1 + lobe.second), lobe.third, paint)
        }
        paint.style = PaintingStyle.Stroke
        paint.strokeWidth = 1.5f
        paint.color = Color(0x7734D399)
        canvas.drawCircle(Offset(cx1, cy1 - 5f), 27f, paint)

        // --- CELL 2: SHRUB THICKET (x: 192..288) ---
        val cx2 = 192f + 48f
        val cy2 = 54f
        paint.style = PaintingStyle.Fill
        paint.color = Color(0x55020509)
        canvas.drawOval(cx2 - 24f, cy2 + 6f, cx2 + 26f, cy2 + 26f, paint)
        for (i in 0..4) {
            val ang = i * 1.25f
            val ox = cos(ang) * 10f
            val oy = sin(ang) * 7f
            paint.color = if (i % 2 == 0) Color(0xFF183D2C) else Color(0xFF24543D)
            canvas.drawCircle(Offset(cx2 + ox, cy2 + oy), 14f, paint)
        }
        paint.color = Color(0xCC38BDF8)
        canvas.drawCircle(Offset(cx2 - 7f, cy2 - 4f), 2.5f, paint)
        canvas.drawCircle(Offset(cx2 + 8f, cy2 + 2f), 2.2f, paint)
        canvas.drawCircle(Offset(cx2 + 2f, cy2 - 8f), 2.0f, paint)

        // --- CELL 3: TRANSLUCENT CANOPY OVERHANG (x: 288..384) ---
        val cx3 = 288f + 48f
        val cy3 = 48f
        paint.style = PaintingStyle.Fill
        paint.color = Color(0x55163A2B)
        canvas.drawCircle(Offset(cx3, cy3), 34f, paint)
        paint.color = Color(0x44285E45)
        canvas.drawCircle(Offset(cx3 - 8f, cy3 - 6f), 22f, paint)
        paint.style = PaintingStyle.Stroke
        paint.strokeWidth = 1.4f
        paint.color = Color(0x6638BDF8)
        canvas.drawCircle(Offset(cx3, cy3), 33f, paint)

        return bmp
    }

    private fun buildLakeBioNoirStamp(): ImageBitmap {
        val bmp = ImageBitmap(LAKE_STAMP_PX, LAKE_STAMP_PX, ImageBitmapConfig.Argb8888)
        val canvas = Canvas(bmp)
        val paint = Paint()
        val cx = LAKE_STAMP_PX / 2f
        val cy = LAKE_STAMP_PX / 2f

        paint.style = PaintingStyle.Fill
        paint.color = Color(0xFF1E293B)
        canvas.drawOval(8f, 14f, LAKE_STAMP_PX - 8f, LAKE_STAMP_PX - 14f, paint)
        paint.color = Color(0xFF0F3B57)
        canvas.drawOval(16f, 22f, LAKE_STAMP_PX - 16f, LAKE_STAMP_PX - 22f, paint)
        paint.color = Color(0xFF0A273D)
        canvas.drawOval(28f, 32f, LAKE_STAMP_PX - 28f, LAKE_STAMP_PX - 32f, paint)
        paint.color = Color(0xFF061626)
        canvas.drawOval(44f, 46f, LAKE_STAMP_PX - 44f, LAKE_STAMP_PX - 46f, paint)

        paint.style = PaintingStyle.Stroke
        paint.strokeWidth = 1.8f
        paint.color = Color(0x6638BDF8)
        canvas.drawOval(24f, 28f, LAKE_STAMP_PX - 24f, LAKE_STAMP_PX - 28f, paint)
        paint.color = Color(0x4400FF66)
        canvas.drawOval(40f, 44f, LAKE_STAMP_PX - 40f, LAKE_STAMP_PX - 44f, paint)
        paint.color = Color(0x777DD3FC)
        canvas.drawLine(Offset(cx - 26f, cy - 10f), Offset(cx + 18f, cy - 10f), paint)
        canvas.drawLine(Offset(cx - 18f, cy + 12f), Offset(cx + 28f, cy + 12f), paint)

        return bmp
    }

    private fun buildBuildingRoofStamp(): ImageBitmap {
        val bmp = ImageBitmap(BUILDING_ROOF_STAMP_PX, BUILDING_ROOF_STAMP_PX, ImageBitmapConfig.Argb8888)
        val canvas = Canvas(bmp)
        val paint = Paint()
        val s = BUILDING_ROOF_STAMP_PX.toFloat()

        paint.style = PaintingStyle.Fill
        paint.color = Color(0xFF111827)
        canvas.drawRect(4f, 4f, s - 4f, s - 4f, paint)

        paint.style = PaintingStyle.Stroke
        paint.strokeWidth = 1.5f
        paint.color = Color(0x4438BDF8)
        canvas.drawRect(4f, 4f, s - 4f, s - 4f, paint)
        for (y in 20 until (s.toInt() - 12) step 18) {
            paint.color = Color(0x3394A3B8)
            canvas.drawLine(Offset(8f, y.toFloat()), Offset(s - 8f, y.toFloat()), paint)
        }
        paint.style = PaintingStyle.Fill
        paint.color = Color(0x3300E5FF)
        canvas.drawRect(24f, s * 0.42f, s - 24f, s * 0.58f, paint)
        paint.style = PaintingStyle.Stroke
        paint.color = Color(0x8838BDF8)
        canvas.drawRect(24f, s * 0.42f, s - 24f, s * 0.58f, paint)

        return bmp
    }

    fun DrawScope.drawOverlappingFoliageSprite(
        foliage: WorldFoliage,
        sx: Float,
        sy: Float,
        scale: Float = 1f,
        swayX: Float = 0f,
        isMidLod: Boolean = false
    ) {
        val cellIndex = when (foliage.kind) {
            FoliageKind.PINE_EVERGREEN_TREE -> 0
            FoliageKind.OAK_CANOPY_TREE, FoliageKind.WEEPING_WILLOW_TREE, FoliageKind.AUTUMN_MAPLE_TREE -> 1
            else -> 2
        }
        val drawDiam = (foliage.radius * 2.95f * scale).roundToInt().coerceAtLeast(18)
        val dstX = (sx + swayX * 0.5f - drawDiam / 2f).roundToInt()
        val dstY = (sy - foliage.heightZ * 0.35f * scale - drawDiam / 2f).roundToInt()

        drawImage(
            image = foliageSpriteSheet,
            srcOffset = IntOffset(cellIndex * TREE_CELL_PX, 0),
            srcSize = IntSize(TREE_CELL_PX, TREE_CELL_PX),
            dstOffset = IntOffset(dstX, dstY),
            dstSize = IntSize(drawDiam, drawDiam),
            alpha = if (isMidLod) 0.82f else 0.96f
        )
    }

    fun DrawScope.drawCanopyOverhangSprite(
        sx: Float,
        sy: Float,
        radiusPx: Float
    ) {
        val diam = (radiusPx * 2.2f).roundToInt().coerceAtLeast(16)
        drawImage(
            image = foliageSpriteSheet,
            srcOffset = IntOffset(3 * TREE_CELL_PX, 0),
            srcSize = IntSize(TREE_CELL_PX, TREE_CELL_PX),
            dstOffset = IntOffset((sx - diam / 2f).roundToInt(), (sy - diam / 2f).roundToInt()),
            dstSize = IntSize(diam, diam),
            alpha = 0.68f
        )
    }

    fun DrawScope.drawBioNoirLakeSprite(
        sx: Float,
        sy: Float,
        rx: Float,
        ry: Float
    ) {
        val w = (rx * 2.35f).roundToInt().coerceAtLeast(32)
        val h = (ry * 2.35f).roundToInt().coerceAtLeast(32)
        drawImage(
            image = lakeBioNoirStamp,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(LAKE_STAMP_PX, LAKE_STAMP_PX),
            dstOffset = IntOffset((sx - w / 2f).roundToInt(), (sy - h / 2f).roundToInt()),
            dstSize = IntSize(w, h),
            alpha = 0.92f
        )
    }

    fun DrawScope.drawBioNoirRoofOverlay(
        roofLeft: Float,
        roofTop: Float,
        roofW: Float,
        roofH: Float
    ) {
        val overhangX = (roofW * 0.08f).coerceAtLeast(6f)
        val overhangY = (roofH * 0.08f).coerceAtLeast(5f)
        drawImage(
            image = buildingRoofStamp,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(BUILDING_ROOF_STAMP_PX, BUILDING_ROOF_STAMP_PX),
            dstOffset = IntOffset((roofLeft - overhangX).roundToInt(), (roofTop - overhangY).roundToInt()),
            dstSize = IntSize(
                (roofW + overhangX * 2f).roundToInt().coerceAtLeast(16),
                (roofH + overhangY * 2f).roundToInt().coerceAtLeast(16)
            ),
            alpha = 0.42f
        )
    }

    fun DrawScope.drawSquadMemberOrthographic(
        peer: CoopPeerState,
        sx: Float,
        sy: Float,
        scale: Float = 1f
    ) {
        val visorColor = Color(peer.visorColorHex)
        val bodyColor = if (peer.isDowned) Color(0xFF991B1B) else Color(0xFF1E293B)

        if (peer.isDowned) {
            val ringRad = 24f * scale
            drawCircle(
                color = Color(0x55EF4444),
                radius = ringRad,
                center = Offset(sx, sy)
            )
            drawArc(
                color = Color(0xFF00FF66),
                startAngle = -90f,
                sweepAngle = 360f * peer.reviveProgress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = Offset(sx - ringRad, sy - ringRad),
                size = Size(ringRad * 2f, ringRad * 2f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = (3.5f * scale).coerceAtLeast(2f))
            )
        } else {
            drawCircle(
                color = visorColor.copy(alpha = 0.22f),
                radius = 19f * scale,
                center = Offset(sx, sy + 2f * scale)
            )
        }

        val w = 22f * scale
        val h = 24f * scale
        val lift = if (peer.isDowned) 3f * scale else 11f * scale

        drawOval(
            color = Color(0x88000000),
            topLeft = Offset(sx - w * 0.6f, sy - h * 0.25f),
            size = Size(w * 1.2f, h * 0.65f)
        )
        drawRoundRect(
            color = bodyColor,
            topLeft = Offset(sx - w / 2f, sy - h / 2f - lift),
            size = Size(w, h),
            cornerRadius = CornerRadius(4f * scale, 4f * scale)
        )
        drawRoundRect(
            color = visorColor,
            topLeft = Offset(sx - w * 0.38f, sy - h * 0.28f - lift),
            size = Size(w * 0.76f, h * 0.26f),
            cornerRadius = CornerRadius(2f * scale, 2f * scale)
        )
        val gunLen = 18f * scale
        val gx = sx + cos(peer.aimAngleRad) * gunLen
        val gy = (sy - lift * 0.6f) + sin(peer.aimAngleRad) * gunLen
        drawLine(
            color = visorColor,
            start = Offset(sx, sy - lift * 0.6f),
            end = Offset(gx, gy),
            strokeWidth = (3.2f * scale).coerceAtLeast(1.5f)
        )

        val barW = 34f * scale
        val barH = (3.5f * scale).coerceAtLeast(2f)
        val barY = sy - h / 2f - lift - 10f * scale
        drawRect(
            color = Color(0xAA020617),
            topLeft = Offset(sx - barW / 2f, barY),
            size = Size(barW, barH)
        )
        val hpFrac = (peer.hp.toFloat() / peer.maxHp.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
        drawRect(
            color = if (peer.isDowned) Color(0xFFFF2A54) else visorColor,
            topLeft = Offset(sx - barW / 2f, barY),
            size = Size(barW * hpFrac, barH)
        )
    }
}
