package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R

/**
 * Hardware-accelerated looping audio controller for the BoxBoy Zombies retro synth soundtrack
 * (Inspired_Retro_Music_Loop_Track_642cf9e3.mp3 / R.raw.retro_music_loop).
 */
class RetroMusicController(private val appContext: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private var isPrepared = false
    private var isMuted = false
    private var baseVolume = 0.78f
    private var isDucked = false
    private var isLifecycleForeground = true
    private var currentSpeedRate = 1.0f

    fun startOrEnsurePlaying() {
        if (mediaPlayer == null) {
            initializePlayer()
        }
        applyPlaybackState()
    }

    private fun initializePlayer() {
        try {
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .build()
            )
            val afd = appContext.resources.openRawResourceFd(R.raw.retro_music_loop)
            if (afd != null) {
                mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
            } else {
                val assetFd = appContext.assets.openFd("Inspired_Retro_Music_Loop_Track_642cf9e3.mp3")
                mp.setDataSource(assetFd.fileDescriptor, assetFd.startOffset, assetFd.length)
                assetFd.close()
            }
            mp.isLooping = true
            mp.setOnPreparedListener { player ->
                isPrepared = true
                player.isLooping = true
                applyPlaybackState()
            }
            mp.setOnErrorListener { _, what, extra ->
                Log.w("RetroMusicController", "MediaPlayer error what=$what extra=$extra")
                false
            }
            mp.prepareAsync()
            mediaPlayer = mp
        } catch (e: Exception) {
            Log.e("RetroMusicController", "Failed to initialize retro music track", e)
        }
    }

    fun setMuted(muted: Boolean) {
        if (isMuted == muted) return
        isMuted = muted
        applyPlaybackState()
    }

    fun setDucked(ducked: Boolean) {
        if (isDucked == ducked) return
        isDucked = ducked
        applyVolumeOnly()
    }

    fun setVolume(volume: Float) {
        baseVolume = volume.coerceIn(0f, 1f)
        applyVolumeOnly()
    }

    fun setPlaybackSpeed(speedMultiplier: Float) {
        val clamped = speedMultiplier.coerceIn(0.75f, 1.35f)
        if ( kotlin.math.abs(clamped - currentSpeedRate) < 0.04f) return
        currentSpeedRate = clamped
        val player = mediaPlayer ?: return
        if (!isPrepared) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && player.isPlaying) {
            try {
                player.playbackParams = player.playbackParams.setSpeed(clamped)
            } catch (_: Exception) {
                // Ignore if device codec rejects dynamic rate change
            }
        }
    }

    fun onAppForegroundChanged(inForeground: Boolean) {
        isLifecycleForeground = inForeground
        applyPlaybackState()
    }

    private fun applyVolumeOnly() {
        val player = mediaPlayer ?: return
        if (!isPrepared) return
        try {
            val effectiveVol = when {
                isMuted -> 0f
                isDucked -> baseVolume * 0.32f
                else -> baseVolume
            }
            player.setVolume(effectiveVol, effectiveVol)
        } catch (_: Exception) {
        }
    }

    private fun applyPlaybackState() {
        val player = mediaPlayer ?: return
        if (!isPrepared) return
        try {
            applyVolumeOnly()
            if (isLifecycleForeground && !isMuted) {
                if (!player.isPlaying) {
                    player.start()
                }
            } else {
                if (player.isPlaying) {
                    player.pause()
                }
            }
        } catch (e: Exception) {
            Log.w("RetroMusicController", "Could not update playback state", e)
        }
    }

    fun release() {
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {
        }
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
        isPrepared = false
    }
}

@Composable
fun RememberRetroMusicTrackEffect(
    isMuted: Boolean,
    isDucked: Boolean,
    emulatorSpeed: Float
): RetroMusicController {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember(context.applicationContext) {
        RetroMusicController(context.applicationContext)
    }

    DisposableEffect(lifecycleOwner, controller) {
        controller.startOrEnsurePlaying()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME, Lifecycle.Event.ON_START -> {
                    controller.onAppForegroundChanged(true)
                }
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    controller.onAppForegroundChanged(false)
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.release()
        }
    }

    LaunchedEffect(isMuted) {
        controller.setMuted(isMuted)
    }

    LaunchedEffect(isDucked) {
        controller.setDucked(isDucked)
    }

    LaunchedEffect(emulatorSpeed) {
        val mappedSpeed = when {
            emulatorSpeed < 0.85f -> 0.90f
            emulatorSpeed > 1.25f -> 1.12f
            else -> 1.0f
        }
        controller.setPlaybackSpeed(mappedSpeed)
    }

    return controller
}
