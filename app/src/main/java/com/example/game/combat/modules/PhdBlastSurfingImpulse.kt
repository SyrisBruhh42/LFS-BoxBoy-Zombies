package com.example.game.combat.modules

import kotlin.math.hypot
import kotlin.math.max

data class PlayerState(
    var hp: Int,
    val maxHp: Int,
    var vx: Float,
    var vy: Float,
    var currentAmmo: Int,
    val maxAmmo: Int,
    var isBlastSurfingActive: Boolean = false
)

object PhdBlastSurfingImpulse {
    /**
     * Applies blast surfing impulse from an explosion if the player is in the blast radius.
     * Takes zero self-damage if blast surfing is active.
     *
     * @param player the current player state
     * @param playerX player's X coordinate
     * @param playerY player's Y coordinate
     * @param explosionX explosion center X coordinate
     * @param explosionY explosion center Y coordinate
     * @param blastRadius explosion blast radius
     * @param baseImpulse the magnitude of the impulse applied
     * @param rawDamage the damage that would normally be applied
     */
    fun applyBlastSurfingImpulse(
        player: PlayerState,
        playerX: Float,
        playerY: Float,
        explosionX: Float,
        explosionY: Float,
        blastRadius: Float,
        baseImpulse: Float,
        rawDamage: Int
    ) {
        val dx = playerX - explosionX
        val dy = playerY - explosionY
        val distance = hypot(dx, dy)

        if (distance <= blastRadius) {
            val impulseMag = baseImpulse * (1f - (distance / blastRadius).coerceIn(0f, 1f))
            if (distance > 0f) {
                player.vx += (dx / distance) * impulseMag
                player.vy += (dy / distance) * impulseMag
            } else {
                player.vy += impulseMag
            }

            if (!player.isBlastSurfingActive) {
                player.hp = max(0, player.hp - rawDamage)
            }
        }
    }

    /**
     * Executes an air-dash reload hook, giving the player a dash impulse
     * and instantly refilling their current weapon's ammo.
     *
     * @param player the current player state
     * @param dashDirX the X direction of the dash (normalized)
     * @param dashDirY the Y direction of the dash (normalized)
     * @param dashSpeed the speed magnitude of the dash
     */
    fun triggerAirDashReload(
        player: PlayerState,
        dashDirX: Float,
        dashDirY: Float,
        dashSpeed: Float
    ) {
        player.vx = dashDirX * dashSpeed
        player.vy = dashDirY * dashSpeed
        player.currentAmmo = player.maxAmmo
    }
}
