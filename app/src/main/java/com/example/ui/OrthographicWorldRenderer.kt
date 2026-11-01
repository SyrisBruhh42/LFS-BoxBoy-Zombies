package com.example.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.example.game.ConvexShadowHull
import com.example.game.DistrictBiome
import com.example.game.ExplosiveBarrel
import com.example.game.FoliageKind
import com.example.game.FortificationEntity
import com.example.game.FortificationType
import com.example.game.GameSessionState
import com.example.game.InteractiveStation
import com.example.game.JaggedOutcrop
import com.example.game.LightingAtmosphereMode
import com.example.game.LootDropEntity
import com.example.game.LotSurfaceKind
import com.example.game.MapLake
import com.example.game.MapRoom
import com.example.game.PointLightSource2D
import com.example.game.RiverSegment
import com.example.game.RoadSideProp
import com.example.game.RoadSidePropKind
import com.example.game.RoadTier
import com.example.game.StationKind
import com.example.game.TerrainPatch
import com.example.game.TerrainSurfaceKind
import com.example.game.TownBuilding
import com.example.game.TownBuildingStyle
import com.example.game.UrbanLandmark
import com.example.game.UrbanLandmarkKind
import com.example.game.WorldChunkStreamer
import com.example.game.WorldFoliage
import com.example.game.WorldRoadSegment
import com.example.game.ZombieArchetype
import com.example.game.ZombieEntity
import com.example.network.CoopPeerState
import com.example.ui.BioNoirSpriteAtlasRegistry.drawBioNoirLakeSprite
import com.example.ui.BioNoirSpriteAtlasRegistry.drawBioNoirRoofOverlay
import com.example.ui.BioNoirSpriteAtlasRegistry.drawCanopyOverhangSprite
import com.example.ui.BioNoirSpriteAtlasRegistry.drawOverlappingFoliageSprite
import com.example.ui.BioNoirSpriteAtlasRegistry.drawSquadMemberOrthographic
import com.example.ui.theme.CryoCyan
import com.example.ui.theme.HandheldObsidian
import com.example.ui.theme.MysteryGold
import com.example.ui.theme.PackPurple
import com.example.ui.theme.PhosphorGreen
import com.example.ui.theme.TextCrisp
import com.example.ui.theme.ZombieCrimson
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * High-Performance 60 FPS 3/4 Orthographic Top-Down Renderer & Procedural Sprite Art Engine.
 *
 * Key Upgrades:
 * 1. True 3/4 Orthographic Projection (Y-axis foreshortening + 3D vertical Z-extrusion for walls,
 *    pillars, perk machines, barrels, and characters with roof caps, front facades, and drop shadows).
 * 2. Zero per-frame heap allocations in tight loops (reuses a single scratch Path for shadow volumes
 *    and directional cones, eliminating GC pauses and frame stutter).
 * 3. Rich articulated procedural art for BoxBoy Survivor (walking boots, 3D armored torso, pauldron
 *    trim, glowing helmet visor, dual-hand weapon with muzzle flash & recoil) and all 6 enemy archetypes.
 */
object OrthographicWorldRenderer {

    // 3/4 Orthographic tilt ratio: compresses Y slightly to give an authentic isometric/orthographic tactical angle
    const val ORTHO_Y_SCALE = 0.82f
    private const val WALL_Z_HEIGHT = 36f
    private const val PILLAR_Z_HEIGHT = 34f

    // Reusable scratch Path so 60 FPS Canvas drawing allocates 0 Path objects per frame
    private val scratchPath = Path()

    fun DrawScope.renderOrthographicWorld(
        state: GameSessionState,
        coopPeer: CoopPeerState?,
        smoothCamX: Float,
        smoothCamY: Float,
        walkPhaseRad: Float,
        isMoving: Boolean,
        frameLights: List<PointLightSource2D>,
        shadowHulls: List<ConvexShadowHull>
    ) {
        val px = state.playerX
        val py = state.playerY

        // Wide viewport frustum culling bounds in world space for the 4800x2760 County Metropolis
        val cullMinX = smoothCamX - 1560f
        val cullMaxX = smoothCamX + 1560f
        val cullMinY = smoothCamY - 1140f
        val cullMaxY = smoothCamY + 1140f

        // P.S. 1 & P.S. 3: Prepare Multi-Threaded RenderGraph Batch from Active 600px Spatial Chunks
        val activeChunkSnap = if (state.chunkSnapshot.activeChunkCount > 0) {
            state.chunkSnapshot
        } else {
            WorldChunkStreamer.queryAndStreamSquadChunks(
                map = state.map,
                playerX = px,
                playerY = py,
                coopPeers = state.coopPeers,
                nowMs = state.clockMs
            )
        }
        val renderGraphFrame = RenderGraphBatcher.prepareRenderGraphFrame(
            chunkSnapshot = activeChunkSnap,
            camMinX = cullMinX,
            camMaxX = cullMaxX,
            camMinY = cullMinY,
            camMaxY = cullMaxY
        )

        // 1. Render Orthographic Open City Districts, Biome Roads, Crosswalks & Locked Sector Barriers
        val rooms = state.map.rooms
        for (i in 0 until rooms.size) {
            val room = rooms[i]
            val b = room.bounds
            if (b.right >= cullMinX && b.left <= cullMaxX && b.bottom >= cullMinY && b.top - 60f <= cullMaxY) {
                drawOrthographicRoom(
                    room = room,
                    unlocked = room.index in state.unlockedRooms,
                    unlockedRooms = state.unlockedRooms,
                    cullMinX = cullMinX,
                    cullMaxX = cullMaxX,
                    cullMinY = cullMinY,
                    cullMaxY = cullMaxY
                )
            }
        }

        val worldAnimRad = ((state.clockMs % 62830L).toFloat()) * 0.0072f

        // 1.05 Pass 1: Render Organic Regional Biome Terrain Patches & Cross-Border Blends
        val terrainPatches = state.map.terrainPatches
        for (i in 0 until terrainPatches.size) {
            val patch = terrainPatches[i]
            if ((patch.isCrossBorderBlend || patch.roomIndex in state.unlockedRooms) &&
                patch.x + patch.radiusX >= cullMinX && patch.x - patch.radiusX <= cullMaxX &&
                patch.y + patch.radiusY >= cullMinY && patch.y - patch.radiusY <= cullMaxY
            ) {
                drawTerrainPatch(patch)
            }
        }

        // 1.08 Pass 3: Render Hierarchical Tier-2 Town Streets & Tier-3 Scenic Dirt Trails
        val roads = state.map.roads
        for (i in 0 until roads.size) {
            val road = roads[i]
            if (road.tier != RoadTier.COUNTY_HIGHWAY && road.roomIndex in state.unlockedRooms) {
                val minRx = min(road.startX, road.endX) - road.width
                val maxRx = max(road.startX, road.endX) + road.width
                val minRy = min(road.startY, road.endY) - road.width
                val maxRy = max(road.startY, road.endY) + road.width
                if (maxRx >= cullMinX && minRx <= cullMaxX && maxRy >= cullMinY && minRy <= cullMaxY) {
                    drawHierarchicalRoadSegment(road)
                }
            }
        }

        // 1.09 Pass 4 Ground Layer: Render Building Lot Parcels (Cobblestone Patios, Fenced Yards, Timber Decks)
        val townBuildings = state.map.townBuildings
        for (i in 0 until townBuildings.size) {
            val tb = townBuildings[i]
            val lb = tb.lotBounds
            if (tb.roomIndex in state.unlockedRooms &&
                lb.right >= cullMinX && lb.left <= cullMaxX &&
                lb.bottom >= cullMinY && lb.top <= cullMaxY
            ) {
                drawBuildingLotParcel(tb)
            }
        }

        // 1.10 Pass 2A: Render Winding County Rivers, Tributary Streams & Stone Highway Bridges
        val rivers = state.map.riverSegments
        for (i in 0 until rivers.size) {
            val seg = rivers[i]
            if (seg.roomIndex in state.unlockedRooms) {
                val minRx = min(seg.startX, seg.endX) - seg.width
                val maxRx = max(seg.startX, seg.endX) + seg.width
                val minRy = min(seg.startY, seg.endY) - seg.width
                val maxRy = max(seg.startY, seg.endY) + seg.width
                if (maxRx >= cullMinX && minRx <= cullMaxX && maxRy >= cullMinY && minRy <= cullMaxY) {
                    drawRiverSegment(seg, worldAnimRad)
                }
            }
        }

        // 1a. Pass 2B: Render Scenic Town Lakes, Lagoons, Shoreline Beaches & Walkable Wooden Footbridges
        val lakes = state.map.lakes
        for (i in 0 until lakes.size) {
            val lake = lakes[i]
            if (lake.roomIndex in state.unlockedRooms &&
                lake.x + lake.radiusX + 30f >= cullMinX && lake.x - lake.radiusX - 30f <= cullMaxX &&
                lake.y + lake.radiusY + 30f >= cullMinY && lake.y - lake.radiusY - 30f <= cullMaxY
            ) {
                drawOrthographicLake(lake, worldAnimRad)
            }
        }

        // 1b. Render Diverse District Landmarks (Rotary Fountain, Blast Crater, Coolant Vat, Railyard Turntable, Transit Cars)
        val landmarks = state.map.landmarks
        for (i in 0 until landmarks.size) {
            val lm = landmarks[i]
            if (lm.roomIndex in state.unlockedRooms &&
                lm.x + lm.radiusX >= cullMinX && lm.x - lm.radiusX <= cullMaxX &&
                lm.y + lm.radiusY >= cullMinY && lm.y - lm.radiusY <= cullMaxY
            ) {
                drawUrbanLandmark(lm)
            }
        }

        // 1c. Render Ground-Level Shrubs, Flowering Hedgerows & Shoreline Reeds
        val foliageList = state.map.foliage
        for (i in 0 until foliageList.size) {
            val fol = foliageList[i]
            if (fol.heightZ <= 20f && fol.roomIndex in state.unlockedRooms &&
                fol.x + fol.radius >= cullMinX && fol.x - fol.radius <= cullMaxX &&
                fol.y + fol.radius >= cullMinY && fol.y - fol.radius <= cullMaxY
            ) {
                drawGroundShrubOrReed(fol)
            }
        }

        // 2. Render Phase 3 Elemental Puddles (Foreshortened Elliptical Glowing Hazard Pools)
        val puddles = state.elementalPuddles
        for (i in 0 until puddles.size) {
            val puddle = puddles[i]
            if (puddle.x < cullMinX || puddle.x > cullMaxX || puddle.y < cullMinY || puddle.y > cullMaxY) continue
            val pColor = Color(puddle.colorHex)
            val rx = puddle.radius
            val ry = puddle.radius * ORTHO_Y_SCALE
            drawOval(
                color = pColor.copy(alpha = 0.26f),
                topLeft = Offset(puddle.x - rx, puddle.y - ry),
                size = Size(rx * 2f, ry * 2f)
            )
            drawOval(
                color = pColor.copy(alpha = 0.45f),
                topLeft = Offset(puddle.x - rx * 0.68f, puddle.y - ry * 0.68f),
                size = Size(rx * 1.36f, ry * 1.36f)
            )
            drawOval(
                color = pColor.copy(alpha = 0.85f),
                topLeft = Offset(puddle.x - rx, puddle.y - ry),
                size = Size(rx * 2f, ry * 2f),
                style = Stroke(width = 2.2f)
            )
        }

        // 3. Depth-Sorted (Painter's Algorithm by Y) 3/4 Orthographic 3D Jagged Borders, Town Buildings, Trees & Props
        val jaggedOutcrops = state.map.jaggedPerimeterOutcrops
        for (i in 0 until jaggedOutcrops.size) {
            val oc = jaggedOutcrops[i]
            val b = oc.bounds
            if (b.bottom <= py && b.right >= cullMinX && b.left <= cullMaxX && b.bottom >= cullMinY && b.top - 65f <= cullMaxY) {
                drawJaggedPerimeterOutcrop3D(oc, oc.roomIndex in state.unlockedRooms)
            }
        }

        val obstacles = state.map.wallObstacles
        if (townBuildings.size == obstacles.size) {
            for (i in 0 until townBuildings.size) {
                val tb = townBuildings[i]
                val b = tb.bounds
                if (b.bottom <= py && b.right >= cullMinX && b.left <= cullMaxX && b.bottom >= cullMinY && b.top - 65f <= cullMaxY) {
                    drawTownBuilding3D(tb)
                }
            }
        } else {
            for (i in 0 until obstacles.size) {
                val pillar = obstacles[i]
                if (pillar.bottom <= py && pillar.right >= cullMinX && pillar.left <= cullMaxX && pillar.bottom >= cullMinY && pillar.top - 45f <= cullMaxY) {
                    drawOrthographicPillar3D(pillar)
                }
            }
        }

        // Render 3D Roadside Props behind player (y <= py)
        val roadProps = state.map.roadSideProps
        for (i in 0 until roadProps.size) {
            val rp = roadProps[i]
            if (rp.y <= py && rp.roomIndex in state.unlockedRooms &&
                rp.x in cullMinX..cullMaxX && rp.y in cullMinY..cullMaxY
            ) {
                drawRoadSideProp3D(rp)
            }
        }

        // Render 3D Trees behind player (y <= py)
        for (i in 0 until foliageList.size) {
            val fol = foliageList[i]
            if (fol.heightZ > 20f && fol.y <= py && fol.roomIndex in state.unlockedRooms &&
                fol.x + fol.radius >= cullMinX && fol.x - fol.radius <= cullMaxX &&
                fol.y + fol.radius >= cullMinY && fol.y - fol.radius - fol.heightZ <= cullMaxY
            ) {
                drawTreeFoliage3D(fol)
            }
        }

        val stations = state.map.stations
        for (i in 0 until stations.size) {
            val st = stations[i]
            if (st.y <= py && st.x in cullMinX..cullMaxX && st.y in cullMinY..cullMaxY) {
                drawOrthographicStation3D(st, state.unlockedRooms)
            }
        }

        val forts = state.fortifications
        for (i in 0 until forts.size) {
            val fort = forts[i]
            if (fort.y <= py && fort.x in cullMinX..cullMaxX && fort.y in cullMinY..cullMaxY) {
                drawOrthographicFortification3D(fort)
            }
        }

        val barrels = state.map.barrels
        for (i in 0 until barrels.size) {
            val barrel = barrels[i]
            if (!barrel.isDetonated && barrel.y <= py && barrel.x in cullMinX..cullMaxX && barrel.y in cullMinY..cullMaxY) {
                drawOrthographicBarrel3D(barrel)
            }
        }

        val drops = state.lootDrops
        for (i in 0 until drops.size) {
            val drop = drops[i]
            if (drop.y <= py && drop.x in cullMinX..cullMaxX && drop.y in cullMinY..cullMaxY) {
                drawOrthographicLootDrop3D(drop, worldAnimRad)
            }
        }

        // Draw Zombies behind player (y <= py)
        val zombies = state.zombies
        for (i in 0 until zombies.size) {
            val z = zombies[i]
            if (z.y <= py && z.x in cullMinX..cullMaxX && z.y in cullMinY..cullMaxY) {
                drawOrthographicZombieSprite(
                    zombie = z,
                    playerX = px,
                    playerY = py,
                    isLightLocked = z.id == state.lightLockTargetId,
                    animPhase = worldAnimRad * z.archetype.speedMultiplier + z.id * 1.1f
                )
            }
        }

        // Draw 4-Player Squad Peers & Legacy Co-Op Partner behind local player (y <= py)
        val squadPeers = state.coopPeers
        for (i in 0 until squadPeers.size) {
            val peer = squadPeers[i]
            if (peer.y <= py && peer.x in cullMinX..cullMaxX && peer.y in cullMinY..cullMaxY) {
                drawSquadMemberOrthographic(peer, peer.x, peer.y, scale = 1.05f)
            }
        }
        if (coopPeer != null && coopPeer.y <= py && squadPeers.none { it.peerId == coopPeer.peerId }) {
            drawOrthographicCoopPartner(coopPeer, worldAnimRad)
        }

        // Draw Local Player Downed/Revive Ring if in Bleedout State
        if (state.isPlayerDowned) {
            val ringRad = 28f
            drawCircle(
                color = Color(0x55EF4444),
                radius = ringRad,
                center = Offset(px, py)
            )
            drawArc(
                color = PhosphorGreen,
                startAngle = -90f,
                sweepAngle = 360f * state.playerReviveProgress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = Offset(px - ringRad, py - ringRad),
                size = Size(ringRad * 2f, ringRad * 2f),
                style = Stroke(width = 4f)
            )
        }

        // Draw Local Armored Survivor (with 3/4 Orthographic 3D Extrusion, Walking Boots & Articulated Gun)
        drawOrthographicPlayerSprite(
            state = state,
            walkPhaseRad = walkPhaseRad,
            isMoving = isMoving
        )

        // Draw 4-Player Squad Peers & Legacy Co-Op Partner in front of local player (y > py)
        for (i in 0 until squadPeers.size) {
            val peer = squadPeers[i]
            if (peer.y > py && peer.x in cullMinX..cullMaxX && peer.y in cullMinY..cullMaxY) {
                drawSquadMemberOrthographic(peer, peer.x, peer.y, scale = 1.05f)
            }
        }
        if (coopPeer != null && coopPeer.y > py && squadPeers.none { it.peerId == coopPeer.peerId }) {
            drawOrthographicCoopPartner(coopPeer, worldAnimRad)
        }

        // Draw Zombies in front of player (y > py)
        for (i in 0 until zombies.size) {
            val z = zombies[i]
            if (z.y > py && z.x in cullMinX..cullMaxX && z.y in cullMinY..cullMaxY) {
                drawOrthographicZombieSprite(
                    zombie = z,
                    playerX = px,
                    playerY = py,
                    isLightLocked = z.id == state.lightLockTargetId,
                    animPhase = worldAnimRad * z.archetype.speedMultiplier + z.id * 1.1f
                )
            }
        }

        // Foreground Props (y > py) — player walks behind these 3D buildings/trees/barrels/stations!
        for (i in 0 until drops.size) {
            val drop = drops[i]
            if (drop.y > py && drop.x in cullMinX..cullMaxX && drop.y in cullMinY..cullMaxY) {
                drawOrthographicLootDrop3D(drop, worldAnimRad)
            }
        }

        for (i in 0 until barrels.size) {
            val barrel = barrels[i]
            if (!barrel.isDetonated && barrel.y > py && barrel.x in cullMinX..cullMaxX && barrel.y in cullMinY..cullMaxY) {
                drawOrthographicBarrel3D(barrel)
            }
        }

        for (i in 0 until forts.size) {
            val fort = forts[i]
            if (fort.y > py && fort.x in cullMinX..cullMaxX && fort.y in cullMinY..cullMaxY) {
                drawOrthographicFortification3D(fort)
            }
        }

        for (i in 0 until stations.size) {
            val st = stations[i]
            if (st.y > py && st.x in cullMinX..cullMaxX && st.y in cullMinY..cullMaxY) {
                drawOrthographicStation3D(st, state.unlockedRooms)
            }
        }

        // Render 3D Trees in front of player (y > py)
        for (i in 0 until foliageList.size) {
            val fol = foliageList[i]
            if (fol.heightZ > 20f && fol.y > py && fol.roomIndex in state.unlockedRooms &&
                fol.x + fol.radius >= cullMinX && fol.x - fol.radius <= cullMaxX &&
                fol.y + fol.radius >= cullMinY && fol.y - fol.radius - fol.heightZ <= cullMaxY
            ) {
                drawTreeFoliage3D(fol)
            }
        }

        // Render 3D Roadside Props in front of player (y > py)
        for (i in 0 until roadProps.size) {
            val rp = roadProps[i]
            if (rp.y > py && rp.roomIndex in state.unlockedRooms &&
                rp.x in cullMinX..cullMaxX && rp.y in cullMinY..cullMaxY
            ) {
                drawRoadSideProp3D(rp)
            }
        }

        if (townBuildings.size == obstacles.size) {
            for (i in 0 until townBuildings.size) {
                val tb = townBuildings[i]
                val b = tb.bounds
                if (b.bottom > py && b.right >= cullMinX && b.left <= cullMaxX && b.bottom >= cullMinY && b.top - 65f <= cullMaxY) {
                    drawTownBuilding3D(tb)
                }
            }
        } else {
            for (i in 0 until obstacles.size) {
                val pillar = obstacles[i]
                if (pillar.bottom > py && pillar.right >= cullMinX && pillar.left <= cullMaxX && pillar.bottom >= cullMinY && pillar.top - 45f <= cullMaxY) {
                    drawOrthographicPillar3D(pillar)
                }
            }
        }

        for (i in 0 until jaggedOutcrops.size) {
            val oc = jaggedOutcrops[i]
            val b = oc.bounds
            if (b.bottom > py && b.right >= cullMinX && b.left <= cullMaxX && b.bottom >= cullMinY && b.top - 65f <= cullMaxY) {
                drawJaggedPerimeterOutcrop3D(oc, oc.roomIndex in state.unlockedRooms)
            }
        }

        // 4. Render Calcified Stalker (Archer) Laser Telegraph Sightlines
        val telegraphs = state.laserTelegraphs
        for (i in 0 until telegraphs.size) {
            val tg = telegraphs[i]
            val alpha = (0.4f + tg.chargeFraction * 0.6f).coerceIn(0.3f, 1f)
            drawLine(
                color = Color(tg.colorHex).copy(alpha = alpha * 0.35f),
                start = Offset(tg.startX, tg.startY - 14f),
                end = Offset(tg.endX, tg.endY - 14f),
                strokeWidth = 6f
            )
            drawLine(
                color = Color(tg.colorHex).copy(alpha = alpha),
                start = Offset(tg.startX, tg.startY - 14f),
                end = Offset(tg.endX, tg.endY - 14f),
                strokeWidth = 2f + tg.chargeFraction * 2f
            )
        }

        // 5. Render Enemy Ranged Projectiles (3D Bone Shards & Elemental Orbs with Drop Shadow)
        val projs = state.enemyProjectiles
        for (i in 0 until projs.size) {
            val proj = projs[i]
            if (proj.x !in cullMinX..cullMaxX || proj.y !in cullMinY..cullMaxY) continue
            val pColor = Color(proj.colorHex)
            drawOval(
                color = Color.Black.copy(alpha = 0.45f),
                topLeft = Offset(proj.x - proj.radius, proj.y + 4f),
                size = Size(proj.radius * 2f, proj.radius * 1.1f)
            )
            drawCircle(
                color = pColor.copy(alpha = 0.35f),
                radius = proj.radius * 1.8f,
                center = Offset(proj.x, proj.y - 12f)
            )
            drawCircle(
                color = pColor,
                radius = proj.radius,
                center = Offset(proj.x, proj.y - 12f)
            )
            drawCircle(
                color = Color.White,
                radius = proj.radius * 0.45f,
                center = Offset(proj.x, proj.y - 12f)
            )
        }

        // 6. Render High-Velocity Weapon Tracers (Elevated at Gun Barrel Height y - 14f)
        val tracers = state.tracers
        for (i in 0 until tracers.size) {
            val tr = tracers[i]
            val c = Color(tr.colorHex)
            drawLine(
                color = c.copy(alpha = 0.35f),
                start = Offset(tr.startX, tr.startY - 14f),
                end = Offset(tr.endX, tr.endY - 14f),
                strokeWidth = 7f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = c,
                start = Offset(tr.startX, tr.startY - 14f),
                end = Offset(tr.endX, tr.endY - 14f),
                strokeWidth = 2.8f,
                cap = StrokeCap.Round
            )
        }

        // 7. Render Particles
        val particles = state.particles
        for (i in 0 until particles.size) {
            val pt = particles[i]
            if (pt.x !in cullMinX..cullMaxX || pt.y !in cullMinY..cullMaxY) continue
            drawCircle(
                color = Color(pt.colorHex),
                radius = 4.2f,
                center = Offset(pt.x, pt.y - 10f)
            )
        }

        // 7b. Pass 4: Render Translucent Overlapping Bio-Noir Canopy Overhangs over actors walking behind tall trees
        val overhangs = renderGraphFrame.canopyOverhangNodes
        for (i in 0 until overhangs.size) {
            val node = overhangs[i]
            if (hypot(node.x - px, node.y - py) <= node.radius * 2.4f) {
                drawCanopyOverhangSprite(node.x, node.y, node.radius)
            }
        }

        // 8. Fast Zero-Allocation Chiaroscuro Lighting & 2-Shadow Pass
        renderFastChiaroscuroPass(
            state = state,
            lights = frameLights,
            shadowHulls = shadowHulls
        )
    }

