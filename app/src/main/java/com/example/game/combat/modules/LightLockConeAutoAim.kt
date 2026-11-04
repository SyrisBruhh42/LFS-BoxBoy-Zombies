package com.example.game.combat.modules

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.abs
import kotlin.math.PI

data class AutoAimTarget(
    val id: String,
    val x: Float,
    val y: Float,
    val isWeakPointExposed: Boolean
)

data class LightLockResult(
    val targetId: String?,
    val isCritMode: Boolean,
    val distance: Float,
    val angleDeviationDegrees: Float
)

object LightLockConeAutoAim {
    
    /**
     * Finds the best target within a lantern cone auto-aim volume.
     * Criteria:
     * 1. Must be within `maxRangePx`.
     * 2. Must be within the `coneFovDegrees` (centered on `aimAngleDegrees`).
     * 3. Selects the target closest to the center of the cone (lowest angular deviation).
     * 
     * Crit logic:
     * If the selected target has `isWeakPointExposed` == true AND its angular
     * deviation from the exact aim angle is <= `critToleranceDegrees`, it triggers `isCritMode` = true.
     */
    fun acquireTarget(
        playerX: Float,
        playerY: Float,
        aimAngleDegrees: Float,
        coneFovDegrees: Float = 60f,
        maxRangePx: Float = 300f,
        critToleranceDegrees: Float = 10f,
        targets: List<AutoAimTarget>
    ): LightLockResult {
        var bestTarget: AutoAimTarget? = null
        var minDeviation = Float.MAX_VALUE
        var bestDist = 0f
        
        val halfFov = coneFovDegrees / 2f
        val aimAngleRad = aimAngleDegrees * PI / 180.0
        
        
        for (target in targets) {
            val dx = target.x - playerX
            val dy = target.y - playerY
            val dist = hypot(dx, dy)
            
            if (dist > maxRangePx) continue
            
            val targetAngleRad = atan2(dy, dx)
            
            // Calculate shortest angular difference between aimAngle and targetAngle
            // Using atan2(sin(a-b), cos(a-b)) to get signed difference between -pi and pi
            val diffRad = atan2(sin(targetAngleRad - aimAngleRad), cos(targetAngleRad - aimAngleRad))
            val deviationDegrees = abs(diffRad * 180.0 / PI).toFloat()
            
            if (deviationDegrees <= halfFov) {
                if (deviationDegrees < minDeviation) {
                    minDeviation = deviationDegrees
                    bestTarget = target
                    bestDist = dist
                }
            }
        }
        
        if (bestTarget != null) {
            val isCrit = bestTarget.isWeakPointExposed && minDeviation <= critToleranceDegrees
            return LightLockResult(
                targetId = bestTarget.id,
                isCritMode = isCrit,
                distance = bestDist,
                angleDeviationDegrees = minDeviation
            )
        }
        
        return LightLockResult(
            targetId = null,
            isCritMode = false,
            distance = 0f,
            angleDeviationDegrees = 0f
        )
    }
}
