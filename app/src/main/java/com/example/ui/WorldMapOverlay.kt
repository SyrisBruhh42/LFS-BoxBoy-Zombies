package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LongState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.game.GameSessionState
import com.example.game.RoadTier
import com.example.game.StationKind
import com.example.network.CoopPeerState
import com.example.ui.theme.CryoCyan
import com.example.ui.theme.HandheldChassis
import com.example.ui.theme.HandheldObsidian
import com.example.ui.theme.MysteryGold
import com.example.ui.theme.PackPurple
import com.example.ui.theme.PhosphorGreen
import com.example.ui.theme.TextCrisp
import com.example.ui.theme.TextMuted
import com.example.ui.theme.ZombieCrimson
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

private val minimapConePath = Path()

/**
 * Compact Live Tactical County Minimap rendered in the top-right corner of the game viewport.
 * Displays all 6 Towns, the Winding Rivers & Lakes, 3-Tier Road Network, Parcel Buildings,
 * Interactive Stations, Zombie Horde Blips, Custom GPS Waypoint, and Player Position + Cone.
 * Tapping opens the Interactive Full-Screen County World Map & Town Atlas Modal.
 */
@Composable
fun LiveCountyMinimapRadar(
    hudState: GameSessionState,
    gameStateFlow: StateFlow<GameSessionState>,
    frameTickState: LongState,
    coopPeer: CoopPeerState?,
    customWaypoint: Offset?,
    isLandscape: Boolean,
    onOpenFullWorldMap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mapW = if (isLandscape) 168.dp else 142.dp
    val mapH = if (isLandscape) 98.dp else 84.dp
    val activeRoom = hudState.map.rooms.getOrNull(hudState.mazeState.activeRoomSector)
        ?: hudState.map.rooms.first()

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(HandheldObsidian.copy(alpha = 0.84f))
            .border(1.2.dp, CryoCyan.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
            .clickable { onOpenFullWorldMap() }
            .padding(5.dp)
            .testTag("live_county_minimap_radar"),
        horizontalAlignment = Alignment.End
    ) {
        // Header row: Active Town Name + [MAP] badge
        Row(
            modifier = Modifier.width(mapW),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = activeRoom.biome.title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = Color(activeRoom.biome.accentColorHex),
                fontWeight = FontWeight.Bold,
                fontSize = 8.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "[ATLAS]",
                style = MaterialTheme.typography.labelSmall,
                color = MysteryGold,
                fontWeight = FontWeight.Bold,
                fontSize = 8.5.sp
            )
        }

        Spacer(modifier = Modifier.height(3.dp))

        Box(
            modifier = Modifier
                .size(width = mapW, height = mapH)
                .clip(RoundedCornerShape(5.dp))
                .background(Color(0xFF070D10))
                .border(1.dp, HandheldChassis, RoundedCornerShape(5.dp))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val tick = frameTickState.longValue
                val live = if (tick >= 0L) gameStateFlow.value else hudState
                drawCountyWorldCartography(
                    state = live,
                    coopPeer = coopPeer,
                    customWaypoint = customWaypoint,
                    isDetailedModal = false
                )
            }
        }

        // Optional GPS Waypoint distance & bearing indicator
        if (customWaypoint != null) {
            val dx = customWaypoint.x - hudState.playerX
            val dy = customWaypoint.y - hudState.playerY
            val distMeters = (hypot(dx, dy) / 18f).roundToInt()
            val dirLabel = compassDirectionLabel(dx, dy)
            Text(
                text = "GPS WAYPOINT: ${distMeters}m $dirLabel",
                style = MaterialTheme.typography.labelSmall,
                color = MysteryGold,
                fontWeight = FontWeight.Bold,
                fontSize = 8.5.sp
            )
        }
    }
}

/**
 * Full-Screen Interactive County World Map & 6-Town Tactical Atlas Modal.
 * Allows inspecting the 5-Pass procedural county topology (Highways, Streets, Trails,
 * Winding Rivers, Lakes, Bridges, 38+ Named Town Buildings, Perks, Mystery Box, Pack-a-Punch)
 * and tapping anywhere on the map to place a live GPS Waypoint.
 */