    private fun DrawScope.drawOrthographicRoom(
        room: MapRoom,
        unlocked: Boolean,
        unlockedRooms: Set<Int>,
        cullMinX: Float,
        cullMaxX: Float,
        cullMinY: Float,
        cullMaxY: Float
    ) {
        val b = room.bounds
        val idx = room.index
        val baseFloor = if (unlocked) Color(room.floorColorHex) else Color(0xFF070B09)

        // Check which neighboring city districts are unlocked so shared boundaries become open avenues!
        val openLeft = unlocked && (
            (idx == 1 && 0 in unlockedRooms) || (idx == 4 && 1 in unlockedRooms) ||
                (idx == 3 && 2 in unlockedRooms) || (idx == 5 && 3 in unlockedRooms)
            )
        val openRight = unlocked && (
            (idx == 0 && 1 in unlockedRooms) || (idx == 1 && 4 in unlockedRooms) ||
                (idx == 2 && 3 in unlockedRooms) || (idx == 3 && 5 in unlockedRooms)
            )
        val openTop = unlocked && (
            (idx == 0 && 2 in unlockedRooms) || (idx == 1 && 3 in unlockedRooms) ||
                (idx == 4 && 5 in unlockedRooms)
            )
        val openBottom = unlocked && (
            (idx == 2 && 0 in unlockedRooms) || (idx == 3 && 1 in unlockedRooms) ||
                (idx == 5 && 4 in unlockedRooms)
            )

        // 1. Main Urban District Pavement Deck
        drawRect(
            color = baseFloor,
            topLeft = b.topLeft,
            size = b.size
        )

        if (unlocked) {
            val biome = room.biome
            val cx = b.center.x
            val cy = b.center.y
            val roadWidth = 152f
            val asphaltColor = Color(biome.roadColorHex)
            val curbColor = Color(biome.secondaryColorHex)
            val biomeAccent = Color(biome.accentColorHex)
            val laneYellow = Color(0xFFE5B842).copy(alpha = 0.65f)

            // Horizontal & Vertical City Boulevards across district
            drawRect(
                color = asphaltColor,
                topLeft = Offset(b.left, cy - roadWidth / 2f),
                size = Size(b.width, roadWidth)
            )
            drawRect(
                color = asphaltColor,
                topLeft = Offset(cx - roadWidth / 2f, b.top),
                size = Size(roadWidth, b.height)
            )

            // Biome-specific surface details along the avenues
            when (biome) {
                DistrictBiome.INDUSTRIAL_RAILYARD -> {
                    // Dual steel railway tracks + wooden sleepers running horizontally across the railyard
                    val trackY1 = cy - 32f
                    val trackY2 = cy + 32f
                    var tieX = b.left + 16f
                    while (tieX < b.right - 16f) {
                        if (tieX in cullMinX..cullMaxX) {
                            drawLine(
                                color = Color(0xFF3E2723),
                                start = Offset(tieX, trackY1 - 14f),
                                end = Offset(tieX, trackY1 + 14f),
                                strokeWidth = 5f
                            )
                            drawLine(
                                color = Color(0xFF3E2723),
                                start = Offset(tieX, trackY2 - 14f),
                                end = Offset(tieX, trackY2 + 14f),
                                strokeWidth = 5f
                            )
                        }
                        tieX += 26f
                    }
                    drawLine(color = Color(0xFF90A4AE), start = Offset(b.left, trackY1 - 8f), end = Offset(b.right, trackY1 - 8f), strokeWidth = 2.4f)
                    drawLine(color = Color(0xFF90A4AE), start = Offset(b.left, trackY1 + 8f), end = Offset(b.right, trackY1 + 8f), strokeWidth = 2.4f)
                    drawLine(color = Color(0xFF90A4AE), start = Offset(b.left, trackY2 - 8f), end = Offset(b.right, trackY2 - 8f), strokeWidth = 2.4f)
                    drawLine(color = Color(0xFF90A4AE), start = Offset(b.left, trackY2 + 8f), end = Offset(b.right, trackY2 + 8f), strokeWidth = 2.4f)
                }
                DistrictBiome.OVERGROWTH_CRATER_PARK -> {
                    // Bioluminescent overgrown park turf strips along avenue shoulders
                    val turfColor = Color(0xFF1E3F29).copy(alpha = 0.65f)
                    drawRect(color = turfColor, topLeft = Offset(b.left + 40f, cy - roadWidth * 0.72f), size = Size(b.width - 80f, 18f))
                    drawRect(color = turfColor, topLeft = Offset(b.left + 40f, cy + roadWidth * 0.72f - 18f), size = Size(b.width - 80f, 18f))
                }
                DistrictBiome.CRYO_HARBOR_PIER -> {
                    // Glowing cryo-coolant pipes along the harbor deck
                    val pipeColor = CryoCyan.copy(alpha = 0.38f)
                    drawLine(color = pipeColor, start = Offset(b.left, cy - roadWidth * 0.56f), end = Offset(b.right, cy - roadWidth * 0.56f), strokeWidth = 4.5f)
                    drawLine(color = pipeColor, start = Offset(b.left, cy + roadWidth * 0.56f), end = Offset(b.right, cy + roadWidth * 0.56f), strokeWidth = 4.5f)
                }
                else -> Unit
            }

            // Sidewalk Curbs along the avenues
            drawLine(color = curbColor, start = Offset(b.left, cy - roadWidth / 2f), end = Offset(b.right, cy - roadWidth / 2f), strokeWidth = 2.5f)
            drawLine(color = curbColor, start = Offset(b.left, cy + roadWidth / 2f), end = Offset(b.right, cy + roadWidth / 2f), strokeWidth = 2.5f)
            drawLine(color = curbColor, start = Offset(cx - roadWidth / 2f, b.top), end = Offset(cx - roadWidth / 2f, b.bottom), strokeWidth = 2.5f)
            drawLine(color = curbColor, start = Offset(cx + roadWidth / 2f, b.top), end = Offset(cx + roadWidth / 2f, b.bottom), strokeWidth = 2.5f)

            // Dashed Yellow Boulevard Centerlines
            var dashX = b.left + 24f
            while (dashX < b.right - 24f) {
                if (kotlin.math.abs(dashX - cx) > roadWidth * 0.55f && dashX in cullMinX..cullMaxX) {
                    drawLine(color = laneYellow, start = Offset(dashX, cy), end = Offset(dashX + 34f, cy), strokeWidth = 3f)
                }
                dashX += 68f
            }
            var dashY = b.top + 24f
            while (dashY < b.bottom - 24f) {
                if (kotlin.math.abs(dashY - cy) > roadWidth * 0.55f && dashY in cullMinY..cullMaxY) {
                    drawLine(color = laneYellow, start = Offset(cx, dashY), end = Offset(cx, dashY + 34f), strokeWidth = 3f)
                }
                dashY += 68f
            }

            // White Pedestrian Crosswalk Stripes at the Central Plaza Intersection
            val stripeColor = Color.White.copy(alpha = 0.22f)
            for (s in -2..2) {
                val offsetS = s * 18f
                drawRect(color = stripeColor, topLeft = Offset(cx - roadWidth * 0.65f, cy + offsetS - 4f), size = Size(22f, 8f))
                drawRect(color = stripeColor, topLeft = Offset(cx + roadWidth * 0.65f - 22f, cy + offsetS - 4f), size = Size(22f, 8f))
                drawRect(color = stripeColor, topLeft = Offset(cx + offsetS - 4f, cy - roadWidth * 0.65f), size = Size(8f, 22f))
                drawRect(color = stripeColor, topLeft = Offset(cx + offsetS - 4f, cy + roadWidth * 0.65f - 22f), size = Size(8f, 22f))
            }

            // Subtle Urban Sidewalk Paving Grid (150x120 blocks, frustum-clamped)
            val tileW = 150f
            val tileH = 120f
            val gridColor = biomeAccent.copy(alpha = 0.045f)
            val topClamped = max(b.top, cullMinY)
            val bottomClamped = min(b.bottom, cullMaxY)
            val leftClamped = max(b.left, cullMinX)
            val rightClamped = min(b.right, cullMaxX)

            var gx = b.left + tileW
            while (gx < b.right) {
                if (gx in cullMinX..cullMaxX) {
                    drawLine(color = gridColor, start = Offset(gx, topClamped), end = Offset(gx, bottomClamped), strokeWidth = 1.1f)
                }
                gx += tileW
            }
            var gy = b.top + tileH
            while (gy < b.bottom) {
                if (gy in cullMinY..cullMaxY) {
                    drawLine(color = gridColor, start = Offset(leftClamped, gy), end = Offset(rightClamped, gy), strokeWidth = 1.1f)
                }
                gy += tileH
            }

            // Glowing Biome-Colored Streetlamps at the 4 corners of the central plaza intersection
            val lampOffset = roadWidth * 0.64f
            val corners = listOf(
                Offset(cx - lampOffset, cy - lampOffset),
                Offset(cx + lampOffset, cy - lampOffset),
                Offset(cx - lampOffset, cy + lampOffset),
                Offset(cx + lampOffset, cy + lampOffset)
            )
            corners.forEach { lc ->
                if (lc.x in cullMinX..cullMaxX && lc.y in cullMinY..cullMaxY) {
                    drawCircle(color = biomeAccent.copy(alpha = 0.10f), radius = 52f, center = lc)
                    drawLine(color = Color(0xFF455A64), start = lc, end = Offset(lc.x, lc.y - 26f), strokeWidth = 3f)
                    drawCircle(color = biomeAccent, radius = 4.8f, center = Offset(lc.x, lc.y - 26f))
                }
            }
        }

        // Only draw a dashed laser barricade line along interior edges that border a LOCKED district
        // (Outer map edges use organic 3D JaggedPerimeterOutcrops instead of straight rectangular lines!)
        val lockedBorderColor = ZombieCrimson.copy(alpha = 0.65f)
        val isInteriorTop = idx in setOf(0, 1, 4)
        val isInteriorBottom = idx in setOf(2, 3, 5)
        val isInteriorLeft = idx in setOf(1, 4, 3, 5)
        val isInteriorRight = idx in setOf(0, 1, 2, 3)

        if (!openTop && isInteriorTop) {
            drawLine(color = lockedBorderColor, start = Offset(b.left, b.top), end = Offset(b.right, b.top), strokeWidth = 4f)
        }
        if (!openBottom && isInteriorBottom) {
            drawLine(color = lockedBorderColor, start = Offset(b.left, b.bottom), end = Offset(b.right, b.bottom), strokeWidth = 4f)
        }
        if (!openLeft && isInteriorLeft) {
            drawLine(color = lockedBorderColor, start = Offset(b.left, b.top), end = Offset(b.left, b.bottom), strokeWidth = 4f)
        }
        if (!openRight && isInteriorRight) {
            drawLine(color = lockedBorderColor, start = Offset(b.right, b.top), end = Offset(b.right, b.bottom), strokeWidth = 4f)
        }
    }

