package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.data.WeaponSpec
import com.example.game.GameSessionState
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Computed acoustic pitch and envelope profile derived dynamically from [WeaponSpec] stats and upgrade tier.
 */
data class WeaponPitchProfile(
    val weaponId: String,
    val weaponLevel: Int,
    val startFrequencyHz: Double,
    val endFrequencyHz: Double,
    val harmonicMultiplier: Double,
    val durationMs: Int,
    val subBassWeight: Double,
    val aetherChimeMix: Double,
    val playbackRateMultiplier: Float
)

/**
 * Low-latency procedural PCM `AudioTrack` sound synthesizer & hit-reg tactile feedback engine.
 *
 * Dynamically computes waveform pitch, sub-bass punch, harmonic overtones, and envelope decay
 * from weapon stats (`baseDamage`, `fireCooldownMs`, `pelletCount`, `isExplosive`, `weaponLevel`)
 * and synchronizes tactile haptics directly to frame-level hit registration (`shotsHit`, `lightLockCrits`, `totalKills`).
 */
class CombatAudioEngine(context: Context? = null) {

    private val appContext: Context? = context?.applicationContext
    val sampleRate: Int = 24000

    var sfxEnabled: Boolean = true
    var sfxVolume: Float = 0.85f
    var hapticsEnabled: Boolean = true

    private val lastTriggerUptimeMap = ConcurrentHashMap<String, Long>()
    private val cachedWeaponBuffers = ConcurrentHashMap<String, ShortArray>()

    private val vibrator: Vibrator? by lazy {
        val ctx = appContext ?: return@lazy null
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (e: Exception) {
            Log.w("CombatAudioEngine", "Vibrator service unavailable", e)
            null
        }
    }

    /**
     * Derives a weapon-specific acoustic pitch & harmonic profile from its combat statistics:
     * - Faster fire rate (`fireCooldownMs` low) -> higher base pitch & tighter transient
     * - Higher `baseDamage` / `pelletCount` -> deeper sub-bass sweep & longer resonance tail
     * - Higher `weaponLevel` (Pack-a-Punch / Anvil tier) -> upward crystalline harmonic shift & aether overtone
     */
    fun computeWeaponPitchProfile(
        weapon: WeaponSpec,
        weaponLevel: Int = 1,
        fireRateMultiplier: Float = 1.0f
    ): WeaponPitchProfile {
        val clampedLevel = weaponLevel.coerceIn(1, 10)
        val cooldownClamped = weapon.fireCooldownMs.coerceIn(60L, 900L).toDouble()
        // Inverse relationship: 95ms UZI -> ~680 Hz; 680ms Railgun -> ~240 Hz
        val fireRateFactor = (300.0 / cooldownClamped).coerceIn(0.42, 2.4)
        val damageFactor = (weapon.baseDamage.coerceIn(15, 350) / 100.0).coerceIn(0.2, 3.2)
        val levelPitchShift = 1.0 + (clampedLevel - 1) * 0.065

        val startFreq = (360.0 * fireRateFactor * levelPitchShift / (1.0 + damageFactor * 0.18))
            .coerceIn(140.0, 1320.0)
        val endFreq = (startFreq * (0.24 - (damageFactor * 0.03).coerceAtMost(0.12)))
            .coerceIn(38.0, 320.0)

        val durationMs = (62 + (damageFactor * 34.0).toInt() + (weapon.pelletCount - 1) * 8)
            .coerceIn(55, 195)
        val subBassWeight = (0.25 + damageFactor * 0.22 + if (weapon.isExplosive) 0.25 else 0.0)
            .coerceIn(0.2, 0.85)
        val aetherChimeMix = if (clampedLevel >= 2 || weapon.isExplosive) {
            (0.18 + (clampedLevel - 1) * 0.08).coerceIn(0.18, 0.65)
        } else {
            0.08
        }
        val harmonicMult = if (clampedLevel >= 2) 1.498 else 1.25 // Perfect fifth for upgraded arms
        val playbackRate = (fireRateMultiplier.coerceIn(0.75f, 1.45f) * (1f + (clampedLevel - 1) * 0.03f))
            .coerceIn(0.75f, 1.6f)

        return WeaponPitchProfile(
            weaponId = weapon.id,
            weaponLevel = clampedLevel,
            startFrequencyHz = startFreq,
            endFrequencyHz = endFreq,
            harmonicMultiplier = harmonicMult,
            durationMs = durationMs,
            subBassWeight = subBassWeight,
            aetherChimeMix = aetherChimeMix,
            playbackRateMultiplier = playbackRate
        )
    }

