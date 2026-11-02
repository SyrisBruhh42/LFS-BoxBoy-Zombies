package com.example.ui.render

import kotlin.math.cos
import kotlin.math.sin

/**
 * Track 9: Stylized 2.5D Isometric / Angled Top-Down Voxel-Box Graphics Pipeline.
 * Combines Sean Cooper Boxhead 2Play boxy character silhouettes and LFS-Block-Studio
 * voxel rig definitions with 2.5D isometric Z-extrusion, real-time directional shadow
 * projection, volumetric muzzle/explosion bloom, and persistent scorch/blood decals.
 */
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
    val bloomIntensity: Float
)

data class PersistentSurfaceDecal(
    val decalId: Int,
    val worldX: Float,
    val worldY: Float,
    val radiusPx: Float,
    val angleRad: Float,
    val colorHex: Long,
    val isScorchMark: Boolean,
    var opacity: Float = 0.85f
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
     * screen coordinates with dynamic point-light shadow casting.
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
        val dx = worldX - lightSourceX
        val dy = worldY - lightSourceY
        val angle = kotlin.math.atan2(dy, dx)
        val shadowLen = (part.heightZPx * 1.35f).coerceIn(8f, 64f)

        return ProjectedVoxelQuad(
            screenX = worldX - part.widthPx * 0.5f,
            screenY = (worldY * ISOMETRIC_TILT_RATIO) - extrusionY,
            screenWidth = part.widthPx,
            screenHeight = part.depthPx * ISOMETRIC_TILT_RATIO,
            extrusionOffsetY = extrusionY,
            shadowTipX = worldX + cos(angle) * shadowLen,
            shadowTipY = (worldY * ISOMETRIC_TILT_RATIO) + sin(angle) * shadowLen * ISOMETRIC_TILT_RATIO,
            topColorHex = part.topColorHex,
            frontColorHex = part.frontFaceColorHex,
            bloomIntensity = bloomBoost.coerceIn(0f, 1.5f)
        )
    }
}
