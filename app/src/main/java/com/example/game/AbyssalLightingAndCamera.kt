package com.example.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * PHASE 1: "Abyssal Bio-Noir" Lighting & Camera Tension Controller
 *
 * Implements:
 * 1. Dynamic Camera Framing (CameraTensionComponent):
 *    - Corridor Exploration State: Tight claustrophobic zoom (2.2x) to emphasize dark maze walls
 *      and directional sound/light cues.
 *    - Siege / Boss Anomaly State: Smoothly tweens (Cubic Ease-In-Out, 0.8s / 800ms duration) to
 *      wide tactical framing (1.1x) when a Siege Fabricator or Boss Anomaly is active.
 * 2. Mobile-Optimized Chiaroscuro Lighting:
 *    - Dark ambient CanvasModulate layer over the world.
 *    - Strict "Two-Shadow" Hardware Limit: Only (1) the Player's directional lantern/muzzle light
 *      and (2) one active Boss/Siege Beacon cast 2D shadow occlusion hulls at any time to guarantee
 *      60 FPS on Android GPUs.
 *    - Unshadowed Additive Glows: Weapon/armor drops, Calcified Stalker (Archer) laser telegraphs,
 *      Abyssal Weaver (Mage) elemental projectiles/puddles, and Boxhead explosive barrels render
 *      fast radial additive glows.
 *    - Simplified Shadow Hulls: Uses 4-to-6 vertex convex hulls inside wall/pillar geometry.
 */
enum class CameraFramingState(
    val displayName: String,
    val targetZoomMultiplier: Float
) {
    CORRIDOR_EXPLORATION("Corridor Exploration (2.2x Claustrophobic)", 2.2f),
    TACTICAL_COMBAT("Standard Tactical (1.45x)", 1.45f),
    SIEGE_BOSS_WIDE("Siege / Boss Anomaly (1.1x Wide)", 1.1f)
}

enum class LightingAtmosphereMode(val label: String) {
    ABYSSAL_BIO_NOIR("Abyssal Bio-Noir (Chiaroscuro 2-Shadow)"),
    TACTICAL_NIGHT_VISION("Phosphor Night-Ops (Low Darkness)"),
    CLASSIC_ARCADE_BRIGHT("Classic Boxhead Bright (No Darkness)")
}

data class CameraTensionState(
    val framingState: CameraFramingState = CameraFramingState.CORRIDOR_EXPLORATION,
    val currentZoom: Float = 2.2f,
    val startZoom: Float = 2.2f,
    val targetZoom: Float = 2.2f,
    val tweenElapsedMs: Long = 800L,
    val tweenDurationMs: Long = 800L,
    val autoTensionEnabled: Boolean = true,
    val lightingMode: LightingAtmosphereMode = LightingAtmosphereMode.TACTICAL_NIGHT_VISION,
    val muzzleFlashIntensity: Float = 0f
)

/**
 * Simplified 4-to-6 vertex convex shadow occluder hull extracted from wall/pillar geometry.
 */
data class ConvexShadowHull(
    val id: Int,
    val vertices: List<Offset>,
    val center: Offset,
    val boundingRadius: Float
) {
    companion object {
        /**
         * Generates a simplifiedchamfered 6-vertex hex/octagonal or 4-vertex inset convex hull
         * inside a rectangular pillar so shadows never snag on jagged sprite edges.
         */
        fun fromRectInset(id: Int, rect: Rect, insetPx: Float = 3f): ConvexShadowHull {
            val l = rect.left + insetPx
            val r = rect.right - insetPx
            val t = rect.top + insetPx
            val b = rect.bottom - insetPx
            val chamfer = min(rect.width, rect.height) * 0.14f
            // 6-vertex simplified convex hull
            val verts = listOf(
                Offset(l + chamfer, t),
                Offset(r, t),
                Offset(r, b - chamfer),
                Offset(r - chamfer, b),
                Offset(l, b),
                Offset(l, t + chamfer)
            )
            val c = rect.center
            val rad = hypot(rect.width / 2f, rect.height / 2f)
            return ConvexShadowHull(id = id, vertices = verts, center = c, boundingRadius = rad)
        }
    }
}

/**
 * Represents a 2D light source respecting the Two-Shadow Hardware Limit.
 */