    /**
     * Synthesizes a 16-bit mono PCM waveform for gunfire based on [WeaponPitchProfile].
     */
    fun generateGunfirePcm(profile: WeaponPitchProfile, pelletCount: Int = 1): ShortArray {
        val totalSamples = ((sampleRate * profile.durationMs) / 1000).coerceAtLeast(256)
        val pcm = ShortArray(totalSamples)
        val rng = Random(profile.weaponId.hashCode().toLong() xor profile.weaponLevel.toLong())
        var primaryPhase = 0.0
        var harmonicPhase = 0.0
        var subPhase = 0.0

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / totalSamples.toDouble()
            val attack = if (t < 0.04) (t / 0.04) else 1.0
            val decay = exp(-4.6 * t)
            val env = attack * decay

            val instFreq = profile.startFrequencyHz + (profile.endFrequencyHz - profile.startFrequencyHz) * t
            primaryPhase += (2.0 * PI * instFreq) / sampleRate
            harmonicPhase += (2.0 * PI * instFreq * profile.harmonicMultiplier) / sampleRate
            subPhase += (2.0 * PI * (instFreq * 0.5)) / sampleRate

            val coreWave = sin(primaryPhase)
            val subWave = sin(subPhase) * profile.subBassWeight
            val aetherWave = sin(harmonicPhase) * profile.aetherChimeMix
            // Multi-pellet transient texture for shotguns/scatter weapons
            val pelletBurst = if (pelletCount > 1 && t < 0.35) {
                sin(2.0 * PI * (instFreq * 2.1) * t * pelletCount) * 0.22
            } else {
                0.0
            }
            val transientNoise = rng.nextDouble(-1.0, 1.0) * exp(-10.0 * t) * 0.38

            val combined = (coreWave * 0.52 + subWave + aetherWave + pelletBurst + transientNoise) * env
            pcm[i] = (combined * 23500.0).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
        return pcm
    }

