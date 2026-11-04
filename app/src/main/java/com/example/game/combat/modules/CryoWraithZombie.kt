package com.example.game.combat.modules

import com.example.game.ElementalAffinity
import com.example.game.ElementalPuddle
import com.example.game.ZombieEntity

class CryoWraithZombie(val zombie: ZombieEntity) {
    private var trailTimerMs: Long = 0L
    private val trailIntervalMs: Long = 500L
    
    companion object {
        var nextPuddleId: Int = 1000
    }

    fun step(deltaMs: Long, puddlesList: MutableList<ElementalPuddle>) {
        trailTimerMs += deltaMs
        while (trailTimerMs >= trailIntervalMs) {
            trailTimerMs -= trailIntervalMs
            puddlesList.add(
                ElementalPuddle(
                    id = nextPuddleId++,
                    x = zombie.x,
                    y = zombie.y,
                    radius = 45f,
                    elementKind = ElementalAffinity.CRYO,
                    colorHex = ElementalAffinity.CRYO.colorHex,
                    hitsPlayers = true,
                    hitsZombies = false,
                    ttlMs = 4500L,
                    tickTimerMs = 0L
                )
            )
        }
    }

    fun onDeath(equippedWeaponId: String, magAmmoMap: MutableMap<String, Int>) {
        val currentAmmo = magAmmoMap[equippedWeaponId] ?: 0
        magAmmoMap[equippedWeaponId] = currentAmmo + 30
    }
}