@Composable
fun InteractiveWorldMapModal(
    state: GameSessionState,
    coopPeer: CoopPeerState?,
    customWaypoint: Offset?,
    onSetWaypoint: (Offset?) -> Unit,
    onGenerateNewWorldSeed: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = HandheldObsidian,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
                .border(1.5.dp, CryoCyan, RoundedCornerShape(12.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Map,
                            contentDescription = null,
                            tint = CryoCyan,
                            modifier = Modifier.size(22.dp)
                        )
                        Column {
                            Text(
                                text = "COUNTY TACTICAL WORLD ATLAS (SEED #${state.mapSeed})",
                                style = MaterialTheme.typography.titleMedium,
                                color = CryoCyan,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "5-Pass Topology: 6 Towns • 3-Tier Road Network • Connected River & 7 Lakes • ${state.map.townBuildings.size} Parcel Buildings • ${state.map.foliage.size} Trees/Shrubs",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (customWaypoint != null) {
                            Button(
                                onClick = { onSetWaypoint(null) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MysteryGold.copy(alpha = 0.2f),
                                    contentColor = MysteryGold
                                )
                            ) {
                                Text("CLEAR GPS PIN", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Button(
                            onClick = onGenerateNewWorldSeed,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PhosphorGreen.copy(alpha = 0.2f),
                                contentColor = PhosphorGreen
                            )
                        ) {
                            Icon(Icons.Default.Explore, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("NEW WORLD SEED", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close World Map", tint = TextCrisp)
                        }
                    }
                }

                // Interactive High-Resolution County Cartography Canvas (Tap anywhere to set GPS Waypoint!)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4800f / 2760f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF060B0E))
                        .border(1.5.dp, PhosphorGreen.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                        .pointerInput(state.map.worldWidth, state.map.worldHeight) {
                            detectTapGestures { tapOffset ->
                                val wx = (tapOffset.x / size.width.coerceAtLeast(1)) * state.map.worldWidth
                                val wy = (tapOffset.y / size.height.coerceAtLeast(1)) * state.map.worldHeight
                                onSetWaypoint(
                                    Offset(
                                        wx.coerceIn(80f, state.map.worldWidth - 80f),
                                        wy.coerceIn(80f, state.map.worldHeight - 80f)
                                    )
                                )
                            }
                        }
                        .testTag("interactive_world_map_canvas")
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCountyWorldCartography(
                            state = state,
                            coopPeer = coopPeer,
                            customWaypoint = customWaypoint,
                            isDetailedModal = true
                        )
                    }

                    // Overlay Town Labels inside each of the 6 Town Quadrants
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TownMapLabelBadge("03: MISTHARBOR VILLAGE", Color(0xFF40C4FF), 2 in state.unlockedRooms)
                        TownMapLabelBadge("04: SUNVALE OLD MARKET", Color(0xFFFFD166), 3 in state.unlockedRooms)
                        TownMapLabelBadge("06: ASHENHOLLOW CITADEL", Color(0xFFB388FF), 5 in state.unlockedRooms)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TownMapLabelBadge("01: OAKRIDGE MILL TOWN", Color(0xFF00E5FF), 0 in state.unlockedRooms)
                        TownMapLabelBadge("02: SILVERLAKE WOODS", Color(0xFF52FF88), 1 in state.unlockedRooms)
                        TownMapLabelBadge("05: REDWOOD RAIL TOWN", Color(0xFFFF8A3D), 4 in state.unlockedRooms)
                    }
                }

                // Map Legend & 6-Town Directory
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(HandheldChassis)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MapLegendDot(PhosphorGreen, "YOU (SURVIVOR)")
                    MapLegendDot(Color(0xFF1CA3EC), "RIVERS & LAKES")
                    MapLegendDot(Color(0xFF8D6E63), "BRIDGES & TRAILS")
                    MapLegendDot(MysteryGold, "BOX / ANVIL / GPS")
                    MapLegendDot(PackPurple, "PACK-A-PUNCH")
                    MapLegendDot(ZombieCrimson, "ZOMBIE HORDE (${state.zombies.size})")
                }

                Text(
                    text = "TIP: Tap anywhere on the County Atlas above to place a gold GPS Waypoint pin. Bridges across lakes and rivers are walkable choke points for kiting zombie waves!",
                    style = MaterialTheme.typography.labelSmall,
                    color = MysteryGold
                )
            }
        }
    }
}

