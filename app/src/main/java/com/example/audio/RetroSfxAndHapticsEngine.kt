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
import com.example.game.GameSessionState
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

enum class CombatSfxEvent {
    WEAPON_FIRE_PISTOL,
    WEAPON_FIRE_RAPID,
    WEAPON_FIRE_HEAVY,
    WEAPON_FIRE_PAP,
    ZOMBIE_ELIMINATED,
    CRYO_SHATTER,
    BARREL_EXPLOSION,
    PACK_A_PUNCH_UPGRADE,
    WAVE_CLEARED,
    PLAYER_DAMAGE,
    UI_CLICK
}

/**
 * Zero-asset procedural PCM 16-bit audio synthesizer & tactile haptics engine.
 * Pre-generates short PCM waveforms in memory and dispatches low-latency AudioTrack + VibrationEffect feedback.
 */
class RetroSfxAndHapticsEngine(context: Context) {

    private val appContext = context.applicationContext
    private val sampleRate = 22050

    var sfxEnabled: Boolean = true
    var sfxVolume: Float = 0.85f
    var hapticsEnabled: Boolean = true

    private val lastPlayUptimeByEvent = HashMap<CombatSfxEvent, Long>()

    private val vibrator: Vibrator? by lazy {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (e: Exception) {
            Log.w("RetroSfxEngine", "Vibrator unavailable", e)
            null
        }
    }

    // Pre-synthesized PCM buffers so runtime combat triggers zero heap churn
    private val pcmBuffers: Map<CombatSfxEvent, ShortArray> by lazy {
        mapOf(
            CombatSfxEvent.WEAPON_FIRE_PISTOL to synthesizeGunshot(durationMs = 85, startFreq = 440.0, endFreq = 95.0, noiseMix = 0.45),
            CombatSfxEvent.WEAPON_FIRE_RAPID to synthesizeGunshot(durationMs = 62, startFreq = 620.0, endFreq = 160.0, noiseMix = 0.35),
            CombatSfxEvent.WEAPON_FIRE_HEAVY to synthesizeGunshot(durationMs = 155, startFreq = 260.0, endFreq = 52.0, noiseMix = 0.68),
            CombatSfxEvent.WEAPON_FIRE_PAP to synthesizeLaserChord(durationMs = 115, baseFreq = 880.0),
            CombatSfxEvent.ZOMBIE_ELIMINATED to synthesizeZombiePop(durationMs = 90),
            CombatSfxEvent.CRYO_SHATTER to synthesizeCryoShatter(durationMs = 180),
            CombatSfxEvent.BARREL_EXPLOSION to synthesizeExplosion(durationMs = 280),
            CombatSfxEvent.PACK_A_PUNCH_UPGRADE to synthesizeFanfare(
                notesHz = doubleArrayOf(440.0, 554.37, 659.25, 880.0),
                noteDurationMs = 65
            ),
            CombatSfxEvent.WAVE_CLEARED to synthesizeFanfare(
                notesHz = doubleArrayOf(392.0, 523.25, 659.25, 783.99),
                noteDurationMs = 85
            ),
            CombatSfxEvent.PLAYER_DAMAGE to synthesizeDamageBuzz(durationMs = 140),
            CombatSfxEvent.UI_CLICK to synthesizeUiClick(durationMs = 18)
        )
    }

    fun trigger(event: CombatSfxEvent) {
        val now = SystemClock.uptimeMillis()
        val minCooldownMs = when (event) {
            CombatSfxEvent.WEAPON_FIRE_RAPID -> 58L
            CombatSfxEvent.WEAPON_FIRE_PISTOL, CombatSfxEvent.WEAPON_FIRE_PAP -> 75L
            CombatSfxEvent.ZOMBIE_ELIMINATED -> 65L
            CombatSfxEvent.UI_CLICK -> 25L
            else -> 110L
        }
        val prev = lastPlayUptimeByEvent[event] ?: 0L
        if (now - prev < minCooldownMs) return
        lastPlayUptimeByEvent[event] = now

        if (sfxEnabled && sfxVolume > 0.02f) {
            playPcmBuffer(event)
        }
        if (hapticsEnabled) {
            triggerHapticForEvent(event)
        }
    }

    private fun playPcmBuffer(event: CombatSfxEvent) {
        val pcm = pcmBuffers[event] ?: return
        try {
            val byteSize = pcm.size * 2
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
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(byteSize.coerceAtLeast(1024))
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(pcm, 0, pcm.size)
            val vol = sfxVolume.coerceIn(0f, 1f)
            track.setVolume(vol)
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
            // Ignore if audio track pool is saturated on emulator
        }
    }