    /**
     * Renders a 3D Jagged Perimeter Outcropping (collapsed building wing, jagged cliff rock,
     * angled concrete bastion, or ruined highway slab) with stepped roof teeth so map borders
     * look organic, jagged, and war-torn rather than straight lines.
     */
    private fun DrawScope.drawJaggedPerimeterOutcrop3D(outcrop: JaggedOutcrop, isRoomUnlocked: Boolean) {
        val b = outcrop.bounds
        val zH = outcrop.heightZ
        val facadeBase = if (isRoomUnlocked) Color(outcrop.primaryColorHex) else Color(0xFF0E1412)
        val roofBase = if (isRoomUnlocked) Color(outcrop.roofColorHex) else Color(0xFF151F1B)
        val rimHighlight = if (isRoomUnlocked) PhosphorGreen.copy(alpha = 0.38f) else ZombieCrimson.copy(alpha = 0.35f)

        // 1. Ground Ambient Shadow
        drawRect(
            color = Color.Black.copy(alpha = 0.45f),
            topLeft = Offset(b.left + 6f, b.top + 8f),
            size = Size(b.width, b.height)
        )

        // 2. Main 3D Extruded Bastion Body + Stepped Jagged Crag / Broken Roof Notch
        val notchW = b.width * (0.38f + outcrop.notchOffsetRatio * 0.28f)
        val notchH = b.height * (0.42f + (1f - outcrop.notchOffsetRatio) * 0.25f)

        // Front vertical cliff/ruin facade
        drawRect(
            color = facadeBase,
            topLeft = Offset(b.left, b.top - zH),
            size = Size(b.width, b.height + zH)
        )

        // Jagged polygon roof cap using zero-allocation scratchPath
        scratchPath.reset()
        val rTop = b.top - zH
        val rBot = b.bottom - zH
        val dentX = b.left + b.width * (0.25f + outcrop.notchOffsetRatio * 0.5f)
        val dentY = rTop + (rBot - rTop) * 0.28f
        scratchPath.moveTo(b.left, rTop + 6f)
        scratchPath.lineTo(dentX, rTop)
        scratchPath.lineTo(b.right - 5f, rTop + 8f)
        scratchPath.lineTo(b.right, rTop + (rBot - rTop) * 0.55f)
        scratchPath.lineTo(b.right - 8f, rBot)
        scratchPath.lineTo(dentX, rBot - 5f)
        scratchPath.lineTo(b.left + 6f, rBot)
        scratchPath.lineTo(b.left, dentY)
        scratchPath.close()

        drawPath(path = scratchPath, color = roofBase)
        drawPath(path = scratchPath, color = rimHighlight, style = Stroke(width = 2f))

        // Secondary raised crag / ruined turret block on top of the bastion
        drawRect(
            color = facadeBase.copy(alpha = 0.9f),
            topLeft = Offset(b.left + b.width * 0.18f, rTop - 10f),
            size = Size(notchW, notchH)
        )
    }

    /**
     * Renders district-specific ground landmarks and urban props (Rotary Fountain, Blast Crater,
     * Cryo-Coolant Vat, Grand Bazaar Skylight Dome, Railway Turntable, Void Altar, Transit Cars).
     */
    private fun DrawScope.drawUrbanLandmark(lm: UrbanLandmark) {
        val cx = lm.x
        val cy = lm.y
        val rx = lm.radiusX
        val ry = lm.radiusY * ORTHO_Y_SCALE
        when (lm.kind) {
            UrbanLandmarkKind.ROTARY_PLAZA_FOUNTAIN -> {
                drawOval(color = Color(0xFF263842), topLeft = Offset(cx - rx, cy - ry), size = Size(rx * 2f, ry * 2f))
                drawOval(color = CryoCyan.copy(alpha = 0.28f), topLeft = Offset(cx - rx * 0.8f, cy - ry * 0.8f), size = Size(rx * 1.6f, ry * 1.6f))
                drawOval(color = CryoCyan.copy(alpha = 0.65f), topLeft = Offset(cx - rx * 0.45f, cy - ry * 0.45f), size = Size(rx * 0.9f, ry * 0.9f), style = Stroke(width = 2.2f))
                drawCircle(color = Color(0xFF455A64), radius = 12f, center = Offset(cx, cy - 6f))
                drawCircle(color = CryoCyan, radius = 6f, center = Offset(cx, cy - 12f))
            }
            UrbanLandmarkKind.EMBER_BLAST_CRATER -> {
                drawOval(color = Color(0xFF0B0E0C), topLeft = Offset(cx - rx, cy - ry), size = Size(rx * 2f, ry * 2f))
                drawOval(color = Color(0xFFFF5252).copy(alpha = 0.32f), topLeft = Offset(cx - rx * 0.65f, cy - ry * 0.65f), size = Size(rx * 1.3f, ry * 1.3f))
                drawOval(color = Color(0xFFFFAB00).copy(alpha = 0.75f), topLeft = Offset(cx - rx * 0.35f, cy - ry * 0.35f), size = Size(rx * 0.7f, ry * 0.7f))
                drawOval(color = PhosphorGreen.copy(alpha = 0.4f), topLeft = Offset(cx - rx, cy - ry), size = Size(rx * 2f, ry * 2f), style = Stroke(width = 3f))
            }
            UrbanLandmarkKind.CRYO_COOLANT_VAT -> {
                drawOval(color = Color(0xFF102838), topLeft = Offset(cx - rx, cy - ry), size = Size(rx * 2f, ry * 2f))
                drawOval(color = CryoCyan.copy(alpha = 0.42f), topLeft = Offset(cx - rx * 0.75f, cy - ry * 0.75f), size = Size(rx * 1.5f, ry * 1.5f))
                drawLine(color = CryoCyan, start = Offset(cx - rx * 0.8f, cy), end = Offset(cx + rx * 0.8f, cy), strokeWidth = 3f)
                drawLine(color = CryoCyan, start = Offset(cx, cy - ry * 0.8f), end = Offset(cx, cy + ry * 0.8f), strokeWidth = 3f)
            }
            UrbanLandmarkKind.BAZAAR_SKYLIGHT_DOME -> {
                drawOval(color = MysteryGold.copy(alpha = 0.14f), topLeft = Offset(cx - rx, cy - ry), size = Size(rx * 2f, ry * 2f))
                drawOval(color = MysteryGold.copy(alpha = 0.65f), topLeft = Offset(cx - rx, cy - ry), size = Size(rx * 2f, ry * 2f), style = Stroke(width = 2.5f))
                drawOval(color = PackPurple.copy(alpha = 0.45f), topLeft = Offset(cx - rx * 0.55f, cy - ry * 0.55f), size = Size(rx * 1.1f, ry * 1.1f), style = Stroke(width = 2f))
            }
            UrbanLandmarkKind.RAILWAY_TURNTABLE -> {
                drawOval(color = Color(0xFF1E1916), topLeft = Offset(cx - rx, cy - ry), size = Size(rx * 2f, ry * 2f))
                drawOval(color = Color(0xFFFF7B00).copy(alpha = 0.55f), topLeft = Offset(cx - rx, cy - ry), size = Size(rx * 2f, ry * 2f), style = Stroke(width = 3.5f))
                drawLine(color = Color(0xFF90A4AE), start = Offset(cx - rx * 0.85f, cy - 8f), end = Offset(cx + rx * 0.85f, cy - 8f), strokeWidth = 3f)
                drawLine(color = Color(0xFF90A4AE), start = Offset(cx - rx * 0.85f, cy + 8f), end = Offset(cx + rx * 0.85f, cy + 8f), strokeWidth = 3f)
            }
            UrbanLandmarkKind.OBSIDIAN_VOID_ALTAR -> {
                drawOval(color = PackPurple.copy(alpha = 0.22f), topLeft = Offset(cx - rx, cy - ry), size = Size(rx * 2f, ry * 2f))
                drawOval(color = PackPurple, topLeft = Offset(cx - rx, cy - ry), size = Size(rx * 2f, ry * 2f), style = Stroke(width = 2.8f))
                drawOval(color = ZombieCrimson.copy(alpha = 0.7f), topLeft = Offset(cx - rx * 0.6f, cy - ry * 0.6f), size = Size(rx * 1.2f, ry * 1.2f), style = Stroke(width = 2f))
            }
            UrbanLandmarkKind.ABANDONED_TRANSIT_CAR -> {
                rotate(degrees = lm.rotationDeg, pivot = Offset(cx, cy)) {
                    drawRoundRect(
                        color = Color.Black.copy(alpha = 0.4f),
                        topLeft = Offset(cx - rx + 4f, cy - ry + 5f),
                        size = Size(rx * 2f, ry * 2f),
                        cornerRadius = CornerRadius(8f, 8f)
                    )
                    drawRoundRect(
                        color = Color(0xFF283740),
                        topLeft = Offset(cx - rx, cy - ry - 8f),
                        size = Size(rx * 2f, ry * 2f),
                        cornerRadius = CornerRadius(8f, 8f)
                    )
                    drawRoundRect(
                        color = CryoCyan.copy(alpha = 0.35f),
                        topLeft = Offset(cx - rx * 0.65f, cy - ry * 0.65f - 8f),
                        size = Size(rx * 1.3f, ry * 1.1f),
                        cornerRadius = CornerRadius(4f, 4f)
                    )
                }
            }
            UrbanLandmarkKind.OVERGROWN_MEDIAN_GROVE -> {
                drawOval(color = Color(0xFF142E1F), topLeft = Offset(cx - rx, cy - ry), size = Size(rx * 2f, ry * 2f))
                drawCircle(color = PhosphorGreen.copy(alpha = 0.28f), radius = rx * 0.45f, center = Offset(cx - 12f, cy - 6f))
                drawCircle(color = PhosphorGreen.copy(alpha = 0.35f), radius = rx * 0.38f, center = Offset(cx + 14f, cy + 4f))
            }
        }
    }