data class PointLightSource2D(
    val id: String,
    val x: Float,
    val y: Float,
    val radius: Float,
    val color: Color,
    val intensity: Float = 1.0f,
    val shadowEnabled: Boolean = false,
    val isDirectionalCone: Boolean = false,
    val coneAngleRad: Float = 0f,
    val coneHalfSpreadRad: Float = (PI / 3.4).toFloat() // ~53 deg half-angle (106 deg total cone)
)

object CameraTensionComponent {
    private const val TWEEN_DURATION_MS = 800L

    /**
     * Cubic ease-in-out (equivalent to Godot Tween.TRANS_CUBIC + EASE_IN_OUT) over 0.8s (800ms).
     */
    fun cubicEaseInOut(t: Float): Float {
        val clamped = t.coerceIn(0f, 1f)
        return if (clamped < 0.5f) {
            4f * clamped * clamped * clamped
        } else {
            val f = (-2f * clamped + 2f)
            1f - (f * f * f) / 2f
        }
    }

    fun stepCameraTension(
        current: CameraTensionState,
        deltaMs: Long,
        isSiegeActive: Boolean,
        hasBossAnomaly: Boolean,
        nearbyHostileCount: Int,
        didFireThisTick: Boolean
    ): CameraTensionState {
        // Keep framing rock-steady during normal exploration/combat; only widen automatically on Siege or Boss Anomaly
        val desiredFraming = if (!current.autoTensionEnabled) {
            current.framingState
        } else if (isSiegeActive || hasBossAnomaly) {
            CameraFramingState.SIEGE_BOSS_WIDE
        } else {
            CameraFramingState.CORRIDOR_EXPLORATION
        }

        val stateWithTarget = if (desiredFraming != current.framingState) {
            current.copy(
                framingState = desiredFraming,
                startZoom = current.currentZoom,
                targetZoom = desiredFraming.targetZoomMultiplier,
                tweenElapsedMs = 0L,
                tweenDurationMs = TWEEN_DURATION_MS
            )
        } else {
            current
        }

        val isTweenFinished = stateWithTarget.tweenElapsedMs >= stateWithTarget.tweenDurationMs
        if (isTweenFinished && !didFireThisTick && stateWithTarget.muzzleFlashIntensity <= 0f) {
            return stateWithTarget
        }

        val nextElapsed = (stateWithTarget.tweenElapsedMs + deltaMs).coerceAtMost(stateWithTarget.tweenDurationMs)
        val progress = nextElapsed.toFloat() / stateWithTarget.tweenDurationMs.coerceAtLeast(1L).toFloat()
        val eased = cubicEaseInOut(progress)
        val interpolatedZoom = stateWithTarget.startZoom + (stateWithTarget.targetZoom - stateWithTarget.startZoom) * eased

        val nextMuzzle = if (didFireThisTick) {
            1.0f
        } else {
            (stateWithTarget.muzzleFlashIntensity - (deltaMs / 110f)).coerceAtLeast(0f)
        }

        return stateWithTarget.copy(
            currentZoom = interpolatedZoom,
            tweenElapsedMs = nextElapsed,
            muzzleFlashIntensity = nextMuzzle
        )
    }
}

object AbyssalChiaroscuroLightingEngine {

    /**
     * Checks whether a world coordinate is illuminated by the Player's directional lantern cone
     * or close-range personal aura. Used by Phase 4 Light-Lock Auto-Aim & crit bonus.
     */
    fun isPointIlluminatedByPlayer(
        targetX: Float,
        targetY: Float,
        playerX: Float,
        playerY: Float,
        aimAngleRad: Float,
        lanternRange: Float = 440f,
        coneHalfSpreadRad: Float = 0.56f
    ): Boolean {
        val dx = targetX - playerX
        val dy = targetY - playerY
        val dist = hypot(dx, dy)
        if (dist <= 78f) return true // Personal lantern proximity halo
        if (dist > lanternRange) return false

        val angleToTarget = atan2(dy, dx)
        val diff = normalizeAngleRad(angleToTarget - aimAngleRad)
        return abs(diff) <= coneHalfSpreadRad
    }