    private fun triggerHapticForEvent(event: CombatSfxEvent) {
        val vib = vibrator ?: return
        try {
            if (!vib.hasVibrator()) return
            val (durationMs, amplitude) = when (event) {
                CombatSfxEvent.UI_CLICK -> 10L to 70
                CombatSfxEvent.WEAPON_FIRE_RAPID -> 12L to 90
                CombatSfxEvent.WEAPON_FIRE_PISTOL, CombatSfxEvent.WEAPON_FIRE_PAP -> 18L to 130
                CombatSfxEvent.WEAPON_FIRE_HEAVY -> 35L to 210
                CombatSfxEvent.ZOMBIE_ELIMINATED -> 16L to 110
                CombatSfxEvent.CRYO_SHATTER -> 28L to 160
                CombatSfxEvent.BARREL_EXPLOSION -> 65L to 255
                CombatSfxEvent.PACK_A_PUNCH_UPGRADE, CombatSfxEvent.WAVE_CLEARED -> 45L to 190
                CombatSfxEvent.PLAYER_DAMAGE -> 55L to 240
            }
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
        lastPlayUptimeByEvent.clear()
    }

    private fun synthesizeGunshot(
        durationMs: Int,
        startFreq: Double,
        endFreq: Double,
        noiseMix: Double
    ): ShortArray {
        val samples = (sampleRate * durationMs) / 1000
        val out = ShortArray(samples)
        val rng = Random(101)
        var phase = 0.0
        for (i in 0 until samples) {
            val t = i.toDouble() / samples.toDouble()
            val env = exp(-4.8 * t)
            val freq = startFreq + (endFreq - startFreq) * t
            phase += (2.0 * PI * freq) / sampleRate
            val tone = sin(phase)
            val noise = rng.nextDouble(-1.0, 1.0)
            val mixed = ((1.0 - noiseMix) * tone + noiseMix * noise) * env
            out[i] = (mixed * 24000.0).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return out
    }

    private fun synthesizeLaserChord(durationMs: Int, baseFreq: Double): ShortArray {
        val samples = (sampleRate * durationMs) / 1000
        val out = ShortArray(samples)
        var p1 = 0.0
        var p2 = 0.0
        for (i in 0 until samples) {
            val t = i.toDouble() / samples.toDouble()
            val env = exp(-3.6 * t)
            val f1 = baseFreq * (1.0 - 0.35 * t)
            val f2 = f1 * 1.498 // fifth harmonic
            p1 += (2.0 * PI * f1) / sampleRate
            p2 += (2.0 * PI * f2) / sampleRate
            val sample = (sin(p1) * 0.6 + sin(p2) * 0.4) * env
            out[i] = (sample * 22000.0).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return out
    }

    private fun synthesizeZombiePop(durationMs: Int): ShortArray {
        val samples = (sampleRate * durationMs) / 1000
        val out = ShortArray(samples)
        var phase = 0.0
        for (i in 0 until samples) {
            val t = i.toDouble() / samples.toDouble()
            val env = exp(-5.2 * t)
            val freq = 180.0 + 220.0 * (1.0 - t)
            phase += (2.0 * PI * freq) / sampleRate
            val sample = sin(phase) * env
            out[i] = (sample * 19000.0).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return out
    }

    private fun synthesizeCryoShatter(durationMs: Int): ShortArray {
        val samples = (sampleRate * durationMs) / 1000
        val out = ShortArray(samples)
        val rng = Random(202)
        for (i in 0 until samples) {
            val t = i.toDouble() / samples.toDouble()
            val env = exp(-3.5 * t)
            val chime = sin(2.0 * PI * 1760.0 * (i.toDouble() / sampleRate)) +
                0.5 * sin(2.0 * PI * 2637.0 * (i.toDouble() / sampleRate))
            val sparkle = rng.nextDouble(-0.4, 0.4)
            val sample = (chime * 0.55 + sparkle) * env
            out[i] = (sample * 20000.0).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return out
    }

    private fun synthesizeExplosion(durationMs: Int): ShortArray {
        val samples = (sampleRate * durationMs) / 1000
        val out = ShortArray(samples)
        val rng = Random(303)
        var lowPass = 0.0
        var subPhase = 0.0
        for (i in 0 until samples) {
            val t = i.toDouble() / samples.toDouble()
            val env = exp(-2.8 * t)
            val white = rng.nextDouble(-1.0, 1.0)
            lowPass = lowPass * 0.84 + white * 0.16
            val subFreq = 95.0 * (1.0 - 0.65 * t)
            subPhase += (2.0 * PI * subFreq) / sampleRate
            val sample = (lowPass * 0.7 + sin(subPhase) * 0.5) * env
            out[i] = (sample * 26000.0).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return out
    }

    private fun synthesizeFanfare(notesHz: DoubleArray, noteDurationMs: Int): ShortArray {
        val samplesPerNote = (sampleRate * noteDurationMs) / 1000
        val totalSamples = samplesPerNote * notesHz.size
        val out = ShortArray(totalSamples)
        var idx = 0
        for (freq in notesHz) {
            var phase = 0.0
            for (i in 0 until samplesPerNote) {
                val t = i.toDouble() / samplesPerNote.toDouble()
                val env = (1.0 - 0.55 * t)
                phase += (2.0 * PI * freq) / sampleRate
                val sq = if (sin(phase) >= 0.0) 0.7 else -0.7
                val sample = (sq * 0.6 + sin(phase) * 0.4) * env
                out[idx++] = (sample * 18000.0).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
        }
        return out
    }

    private fun synthesizeDamageBuzz(durationMs: Int): ShortArray {
        val samples = (sampleRate * durationMs) / 1000
        val out = ShortArray(samples)
        for (i in 0 until samples) {
            val t = i.toDouble() / samples.toDouble()
            val env = exp(-3.0 * t)
            val s1 = sin(2.0 * PI * 110.0 * (i.toDouble() / sampleRate))
            val s2 = sin(2.0 * PI * 148.0 * (i.toDouble() / sampleRate))
            val raw = if (s1 + s2 > 0) 0.8 else -0.8
            out[i] = (raw * env * 22000.0).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return out
    }

    private fun synthesizeUiClick(durationMs: Int): ShortArray {
        val samples = (sampleRate * durationMs) / 1000
        val out = ShortArray(samples)
        for (i in 0 until samples) {
            val t = i.toDouble() / samples.toDouble()
            val env = exp(-8.0 * t)
            val s = sin(2.0 * PI * 1200.0 * (i.toDouble() / sampleRate)) * env
            out[i] = (s * 16000.0).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return out
    }
}

@Composable
fun RememberRetroSfxAndHapticsEffect(
    gameState: GameSessionState,
    sfxEnabled: Boolean,
    sfxVolume: Float,
    hapticsEnabled: Boolean
): RetroSfxAndHapticsEngine {
    val context = LocalContext.current
    val engine = remember(context.applicationContext) {
        RetroSfxAndHapticsEngine(context.applicationContext)
    }

    LaunchedEffect(sfxEnabled, sfxVolume, hapticsEnabled) {
        engine.sfxEnabled = sfxEnabled
        engine.sfxVolume = sfxVolume
        engine.hapticsEnabled = hapticsEnabled
    }

    var prevShotsFired by remember { mutableIntStateOf(gameState.ammoTelemetry.shotsFired) }
    var prevKills by remember { mutableIntStateOf(gameState.totalKills) }
    var prevWave by remember { mutableIntStateOf(gameState.wave) }
    var prevHp by remember { mutableIntStateOf(gameState.hp) }
    var prevWeaponLevel by remember { mutableIntStateOf(gameState.currentWeaponLevel) }

    LaunchedEffect(
        gameState.ammoTelemetry.shotsFired,
        gameState.totalKills,
        gameState.wave,
        gameState.hp,
        gameState.currentWeaponLevel
    ) {
        if (gameState.ammoTelemetry.shotsFired > prevShotsFired) {
            prevShotsFired = gameState.ammoTelemetry.shotsFired
            val wpnId = gameState.currentWeapon.id
            val isPap = gameState.currentWeaponLevel >= 2
            val event = when {
                isPap && wpnId == "raybox" -> CombatSfxEvent.WEAPON_FIRE_PAP
                wpnId == "shotgun" || wpnId == "barrels" || wpnId == "railgun" -> CombatSfxEvent.WEAPON_FIRE_HEAVY
                wpnId == "uzi" || wpnId == "ar" -> CombatSfxEvent.WEAPON_FIRE_RAPID
                isPap -> CombatSfxEvent.WEAPON_FIRE_PAP
                else -> CombatSfxEvent.WEAPON_FIRE_PISTOL
            }
            engine.trigger(event)
        }

        if (gameState.totalKills > prevKills) {
            val deltaKills = gameState.totalKills - prevKills
            prevKills = gameState.totalKills
            if (deltaKills >= 3) {
                engine.trigger(CombatSfxEvent.BARREL_EXPLOSION)
            } else {
                engine.trigger(CombatSfxEvent.ZOMBIE_ELIMINATED)
            }
        }

        if (gameState.wave > prevWave) {
            prevWave = gameState.wave
            engine.trigger(CombatSfxEvent.WAVE_CLEARED)
        }

        if (gameState.hp < prevHp) {
            prevHp = gameState.hp
            engine.trigger(CombatSfxEvent.PLAYER_DAMAGE)
        } else if (gameState.hp > prevHp) {
            prevHp = gameState.hp
        }

        if (gameState.currentWeaponLevel > prevWeaponLevel) {
            prevWeaponLevel = gameState.currentWeaponLevel
            engine.trigger(CombatSfxEvent.PACK_A_PUNCH_UPGRADE)
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
