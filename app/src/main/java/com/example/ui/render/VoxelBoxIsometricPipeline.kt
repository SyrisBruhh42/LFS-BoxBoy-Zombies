package com.example.ui.render

import androidx.compose.ui.geometry.Rect
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Track 9: Stylized 2.5D Isometric / Angled Top-Down Voxel-Box Graphics Pipeline.
 * Combines Sean Cooper Boxhead 2Play boxy character silhouettes and LFS-Block-Studio
 * voxel rig definitions with 2.5D isometric Z-extrusion, real-time directional shadow
 * projection, volumetric muzzle/explosion bloom, and persistent scorch/blood decals.
 */
enum class SurfaceDecalKind {
    ZOMBIE_ICHOR_SPLATTER,
    BARREL_SCORCH_CRATER,
    SCORCH_CRATER,
    SHELL_CASING_CLUSTER,
    CRYO_FROST_RING
}

data class VoxelBoxPartSpec(
    val partName: String,
    val widthPx: Float,
    val depthPx: Float,
    val heightZPx: Float,
    val topColorHex: Long,
    val frontFaceColorHex: Long,
    val sideFaceColorHex: Long
)

data class ProjectedVoxelQuad(
    val screenX: Float,
    val screenY: Float,
    val screenWidth: Float,
    val screenHeight: Float,
    val extrusionOffsetY: Float,
    val shadowTipX: Float,
    val shadowTipY: Float,
    val topColorHex: Long,
    val frontColorHex: Long,
    val bloomIntensity: Float,
    val sortDepthKey: Float = screenY + extrusionOffsetY,
    val topFaceRect: Rect = Rect(
        screenX,
        screenY - screenHeight,
        screenX + screenWidth,
        screenY
    ),
    val frontFaceRect: Rect = Rect(
        screenX,
        screenY,
        screenX + screenWidth,
        screenY + extrusionOffsetY
    ),
    val sideFaceRect: Rect = Rect(
        screenX + screenWidth,
        screenY - screenHeight * 0.25f,
        screenX + screenWidth + 6f,
        screenY + extrusionOffsetY
    ),
    val shadowRect: Rect = Rect(
        screenX - 4f,
        screenY + extrusionOffsetY - 2f,
        shadowTipX,
        shadowTipY
    ),
    val topColorArgb: Long = topColorHex,
    val frontColorArgb: Long = frontColorHex,
    val sideColorArgb: Long = frontColorHex,
    val strokeHighlightColor: Long = 0xFF00E5FF
)

data class PersistentSurfaceDecal(
    val decalId: Any,
    val worldX: Float,
    val worldY: Float,
    val radiusPx: Float,
    val angleRad: Float = 0f,
    val colorHex: Long = 0x993A0CA3,
    val isScorchMark: Boolean = false,
    var opacity: Float = 0.85f,
    val kind: SurfaceDecalKind = if (isScorchMark) SurfaceDecalKind.BARREL_SCORCH_CRATER else SurfaceDecalKind.ZOMBIE_ICHOR_SPLATTER,
    val colorArgb: Long = colorHex,
    val createdAtTick: Long = 0L
)

object VoxelBoxIsometricPipeline {
    const val ISOMETRIC_TILT_RATIO = 0.78f // Classic Boxhead2 angled top-down foreshortening
    const val Z_EXTRUSION_SCALE = 0.65f
    const val MAX_PERSISTENT_DECALS = 192

    val BOXBOY_HERO_RIG: List<VoxelBoxPartSpec> = listOf(
        VoxelBoxPartSpec("torso_cube", 20f, 14f, 18f, 0xFF00E5FF, 0xFF0097A7, 0xFF006064),
        VoxelBoxPartSpec("head_cube", 18f, 18f, 16f, 0xFFFFE0B2, 0xFFFFCC80, 0xFFFFB74D),
        VoxelBoxPartSpec("headband_voxel", 19f, 19f, 4f, 0xFFFF2A54, 0xFFD50000, 0xFF9B0000)
    )

