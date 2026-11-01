package com.example.game

import com.example.data.DEFAULT_WEAPONS
import com.example.data.PerkType
import com.example.viewmodel.AiDirectiveResult
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * 100% Offline, On-Device Tactical AI & Procedural Mission Synthesizer.
 *
 * Ensures every AI feature in BoxBoy Zombies (Procedural Mission Architect,
 * Live Tactical Radio Callouts, Threat Telemetry Analysis, and Autonomous Bot Strategy)
 * works 100% offline with zero internet connection and zero Google account required.
 */
object OfflineTacticalAiEngine {

    /**
     * Deterministically synthesizes a complete [AiDirectiveResult] on-device from
     * a user's natural-language prompt and current [GameSessionState] telemetry.
     */
    fun synthesizeOfflineDirective(
        userPrompt: String,
        state: GameSessionState
    ): AiDirectiveResult {
        val cleanPrompt = userPrompt.trim().ifBlank {
            "High-voltage Cryo Bunker siege with extra explosive barrels and fast sprinter waves"
        }
        val lower = cleanPrompt.lowercase()

        // 1. Compute deterministic positive seed from prompt + current wave
        var hash = 1125899906842597L
        for (ch in cleanPrompt) {
            hash = 31L * hash + ch.code.toLong()
        }
        val mapSeed = (abs(hash xor (state.wave.toLong() * 7919L)) % 89999999L) + 10000000L

        // 2. Semantic keyword matching for recommended weapon
        val recommendedWeaponId = when {
            lower.contains("ray") || lower.contains("alien") || lower.contains("plasma") || lower.contains("wonder") -> "raybox"
            lower.contains("rail") || lower.contains("sniper") || lower.contains("pierce") || lower.contains("beam") -> "railgun"
            lower.contains("barrel") || lower.contains("explos") || lower.contains("trap") || lower.contains("bomb") -> "barrels"
            lower.contains("shotgun") || lower.contains("close") || lower.contains("buckshot") || lower.contains("breach") -> "shotgun"
            lower.contains("ar") || lower.contains("rifle") || lower.contains("commando") || lower.contains("military") -> "ar"
            lower.contains("uzi") || lower.contains("smg") || lower.contains("fast") || lower.contains("spray") || lower.contains("run") -> "uzi"
            else -> {
                // Pick a strong progression upgrade relative to current loadout
                val candidates = listOf("shotgun", "ar", "railgun", "raybox", "barrels", "uzi")
                candidates[(mapSeed % candidates.size).toInt()]
            }
        }

        // 3. Determine challenge intensity -> startingWave & bonusPoints
        val isNightmare = lower.contains("nightmare") || lower.contains("boss") || lower.contains("devil") ||
            lower.contains("extreme") || lower.contains("hard") || lower.contains("hell")
        val isChill = lower.contains("chill") || lower.contains("easy") || lower.contains("starter") ||
            lower.contains("casual") || lower.contains("warmup")
        val isSpeed = lower.contains("speed") || lower.contains("sprint") || lower.contains("runner") ||
            lower.contains("rush")

        val startingWave = when {
            isNightmare -> (state.wave + 4).coerceIn(5, 20)
            isSpeed -> (state.wave + 2).coerceIn(3, 15)
            isChill -> 1
            else -> (state.wave + 1).coerceIn(2, 12)
        }

        val bonusPoints = when {
            isNightmare -> 4500
            isSpeed -> 3000
            isChill -> 2500
            else -> 2000 + ((mapSeed % 1500L).toInt() / 100) * 100
        }

        // 4. Construct Operation Codename
        val codenamePrefix = when {
            isNightmare -> "OP: RED DEVIL PROTOCOL"
            isSpeed -> "OP: HYPER SPRINTER BLITZ"
            lower.contains("cryo") || lower.contains("ice") -> "OP: CRYO SECTOR LOCKDOWN"
            lower.contains("barrel") || lower.contains("explos") -> "OP: CHAIN DETONATION"
            recommendedWeaponId == "raybox" -> "OP: PHOSPHOR WONDER SIEGE"
            recommendedWeaponId == "railgun" -> "OP: MAGNETIC CORRIDOR"
            else -> "OP: BUNKER OMEGA-${mapSeed.toString().takeLast(3)}"
        }

        val weaponSpec = DEFAULT_WEAPONS.find { it.id == recommendedWeaponId } ?: DEFAULT_WEAPONS.first()

        // 5. Synthesize tactical briefing using real bunker topology
        val briefing = buildString {
            append("On-Device Tactical Architect seeded Bunker #$mapSeed for Wave $startingWave ")
            append("with ${weaponSpec.name} and +$bonusPoints PTS. ")
            when {
                isNightmare -> append("Prioritize unlocking Sector 02 East Armory and Sector 06 Pack-a-Punch Altar immediately, and kite Boxhead Devil bosses past primed Red Barrels.")
                isSpeed -> append("Hold wide perimeter lanes in Sector 01 Courtyard, acquire Stamin-Up & Speed Cola perks early, and funnel Hell Runners through doorway choke points.")
                recommendedWeaponId == "railgun" || recommendedWeaponId == "ar" ->
                    append("Line up zombie hordes along straight bunker corridors to maximize multi-target penetration and preserve magazine economy.")
                else -> append("Clear the East Armory debris door (750 PTS) on Wave $startingWave, cluster Shamblers near explosive barrels, and maintain reload discipline.")
            }
        }

        return AiDirectiveResult(
            operationTitle = "$codenamePrefix [OFFLINE AI]",
            mapSeed = mapSeed,
            startingWave = startingWave,
            bonusPoints = bonusPoints,
            recommendedWeaponId = recommendedWeaponId,
            tacticalBriefing = briefing
        )
    }