    /**
     * Synthesizes a low-latency hit-registration impact waveform with crystalline chime on critical/kill hits.
     */
    fun generateImpactPcm(
        damageDealt: Int,
        isCritical: Boolean,
        isKill: Boolean
    ): ShortArray {
        val durationMs = when {
            isKill -> 95
            isCritical -> 75
            else -> 48
        }
        val totalSamples = ((sampleRate * durationMs) / 1000).coerceAtLeast(128)
        val pcm = ShortArray(totalSamples)
        val damageScale = (damageDealt.coerceIn(10, 400) / 100.0).coerceIn(0.2, 3.0)
        // Higher damage produces a deeper body thud paired with a brighter crystalline hit-marker tick
        val bodyFreq = (210.0 / (0.8 + damageScale * 0.25)).coerceIn(85.0, 260.0)
        val crystalFreq = when {
            isKill -> 1318.5 // E6 crystalline finisher
            isCritical -> 1046.5 // C6 critical resonance
            else -> 783.99 // G5 crisp hit-reg tick
        }

        var bodyPhase = 0.0
        var crystalPhase = 0.0
        for (i in 0 until totalSamples) {
            val t = i.toDouble() / totalSamples.toDouble()
            val env = exp(-5.8 * t)
            bodyPhase += (2.0 * PI * bodyFreq * (1.0 - 0.35 * t)) / sampleRate
            crystalPhase += (2.0 * PI * crystalFreq) / sampleRate

            val crystalWeight = if (isCritical || isKill) 0.55 else 0.32
            val sample = (sin(bodyPhase) * 0.55 + sin(crystalPhase) * crystalWeight) * env
            pcm[i] = (sample * 21000.0).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
        return pcm
    }

    /**
     * Synthesizes a multi-layered detonation waveform with pitch & duration scaled by blast power.
     */
    fun generateExplosionPcm(
        blastDamage: Int = 180,
        isCryoShatter: Boolean = false
    ): ShortArray {
        val intensity = (blastDamage.coerceIn(60, 500) / 180.0).coerceIn(0.5, 2.2)
        val durationMs = (210 * intensity).toInt().coerceIn(150, 360)
        val totalSamples = ((sampleRate * durationMs) / 1000).coerceAtLeast(512)
        val pcm = ShortArray(totalSamples)
        val rng = Random(if (isCryoShatter) 777L else 999L)
        var filteredNoise = 0.0
        var subPhase = 0.0
        var crystalPhase = 0.0
        val startSubHz = (115.0 / intensity.coerceAtLeast(0.7)).coerceIn(55.0, 140.0)

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / totalSamples.toDouble()
            val env = exp(-2.9 * t)
            val white = rng.nextDouble(-1.0, 1.0)
            filteredNoise = filteredNoise * 0.82 + white * 0.18

            val instSubHz = startSubHz * (1.0 - 0.68 * t)
            subPhase += (2.0 * PI * instSubHz) / sampleRate
            crystalPhase += (2.0 * PI * (if (isCryoShatter) 1975.5 else 587.33)) / sampleRate

            val cryoSparkle = if (isCryoShatter) sin(crystalPhase) * 0.38 else sin(crystalPhase) * 0.12
            val sample = (filteredNoise * 0.62 + sin(subPhase) * 0.52 + cryoSparkle) * env
            pcm[i] = (sample * 25500.0).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
        return pcm
    }

    /**
     * Synthesizes a Final Fantasy inspired crystalline arpeggio fanfare for Wave Clear, Forge, or UI chimes.
     */
    fun generateCrystallineArpeggioPcm(notesHz: DoubleArray, noteDurationMs: Int = 70): ShortArray {
        val samplesPerNote = ((sampleRate * noteDurationMs) / 1000).coerceAtLeast(128)
        val totalSamples = samplesPerNote * notesHz.size
        val pcm = ShortArray(totalSamples)
        var outIndex = 0
        for (freq in notesHz) {
            var p1 = 0.0
            var p2 = 0.0
            for (i in 0 until samplesPerNote) {
                val t = i.toDouble() / samplesPerNote.toDouble()
                val env = exp(-2.4 * t)
                p1 += (2.0 * PI * freq) / sampleRate
                p2 += (2.0 * PI * freq * 2.0) / sampleRate
                val sample = (sin(p1) * 0.72 + sin(p2) * 0.28) * env
                pcm[outIndex++] = (sample * 19500.0).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    .toShort()
            }
        }
        return pcm
    }

    fun playGunfire(
        weapon: WeaponSpec,
        weaponLevel: Int = 1,
        fireRateMultiplier: Float = 1.0f
    ) {
        val now = SystemClock.uptimeMillis()
        val minCooldown = (weapon.fireCooldownMs / 2).coerceIn(42L, 110L)
        val prev = lastTriggerUptimeMap["gunfire"] ?: 0L
        if (now - prev < minCooldown) return
        lastTriggerUptimeMap["gunfire"] = now

        val profile = computeWeaponPitchProfile(weapon, weaponLevel, fireRateMultiplier)
        val cacheKey = "${weapon.id}_lv${profile.weaponLevel}"
        val pcm = cachedWeaponBuffers.getOrPut(cacheKey) {
            generateGunfirePcm(profile, weapon.pelletCount)
        }
        dispatchPcmToAudioTrack(pcm, profile.playbackRateMultiplier)
        val hapticDuration = (10L + weapon.baseDamage / 18L).coerceIn(10L, 36L)
        val hapticAmp = (80 + weapon.baseDamage / 2).coerceIn(75, 225)
        dispatchHapticPulse(hapticDuration, hapticAmp)
    }

    fun playHitRegImpact(
        damageDealt: Int,
        isCritical: Boolean,
        isKill: Boolean
    ) {
        val now = SystemClock.uptimeMillis()
        val prev = lastTriggerUptimeMap["hitreg"] ?: 0L
        if (now - prev < 35L) return
        lastTriggerUptimeMap["hitreg"] = now

        val cacheKey = "hit_${(damageDealt / 25).coerceIn(1, 10)}_crit${isCritical}_kill$isKill"
        val pcm = cachedWeaponBuffers.getOrPut(cacheKey) {
            generateImpactPcm(damageDealt, isCritical, isKill)
        }
        val pitchRate = when {
            isKill -> 1.08f
            isCritical -> 1.15f
            else -> 1.0f
        }
        dispatchPcmToAudioTrack(pcm, pitchRate)
        // Low-latency tactile hit-reg pulse synced directly to impact
        val durationMs = when {
            isKill -> 24L
            isCritical -> 18L
            else -> 11L
        }
        val amplitude = when {
            isKill -> 185
            isCritical -> 150
            else -> 105
        }
        dispatchHapticPulse(durationMs, amplitude)
    }

    fun playExplosion(blastDamage: Int = 180, isCryoShatter: Boolean = false) {
        val now = SystemClock.uptimeMillis()
        val prev = lastTriggerUptimeMap["explosion"] ?: 0L
        if (now - prev < 95L) return
        lastTriggerUptimeMap["explosion"] = now

        val cacheKey = "exp_${(blastDamage / 50)}_cryo$isCryoShatter"
        val pcm = cachedWeaponBuffers.getOrPut(cacheKey) {
            generateExplosionPcm(blastDamage, isCryoShatter)
        }
        dispatchPcmToAudioTrack(pcm, 1.0f)
        dispatchHapticPulse(durationMs = 60L, amplitude = 250)
    }

    fun playCrystallineFanfare(isWaveClear: Boolean = true) {
        val cacheKey = if (isWaveClear) "fanfare_wave" else "fanfare_forge"
        val pcm = cachedWeaponBuffers.getOrPut(cacheKey) {
            val notes = if (isWaveClear) {
                // Final Fantasy-inspired victory crystal prelude chord (C5 - D5 - E5 - G5 - C6)
                doubleArrayOf(523.25, 587.33, 659.25, 783.99, 1046.50)
            } else {
                // Aether Forge tempered chord (A4 - C#5 - E5 - A5)
                doubleArrayOf(440.0, 554.37, 659.25, 880.0)
            }
            generateCrystallineArpeggioPcm(notes, noteDurationMs = 72)
        }
        dispatchPcmToAudioTrack(pcm, 1.0f)
        dispatchHapticPulse(durationMs = 38L, amplitude = 175)
    }

    fun playUiCrystalChime() {
        val now = SystemClock.uptimeMillis()
        val prev = lastTriggerUptimeMap["ui_chime"] ?: 0L
        if (now - prev < 25L) return
        lastTriggerUptimeMap["ui_chime"] = now

        val pcm = cachedWeaponBuffers.getOrPut("ui_crystal_chime") {
            generateCrystallineArpeggioPcm(doubleArrayOf(1046.50, 1318.51), noteDurationMs = 18)
        }
        dispatchPcmToAudioTrack(pcm, 1.0f)
        dispatchHapticPulse(durationMs = 9L, amplitude = 70)
    }

    /**
     * Backwards-compatible event dispatcher for existing UI callers.
     */
    fun trigger(event: CombatSfxEvent, activeWeapon: WeaponSpec? = null, weaponLevel: Int = 1) {
        when (event) {
            CombatSfxEvent.WEAPON_FIRE_PISTOL,
            CombatSfxEvent.WEAPON_FIRE_RAPID,
            CombatSfxEvent.WEAPON_FIRE_HEAVY,
            CombatSfxEvent.WEAPON_FIRE_PAP -> {
                if (activeWeapon != null) {
                    playGunfire(activeWeapon, weaponLevel)
                } else {
                    playHitRegImpact(damageDealt = 45, isCritical = event == CombatSfxEvent.WEAPON_FIRE_PAP, isKill = false)
                }
            }
            CombatSfxEvent.ZOMBIE_ELIMINATED -> playHitRegImpact(damageDealt = 85, isCritical = false, isKill = true)
            CombatSfxEvent.CRYO_SHATTER -> playExplosion(blastDamage = 160, isCryoShatter = true)
            CombatSfxEvent.BARREL_EXPLOSION -> playExplosion(blastDamage = 240, isCryoShatter = false)
            CombatSfxEvent.PACK_A_PUNCH_UPGRADE -> playCrystallineFanfare(isWaveClear = false)
            CombatSfxEvent.WAVE_CLEARED -> playCrystallineFanfare(isWaveClear = true)
            CombatSfxEvent.PLAYER_DAMAGE -> playHitRegImpact(damageDealt = 120, isCritical = true, isKill = false)
            CombatSfxEvent.UI_CLICK -> playUiCrystalChime()
        }
    }

    private fun dispatchPcmToAudioTrack(pcm: ShortArray, pitchRateMultiplier: Float = 1.0f) {
        if (!sfxEnabled || sfxVolume <= 0.02f || appContext == null) return
        try {
            val byteSize = pcm.size * 2
            val effectiveSampleRate = (sampleRate * pitchRateMultiplier.coerceIn(0.65f, 1.65f)).toInt()
                .coerceIn(12000, 48000)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(effectiveSampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(byteSize.coerceAtLeast(1024))
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(pcm, 0, pcm.size)
            track.setVolume(sfxVolume.coerceIn(0f, 1f))
            track.setNotificationMarkerPosition(pcm.size)
            track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(t: AudioTrack?) {
                    try {
                        t?.stop()
                        t?.release()
                    } catch (_: Exception) {
                    }
                }

                override fun onPeriodicNotification(t: AudioTrack?) = Unit
            })
            track.play()
        } catch (_: Exception) {
        }
    }