@Composable
private fun TownMapLabelBadge(title: String, color: Color, unlocked: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(HandheldObsidian.copy(alpha = 0.78f))
            .border(1.dp, if (unlocked) color else ZombieCrimson.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = if (unlocked) title else "$title [LOCKED]",
            style = MaterialTheme.typography.labelSmall,
            color = if (unlocked) color else ZombieCrimson,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp
        )
    }
}

@Composable
private fun MapLegendDot(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextCrisp,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun DrawScope.drawCountyWorldCartography(
    state: GameSessionState,
    coopPeer: CoopPeerState?,
    customWaypoint: Offset?,
    isDetailedModal: Boolean
) {
    val map = state.map
    val scaleX = size.width / map.worldWidth
    val scaleY = size.height / map.worldHeight

    // 1. Render 6 Town Biomes
    for (i in 0 until map.rooms.size) {
        val room = map.rooms[i]
        val b = room.bounds
        val unlocked = room.index in state.unlockedRooms
        val baseCol = if (unlocked) Color(room.floorColorHex) else Color(0xFF0A0D0E)
        drawRect(
            color = baseCol,
            topLeft = Offset(b.left * scaleX, b.top * scaleY),
            size = Size(b.width * scaleX, b.height * scaleY)
        )
        drawRect(
            color = if (unlocked) Color(room.biome.accentColorHex).copy(alpha = 0.25f) else ZombieCrimson.copy(alpha = 0.35f),
            topLeft = Offset(b.left * scaleX, b.top * scaleY),
            size = Size(b.width * scaleX, b.height * scaleY),
            style = Stroke(width = if (isDetailedModal) 1.5f else 0.8f)
        )
    }

    // 2. Render Organic Terrain Patches (in detailed modal)
    if (isDetailedModal) {
        val patches = map.terrainPatches
        for (i in 0 until patches.size) {
            val p = patches[i]
            if (p.roomIndex !in state.unlockedRooms && !p.isCrossBorderBlend) continue
            drawOval(
                color = Color(p.kind.fillColorHex).copy(alpha = 0.65f),
                topLeft = Offset((p.x - p.radiusX) * scaleX, (p.y - p.radiusY) * scaleY),
                size = Size(p.radiusX * 2f * scaleX, p.radiusY * 2f * scaleY)
            )
        }
    }

    // 3. Render 3-Tier Hierarchical Road Network
    val roads = map.roads
    for (i in 0 until roads.size) {
        val rd = roads[i]
        if (rd.roomIndex !in state.unlockedRooms) continue
        val strokeW = when (rd.tier) {
            RoadTier.COUNTY_HIGHWAY -> if (isDetailedModal) 6.5f else 2.8f
            RoadTier.TOWN_AVENUE_STREET -> if (isDetailedModal) 3.8f else 1.5f
            RoadTier.SCENIC_DIRT_TRAIL -> if (isDetailedModal) 2.4f else 1.0f
        }
        val rdCol = when (rd.tier) {
            RoadTier.COUNTY_HIGHWAY -> Color(0xFF37474F)
            RoadTier.TOWN_AVENUE_STREET -> Color(0xFF455A64)
            RoadTier.SCENIC_DIRT_TRAIL -> Color(0xFF6D4C41)
        }
        drawLine(
            color = rdCol,
            start = Offset(rd.startX * scaleX, rd.startY * scaleY),
            end = Offset(rd.endX * scaleX, rd.endY * scaleY),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
    }

    // 4. Render Winding County Rivers & Stone Highway Bridges
    val rivers = map.riverSegments
    for (i in 0 until rivers.size) {
        val seg = rivers[i]
        drawLine(
            color = Color(seg.shallowColorHex).copy(alpha = 0.85f),
            start = Offset(seg.startX * scaleX, seg.startY * scaleY),
            end = Offset(seg.endX * scaleX, seg.endY * scaleY),
            strokeWidth = if (isDetailedModal) 5.5f else 2.2f,
            cap = StrokeCap.Round
        )
    }

    // 5. Render Scenic Town Lakes & Walkable Wooden Footbridges
    val lakes = map.lakes
    for (i in 0 until lakes.size) {
        val lake = lakes[i]
        if (lake.roomIndex !in state.unlockedRooms) continue
        val lx = (lake.x - lake.radiusX) * scaleX
        val ly = (lake.y - lake.radiusY) * scaleY
        val lw = lake.radiusX * 2f * scaleX
        val lh = lake.radiusY * 2f * scaleY
        drawOval(
            color = Color(lake.style.shallowColorHex),
            topLeft = Offset(lx, ly),
            size = Size(lw, lh)
        )
        drawOval(
            color = Color(lake.style.deepColorHex),
            topLeft = Offset(lx + lw * 0.15f, ly + lh * 0.15f),
            size = Size(lw * 0.7f, lh * 0.7f)
        )
        // Wooden bridge line across lake
        val bridgeCol = Color(0xFFA1887F)
        if (lake.bridgeHorizontal) {
            drawLine(
                color = bridgeCol,
                start = Offset((lake.x - lake.radiusX) * scaleX, lake.y * scaleY),
                end = Offset((lake.x + lake.radiusX) * scaleX, lake.y * scaleY),
                strokeWidth = if (isDetailedModal) 3.5f else 1.6f
            )
        } else {
            drawLine(
                color = bridgeCol,
                start = Offset(lake.x * scaleX, (lake.y - lake.radiusY) * scaleY),
                end = Offset(lake.x * scaleX, (lake.y + lake.radiusY) * scaleY),
                strokeWidth = if (isDetailedModal) 3.5f else 1.6f
            )
        }
    }

    // 6. Render Woodland Groves & Trees (in detailed modal)
    if (isDetailedModal) {
        val foliage = map.foliage
        for (i in 0 until foliage.size) {
            val fol = foliage[i]
            if (fol.roomIndex !in state.unlockedRooms) continue
            drawCircle(
                color = Color(fol.primaryColorHex).copy(alpha = 0.78f),
                radius = (fol.radius * scaleX * 0.85f).coerceAtLeast(1.8f),
                center = Offset(fol.x * scaleX, fol.y * scaleY)
            )
        }
    }

    // 7. Render Parcel-Aligned Town Buildings
    val buildings = map.townBuildings
    for (i in 0 until buildings.size) {
        val tb = buildings[i]
        if (tb.roomIndex !in state.unlockedRooms) continue
        val b = tb.bounds
        drawRect(
            color = Color(tb.roofColorHex),
            topLeft = Offset(b.left * scaleX, b.top * scaleY),
            size = Size((b.width * scaleX).coerceAtLeast(2.5f), (b.height * scaleY).coerceAtLeast(2.5f))
        )
        if (isDetailedModal) {
            drawRect(
                color = Color(tb.trimColorHex).copy(alpha = 0.85f),
                topLeft = Offset(b.left * scaleX, b.top * scaleY),
                size = Size((b.width * scaleX).coerceAtLeast(2.5f), (b.height * scaleY).coerceAtLeast(2.5f)),
                style = Stroke(width = 1.2f)
            )
        }
    }

    // 8. Render Interactive Stations (Mystery Box, Pack-a-Punch, Perks, Siege Fabricators, Anvils)
    val stations = map.stations
    for (i in 0 until stations.size) {
        val st = stations[i]
        if (st.roomIndex !in state.unlockedRooms || st.kind == StationKind.BARRICADE_WINDOW) continue
        val stCol = when (st.kind) {
            StationKind.MYSTERY_BOX, StationKind.UPGRADE_ANVIL -> MysteryGold
            StationKind.PACK_A_PUNCH -> PackPurple
            StationKind.SIEGE_FABRICATOR -> ZombieCrimson
            StationKind.PERK_MACHINE -> CryoCyan
            else -> PhosphorGreen
        }
        drawCircle(
            color = stCol,
            radius = if (isDetailedModal) 4.8f else 2.2f,
            center = Offset(st.x * scaleX, st.y * scaleY)
        )
    }

    // 9. Render Active Zombie Horde Blips
    val zombies = state.zombies
    for (i in 0 until zombies.size) {
        val z = zombies[i]
        drawCircle(
            color = ZombieCrimson,
            radius = if (isDetailedModal) 3.8f else 1.8f,
            center = Offset(z.x * scaleX, z.y * scaleY)
        )
    }

    // 10. Render Custom GPS Waypoint Pin
    if (customWaypoint != null) {
        val wx = customWaypoint.x * scaleX
        val wy = customWaypoint.y * scaleY
        val px = state.playerX * scaleX
        val py = state.playerY * scaleY
        drawLine(
            color = MysteryGold.copy(alpha = 0.55f),
            start = Offset(px, py),
            end = Offset(wx, wy),
            strokeWidth = if (isDetailedModal) 2.2f else 1.2f
        )
        drawCircle(
            color = MysteryGold.copy(alpha = 0.32f),
            radius = if (isDetailedModal) 11f else 5.5f,
            center = Offset(wx, wy)
        )
        drawCircle(
            color = MysteryGold,
            radius = if (isDetailedModal) 5.5f else 3f,
            center = Offset(wx, wy)
        )
    }

    // 11. Render Co-Op Peer if present
    if (coopPeer != null) {
        drawCircle(
            color = CryoCyan,
            radius = if (isDetailedModal) 6f else 3.2f,
            center = Offset(coopPeer.x * scaleX, coopPeer.y * scaleY)
        )
    }

    // 12. Render Local Player Position & Directional Aim Cone
    val pMapX = state.playerX * scaleX
    val pMapY = state.playerY * scaleY
    val coneLen = if (isDetailedModal) 28f else 12f
    val halfSpread = 0.45f
    minimapConePath.reset()
    minimapConePath.moveTo(pMapX, pMapY)
    minimapConePath.lineTo(
        pMapX + cos(state.aimAngleRad - halfSpread) * coneLen,
        pMapY + sin(state.aimAngleRad - halfSpread) * coneLen
    )
    minimapConePath.lineTo(
        pMapX + cos(state.aimAngleRad + halfSpread) * coneLen,
        pMapY + sin(state.aimAngleRad + halfSpread) * coneLen
    )
    minimapConePath.close()
    drawPath(path = minimapConePath, color = PhosphorGreen.copy(alpha = 0.35f))

    drawCircle(
        color = Color.White,
        radius = if (isDetailedModal) 6.5f else 3.5f,
        center = Offset(pMapX, pMapY)
    )
    drawCircle(
        color = PhosphorGreen,
        radius = if (isDetailedModal) 4.8f else 2.5f,
        center = Offset(pMapX, pMapY)
    )
}

private fun compassDirectionLabel(dx: Float, dy: Float): String {
    val deg = ((Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())) + 360.0) % 360.0)
    return when {
        deg < 22.5 || deg >= 337.5 -> "E"
        deg < 67.5 -> "SE"
        deg < 112.5 -> "S"
        deg < 157.5 -> "SW"
        deg < 202.5 -> "W"
        deg < 247.5 -> "NW"
        deg < 292.5 -> "N"
        else -> "NE"
    }
}