    /**
     * Projects a 3D world-space voxel box `(worldX, worldY, heightZ)` into 2.5D angled top-down
     * screen coordinates with 1:1 `worldY` camera alignment (`screenY = worldY - heightZ * Z_EXTRUSION_SCALE`)
     * and dynamic point-light shadow casting.
     */
    fun projectVoxelBox25D(
        worldX: Float,
        worldY: Float,
        part: VoxelBoxPartSpec,
        lightSourceX: Float,
        lightSourceY: Float,
        bloomBoost: Float = 0f
    ): ProjectedVoxelQuad {
        val extrusionY = part.heightZPx * Z_EXTRUSION_SCALE
        val topBottomY = worldY - extrusionY
        val topHeight = part.depthPx * ISOMETRIC_TILT_RATIO
        val dx = worldX - lightSourceX
        val dy = worldY - lightSourceY
        val angle = atan2(dy, dx)
        val shadowLen = (part.heightZPx * 1.35f).coerceIn(8f, 64f)
        val leftX = worldX - part.widthPx * 0.5f

        return ProjectedVoxelQuad(
            screenX = leftX,
            screenY = topBottomY,
            screenWidth = part.widthPx,
            screenHeight = topHeight,
            extrusionOffsetY = extrusionY,
            shadowTipX = worldX + cos(angle) * shadowLen,
            shadowTipY = worldY + sin(angle) * shadowLen * ISOMETRIC_TILT_RATIO,
            topColorHex = part.topColorHex,
            frontColorHex = part.frontFaceColorHex,
            bloomIntensity = bloomBoost.coerceIn(0f, 1.5f),
            sortDepthKey = worldY,
            topFaceRect = Rect(leftX, topBottomY - topHeight, leftX + part.widthPx, topBottomY),
            frontFaceRect = Rect(leftX, topBottomY, leftX + part.widthPx, worldY),
            sideFaceRect = Rect(leftX + part.widthPx, topBottomY - topHeight * 0.25f, leftX + part.widthPx + 6f, worldY),
            topColorArgb = part.topColorHex,
            frontColorArgb = part.frontFaceColorHex,
            sideColorArgb = part.sideFaceColorHex
        )
    }

    /**
     * Explicit dimensions overload for pillars, walls, and obstacles in OrthographicWorldRenderer.
     */
    fun projectVoxelBox25D(
        entityId: String = "",
        worldX: Float,
        worldY: Float,
        widthX: Float,
        depthY: Float,
        heightZ: Float,
        primaryLightX: Float = worldX - 120f,
        primaryLightY: Float = worldY - 120f,
        sunAzimuthRad: Float = 0.78f,
        topColorHex: Long = 0xFF283740,
        frontColorHex: Long = 0xFF1C2830,
        sideColorHex: Long = 0xFF121A20,
        bloomBoost: Float = 0f
    ): ProjectedVoxelQuad {
        val spec = VoxelBoxPartSpec(
            partName = entityId.ifEmpty { "voxel_box" },
            widthPx = widthX,
            depthPx = depthY,
            heightZPx = heightZ,
            topColorHex = topColorHex,
            frontFaceColorHex = frontColorHex,
            sideFaceColorHex = sideColorHex
        )
        val dx = worldX - primaryLightX
        val dy = worldY - primaryLightY
        val effectiveAngle = if (dx == 0f && dy == 0f) sunAzimuthRad else atan2(dy, dx)
        val extrusionY = heightZ * Z_EXTRUSION_SCALE
        val topBottomY = worldY - extrusionY
        val topHeight = depthY * ISOMETRIC_TILT_RATIO
        val shadowLen = (heightZ * 1.35f).coerceIn(8f, 64f)
        val leftX = worldX - widthX * 0.5f

        return ProjectedVoxelQuad(
            screenX = leftX,
            screenY = topBottomY,
            screenWidth = widthX,
            screenHeight = topHeight,
            extrusionOffsetY = extrusionY,
            shadowTipX = worldX + cos(effectiveAngle) * shadowLen,
            shadowTipY = worldY + sin(effectiveAngle) * shadowLen * ISOMETRIC_TILT_RATIO,
            topColorHex = spec.topColorHex,
            frontColorHex = spec.frontFaceColorHex,
            bloomIntensity = bloomBoost.coerceIn(0f, 1.5f),
            sortDepthKey = worldY,
            topFaceRect = Rect(leftX, topBottomY - topHeight, leftX + widthX, topBottomY),
            frontFaceRect = Rect(leftX, topBottomY, leftX + widthX, worldY),
            sideFaceRect = Rect(leftX + widthX, topBottomY - topHeight * 0.25f, leftX + widthX + 6f, worldY),
            topColorArgb = spec.topColorHex,
            frontColorArgb = spec.frontFaceColorHex,
            sideColorArgb = spec.sideFaceColorHex
        )
    }
}