    private fun dispatchHapticPulse(durationMs: Long, amplitude: Int) {
        if (!hapticsEnabled) return
        val vib = vibrator ?: return
        try {
            if (!vib.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createOneShot(durationMs, amplitude.coerceIn(1, 255)))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(durationMs)
            }
        } catch (_: Exception) {
        }
    }

    fun release() {
        lastTriggerUptimeMap.clear()
    }
}

@Composable
fun RememberCombatAudioEngineEffect(
    gameState: GameSessionState,
    sfxEnabled: Boolean,
    sfxVolume: Float,
    hapticsEnabled: Boolean
): CombatAudioEngine {
    val context = LocalContext.current
    val engine = remember(context.applicationContext) {
        CombatAudioEngine(context.applicationContext)
    }

    LaunchedEffect(sfxEnabled, sfxVolume, hapticsEnabled) {
        engine.sfxEnabled = sfxEnabled
        engine.sfxVolume = sfxVolume
        engine.hapticsEnabled = hapticsEnabled
    }

    var prevShotsFired by remember { mutableIntStateOf(gameState.ammoTelemetry.shotsFired) }
    var prevShotsHit by remember { mutableIntStateOf(gameState.ammoTelemetry.shotsHit) }
    var prevLightCrits by remember { mutableIntStateOf(gameState.ammoTelemetry.lightLockCrits) }
    var prevKills by remember { mutableIntStateOf(gameState.totalKills) }
    var prevWave by remember { mutableIntStateOf(gameState.wave) }
    var prevHp by remember { mutableIntStateOf(gameState.hp) }
    var prevWeaponLevel by remember { mutableIntStateOf(gameState.currentWeaponLevel) }

    LaunchedEffect(
        gameState.ammoTelemetry.shotsFired,
        gameState.ammoTelemetry.shotsHit,
        gameState.ammoTelemetry.lightLockCrits,
        gameState.totalKills,
        gameState.wave,
        gameState.hp,
        gameState.currentWeaponLevel
    ) {
        val weapon = gameState.currentWeapon
        val weaponLevel = gameState.currentWeaponLevel

        if (gameState.ammoTelemetry.shotsFired > prevShotsFired) {
            prevShotsFired = gameState.ammoTelemetry.shotsFired
            engine.playGunfire(
                weapon = weapon,
                weaponLevel = weaponLevel,
                fireRateMultiplier = gameState.speedConfig.fireReloadMultiplier
            )
        }

        val hitDelta = gameState.ammoTelemetry.shotsHit - prevShotsHit
        val critDelta = gameState.ammoTelemetry.lightLockCrits - prevLightCrits
        val killDelta = gameState.totalKills - prevKills

        if (hitDelta > 0 || killDelta > 0) {
            prevShotsHit = gameState.ammoTelemetry.shotsHit
            prevLightCrits = gameState.ammoTelemetry.lightLockCrits
            prevKills = gameState.totalKills

            if (killDelta >= 3) {
                engine.playExplosion(blastDamage = weapon.baseDamage * 2, isCryoShatter = false)
            } else {
                engine.playHitRegImpact(
                    damageDealt = weapon.baseDamage * weaponLevel,
                    isCritical = critDelta > 0,
                    isKill = killDelta > 0
                )
            }
        }

        if (gameState.wave > prevWave) {
            prevWave = gameState.wave
            engine.playCrystallineFanfare(isWaveClear = true)
        }

        if (gameState.hp < prevHp) {
            prevHp = gameState.hp
            engine.playHitRegImpact(damageDealt = 100, isCritical = true, isKill = false)
        } else if (gameState.hp > prevHp) {
            prevHp = gameState.hp
        }

        if (gameState.currentWeaponLevel > prevWeaponLevel) {
            prevWeaponLevel = gameState.currentWeaponLevel
            engine.playCrystallineFanfare(isWaveClear = false)
        } else if (gameState.currentWeaponLevel < prevWeaponLevel) {
            prevWeaponLevel = gameState.currentWeaponLevel
        }
    }

    DisposableEffect(engine) {
        onDispose {
            engine.release()
        }
    }

    return engine
}