    /**
     * Generates an instant, 100% offline tactical survival radio callout by analyzing
     * real-time player health, ammo telemetry, active perks, nearby stations, and hostile composition.
     */
    fun generateOfflineRadioCallout(state: GameSessionState): String {
        val devils = state.zombies.count { it.archetype == ZombieArchetype.DEVIL_BOSS }
        val sprinters = state.zombies.count { it.archetype == ZombieArchetype.SPRINTER }
        val crawlers = state.zombies.count { it.archetype == ZombieArchetype.CRAWLER }
        val activeBarrels = state.map.barrels.count { !it.isDetonated && it.roomIndex in state.unlockedRooms }
        val nearestDist = state.zombies.minOfOrNull {
            hypot(it.x - state.playerX, it.y - state.playerY)
        }?.roundToInt() ?: 999

        val wpn = state.currentWeapon
        val wpnLvl = state.currentWeaponLevel
        val upgradeCost = wpn.upgradeBaseCost * wpnLvl

        val primaryAlert = when {
            state.hp <= 40 ->
                "CRITICAL VITALS (${state.hp}/${state.maxHp} HP): Break line-of-sight around bunker pillars immediately and prioritize Juggernog (+100 Max HP) or Quick Revive!"
            devils > 0 ->
                "BOSS ALERT ($devils Boxhead Devil${if (devils > 1) "s" else ""} active, closest ${nearestDist}px): Do not trade hits—lure the Devil past $activeBarrels primed Red Barrels and fire from maximum range with ${wpn.name}!"
            sprinters >= 4 ->
                "HIGH-SPEED SWARM ($sprinters Hell Runners closing at ${nearestDist}px): Backpedal through doorway choke points and trigger a tactical reload before your ${wpn.shortCode} magazine (${state.currentAmmoCount}/${state.currentMaxMagSize}) runs dry."
            state.currentAmmoCount <= (state.currentMaxMagSize / 4).coerceAtLeast(2) ->
                "LOW MAGAZINE WARNING (${state.currentAmmoCount}/${state.currentMaxMagSize} rounds): Hit RELOAD [Y] now while nearest hostile is ${nearestDist}px away (Current Hit Accuracy: ${state.ammoTelemetry.accuracyPercent}%)."
            else ->
                "SECTOR TELEMETRY STABLE (Wave ${state.wave} • ${state.zombies.size} active hostiles, $crawlers Acid Crawlers, $activeBarrels active barrels in ${state.unlockedRooms.size}/6 unlocked sectors)."
        }

        val economyRecommendation = when {
            state.unlockedRooms.size == 1 && state.points >= 750 ->
                "You have ${state.points} PTS—move to the East Boundary door and press [B] to unlock Sector 02 East Armory (750 PTS) for wider kiting lanes."
            PerkType.JUGGERNOG !in state.activePerks && state.points >= 1200 ->
                "Bank is at ${state.points} PTS: prioritize buying Juggernog Soda (200 Max HP) or unlocking North Cryo Lab (1000 PTS)."
            PerkType.SPEED_COLA !in state.activePerks && state.points >= 1000 && state.ammoTelemetry.reloadsCompleted >= 2 ->
                "You've completed ${state.ammoTelemetry.reloadsCompleted} reloads—grab Speed Cola (-50% reload time) or Double Tap (+35% fire rate) to boost sustained DPS."
            wpnLvl < 5 && state.points >= upgradeCost ->
                "You have ${state.points} PTS ready: open the Armory and upgrade your ${wpn.name} to Pack-a-Punch MK-${wpnLvl + 1} ($upgradeCost PTS) for +45% damage & +4 mag capacity!"
            else ->
                "Keep building combo multipliers (${state.comboCount}x active) and kite zombies into tight groups before firing ${wpn.name} [MK-$wpnLvl]."
        }

        return "[OFFLINE TACTICAL AI RADIO] $primaryAlert $economyRecommendation"
    }
}