    /**
     * Renders a 3/4 Orthographic 3D Urban Building / Armored City Structure with:
     * - Ground drop shadow
     * - Front vertical architectural facade with illuminated city windows
     * - Elevated rooftop slab with HVAC vent & neon trim
     */
    private fun DrawScope.drawOrthographicPillar3D(pillar: Rect) {
        val zH = PILLAR_Z_HEIGHT + 8f

        // 1. Ground Ambient Occlusion Shadow
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.52f),
            topLeft = Offset(pillar.left - 5f, pillar.top + 6f),
            size = Size(pillar.width + 12f, pillar.height + 10f),
            cornerRadius = CornerRadius(6f, 6f)
        )

        // 2. Front Vertical Extruded Building Facade
        val facadeTop = pillar.top - zH
        val facadeHeight = pillar.height + zH
        drawRoundRect(
            color = Color(0xFF1C2830),
            topLeft = Offset(pillar.left, facadeTop),
            size = Size(pillar.width, facadeHeight),
            cornerRadius = CornerRadius(4f, 4f)
        )

        // Ground-floor dark storefront base
        drawRect(
            color = Color(0xFF121A20),
            topLeft = Offset(pillar.left, pillar.bottom - 18f),
            size = Size(pillar.width, 18f)
        )

        // Illuminated City Building Windows on the front facade
        val winY = pillar.bottom - 30f
        var wx = pillar.left + 8f
        while (wx + 8f <= pillar.right - 6f) {
            drawRect(
                color = CryoCyan.copy(alpha = 0.65f),
                topLeft = Offset(wx, winY),
                size = Size(7f, 9f)
            )
            wx += 13f
        }

        // 3. Elevated Top Rooftop Slab (shifted up by -zH)
        val roofRect = Rect(pillar.left, pillar.top - zH, pillar.right, pillar.bottom - zH)
        drawRoundRect(
            color = Color(0xFF2B3B45),
            topLeft = roofRect.topLeft,
            size = roofRect.size,
            cornerRadius = CornerRadius(4f, 4f)
        )
        // Inner recessed rooftop gravel/HVAC deck
        drawRect(
            color = Color(0xFF1F2C34),
            topLeft = Offset(roofRect.left + 5f, roofRect.top + 5f),
            size = Size((roofRect.width - 10f).coerceAtLeast(4f), (roofRect.height - 10f).coerceAtLeast(4f))
        )
        // Crisp 3D roof rim outline
        drawRoundRect(
            color = CryoCyan.copy(alpha = 0.55f),
            topLeft = roofRect.topLeft,
            size = roofRect.size,
            cornerRadius = CornerRadius(4f, 4f),
            style = Stroke(width = 1.8f)
        )
    }

    /**
     * Renders a scenic Town Lake / Lagoon / Quarry Pond with sandy shoreline, shallow water shelf,
     * deep water core, animated ripples, lilypads, and a walkable 3D wooden plank footbridge.
     */
    private fun DrawScope.drawOrthographicLake(lake: MapLake, animRad: Float) {
        val cx = lake.x
        val cy = lake.y
        val rx = lake.radiusX
        val ry = lake.radiusY
        val shoreColor = Color(lake.style.shoreColorHex)
        val shallowColor = Color(lake.style.shallowColorHex)
        val deepColor = Color(lake.style.deepColorHex)

        // 0. Dark Bio-Noir Overlapping Lake Atlas Stamp (P.S. 2)
        drawBioNoirLakeSprite(cx, cy, rx, ry)

        // 1. Sandy / Pebble Shoreline Beach
        drawOval(
            color = shoreColor.copy(alpha = 0.65f),
            topLeft = Offset(cx - rx * 1.10f, cy - ry * 1.10f),
            size = Size(rx * 2.20f, ry * 2.20f)
        )

        // 2. Shallow Turquoise Water Rim
        drawOval(
            color = shallowColor,
            topLeft = Offset(cx - rx, cy - ry),
            size = Size(rx * 2f, ry * 2f)
        )

        // 3. Deep Water Core
        drawOval(
            color = deepColor,
            topLeft = Offset(cx - rx * 0.76f, cy - ry * 0.76f),
            size = Size(rx * 1.52f, ry * 1.52f)
        )

        // 4. Shimmering Water Ripples & Lilypads
        val rippleShift = sin(animRad * 0.7f + lake.roomIndex) * 6f
        for (r in -1..1) {
            val ryOff = r * (ry * 0.36f)
            val halfSpan = rx * (0.48f - kotlin.math.abs(r) * 0.12f)
            drawLine(
                color = Color.White.copy(alpha = 0.22f),
                start = Offset(cx - halfSpan + rippleShift, cy + ryOff),
                end = Offset(cx + halfSpan + rippleShift, cy + ryOff),
                strokeWidth = 2.2f,
                cap = StrokeCap.Round
            )
        }
        // Floating green lilypads near the shoreline
        drawCircle(color = Color(0xFF2E7D32), radius = 7.5f, center = Offset(cx - rx * 0.58f, cy - ry * 0.42f))
        drawCircle(color = Color(0xFF43A047), radius = 6.2f, center = Offset(cx + rx * 0.54f, cy + ry * 0.46f))
        drawCircle(color = Color(0xFFF48FB1), radius = 2.6f, center = Offset(cx - rx * 0.58f, cy - ry * 0.42f))

        // 5. Walkable 3D Wooden Plank Footbridge Across the Lake
        val bridgeHalfWidth = 24f
        val woodDark = Color(0xFF3E2723)
        val woodPlank = Color(0xFF6D4C41)
        val woodRail = Color(0xFF8D6E63)
        if (lake.bridgeHorizontal) {
            val bLeft = cx - rx * 1.08f
            val bRight = cx + rx * 1.08f
            val bWidth = bRight - bLeft
            // Drop shadow on water
            drawRect(
                color = Color.Black.copy(alpha = 0.42f),
                topLeft = Offset(bLeft, cy - bridgeHalfWidth + 6f),
                size = Size(bWidth, bridgeHalfWidth * 2f)
            )
            // Bridge side beams
            drawRect(
                color = woodDark,
                topLeft = Offset(bLeft, cy - bridgeHalfWidth),
                size = Size(bWidth, bridgeHalfWidth * 2f)
            )
            // Individual wooden planks
            var px = bLeft + 6f
            while (px < bRight - 6f) {
                drawRect(
                    color = woodPlank,
                    topLeft = Offset(px, cy - bridgeHalfWidth + 3f),
                    size = Size(9f, bridgeHalfWidth * 2f - 6f)
                )
                px += 13f
            }
            // Elevated 3D wooden handrails & posts
            drawLine(color = woodRail, start = Offset(bLeft, cy - bridgeHalfWidth - 6f), end = Offset(bRight, cy - bridgeHalfWidth - 6f), strokeWidth = 3.2f)
            drawLine(color = woodRail, start = Offset(bLeft, cy + bridgeHalfWidth - 6f), end = Offset(bRight, cy + bridgeHalfWidth - 6f), strokeWidth = 3.2f)
        } else {
            val bTop = cy - ry * 1.08f
            val bBot = cy + ry * 1.08f
            val bHeight = bBot - bTop
            drawRect(
                color = Color.Black.copy(alpha = 0.42f),
                topLeft = Offset(cx - bridgeHalfWidth + 6f, bTop),
                size = Size(bridgeHalfWidth * 2f, bHeight)
            )
            drawRect(
                color = woodDark,
                topLeft = Offset(cx - bridgeHalfWidth, bTop),
                size = Size(bridgeHalfWidth * 2f, bHeight)
            )
            var py = bTop + 6f
            while (py < bBot - 6f) {
                drawRect(
                    color = woodPlank,
                    topLeft = Offset(cx - bridgeHalfWidth + 3f, py),
                    size = Size(bridgeHalfWidth * 2f - 6f, 9f)
                )
                py += 13f
            }
            drawLine(color = woodRail, start = Offset(cx - bridgeHalfWidth, bTop - 6f), end = Offset(cx - bridgeHalfWidth, bBot - 6f), strokeWidth = 3.2f)
            drawLine(color = woodRail, start = Offset(cx + bridgeHalfWidth, bTop - 6f), end = Offset(cx + bridgeHalfWidth, bBot - 6f), strokeWidth = 3.2f)
        }
    }

    /**
     * Renders ground-level shrubs, flowering town hedgerows, and shoreline reeds/ferns.
     */
    private fun DrawScope.drawGroundShrubOrReed(fol: WorldFoliage) {
        val x = fol.x
        val y = fol.y
        val r = fol.radius
        val baseColor = Color(fol.primaryColorHex)
        val hiColor = Color(fol.highlightColorHex)

        // Ground shadow
        drawOval(
            color = Color.Black.copy(alpha = 0.38f),
            topLeft = Offset(x - r * 1.05f, y - r * 0.55f + 4f),
            size = Size(r * 2.1f, r * 1.25f)
        )

        when (fol.kind) {
            FoliageKind.SHORELINE_REEDS_FERN -> {
                // Lakeside cattails & fern blades
                drawCircle(color = baseColor, radius = r * 0.72f, center = Offset(x, y - 4f))
                for (b in -2..2) {
                    val bx = x + b * 4.5f
                    drawLine(
                        color = hiColor,
                        start = Offset(bx, y),
                        end = Offset(bx + b * 2.2f, y - r * 1.25f),
                        strokeWidth = 2.4f,
                        cap = StrokeCap.Round
                    )
                }
                // Cattail brown seed heads
                drawCircle(color = Color(0xFF5D4037), radius = 2.8f, center = Offset(x - 5f, y - r * 1.15f))
                drawCircle(color = Color(0xFF5D4037), radius = 2.8f, center = Offset(x + 5f, y - r * 1.10f))
            }
            FoliageKind.FLOWERING_HEDGEROW -> {
                // Sculpted town hedgerow bush with blossoms
                drawRoundRect(
                    color = baseColor,
                    topLeft = Offset(x - r * 1.15f, y - r * 0.75f - 6f),
                    size = Size(r * 2.3f, r * 1.4f),
                    cornerRadius = CornerRadius(r * 0.55f, r * 0.55f)
                )
                drawRoundRect(
                    color = hiColor.copy(alpha = 0.55f),
                    topLeft = Offset(x - r * 0.95f, y - r * 0.65f - 9f),
                    size = Size(r * 1.9f, r * 0.85f),
                    cornerRadius = CornerRadius(r * 0.45f, r * 0.45f)
                )
                // Blossom dots
                drawCircle(color = hiColor, radius = 3.2f, center = Offset(x - r * 0.48f, y - 8f))
                drawCircle(color = hiColor, radius = 3.2f, center = Offset(x + r * 0.42f, y - 10f))
                drawCircle(color = Color.White.copy(alpha = 0.85f), radius = 2.5f, center = Offset(x + 2f, y - 5f))
            }
            else -> {
                // Multi-clustered Lush Shrub Bush
                drawCircle(color = baseColor, radius = r * 0.85f, center = Offset(x - r * 0.35f, y - 5f))
                drawCircle(color = baseColor, radius = r * 0.85f, center = Offset(x + r * 0.35f, y - 5f))
                drawCircle(color = hiColor.copy(alpha = 0.85f), radius = r * 0.78f, center = Offset(x, y - 10f))
                drawCircle(color = hiColor, radius = r * 0.42f, center = Offset(x - 3f, y - 13f))
            }
        }
    }

    /**
     * Renders a 3/4 Orthographic 3D Tree (`OAK_CANOPY_TREE`, `PINE_EVERGREEN_TREE`,
     * `WEEPING_WILLOW_TREE`, `AUTUMN_MAPLE_TREE`, `DEADWOOD_BIO_TREE`) with ground drop shadow,
     * 3D wooden trunk, and multi-tiered canopy foliage.
     */
    private fun DrawScope.drawTreeFoliage3D(fol: WorldFoliage) {
        val x = fol.x
        val y = fol.y
        val r = fol.radius
        val zH = fol.heightZ
        val canopyBase = Color(fol.primaryColorHex)
        val canopyHi = Color(fol.highlightColorHex)
        val trunkColor = if (fol.kind == FoliageKind.WEEPING_WILLOW_TREE) Color(0xFF4E342E) else Color(0xFF5D4037)

        // 0. Dark Bio-Noir Overlapping Foliage Sprite Atlas Underlay (P.S. 2)
        drawOverlappingFoliageSprite(fol, x, y, scale = 1.0f, swayX = 0f, isMidLod = false)

        // 1. Elliptical Ground Canopy Shadow
        drawOval(
            color = Color.Black.copy(alpha = 0.46f),
            topLeft = Offset(x - r * 0.95f + 5f, y - r * 0.45f + 4f),
            size = Size(r * 1.9f, r * 1.05f)
        )

        // 2. 3D Wooden Tree Trunk & Root Flare
        val trunkW = (r * 0.34f).coerceIn(8f, 16f)
        val trunkTopY = y - zH * 0.58f
        drawRoundRect(
            color = trunkColor,
            topLeft = Offset(x - trunkW / 2f, trunkTopY),
            size = Size(trunkW, y - trunkTopY + 4f),
            cornerRadius = CornerRadius(3f, 3f)
        )
        drawRect(
            color = Color(0xFF3E2723),
            topLeft = Offset(x, trunkTopY),
            size = Size(trunkW * 0.45f, y - trunkTopY + 4f)
        )

        // 3. Species-Specific 3D Canopy
        when (fol.kind) {
            FoliageKind.PINE_EVERGREEN_TREE -> {
                // 3-Tiered Alpine Conifer / Redwood Pyramid
                for (tier in 0..2) {
                    val tierScale = 1.0f - tier * 0.24f
                    val tierW = r * 1.15f * tierScale
                    val tierBaseY = y - 16f - tier * (zH * 0.28f)
                    val tierPeakY = tierBaseY - zH * 0.46f
                    scratchPath.reset()
                    scratchPath.moveTo(x, tierPeakY)
                    scratchPath.lineTo(x + tierW, tierBaseY)
                    scratchPath.lineTo(x, tierBaseY + 6f)
                    scratchPath.lineTo(x - tierW, tierBaseY)
                    scratchPath.close()
                    drawPath(path = scratchPath, color = if (tier == 2) canopyHi else canopyBase)
                }
            }
            FoliageKind.WEEPING_WILLOW_TREE -> {
                // Wide cascading willow dome + hanging fronds
                val cY = y - zH * 0.72f
                drawCircle(color = canopyBase, radius = r * 0.96f, center = Offset(x, cY))
                drawCircle(color = canopyHi.copy(alpha = 0.75f), radius = r * 0.74f, center = Offset(x - 4f, cY - 7f))
                for (f in -3..3) {
                    val fx = x + f * (r * 0.26f)
                    drawLine(
                        color = canopyHi.copy(alpha = 0.82f),
                        start = Offset(fx, cY),
                        end = Offset(fx, y - 8f),
                        strokeWidth = 2.6f,
                        cap = StrokeCap.Round
                    )
                }
            }
            FoliageKind.DEADWOOD_BIO_TREE -> {
                // Gnarled Ashenhollow branches + glowing bioluminescent spore clusters
                val topY = y - zH * 0.65f
                drawLine(color = trunkColor, start = Offset(x, topY + 8f), end = Offset(x - r * 0.65f, topY - 16f), strokeWidth = 4.5f, cap = StrokeCap.Round)
                drawLine(color = trunkColor, start = Offset(x, topY + 4f), end = Offset(x + r * 0.65f, topY - 18f), strokeWidth = 4.5f, cap = StrokeCap.Round)
                drawCircle(color = canopyBase.copy(alpha = 0.85f), radius = r * 0.48f, center = Offset(x - r * 0.55f, topY - 18f))
                drawCircle(color = canopyBase.copy(alpha = 0.85f), radius = r * 0.48f, center = Offset(x + r * 0.55f, topY - 20f))
                drawCircle(color = canopyHi, radius = r * 0.36f, center = Offset(x, topY - 24f))
            }
            else -> {
                // Lush Multi-Lobe Oak / Autumn Maple 3D Canopy
                val cY = y - zH * 0.74f
                drawCircle(color = canopyBase, radius = r * 0.82f, center = Offset(x - r * 0.36f, cY + 4f))
                drawCircle(color = canopyBase, radius = r * 0.82f, center = Offset(x + r * 0.36f, cY + 4f))
                drawCircle(color = canopyBase, radius = r * 0.88f, center = Offset(x, cY - 5f))
                drawCircle(color = canopyHi.copy(alpha = 0.88f), radius = r * 0.64f, center = Offset(x - 4f, cY - 10f))
                drawCircle(color = canopyHi, radius = r * 0.36f, center = Offset(x - 7f, cY - 14f))
            }
        }
    }

    /**
     * PASS 1: Renders an organic rotated biome terrain patch (meadow grass, pine forest floor,
     * cobblestone town square, coastal dunes, gravel ballast, wooden boardwalk, or obsidian crust).
     */
    private fun DrawScope.drawTerrainPatch(patch: TerrainPatch) {
        val cx = patch.x
        val cy = patch.y
        val rx = patch.radiusX
        val ry = patch.radiusY * ORTHO_Y_SCALE
        val fillCol = Color(patch.kind.fillColorHex)
        val rimCol = Color(patch.kind.rimColorHex)
        val detailCol = Color(patch.kind.detailColorHex)

        rotate(degrees = patch.rotationDeg, pivot = Offset(cx, cy)) {
            drawOval(
                color = fillCol.copy(alpha = if (patch.isCrossBorderBlend) 0.72f else 0.85f),
                topLeft = Offset(cx - rx, cy - ry),
                size = Size(rx * 2f, ry * 2f)
            )
            drawOval(
                color = rimCol.copy(alpha = 0.55f),
                topLeft = Offset(cx - rx * 0.84f, cy - ry * 0.84f),
                size = Size(rx * 1.68f, ry * 1.68f)
            )
            // Surface texture accents
            when (patch.kind) {
                TerrainSurfaceKind.WOODEN_HARBOR_BOARDWALK -> {
                    var px = cx - rx * 0.65f
                    while (px < cx + rx * 0.65f) {
                        drawLine(
                            color = detailCol.copy(alpha = 0.45f),
                            start = Offset(px, cy - ry * 0.55f),
                            end = Offset(px, cy + ry * 0.55f),
                            strokeWidth = 2f
                        )
                        px += 18f
                    }
                }
                TerrainSurfaceKind.COBBLESTONE_TOWN_PLAZA -> {
                    drawOval(
                        color = detailCol.copy(alpha = 0.4f),
                        topLeft = Offset(cx - rx * 0.5f, cy - ry * 0.5f),
                        size = Size(rx, ry),
                        style = Stroke(width = 2f)
                    )
                }
                else -> {
                    drawCircle(color = detailCol.copy(alpha = 0.35f), radius = 8f, center = Offset(cx - rx * 0.32f, cy - ry * 0.22f))
                    drawCircle(color = detailCol.copy(alpha = 0.35f), radius = 6f, center = Offset(cx + rx * 0.28f, cy + ry * 0.26f))
                }
            }
        }
    }

    /**
     * PASS 2A: Renders a winding county river / stream segment connecting town lakes,
     * complete with pebble banks, flowing ripples, and a 3D stone highway bridge at road crossings.
     */
    private fun DrawScope.drawRiverSegment(seg: RiverSegment, animRad: Float) {
        val start = Offset(seg.startX, seg.startY)
        val end = Offset(seg.endX, seg.endY)
        val bankCol = Color(seg.bankColorHex)
        val shallowCol = Color(seg.shallowColorHex)
        val deepCol = Color(seg.deepColorHex)

        // 1. Outer Pebble / Mud Riverbank
        drawLine(
            color = bankCol.copy(alpha = 0.75f),
            start = start,
            end = end,
            strokeWidth = seg.width * 1.32f,
            cap = StrokeCap.Round
        )
        // 2. Shallow Water Channel
        drawLine(
            color = shallowCol,
            start = start,
            end = end,
            strokeWidth = seg.width,
            cap = StrokeCap.Round
        )
        // 3. Deep Water Core
        drawLine(
            color = deepCol,
            start = start,
            end = end,
            strokeWidth = seg.width * 0.62f,
            cap = StrokeCap.Round
        )

        // 4. Animated Current Flow Streaks
        val midX = (seg.startX + seg.endX) * 0.5f
        val midY = (seg.startY + seg.endY) * 0.5f
        val pulse = sin(animRad + seg.roomIndex) * 8f
        drawCircle(
            color = Color.White.copy(alpha = 0.22f),
            radius = 5f,
            center = Offset(midX + pulse, midY - pulse * 0.5f)
        )

        // 5. Walkable 3D Stone Highway Bridge at Midpoint
        if (seg.hasStoneBridgeAtMidpoint) {
            val bW = 78f
            val bH = 56f
            // Drop shadow
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.45f),
                topLeft = Offset(midX - bW * 0.5f + 4f, midY - bH * 0.5f + 5f),
                size = Size(bW, bH),
                cornerRadius = CornerRadius(6f, 6f)
            )
            // Stone Bridge Deck
            drawRoundRect(
                color = Color(0xFF37474F),
                topLeft = Offset(midX - bW * 0.5f, midY - bH * 0.5f),
                size = Size(bW, bH),
                cornerRadius = CornerRadius(6f, 6f)
            )
            // Stone Parapet Walls (top & bottom)
            drawRect(
                color = Color(0xFF78909C),
                topLeft = Offset(midX - bW * 0.5f, midY - bH * 0.5f - 5f),
                size = Size(bW, 7f)
            )
            drawRect(
                color = Color(0xFF78909C),
                topLeft = Offset(midX - bW * 0.5f, midY + bH * 0.5f - 5f),
                size = Size(bW, 7f)
            )
        }
    }

    /**
     * PASS 3A: Renders Tier-2 Town Neighborhood Streets & Tier-3 Scenic Dirt Trails.
     */
    private fun DrawScope.drawHierarchicalRoadSegment(road: WorldRoadSegment) {
        val start = Offset(road.startX, road.startY)
        val end = Offset(road.endX, road.endY)
        val curbCol = Color(road.tier.curbColorHex)
        val surfaceCol = Color(road.tier.surfaceColorHex)

        // Outer sidewalk/shoulder curb
        drawLine(
            color = curbCol.copy(alpha = 0.78f),
            start = start,
            end = end,
            strokeWidth = road.width + 8f,
            cap = StrokeCap.Round
        )
        // Main street or dirt trail surface
        drawLine(
            color = surfaceCol,
            start = start,
            end = end,
            strokeWidth = road.width,
            cap = StrokeCap.Round
        )
        // Subtle dashed centerline on Tier-2 Town Streets
        if (road.tier.hasCenterline) {
            drawLine(
                color = Color(0xFFE5B842).copy(alpha = 0.38f),
                start = start,
                end = end,
                strokeWidth = 2.2f,
                cap = StrokeCap.Round
            )
        }
    }

    /**
     * PASS 3B: Renders 3/4 Orthographic 3D Roadside Story & Infrastructure Props:
     * - Highway Direction Signpost
     * - Wooden Ranch Fence
     * - Town Bus Shelter
     * - Abandoned Supply Truck
     */
    private fun DrawScope.drawRoadSideProp3D(prop: RoadSideProp) {
        val x = prop.x
        val y = prop.y
        val accent = Color(prop.accentColorHex)

        when (prop.kind) {
            RoadSidePropKind.HIGHWAY_DIRECTION_SIGNPOST -> {
                // Ground shadow
                drawOval(color = Color.Black.copy(alpha = 0.45f), topLeft = Offset(x - 12f, y - 4f), size = Size(24f, 10f))
                // Steel post
                drawLine(color = Color(0xFF78909C), start = Offset(x, y), end = Offset(x, y - 34f), strokeWidth = 3.5f)
                // Upper highway direction board
                drawRoundRect(
                    color = Color(0xFF1B5E20),
                    topLeft = Offset(x - 24f, y - 36f),
                    size = Size(48f, 11f),
                    cornerRadius = CornerRadius(2f, 2f)
                )
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.85f),
                    topLeft = Offset(x - 24f, y - 36f),
                    size = Size(48f, 11f),
                    cornerRadius = CornerRadius(2f, 2f),
                    style = Stroke(width = 1.2f)
                )
                // Lower town arrow board
                drawRoundRect(
                    color = Color(0xFF4E342E),
                    topLeft = Offset(x - 20f, y - 23f),
                    size = Size(40f, 9f),
                    cornerRadius = CornerRadius(2f, 2f)
                )
            }
            RoadSidePropKind.WOODEN_RANCH_FENCE -> {
                val halfSpan = 34f
                // Shadow
                drawLine(color = Color.Black.copy(alpha = 0.35f), start = Offset(x - halfSpan, y + 3f), end = Offset(x + halfSpan, y + 3f), strokeWidth = 4f)
                // 3 vertical wooden fence posts
                for (p in -1..1) {
                    val px = x + p * halfSpan
                    drawRect(color = Color(0xFF4E342E), topLeft = Offset(px - 2.5f, y - 16f), size = Size(5f, 18f))
                }
                // Top and middle wooden rails
                drawLine(color = Color(0xFF6D4C41), start = Offset(x - halfSpan, y - 13f), end = Offset(x + halfSpan, y - 13f), strokeWidth = 2.8f)
                drawLine(color = Color(0xFF6D4C41), start = Offset(x - halfSpan, y - 6f), end = Offset(x + halfSpan, y - 6f), strokeWidth = 2.8f)
            }
            RoadSidePropKind.TOWN_BUS_SHELTER -> {
                val w = 52f
                val h = 22f
                drawRoundRect(color = Color.Black.copy(alpha = 0.45f), topLeft = Offset(x - w * 0.5f + 3f, y - h * 0.5f + 4f), size = Size(w, h), cornerRadius = CornerRadius(4f, 4f))
                // Glass & steel canopy roof
                drawRoundRect(color = Color(0xFF263238), topLeft = Offset(x - w * 0.5f, y - h * 0.5f - 18f), size = Size(w, h), cornerRadius = CornerRadius(4f, 4f))
                drawRoundRect(color = accent.copy(alpha = 0.45f), topLeft = Offset(x - w * 0.44f, y - h * 0.42f - 18f), size = Size(w * 0.88f, h * 0.84f), cornerRadius = CornerRadius(3f, 3f))
            }
            RoadSidePropKind.ABANDONED_SUPPLY_TRUCK -> {
                rotate(degrees = prop.rotationDeg, pivot = Offset(x, y)) {
                    // Ground shadow
                    drawRoundRect(color = Color.Black.copy(alpha = 0.48f), topLeft = Offset(x - 36f, y - 14f), size = Size(74f, 34f), cornerRadius = CornerRadius(5f, 5f))
                    // Cargo bed
                    drawRoundRect(color = Color(0xFF3E4A3D), topLeft = Offset(x - 38f, y - 26f), size = Size(50f, 32f), cornerRadius = CornerRadius(4f, 4f))
                    // Truck cab
                    drawRoundRect(color = Color(0xFF546E7A), topLeft = Offset(x + 12f, y - 24f), size = Size(22f, 28f), cornerRadius = CornerRadius(4f, 4f))
                    // Windshield
                    drawRect(color = CryoCyan.copy(alpha = 0.55f), topLeft = Offset(x + 15f, y - 21f), size = Size(8f, 22f))
                }
            }
            RoadSidePropKind.VINTAGE_STREETLAMP -> {
                drawCircle(color = accent.copy(alpha = 0.14f), radius = 38f, center = Offset(x, y))
                drawLine(color = Color(0xFF37474F), start = Offset(x, y), end = Offset(x, y - 26f), strokeWidth = 3f)
                drawCircle(color = accent, radius = 4.5f, center = Offset(x, y - 26f))
            }
        }
    }

    /**
     * PASS 4A: Renders the ground Lot Parcel (Cobblestone Patio, Fenced Grass Yard, Timber Deck Porch,
     * Sunvale Terracotta Courtyard, Concrete Apron, or Gothic Cloister) beneath a TownBuilding,
     * plus a front walkway spur connecting the building's front door to the street.
     */
    private fun DrawScope.drawBuildingLotParcel(building: TownBuilding) {
        val lb = building.lotBounds
        val fillCol = Color(building.lotSurface.fillColorHex)
        val borderCol = Color(building.lotSurface.borderColorHex)

        // Lot parcel ground pad
        drawRoundRect(
            color = fillCol,
            topLeft = lb.topLeft,
            size = lb.size,
            cornerRadius = CornerRadius(6f, 6f)
        )

        // Front stone/timber walkway leading from building doorway to lot front gate
        val walkW = 22f
        drawRect(
            color = borderCol.copy(alpha = 0.75f),
            topLeft = Offset(building.bounds.center.x - walkW * 0.5f, building.bounds.bottom - 2f),
            size = Size(walkW, (lb.bottom - building.bounds.bottom + 4f).coerceAtLeast(8f))
        )

        // Low perimeter yard/courtyard fence with an open front walkway gate
        if (building.hasPerimeterFence) {
            val gateLeft = building.bounds.center.x - 16f
            val gateRight = building.bounds.center.x + 16f
            // Top, Left, Right lot fence rails
            drawLine(color = borderCol, start = Offset(lb.left, lb.top), end = Offset(lb.right, lb.top), strokeWidth = 2.6f)
            drawLine(color = borderCol, start = Offset(lb.left, lb.top), end = Offset(lb.left, lb.bottom), strokeWidth = 2.6f)
            drawLine(color = borderCol, start = Offset(lb.right, lb.top), end = Offset(lb.right, lb.bottom), strokeWidth = 2.6f)
            // Bottom lot fence split by the open front gate
            drawLine(color = borderCol, start = Offset(lb.left, lb.bottom), end = Offset(gateLeft, lb.bottom), strokeWidth = 2.8f)
            drawLine(color = borderCol, start = Offset(gateRight, lb.bottom), end = Offset(lb.right, lb.bottom), strokeWidth = 2.8f)
            // Gate posts
            drawCircle(color = Color(building.trimColorHex), radius = 3.2f, center = Offset(gateLeft, lb.bottom))
            drawCircle(color = Color(building.trimColorHex), radius = 3.2f, center = Offset(gateRight, lb.bottom))
        }
    }

    /**
     * PASS 4B: Renders an architecturally distinct 3/4 Orthographic 3D Town Building with style-specific
     * facades, optional L-wing compound extension, pitched/gabled roofs, chimneys, warm glowing windows,
     * and storefront awnings.
     */
    private fun DrawScope.drawTownBuilding3D(building: TownBuilding) {
        val b = building.bounds
        val zH = building.heightZ
        val facadeColor = Color(building.facadeColorHex)
        val roofColor = Color(building.roofColorHex)
        val trimColor = Color(building.trimColorHex)

        // 1. Ground Ambient Shadow
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.52f),
            topLeft = Offset(b.left - 5f, b.top + 7f),
            size = Size(b.width + 14f, b.height + 12f),
            cornerRadius = CornerRadius(6f, 6f)
        )

        // 1b. Optional L-Wing Architectural Side Extension (creates realistic L-shaped & compound buildings)
        if (building.hasLWingExtension) {
            val wingW = b.width * 0.46f
            val wingH = b.height * 0.58f
            val wingZ = zH * 0.78f
            val wingLeft = b.right - wingW + 10f
            val wingTop = b.top - 10f
            drawRoundRect(
                color = facadeColor.copy(alpha = 0.94f),
                topLeft = Offset(wingLeft, wingTop - wingZ),
                size = Size(wingW, wingH + wingZ),
                cornerRadius = CornerRadius(4f, 4f)
            )
            drawRoundRect(
                color = roofColor.copy(alpha = 0.95f),
                topLeft = Offset(wingLeft - 2f, wingTop - wingZ - 2f),
                size = Size(wingW + 4f, wingH + 2f),
                cornerRadius = CornerRadius(4f, 4f)
            )
            drawRoundRect(
                color = trimColor.copy(alpha = 0.55f),
                topLeft = Offset(wingLeft - 2f, wingTop - wingZ - 2f),
                size = Size(wingW + 4f, wingH + 2f),
                cornerRadius = CornerRadius(4f, 4f),
                style = Stroke(width = 1.4f)
            )
        }

        // 2. Extruded 3D Building Facade
        val facadeTop = b.top - zH
        val facadeHeight = b.height + zH
        drawRoundRect(
            color = facadeColor,
            topLeft = Offset(b.left, facadeTop),
            size = Size(b.width, facadeHeight),
            cornerRadius = CornerRadius(4f, 4f)
        )

        // Horizontal timber logs or brick coursework lines on front wall
        if (building.style == TownBuildingStyle.LAKESIDE_TIMBER_CABIN ||
            building.style == TownBuildingStyle.REDWOOD_SAWMILL_DEPOT
        ) {
            var logY = b.bottom - 10f
            while (logY > b.bottom - zH + 6f) {
                drawLine(
                    color = Color.Black.copy(alpha = 0.25f),
                    start = Offset(b.left + 2f, logY),
                    end = Offset(b.right - 2f, logY),
                    strokeWidth = 1.6f
                )
                logY -= 8f
            }
        }

        // Ground-floor foundation wainscoting & wooden/arched doorway
        drawRect(
            color = Color.Black.copy(alpha = 0.28f),
            topLeft = Offset(b.left, b.bottom - 18f),
            size = Size(b.width, 18f)
        )
        val doorW = 14f
        val doorH = 20f
        drawRoundRect(
            color = Color(0xFF2D1B14),
            topLeft = Offset(b.center.x - doorW / 2f, b.bottom - doorH),
            size = Size(doorW, doorH),
            cornerRadius = CornerRadius(3f, 3f)
        )

        // Warm Golden or Cyan Illuminated Town Windows across the front facade
        val windowGlow = when (building.style) {
            TownBuildingStyle.HARBOR_BOATHOUSE -> CryoCyan.copy(alpha = 0.78f)
            TownBuildingStyle.ASHEN_GOTHIC_SANCTUM -> PackPurple.copy(alpha = 0.82f)
            else -> Color(0xFFFFD54F).copy(alpha = 0.85f)
        }
        val winY = b.bottom - 34f
        var wx = b.left + 10f
        while (wx + 10f <= b.right - 8f) {
            if (kotlin.math.abs(wx + 4f - b.center.x) > 10f) {
                drawRoundRect(
                    color = windowGlow,
                    topLeft = Offset(wx, winY),
                    size = Size(9f, 11f),
                    cornerRadius = CornerRadius(2f, 2f)
                )
            }
            wx += 18f
        }

        // Optional Storefront Striped Awning projecting over the front sidewalk
        if (building.hasAwning) {
            val awningTop = b.bottom - 22f
            drawRoundRect(
                color = trimColor,
                topLeft = Offset(b.left + 4f, awningTop),
                size = Size(b.width - 8f, 9f),
                cornerRadius = CornerRadius(3f, 3f)
            )
        }

        // 3. Elevated 3D Roof Deck & Pitched Gable Ridge + Overlapping Bio-Noir Roof Atlas Overlay (P.S. 2)
        val roofRect = Rect(b.left - 3f, b.top - zH - 2f, b.right + 3f, b.bottom - zH + 2f)
        drawRoundRect(
            color = roofColor,
            topLeft = roofRect.topLeft,
            size = roofRect.size,
            cornerRadius = CornerRadius(5f, 5f)
        )
        drawBioNoirRoofOverlay(roofRect.left, roofRect.top, roofRect.width, roofRect.height)

        // Pitched Roof Ridge Beam & Shingle Slopes
        val ridgeY = roofRect.center.y - 4f
        drawRect(
            color = Color.White.copy(alpha = 0.08f),
            topLeft = Offset(roofRect.left + 3f, roofRect.top + 2f),
            size = Size(roofRect.width - 6f, (ridgeY - roofRect.top).coerceAtLeast(4f))
        )
        drawLine(
            color = trimColor.copy(alpha = 0.85f),
            start = Offset(roofRect.left + 4f, ridgeY),
            end = Offset(roofRect.right - 4f, ridgeY),
            strokeWidth = 3.2f,
            cap = StrokeCap.Round
        )
        drawRoundRect(
            color = trimColor.copy(alpha = 0.65f),
            topLeft = roofRect.topLeft,
            size = roofRect.size,
            cornerRadius = CornerRadius(5f, 5f),
            style = Stroke(width = 1.8f)
        )

        // Optional Stone / Brick Chimney on the roof
        if (building.hasChimney) {
            val chimX = roofRect.right - 22f
            val chimY = roofRect.top + 6f
            drawRoundRect(
                color = Color(0xFF4E342E),
                topLeft = Offset(chimX, chimY - 14f),
                size = Size(12f, 18f),
                cornerRadius = CornerRadius(2f, 2f)
            )
            drawRect(
                color = Color(0xFF263238),
                topLeft = Offset(chimX + 2f, chimY - 16f),
                size = Size(8f, 4f)
            )
        }
    }

    /**
     * Renders a 3/4 Orthographic Standing 3D Red Explosive Barrel with cylindrical shading,
     * metallic rib bands, and top hazard cap.
     */
    private fun DrawScope.drawOrthographicBarrel3D(barrel: ExplosiveBarrel) {
        val bx = barrel.x
        val by = barrel.y
        val r = 14f
        val zH = 22f

        // Ground elliptical drop shadow
        drawOval(
            color = Color.Black.copy(alpha = 0.55f),
            topLeft = Offset(bx - r * 1.15f, by - r * 0.45f),
            size = Size(r * 2.3f, r * 1.3f)
        )

        // Cylindrical Body
        drawRoundRect(
            color = Color(0xFFB71C1C),
            topLeft = Offset(bx - r, by - zH),
            size = Size(r * 2f, zH + 6f),
            cornerRadius = CornerRadius(6f, 6f)
        )
        // Specular highlight strip on left side of cylinder
        drawRect(
            color = Color(0xFFFF5252),
            topLeft = Offset(bx - r + 3f, by - zH + 2f),
            size = Size(5f, zH + 2f)
        )
        // Metallic Steel Rib Bands
        drawLine(
            color = Color(0xFF263238),
            start = Offset(bx - r, by - zH * 0.6f),
            end = Offset(bx + r, by - zH * 0.6f),
            strokeWidth = 2.5f
        )
        drawLine(
            color = Color(0xFF263238),
            start = Offset(bx - r, by - zH * 0.2f),
            end = Offset(bx + r, by - zH * 0.2f),
            strokeWidth = 2.5f
        )

        // Top Elliptical Lid
        drawOval(
            color = ZombieCrimson,
            topLeft = Offset(bx - r, by - zH - r * 0.55f),
            size = Size(r * 2f, r * 1.1f)
        )
        drawOval(
            color = MysteryGold,
            topLeft = Offset(bx - 5f, by - zH - 3f),
            size = Size(10f, 6f)
        )
    }

    /**
     * Renders 3/4 Orthographic 3D Interactive Stations (Perk Vending Machines, Mystery Box Crate,
     * Pack-a-Punch Altar, Siege Fabricator Radar, Upgrade Anvil, Debris Doors).
     */
    private fun DrawScope.drawOrthographicStation3D(st: InteractiveStation, unlockedRooms: Set<Int>) {
        if (st.roomIndex !in unlockedRooms) return
        val sx = st.x
        val sy = st.y
        val stColor = when (st.kind) {
            StationKind.DEBRIS_DOOR -> ZombieCrimson
            StationKind.WALL_WEAPON -> CryoCyan
            StationKind.MYSTERY_BOX -> MysteryGold
            StationKind.PACK_A_PUNCH -> PackPurple
            StationKind.PERK_MACHINE -> Color(st.perkType?.colorHex ?: 0xFF00FF66)
            StationKind.BARRICADE_WINDOW -> Color(0xFFD97706)
            StationKind.SIEGE_FABRICATOR -> Color(0xFFFF2A54)
            StationKind.UPGRADE_ANVIL -> Color(0xFFFFD166)
        }

        // Foreshortened Ground Interaction Ring
        val rx = st.radius * 0.85f
        val ry = st.radius * 0.85f * ORTHO_Y_SCALE
        drawOval(
            color = stColor.copy(alpha = 0.20f),
            topLeft = Offset(sx - rx, sy - ry),
            size = Size(rx * 2f, ry * 2f)
        )
        drawOval(
            color = stColor.copy(alpha = 0.65f),
            topLeft = Offset(sx - rx, sy - ry),
            size = Size(rx * 2f, ry * 2f),
            style = Stroke(width = 1.8f)
        )

        when (st.kind) {
            StationKind.PERK_MACHINE, StationKind.PACK_A_PUNCH, StationKind.SIEGE_FABRICATOR -> {
                // Tall 3D Vending / Beacon Cabinet
                val w = 28f
                val zH = 38f
                drawRoundRect(
                    color = Color(0xFF16201B),
                    topLeft = Offset(sx - w / 2f, sy - zH),
                    size = Size(w, zH + 6f),
                    cornerRadius = CornerRadius(4f, 4f)
                )
                // Illuminated Glass Front Panel
                drawRect(
                    color = stColor.copy(alpha = 0.85f),
                    topLeft = Offset(sx - w / 2f + 4f, sy - zH + 6f),
                    size = Size(w - 8f, 16f)
                )
                // Top Roof Cap
                drawRect(
                    color = stColor,
                    topLeft = Offset(sx - w / 2f, sy - zH - 5f),
                    size = Size(w, 6f)
                )
            }
            StationKind.MYSTERY_BOX, StationKind.UPGRADE_ANVIL -> {
                // Wide 3D Munitions Chest / Forge Anvil
                val w = 38f
                val zH = 22f
                drawRoundRect(
                    color = Color(0xFF2D2212),
                    topLeft = Offset(sx - w / 2f, sy - zH),
                    size = Size(w, zH + 6f),
                    cornerRadius = CornerRadius(4f, 4f)
                )
                drawRect(
                    color = stColor,
                    topLeft = Offset(sx - w / 2f, sy - zH),
                    size = Size(w, 7f)
                )
                drawRoundRect(
                    color = stColor,
                    topLeft = Offset(sx - w / 2f, sy - zH),
                    size = Size(w, zH + 6f),
                    cornerRadius = CornerRadius(4f, 4f),
                    style = Stroke(width = 2f)
                )
            }
            else -> {
                // Barricade Window / Wall-Buy / Door Node
                drawRoundRect(
                    color = stColor,
                    topLeft = Offset(sx - 14f, sy - 18f),
                    size = Size(28f, 20f),
                    cornerRadius = CornerRadius(4f, 4f)
                )
            }
        }
    }

    private fun DrawScope.drawOrthographicFortification3D(fort: FortificationEntity) {
        val fc = Color(fort.kind.colorHex)
        when (fort.kind) {
            FortificationType.BARRICADE_WALL -> {
                val w = 48f
                val zH = 26f
                drawRoundRect(
                    color = Color(0xFF1A2E22),
                    topLeft = Offset(fort.x - w / 2f, fort.y - zH),
                    size = Size(w, zH + 8f),
                    cornerRadius = CornerRadius(4f, 4f)
                )
                drawRect(
                    color = fc,
                    topLeft = Offset(fort.x - w / 2f, fort.y - zH),
                    size = Size(w, 7f)
                )
                drawRoundRect(
                    color = fc,
                    topLeft = Offset(fort.x - w / 2f, fort.y - zH),
                    size = Size(w, zH + 8f),
                    cornerRadius = CornerRadius(4f, 4f),
                    style = Stroke(width = 2.2f)
                )
            }
            FortificationType.CRYO_MINE -> {
                drawOval(
                    color = fc.copy(alpha = 0.32f),
                    topLeft = Offset(fort.x - 18f, fort.y - 14f),
                    size = Size(36f, 28f)
                )
                drawCircle(color = fc, radius = 8f, center = Offset(fort.x, fort.y - 4f))
            }
            FortificationType.AUTO_TURRET_BEACON -> {
                // Tripod base + elevated rotating sentry head
                drawOval(
                    color = Color.Black.copy(alpha = 0.5f),
                    topLeft = Offset(fort.x - 16f, fort.y - 6f),
                    size = Size(32f, 16f)
                )
                drawRoundRect(
                    color = Color(0xFF263238),
                    topLeft = Offset(fort.x - 10f, fort.y - 26f),
                    size = Size(20f, 24f),
                    cornerRadius = CornerRadius(4f, 4f)
                )
                drawCircle(color = fc, radius = 8f, center = Offset(fort.x, fort.y - 24f))
            }
            else -> Unit
        }
    }

    private fun DrawScope.drawOrthographicLootDrop3D(drop: LootDropEntity, animPhase: Float) {
        val dc = Color(drop.rarity.colorHex)
        val bobOffset = sin(animPhase * 1.5f) * 4f

        // Ground holo-pad ellipse
        drawOval(
            color = dc.copy(alpha = 0.32f),
            topLeft = Offset(drop.x - 18f, drop.y - 11f),
            size = Size(36f, 22f)
        )
        // Vertical Rarity Loot Beam Pillar (Borderlands style!)
        drawLine(
            color = dc.copy(alpha = 0.45f),
            start = Offset(drop.x, drop.y),
            end = Offset(drop.x, drop.y - 48f),
            strokeWidth = 6f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color.White.copy(alpha = 0.8f),
            start = Offset(drop.x, drop.y - 4f),
            end = Offset(drop.x, drop.y - 44f),
            strokeWidth = 2f,
            cap = StrokeCap.Round
        )
        // Floating 3D Hologram Case
        drawRoundRect(
            color = HandheldObsidian,
            topLeft = Offset(drop.x - 11f, drop.y - 24f + bobOffset),
            size = Size(22f, 15f),
            cornerRadius = CornerRadius(3f, 3f)
        )
        drawRoundRect(
            color = dc,
            topLeft = Offset(drop.x - 11f, drop.y - 24f + bobOffset),
            size = Size(22f, 15f),
            cornerRadius = CornerRadius(3f, 3f),
            style = Stroke(width = 2.2f)
        )
    }

    /**
     * Renders the Local Armored Survivor in 3/4 Orthographic Perspective:
     * - Ground ambient occlusion shadow + Set Synergy Aura Ring
     * - Animated 2-leg combat boots that stride when moving
     * - 3D Armored Carapace Torso + Shoulder Pauldrons (tinted via Phase 5 Armor Palette Shader)
     * - 3D Boxhead Tactical Helmet + Glowing Visor + Articulated Gun with Laser Sight
     */
    private fun DrawScope.drawOrthographicPlayerSprite(
        state: GameSessionState,
        walkPhaseRad: Float,
        isMoving: Boolean
    ) {
        val px = state.playerX
        val py = state.playerY
        val palette = state.armorRig.paletteProfile
        val chassisColor = if (state.aiBotEnabled) CryoCyan else Color(palette.chassisColorHex)
        val trimColor = Color(palette.trimColorHex)
        val visorColor = Color(palette.visorColorHex)
        val gunColor = Color(state.activeModularWeaponForCurrentSlot.magazine.elementalAffinity.colorHex)

        val legSwing = if (isMoving) sin(walkPhaseRad) * 6.5f else 0f
        val bodyBob = if (isMoving) kotlin.math.abs(cos(walkPhaseRad)) * 2.5f else 0f
        val recoilKick = state.cameraTension.muzzleFlashIntensity * 5.5f

        // 1. Foreshortened Set Synergy Aura Ring & Ground Contact Shadow
        drawOval(
            color = Color(palette.auraColorHex).copy(alpha = 0.36f),
            topLeft = Offset(px - 24f, py - 12f),
            size = Size(48f, 28f),
            style = Stroke(width = 2.2f)
        )
        drawOval(
            color = Color.Black.copy(alpha = 0.58f),
            topLeft = Offset(px - 17f, py - 7f),
            size = Size(34f, 18f)
        )

        // 2. Animated Stride Combat Boots
        drawRoundRect(
            color = Color(0xFF1C2621),
            topLeft = Offset(px - 11f, py - 6f + legSwing * 0.5f),
            size = Size(9f, 11f),
            cornerRadius = CornerRadius(3f, 3f)
        )
        drawRoundRect(
            color = Color(0xFF1C2621),
            topLeft = Offset(px + 2f, py - 6f - legSwing * 0.5f),
            size = Size(9f, 11f),
            cornerRadius = CornerRadius(3f, 3f)
        )

        // 3. 3D Armored Torso Carapace + Shoulder Pauldrons
        val torsoTop = py - 26f - bodyBob
        drawRoundRect(
            color = chassisColor,
            topLeft = Offset(px - 15f, torsoTop),
            size = Size(30f, 22f),
            cornerRadius = CornerRadius(5f, 5f)
        )
        // Left & Right Armored Pauldrons
        drawRoundRect(
            color = trimColor,
            topLeft = Offset(px - 19f, torsoTop + 2f),
            size = Size(7f, 14f),
            cornerRadius = CornerRadius(3f, 3f)
        )
        drawRoundRect(
            color = trimColor,
            topLeft = Offset(px + 12f, torsoTop + 2f),
            size = Size(7f, 14f),
            cornerRadius = CornerRadius(3f, 3f)
        )
        // Chest Core Plate
        drawRect(
            color = trimColor.copy(alpha = 0.85f),
            topLeft = Offset(px - 8f, torsoTop + 5f),
            size = Size(16f, 8f)
        )

        // 4. 3D Boxhead Helmet Cube (offset upward for 3/4 Orthographic perspective)
        val headTop = torsoTop - 16f
        drawRoundRect(
            color = Color(0xFF26352D),
            topLeft = Offset(px - 13f, headTop),
            size = Size(26f, 19f),
            cornerRadius = CornerRadius(4f, 4f)
        )
        drawRoundRect(
            color = trimColor,
            topLeft = Offset(px - 13f, headTop),
            size = Size(26f, 19f),
            cornerRadius = CornerRadius(4f, 4f),
            style = Stroke(width = 2f)
        )

        // Directional Glowing Tactical Visor oriented toward aimAngleRad
        val lookX = (cos(state.aimAngleRad) * 5f).coerceIn(-6f, 6f)
        val lookY = (sin(state.aimAngleRad) * 3f).coerceIn(-3f, 4f)
        drawRoundRect(
            color = visorColor,
            topLeft = Offset(px - 8f + lookX, headTop + 6f + lookY),
            size = Size(16f, 6.5f),
            cornerRadius = CornerRadius(2f, 2f)
        )

        // 5. Articulated 3D Weapon Receiver, Barrel & Laser Sight
        val gunPivotX = px + cos(state.aimAngleRad) * (10f - recoilKick)
        val gunPivotY = (py - 16f - bodyBob) + sin(state.aimAngleRad) * (8f - recoilKick)
        val barrelTipX = gunPivotX + cos(state.aimAngleRad) * 22f
        val barrelTipY = gunPivotY + sin(state.aimAngleRad) * 22f
        val laserEndX = barrelTipX + cos(state.aimAngleRad) * 85f
        val laserEndY = barrelTipY + sin(state.aimAngleRad) * 85f

        // Tactical Laser Sight Line
        drawLine(
            color = gunColor.copy(alpha = 0.42f),
            start = Offset(barrelTipX, barrelTipY),
            end = Offset(laserEndX, laserEndY),
            strokeWidth = 2f
        )

        // Weapon Chassis & Barrel
        drawLine(
            color = Color(0xFF111715),
            start = Offset(gunPivotX, gunPivotY),
            end = Offset(barrelTipX, barrelTipY),
            strokeWidth = 8f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = gunColor,
            start = Offset(gunPivotX, gunPivotY),
            end = Offset(barrelTipX, barrelTipY),
            strokeWidth = 3.5f,
            cap = StrokeCap.Round
        )

        // Muzzle Flash Starburst when firing
        if (state.cameraTension.muzzleFlashIntensity > 0.15f) {
            val flashRadius = 9f * state.cameraTension.muzzleFlashIntensity
            drawCircle(
                color = MysteryGold.copy(alpha = 0.85f),
                radius = flashRadius,
                center = Offset(barrelTipX, barrelTipY)
            )
            drawCircle(
                color = Color.White,
                radius = flashRadius * 0.5f,
                center = Offset(barrelTipX, barrelTipY)
            )
        }

        // Overhead Reload Progress Bar
        if (state.reloadRemainingMs > 0) {
            val barW = 46f
            val barH = 6f
            val barTopLeft = Offset(px - barW / 2f, headTop - 11f)
            drawRoundRect(
                color = HandheldObsidian,
                topLeft = barTopLeft,
                size = Size(barW, barH),
                cornerRadius = CornerRadius(3f, 3f)
            )
            drawRoundRect(
                color = MysteryGold,
                topLeft = barTopLeft,
                size = Size(barW * state.reloadProgressFraction, barH),
                cornerRadius = CornerRadius(3f, 3f)
            )
        }
    }

    private fun DrawScope.drawOrthographicCoopPartner(peer: CoopPeerState, walkPhaseRad: Float) {
        val px = peer.x
        val py = peer.y
        drawOval(
            color = Color.Black.copy(alpha = 0.5f),
            topLeft = Offset(px - 15f, py - 6f),
            size = Size(30f, 15f)
        )
        drawRoundRect(
            color = CryoCyan,
            topLeft = Offset(px - 14f, py - 28f),
            size = Size(28f, 24f),
            cornerRadius = CornerRadius(5f, 5f)
        )
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(px - 8f, py - 38f),
            size = Size(16f, 12f),
            cornerRadius = CornerRadius(3f, 3f)
        )
    }

    /**
     * Renders distinct 3/4 Orthographic Enemy Archetype Artwork:
     * - SHAMBLER: Boxhead Rotting Husk with shambling boots, ribcage armor, outstretched zombie arms & glowing eyes.
     * - SPRINTER: Forward-leaning Hell Runner with glowing orange spine vents.
     * - CRAWLER: Low-slung 4-limbed bio-acid arachnid crawler.
     * - CALCIFIED_STALKER: Bone-plated sniper with drawn bow arc.
     * - ABYSSAL_WEAVER: Levitating hooded mage with arcane rune ring.
     * - DEVIL_BOSS: Massive horned Boxhead Red Devil juggernaut with shoulder spikes & molten core.
     */
    private fun DrawScope.drawOrthographicZombieSprite(
        zombie: ZombieEntity,
        playerX: Float,
        playerY: Float,
        isLightLocked: Boolean,
        animPhase: Float
    ) {
        val zx = zombie.x
        val zy = zombie.y
        val r = zombie.archetype.radius
        val baseColor = Color(zombie.archetype.colorHex)
        val stride = sin(animPhase * 1.3f) * 4.5f
        val bob = kotlin.math.abs(cos(animPhase * 1.3f)) * 2f
        val dirX = if (playerX >= zx) 1f else -1f

        // 1. Foreshortened Ground Drop Shadow
        drawOval(
            color = Color.Black.copy(alpha = 0.52f),
            topLeft = Offset(zx - r * 1.05f, zy - r * 0.42f),
            size = Size(r * 2.1f, r * 1.05f)
        )

        // Phase 4 Light-Lock Illuminated Reticle
        if (isLightLocked) {
            drawOval(
                color = CryoCyan,
                topLeft = Offset(zx - r * 1.35f, zy - r * 0.85f),
                size = Size(r * 2.7f, r * 1.7f),
                style = Stroke(width = 2.5f)
            )
        }

        when (zombie.archetype) {
            ZombieArchetype.DEVIL_BOSS -> {
                // Towering 3/4 Orthographic Horned Boxhead Red Devil Boss
                val zH = 42f + bob
                // Molten ground shockwave aura
                drawOval(
                    color = ZombieCrimson.copy(alpha = 0.30f),
                    topLeft = Offset(zx - r * 1.4f, zy - r * 0.8f),
                    size = Size(r * 2.8f, r * 1.6f)
                )
                // Heavy Demon Legs
                drawRect(color = Color(0xFF4A0E17), topLeft = Offset(zx - 14f, zy - 10f + stride * 0.5f), size = Size(10f, 14f))
                drawRect(color = Color(0xFF4A0E17), topLeft = Offset(zx + 4f, zy - 10f - stride * 0.5f), size = Size(10f, 14f))
                // Hulking Crimson Carapace Torso
                drawRoundRect(
                    color = Color(0xFF9B1126),
                    topLeft = Offset(zx - 22f, zy - zH),
                    size = Size(44f, 34f),
                    cornerRadius = CornerRadius(6f, 6f)
                )
                // Molten Core Chest Furnace
                drawCircle(color = MysteryGold, radius = 8f, center = Offset(zx, zy - zH + 17f))
                // Horned Boxhead Demon Skull
                drawRoundRect(
                    color = ZombieCrimson,
                    topLeft = Offset(zx - 15f, zy - zH - 16f),
                    size = Size(30f, 20f),
                    cornerRadius = CornerRadius(4f, 4f)
                )
                // Left & Right Demon Horns
                drawLine(color = MysteryGold, start = Offset(zx - 13f, zy - zH - 12f), end = Offset(zx - 22f, zy - zH - 25f), strokeWidth = 4f, cap = StrokeCap.Round)
                drawLine(color = MysteryGold, start = Offset(zx + 13f, zy - zH - 12f), end = Offset(zx + 22f, zy - zH - 25f), strokeWidth = 4f, cap = StrokeCap.Round)
                // Glowing Yellow Eyes
                drawCircle(color = Color.White, radius = 3.2f, center = Offset(zx - 5f + dirX * 2f, zy - zH - 6f))
                drawCircle(color = Color.White, radius = 3.2f, center = Offset(zx + 5f + dirX * 2f, zy - zH - 6f))
            }

            ZombieArchetype.ABYSSAL_WEAVER -> {
                // Levitating Hooded Elemental Mage with Arcane Ring
                val hover = sin(animPhase) * 4f
                drawOval(
                    color = PackPurple.copy(alpha = 0.45f),
                    topLeft = Offset(zx - 18f, zy - 10f),
                    size = Size(36f, 20f),
                    style = Stroke(width = 2f)
                )
                // Robed Body Cone
                drawRoundRect(
                    color = Color(0xFF311B4E),
                    topLeft = Offset(zx - 13f, zy - 30f + hover),
                    size = Size(26f, 28f),
                    cornerRadius = CornerRadius(6f, 6f)
                )
                drawRoundRect(
                    color = baseColor,
                    topLeft = Offset(zx - 13f, zy - 30f + hover),
                    size = Size(26f, 28f),
                    cornerRadius = CornerRadius(6f, 6f),
                    style = Stroke(width = 2f)
                )
                // Hooded cowl & glowing cyan/violet eyes
                drawRoundRect(
                    color = Color(0xFF1A0E2E),
                    topLeft = Offset(zx - 10f, zy - 40f + hover),
                    size = Size(20f, 15f),
                    cornerRadius = CornerRadius(4f, 4f)
                )
                drawCircle(color = CryoCyan, radius = 2.8f, center = Offset(zx - 4f + dirX * 2f, zy - 33f + hover))
                drawCircle(color = CryoCyan, radius = 2.8f, center = Offset(zx + 4f + dirX * 2f, zy - 33f + hover))
            }

            ZombieArchetype.CALCIFIED_STALKER -> {
                // Bone-Plated Sniper / Archer with Compound Bone-Bow
                val zH = 26f + bob
                drawRect(color = Color(0xFF2D1E20), topLeft = Offset(zx - 8f, zy - 6f + stride * 0.4f), size = Size(6f, 9f))
                drawRect(color = Color(0xFF2D1E20), topLeft = Offset(zx + 2f, zy - 6f - stride * 0.4f), size = Size(6f, 9f))
                drawRoundRect(
                    color = Color(0xFF4E2A2E),
                    topLeft = Offset(zx - 12f, zy - zH),
                    size = Size(24f, 20f),
                    cornerRadius = CornerRadius(4f, 4f)
                )
                // Calcified Bone Skull Crest
                drawRoundRect(
                    color = Color(0xFFECEFF1),
                    topLeft = Offset(zx - 10f, zy - zH - 13f),
                    size = Size(20f, 15f),
                    cornerRadius = CornerRadius(3f, 3f)
                )
                // Crimson Sniper Optic Eye
                drawCircle(color = ZombieCrimson, radius = 3.5f, center = Offset(zx + dirX * 3f, zy - zH - 5f))
            }

            ZombieArchetype.CRAWLER -> {
                // Low-slung 4-legged Bio-Acid Crawler
                val legWiggle = sin(animPhase * 2f) * 5f
                drawLine(color = baseColor, start = Offset(zx - 14f, zy - 8f + legWiggle), end = Offset(zx + 14f, zy - 2f - legWiggle), strokeWidth = 3f)
                drawLine(color = baseColor, start = Offset(zx - 14f, zy - 2f - legWiggle), end = Offset(zx + 14f, zy - 8f + legWiggle), strokeWidth = 3f)
                drawOval(
                    color = Color(0xFF103840),
                    topLeft = Offset(zx - 13f, zy - 15f),
                    size = Size(26f, 16f)
                )
                drawCircle(color = baseColor, radius = 6f, center = Offset(zx - dirX * 3f, zy - 9f))
            }

            else -> {
                // SHAMBLER (Rotting Husk) & SPRINTER (Hell Runner): Boxhead 3/4 Orthographic Undead
                val zH = 24f + bob
                // Shambling Legs
                drawRect(color = Color(0xFF1B261E), topLeft = Offset(zx - 8f, zy - 6f + stride * 0.5f), size = Size(6f, 9f))
                drawRect(color = Color(0xFF1B261E), topLeft = Offset(zx + 2f, zy - 6f - stride * 0.5f), size = Size(6f, 9f))

                // Torso
                val torsoColor = if (zombie.archetype == ZombieArchetype.SPRINTER) Color(0xFF682812) else Color(0xFF253B2B)
                drawRoundRect(
                    color = torsoColor,
                    topLeft = Offset(zx - 12f, zy - zH),
                    size = Size(24f, 19f),
                    cornerRadius = CornerRadius(4f, 4f)
                )

                // Outstretched Zombie Arms reaching toward player
                drawLine(
                    color = baseColor,
                    start = Offset(zx - 10f, zy - zH + 6f),
                    end = Offset(zx + dirX * 16f, zy - zH + 10f + stride * 0.3f),
                    strokeWidth = 4f,
                    cap = StrokeCap.Round
                )

                // Iconic Boxhead 3D Blocky Zombie Head
                drawRoundRect(
                    color = baseColor,
                    topLeft = Offset(zx - 11f, zy - zH - 13f),
                    size = Size(22f, 15f),
                    cornerRadius = CornerRadius(3f, 3f)
                )
                drawRoundRect(
                    color = HandheldObsidian.copy(alpha = 0.65f),
                    topLeft = Offset(zx - 11f, zy - zH - 13f),
                    size = Size(22f, 15f),
                    cornerRadius = CornerRadius(3f, 3f),
                    style = Stroke(width = 1.6f)
                )
                // Glowing Crimson/Amber Undead Eyes
                val eyeColor = if (zombie.archetype == ZombieArchetype.SPRINTER) MysteryGold else ZombieCrimson
                drawCircle(color = eyeColor, radius = 2.4f, center = Offset(zx - 4f + dirX * 2.5f, zy - zH - 6f))
                drawCircle(color = eyeColor, radius = 2.4f, center = Offset(zx + 4f + dirX * 2.5f, zy - zH - 6f))
            }
        }

        // Elevated Floating Health Bar (when damaged or Boss)
        if (zombie.hp < zombie.maxHp || zombie.archetype == ZombieArchetype.DEVIL_BOSS) {
            val ratio = (zombie.hp.toFloat() / zombie.maxHp.toFloat()).coerceIn(0f, 1f)
            val barW = max(r * 2.1f, 28f)
            val barTopY = if (zombie.archetype == ZombieArchetype.DEVIL_BOSS) zy - 66f else zy - 44f
            drawRoundRect(
                color = HandheldObsidian,
                topLeft = Offset(zx - barW / 2f, barTopY),
                size = Size(barW, 5f),
                cornerRadius = CornerRadius(2f, 2f)
            )
            drawRoundRect(
                color = if (zombie.archetype == ZombieArchetype.DEVIL_BOSS) MysteryGold else ZombieCrimson,
                topLeft = Offset(zx - barW / 2f, barTopY),
                size = Size(barW * ratio, 5f),
                cornerRadius = CornerRadius(2f, 2f)
            )
        }
    }

    /**
     * Zero-Allocation Fast Chiaroscuro Lighting & Two-Shadow Convex Hull Pass.
     * Avoids creating per-frame Brush.radialGradient or Path allocations in tight loops,
     * keeping frame rendering under 2ms for silky 60 FPS gameplay.
     */
    private fun DrawScope.renderFastChiaroscuroPass(
        state: GameSessionState,
        lights: List<PointLightSource2D>,
        shadowHulls: List<ConvexShadowHull>
    ) {
        val mode = state.cameraTension.lightingMode
        if (mode == LightingAtmosphereMode.CLASSIC_ARCADE_BRIGHT) return

        val px = state.playerX
        val py = state.playerY

        // 1. Concentric Layered Additive Halo Rings (Zero Brush allocation!)
        lights.forEach { light ->
            if (!light.isDirectionalCone) {
                val baseAlpha = (0.14f * light.intensity).coerceIn(0.04f, 0.28f)
                drawOval(
                    color = light.color.copy(alpha = baseAlpha * 0.45f),
                    topLeft = Offset(light.x - light.radius, light.y - light.radius * ORTHO_Y_SCALE),
                    size = Size(light.radius * 2f, light.radius * 2f * ORTHO_Y_SCALE)
                )
                drawOval(
                    color = light.color.copy(alpha = baseAlpha),
                    topLeft = Offset(light.x - light.radius * 0.55f, light.y - light.radius * 0.55f * ORTHO_Y_SCALE),
                    size = Size(light.radius * 1.1f, light.radius * 1.1f * ORTHO_Y_SCALE)
                )
            }
        }

        // 2. Player Directional Lantern Cone Beam (reusing scratchPath)
        val playerLight = lights.firstOrNull { it.isDirectionalCone }
        if (playerLight != null) {
            scratchPath.reset()
            scratchPath.moveTo(px, py - 14f)
            val steps = 10
            val startAng = playerLight.coneAngleRad - playerLight.coneHalfSpreadRad
            val endAng = playerLight.coneAngleRad + playerLight.coneHalfSpreadRad
            for (i in 0..steps) {
                val t = i.toFloat() / steps.toFloat()
                val ang = startAng + (endAng - startAng) * t
                val rx = px + cos(ang) * playerLight.radius
                val ry = (py - 14f) + sin(ang) * playerLight.radius
                scratchPath.lineTo(rx, ry)
            }
            scratchPath.close()

            drawPath(
                path = scratchPath,
                color = playerLight.color.copy(alpha = 0.11f)
            )
            // Inner high-intensity core cone
            scratchPath.reset()
            scratchPath.moveTo(px, py - 14f)
            for (i in 0..6) {
                val t = i.toFloat() / 6f
                val ang = (playerLight.coneAngleRad - playerLight.coneHalfSpreadRad * 0.55f) +
                    (playerLight.coneHalfSpreadRad * 1.1f) * t
                val rx = px + cos(ang) * (playerLight.radius * 0.68f)
                val ry = (py - 14f) + sin(ang) * (playerLight.radius * 0.68f)
                scratchPath.lineTo(rx, ry)
            }
            scratchPath.close()
            drawPath(
                path = scratchPath,
                color = playerLight.color.copy(alpha = 0.10f)
            )
        }

        // 3. Two-Shadow Hardware Limit: Project Simplified Convex Shadow Volumes (reusing scratchPath)
        val shadowedLights = lights.filter { it.shadowEnabled }.take(2)
        val shadowColor = if (mode == LightingAtmosphereMode.ABYSSAL_BIO_NOIR) {
            Color(0xAA020504)
        } else {
            Color(0x55020504)
        }

        shadowedLights.forEach { light ->
            val lx = light.x
            val ly = light.y
            shadowHulls.forEach { hull ->
                val distToHull = hypot(hull.center.x - lx, hull.center.y - ly)
                if (distToHull in 22f..(light.radius * 1.05f)) {
                    var minAngle = Float.MAX_VALUE
                    var maxAngle = -Float.MAX_VALUE
                    var minVertex = hull.vertices.first()
                    var maxVertex = hull.vertices.first()
                    val baseAngle = kotlin.math.atan2(hull.center.y - ly, hull.center.x - lx)

                    hull.vertices.forEach { v ->
                        var relAng = kotlin.math.atan2(v.y - ly, v.x - lx) - baseAngle
                        while (relAng > PI.toFloat()) relAng -= (2.0 * PI).toFloat()
                        while (relAng < -PI.toFloat()) relAng += (2.0 * PI).toFloat()
                        if (relAng < minAngle) {
                            minAngle = relAng
                            minVertex = v
                        }
                        if (relAng > maxAngle) {
                            maxAngle = relAng
                            maxVertex = v
                        }
                    }

                    val shadowLength = light.radius * 1.1f
                    val dirMinX = minVertex.x - lx
                    val dirMinY = minVertex.y - ly
                    val lenMin = hypot(dirMinX, dirMinY).coerceAtLeast(1f)
                    val farMinX = minVertex.x + (dirMinX / lenMin) * shadowLength
                    val farMinY = minVertex.y + (dirMinY / lenMin) * shadowLength

                    val dirMaxX = maxVertex.x - lx
                    val dirMaxY = maxVertex.y - ly
                    val lenMax = hypot(dirMaxX, dirMaxY).coerceAtLeast(1f)
                    val farMaxX = maxVertex.x + (dirMaxX / lenMax) * shadowLength
                    val farMaxY = maxVertex.y + (dirMaxY / lenMax) * shadowLength

                    scratchPath.reset()
                    scratchPath.moveTo(minVertex.x, minVertex.y)
                    scratchPath.lineTo(farMinX, farMinY)
                    scratchPath.lineTo(farMaxX, farMaxY)
                    scratchPath.lineTo(maxVertex.x, maxVertex.y)
                    scratchPath.close()
                    drawPath(path = scratchPath, color = shadowColor)
                }
            }
        }
    }
}