    fun normalizeAngleRad(angle: Float): Float {
        var a = angle
        while (a > PI.toFloat()) a -= (2.0 * PI).toFloat()
        while (a < -PI.toFloat()) a += (2.0 * PI).toFloat()
        return a
    }

    /**
     * Builds the simplified 6-vertex convex shadow hulls for obstacles near the camera.
     */
    fun buildSimplifiedShadowHulls(obstacles: List<Rect>, playerX: Float, playerY: Float): List<ConvexShadowHull> {
        return obstacles
            .filter { hypot(it.center.x - playerX, it.center.y - playerY) <= 680f }
            .mapIndexed { idx, rect -> ConvexShadowHull.fromRectInset(idx, rect) }
    }

    /**
     * Enforces the strict "Two-Shadow" Hardware Limit:
     * - Light #1 (shadowEnabled = true): Player directional lantern + muzzle flash
     * - Light #2 (shadowEnabled = true): Nearest Boss Anomaly or Active Siege/Perk Beacon
     * All other lights (barrels, loot drops, elemental puddles, archer lasers) have shadowEnabled = false.
     */
    fun collectFrameLights(
        state: GameSessionState,
        lanternRadiusBonus: Float = 0f
    ): List<PointLightSource2D> {
        val lights = mutableListOf<PointLightSource2D>()

        // SHADOW SOURCE #1: Player Directional Lantern / Muzzle Light
        val muzzleBoost = state.cameraTension.muzzleFlashIntensity * 95f
        lights.add(
            PointLightSource2D(
                id = "player_lantern_shadow_1",
                x = state.playerX,
                y = state.playerY,
                radius = 430f + lanternRadiusBonus + muzzleBoost,
                color = Color(0xFF90F8FF),
                intensity = 0.95f + state.cameraTension.muzzleFlashIntensity * 0.25f,
                shadowEnabled = true,
                isDirectionalCone = true,
                coneAngleRad = state.aimAngleRad,
                coneHalfSpreadRad = 0.58f
            )
        )

        // SHADOW SOURCE #2: Active Boss Anomaly OR Nearest High-Tier Beacon
        val boss = state.zombies.firstOrNull {
            it.archetype == ZombieArchetype.DEVIL_BOSS || it.archetype == ZombieArchetype.ABYSSAL_WEAVER
        }
        if (boss != null) {
            lights.add(
                PointLightSource2D(
                    id = "boss_anomaly_shadow_2",
                    x = boss.x,
                    y = boss.y,
                    radius = 270f,
                    color = Color(0xFFFF2A54),
                    intensity = 0.85f,
                    shadowEnabled = true,
                    isDirectionalCone = false
                )
            )
        } else {
            val nearestBeacon = state.map.stations
                .filter { it.roomIndex in state.unlockedRooms }
                .minByOrNull { hypot(it.x - state.playerX, it.y - state.playerY) }
            if (nearestBeacon != null) {
                lights.add(
                    PointLightSource2D(
                        id = "beacon_shadow_2",
                        x = nearestBeacon.x,
                        y = nearestBeacon.y,
                        radius = 210f,
                        color = Color(0xFFB388FF),
                        intensity = 0.72f,
                        shadowEnabled = true,
                        isDirectionalCone = false
                    )
                )
            }
        }

        // UNSHADOWED ADDITIVE GLOWS (shadowEnabled = false)
        // 1. Explosive Red Barrels
        state.map.barrels.filter { !it.isDetonated && it.roomIndex in state.unlockedRooms }.forEach { barrel ->
            if (hypot(barrel.x - state.playerX, barrel.y - state.playerY) <= 650f) {
                lights.add(
                    PointLightSource2D(
                        id = "barrel_glow_${barrel.id}",
                        x = barrel.x,
                        y = barrel.y,
                        radius = 78f,
                        color = Color(0xFFFF4D2A),
                        intensity = 0.42f,
                        shadowEnabled = false
                    )
                )
            }
        }

        // 2. Elemental Hazard Puddles (Phase 3)
        state.elementalPuddles.forEach { puddle ->
            lights.add(
                PointLightSource2D(
                    id = "puddle_glow_${puddle.id}",
                    x = puddle.x,
                    y = puddle.y,
                    radius = puddle.radius * 1.45f,
                    color = Color(puddle.colorHex),
                    intensity = 0.50f,
                    shadowEnabled = false
                )
            )
        }

        // 3. Modular Loot Drops (Phase 4)
        state.lootDrops.forEach { drop ->
            lights.add(
                PointLightSource2D(
                    id = "loot_glow_${drop.id}",
                    x = drop.x,
                    y = drop.y,
                    radius = 92f,
                    color = Color(drop.rarity.colorHex),
                    intensity = 0.65f,
                    shadowEnabled = false
                )
            )
        }

        // 4. Deployed Fortifications (Phase 3)
        state.fortifications.forEach { fort ->
            if (fort.kind != FortificationType.BARRICADE_WALL) {
                lights.add(
                    PointLightSource2D(
                        id = "fort_glow_${fort.id}",
                        x = fort.x,
                        y = fort.y,
                        radius = 85f,
                        color = Color(fort.kind.colorHex),
                        intensity = 0.48f,
                        shadowEnabled = false
                    )
                )
            }
        }

        return lights
    }

    /**
     * Renders the mobile-optimized Chiaroscuro lighting, 2-shadow convex hull volumes,
     * directional lantern cone, and unshadowed additive glows onto the world DrawScope.
     */
    fun DrawScope.renderChiaroscuroLightingPass(
        state: GameSessionState,
        lights: List<PointLightSource2D>,
        shadowHulls: List<ConvexShadowHull>
    ) {
        val mode = state.cameraTension.lightingMode
        if (mode == LightingAtmosphereMode.CLASSIC_ARCADE_BRIGHT) return

        val px = state.playerX
        val py = state.playerY

        // 1. Unshadowed Additive Glows (BlendMode.Plus) for barrels, loot drops, elemental puddles, beacons
        lights.forEach { light ->
            val glowAlpha = if (light.shadowEnabled) 0.22f * light.intensity else 0.34f * light.intensity
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        light.color.copy(alpha = glowAlpha),
                        light.color.copy(alpha = glowAlpha * 0.35f),
                        Color.Transparent
                    ),
                    center = Offset(light.x, light.y),
                    radius = max(light.radius, 12f)
                ),
                radius = max(light.radius, 12f),
                center = Offset(light.x, light.y),
                blendMode = BlendMode.Plus
            )
        }

        // 2. Player Directional Lantern Cone Highlight (Chiaroscuro Beam)
        val playerLight = lights.firstOrNull { it.isDirectionalCone }
        if (playerLight != null) {
            val conePath = Path().apply {
                moveTo(px, py)
                val steps = 14
                val startAng = playerLight.coneAngleRad - playerLight.coneHalfSpreadRad
                val endAng = playerLight.coneAngleRad + playerLight.coneHalfSpreadRad
                for (i in 0..steps) {
                    val t = i.toFloat() / steps.toFloat()
                    val ang = startAng + (endAng - startAng) * t
                    val rx = px + cos(ang) * playerLight.radius
                    val ry = py + sin(ang) * playerLight.radius
                    lineTo(rx, ry)
                }
                close()
            }
            drawPath(
                path = conePath,
                brush = Brush.radialGradient(
                    colors = listOf(
                        playerLight.color.copy(alpha = 0.26f),
                        playerLight.color.copy(alpha = 0.10f),
                        Color.Transparent
                    ),
                    center = Offset(px, py),
                    radius = playerLight.radius
                ),
                blendMode = BlendMode.Plus
            )
            // Crisp tactical cone rim lines
            val leftRimX = px + cos(playerLight.coneAngleRad - playerLight.coneHalfSpreadRad) * playerLight.radius * 0.85f
            val leftRimY = py + sin(playerLight.coneAngleRad - playerLight.coneHalfSpreadRad) * playerLight.radius * 0.85f
            val rightRimX = px + cos(playerLight.coneAngleRad + playerLight.coneHalfSpreadRad) * playerLight.radius * 0.85f
            val rightRimY = py + sin(playerLight.coneAngleRad + playerLight.coneHalfSpreadRad) * playerLight.radius * 0.85f
            drawLine(
                color = playerLight.color.copy(alpha = 0.22f),
                start = Offset(px, py),
                end = Offset(leftRimX, leftRimY),
                strokeWidth = 1.8f
            )
            drawLine(
                color = playerLight.color.copy(alpha = 0.22f),
                start = Offset(px, py),
                end = Offset(rightRimX, rightRimY),
                strokeWidth = 1.8f
            )
        }

        // 3. Two-Shadow Hardware Limit: Project Simplified Convex Shadow Volumes from the max 2 Shadow Lights
        val shadowedLights = lights.filter { it.shadowEnabled }.take(2)
        val shadowColor = if (mode == LightingAtmosphereMode.ABYSSAL_BIO_NOIR) {
            Color(0xDD030605)
        } else {
            Color(0x88030806)
        }

        shadowedLights.forEach { light ->
            val lightPos = Offset(light.x, light.y)
            shadowHulls.forEach { hull ->
                val distToHull = hypot(hull.center.x - lightPos.x, hull.center.y - lightPos.y)
                if (distToHull in 18f..(light.radius * 1.15f)) {
                    // Find extreme silhouette vertices relative to light source
                    var minAngle = Float.MAX_VALUE
                    var maxAngle = -Float.MAX_VALUE
                    var minVertex = hull.vertices.first()
                    var maxVertex = hull.vertices.first()
                    val baseAngle = atan2(hull.center.y - lightPos.y, hull.center.x - lightPos.x)

                    hull.vertices.forEach { v ->
                        val relAng = normalizeAngleRad(atan2(v.y - lightPos.y, v.x - lightPos.x) - baseAngle)
                        if (relAng < minAngle) {
                            minAngle = relAng
                            minVertex = v
                        }
                        if (relAng > maxAngle) {
                            maxAngle = relAng
                            maxVertex = v
                        }
                    }

                    val shadowLength = light.radius * 1.35f
                    val dirMinX = (minVertex.x - lightPos.x)
                    val dirMinY = (minVertex.y - lightPos.y)
                    val lenMin = hypot(dirMinX, dirMinY).coerceAtLeast(1f)
                    val farMin = Offset(
                        minVertex.x + (dirMinX / lenMin) * shadowLength,
                        minVertex.y + (dirMinY / lenMin) * shadowLength
                    )

                    val dirMaxX = (maxVertex.x - lightPos.x)
                    val dirMaxY = (maxVertex.y - lightPos.y)
                    val lenMax = hypot(dirMaxX, dirMaxY).coerceAtLeast(1f)
                    val farMax = Offset(
                        maxVertex.x + (dirMaxX / lenMax) * shadowLength,
                        maxVertex.y + (dirMaxY / lenMax) * shadowLength
                    )

                    val umbraPath = Path().apply {
                        moveTo(minVertex.x, minVertex.y)
                        lineTo(farMin.x, farMin.y)
                        lineTo(farMax.x, farMax.y)
                        lineTo(maxVertex.x, maxVertex.y)
                        close()
                    }
                    drawPath(path = umbraPath, color = shadowColor)

                    // Draw subtle convex occluder hull outline for bio-noir readability
                    val hullOutline = Path().apply {
                        hull.vertices.forEachIndexed { idx, pt ->
                            if (idx == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
                        }
                        close()
                    }
                    drawPath(
                        path = hullOutline,
                        color = light.color.copy(alpha = 0.25f),
                        style = Stroke(width = 1.5f)
                    )
                }
            }
        }

        // 4. Dark Ambient CanvasModulate Vignette Shroud around Player
        val shroudRadius = if (mode == LightingAtmosphereMode.ABYSSAL_BIO_NOIR) 560f else 760f
        val shroudAlpha = if (mode == LightingAtmosphereMode.ABYSSAL_BIO_NOIR) 0.82f else 0.45f
        val shroudRectSize = 2400f
        drawRect(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.0f to Color.Transparent,
                    0.26f to Color(0x33040807),
                    0.62f to Color(0xAA030605).copy(alpha = shroudAlpha * 0.72f),
                    1.0f to Color(0xF2020403).copy(alpha = shroudAlpha)
                ),
                center = Offset(px, py),
                radius = shroudRadius
            ),
            topLeft = Offset(px - shroudRectSize / 2f, py - shroudRectSize / 2f),
            size = Size(shroudRectSize, shroudRectSize)
        )
    }
}
