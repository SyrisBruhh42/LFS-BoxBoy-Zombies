package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import com.example.data.OpenAudibleChapterTrack
import com.example.data.SanctuaryStorytellerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * AudioBookshelf & OpenAudible Sanctuary Storyteller (`cb64f9e7`) Controller.
 *
 * Connects to `/api/lfs/openaudible/library` and `/api/lfs/openaudible/stream/[key]`
 * authenticated with the Tailscale `:13379/lfs-sso/handshake` Hearth Key (`Node.StableID`),
 * and automatically falls back to a procedural 16-bit PCM Starlight Music-Box Lullaby
 * synthesizer with a 15m / 30m / 45m Starlight Sleep-Fade timer.
 */
class SanctuaryStorytellerController(
    private val scope: CoroutineScope
) {
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(1400, TimeUnit.MILLISECONDS)
        .readTimeout(2000, TimeUnit.MILLISECONDS)
        .build()

    private val _state = MutableStateFlow(SanctuaryStorytellerState())
    val state: StateFlow<SanctuaryStorytellerState> = _state.asStateFlow()

    private var sleepTimerJob: Job? = null
    private var lullabySynthJob: Job? = null
    private var mediaPlayer: MediaPlayer? = null

    fun refreshOpenAudibleLibrary(
        baseUrls: List<String> = listOf(
            "https://lfs-prod.tail420c4d.ts.net",
            "https://play.lewisfamilysystems.com",
            "http://100.106.12.104:13379"
        ),
        hearthKeyNodeId: String = "node-stable-lfs-android-01",
        subuserId: String = "primary"
    ) {
        scope.launch(Dispatchers.IO) {
            val fetched = fetchLibraryTracks(baseUrls, hearthKeyNodeId, subuserId)
            if (fetched.isNotEmpty()) {
                val cur = _state.value
                val first = fetched.first()
                _state.value = cur.copy(
                    availableTracks = fetched,
                    activeTrackKey = first.key,
                    bookTitle = first.bookTitle,
                    chapterTitle = first.chapterTitle,
                    sourceModeLabel = "OpenAudible Sanctuary Library (${first.key})",
                    isUsingOfflineLullabyFallback = false
                )
            }
        }
    }

    suspend fun fetchLibraryTracks(
        baseUrls: List<String>,
        hearthKeyNodeId: String,
        subuserId: String
    ): List<OpenAudibleChapterTrack> = withContext(Dispatchers.IO) {
        for (rawBase in baseUrls) {
            val base = rawBase.trim().trimEnd('/')
            if (base.isEmpty()) continue
            val url = "$base/api/lfs/openaudible/library"
            try {
                val req = Request.Builder()
                    .url(url)
                    .get()
                    .header("X-Oasis-Hearth-Key", hearthKeyNodeId)
                    .header("X-Oasis-Subuser", subuserId)
                    .header("X-Oasis-Protocol", "oasis-v2-cp12")
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string().orEmpty()
                        val parsed = parseLibraryJson(body)
                        if (parsed.isNotEmpty()) return@withContext parsed
                    }
                }
            } catch (_: Exception) {
                // Non-blocking fallback to next candidate or offline sample tracks
            }
        }
        emptyList()
    }

    fun togglePlayPause(
        baseUrl: String = "https://lfs-prod.tail420c4d.ts.net",
        hearthKeyNodeId: String = "node-stable-lfs-android-01"
    ) {
        val current = _state.value
        if (current.isPlaying) {
            pausePlayback()
        } else {
            startPlayback(baseUrl, hearthKeyNodeId)
        }
    }

    fun selectChapterTrack(
        trackKey: String,
        baseUrl: String = "https://lfs-prod.tail420c4d.ts.net",
        hearthKeyNodeId: String = "node-stable-lfs-android-01"
    ) {
        val cur = _state.value
        val track = cur.availableTracks.find { it.key == trackKey } ?: return
        val wasPlaying = cur.isPlaying
        stopAudioResources()
        _state.value = cur.copy(
            activeTrackKey = track.key,
            bookTitle = track.bookTitle,
            chapterTitle = track.chapterTitle,
            isPlaying = false,
            sourceModeLabel = "Selected ${track.chapterTitle} (${track.key})"
        )
        if (wasPlaying) {
            startPlayback(baseUrl, hearthKeyNodeId)
        }
    }

    fun cycleNextChapter(
        baseUrl: String = "https://lfs-prod.tail420c4d.ts.net",
        hearthKeyNodeId: String = "node-stable-lfs-android-01"
    ) {
        val cur = _state.value
        val list = cur.availableTracks
        if (list.isEmpty()) return
        val idx = list.indexOfFirst { it.key == cur.activeTrackKey }.coerceAtLeast(0)
        val next = list[(idx + 1) % list.size]
        selectChapterTrack(next.key, baseUrl, hearthKeyNodeId)
    }

    /**
     * Sets or cycles the 15m / 30m / 45m Starlight Sleep-Fade timer (`0` = Off).
     */
    fun setStarlightSleepTimerMinutes(minutes: Int) {
        val validMin = when (minutes) {
            15, 30, 45 -> minutes
            else -> 0
        }
        sleepTimerJob?.cancel()
        if (validMin == 0) {
            _state.value = _state.value.copy(
                sleepTimerMinutes = 0,
                sleepRemainingSeconds = 0,
                sleepFadeVolumeMultiplier = 1.0f
            )
            runCatching {
                mediaPlayer?.setVolume(1.0f, 1.0f)
            }
            return
        }

        val totalSeconds = validMin * 60
        _state.value = _state.value.copy(
            sleepTimerMinutes = validMin,
            sleepRemainingSeconds = totalSeconds,
            sleepFadeVolumeMultiplier = 1.0f
        )
        runCatching {
            mediaPlayer?.setVolume(1.0f, 1.0f)
        }

        sleepTimerJob = scope.launch {
            var remaining = totalSeconds
            while (isActive && remaining >= 0) {
                delay(1000L)
                remaining--
                val fade = computeSleepFadeVolumeMultiplier(validMin, remaining)
                _state.value = _state.value.copy(
                    sleepRemainingSeconds = remaining,
                    sleepFadeVolumeMultiplier = fade
                )
                runCatching {
                    mediaPlayer?.setVolume(fade, fade)
                }
            }
            if (remaining < 0) {
                pausePlayback()
                _state.value = _state.value.copy(
                    sleepTimerMinutes = 0,
                    sleepRemainingSeconds = 0,
                    sleepFadeVolumeMultiplier = 1.0f,
                    sourceModeLabel = "Starlight Sleep-Fade Complete (Sweet Dreams)"
                )
            }
        }
    }

    fun cycleStarlightSleepTimer() {
        val nextMin = when (_state.value.sleepTimerMinutes) {
            0 -> 15
            15 -> 30
            30 -> 45
            else -> 0
        }
        setStarlightSleepTimerMinutes(nextMin)
    }

    private fun startPlayback(baseUrl: String, hearthKeyNodeId: String) {
        val cur = _state.value
        val streamUrl = buildStreamUrl(baseUrl, cur.activeTrackKey)
        _state.value = cur.copy(
            isPlaying = true,
            sourceModeLabel = "Connecting Sanctuary Stream (${cur.activeTrackKey})..."
        )

        scope.launch(Dispatchers.IO) {
            val streamReachable = probeStreamEndpointReachable(streamUrl, hearthKeyNodeId)
            if (streamReachable) {
                val startedRemote = runCatching {
                    val mp = MediaPlayer()
                    mp.setDataSource(streamUrl)
                    mp.prepare()
                    val vol = _state.value.sleepFadeVolumeMultiplier
                    mp.setVolume(vol, vol)
                    mp.start()
                    mediaPlayer = mp
                    true
                }.getOrDefault(false)

                if (startedRemote) {
                    _state.value = _state.value.copy(
                        isPlaying = true,
                        isUsingOfflineLullabyFallback = false,
                        sourceModeLabel = "Streaming OpenAudible [${cur.activeTrackKey}]"
                    )
                    return@launch
                }
            }

            // Automatic fallback to procedural Starlight Music-Box Lullaby synthesis
            _state.value = _state.value.copy(
                isPlaying = true,
                isUsingOfflineLullabyFallback = true,
                sourceModeLabel = "Starlight Music-Box Lullaby (${cur.activeTrackKey})"
            )
            startProceduralLullabyLoop()
        }
    }

    private fun startProceduralLullabyLoop() {
        lullabySynthJob?.cancel()
        lullabySynthJob = scope.launch(Dispatchers.Default) {
            var phraseIndex = 0
            while (isActive && _state.value.isPlaying) {
                val vol = _state.value.sleepFadeVolumeMultiplier
                val pcm = synthesizeMusicBoxLullabyPcm(
                    phraseIndex = phraseIndex,
                    volumeScale = vol * 0.45f
                )
                playPcmChunkBlocking(pcm)
                phraseIndex++
                delay(180L)
            }
        }
    }

    private fun playPcmChunkBlocking(pcm: ShortArray) {
        if (pcm.isEmpty()) return
        runCatching {
            val sampleRate = SAMPLE_RATE_HZ
            val byteCount = pcm.size * 2
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(byteCount.coerceAtLeast(4096))
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(pcm, 0, pcm.size)
            track.play()
            val durationMs = (pcm.size * 1000L) / sampleRate
            Thread.sleep(durationMs.coerceIn(100L, 2500L))
            track.stop()
            track.release()
        }
    }

    fun pausePlayback() {
        stopAudioResources()
        _state.value = _state.value.copy(
            isPlaying = false,
            sourceModeLabel = "Paused (${_state.value.activeTrackKey})"
        )
    }

    private fun stopAudioResources() {
        lullabySynthJob?.cancel()
        lullabySynthJob = null
        runCatching {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        }
        mediaPlayer = null
    }

    private fun probeStreamEndpointReachable(streamUrl: String, hearthKeyNodeId: String): Boolean {
        return try {
            val req = Request.Builder()
                .url(streamUrl)
                .head()
                .header("X-Oasis-Hearth-Key", hearthKeyNodeId)
                .header("X-Oasis-Protocol", "oasis-v2-cp12")
                .build()
            httpClient.newCall(req).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    fun refreshLibraryAsync(
        config: com.example.data.LfsServerEndpointConfig,
        hearthKeyNodeId: String = "node-stable-lfs-android-01"
    ) {
        val candidates = listOf(
            config.customServerUrl,
            config.tailscaleAnchorHost,
            config.primaryEdgeHost,
            "http://100.106.12.104:13379"
        ).filter { it.isNotBlank() }
        refreshOpenAudibleLibrary(candidates, hearthKeyNodeId)
    }

    fun togglePlayPause(
        config: com.example.data.LfsServerEndpointConfig,
        hearthKeyNodeId: String = "node-stable-lfs-android-01"
    ) {
        val base = config.customServerUrl.ifBlank {
            config.tailscaleAnchorHost.ifBlank { config.primaryEdgeHost }
        }
        togglePlayPause(baseUrl = base, hearthKeyNodeId = hearthKeyNodeId)
    }

    fun nextChapter(
        config: com.example.data.LfsServerEndpointConfig,
        hearthKeyNodeId: String = "node-stable-lfs-android-01"
    ) {
        val base = config.customServerUrl.ifBlank {
            config.tailscaleAnchorHost.ifBlank { config.primaryEdgeHost }
        }
        cycleNextChapter(baseUrl = base, hearthKeyNodeId = hearthKeyNodeId)
    }

    fun selectTrack(
        trackKey: String,
        config: com.example.data.LfsServerEndpointConfig,
        hearthKeyNodeId: String = "node-stable-lfs-android-01"
    ) {
        val base = config.customServerUrl.ifBlank {
            config.tailscaleAnchorHost.ifBlank { config.primaryEdgeHost }
        }
        selectChapterTrack(trackKey = trackKey, baseUrl = base, hearthKeyNodeId = hearthKeyNodeId)
    }

    fun setSleepFadeTimerMinutes(minutes: Int) = setStarlightSleepTimerMinutes(minutes)

    fun cycleSleepFadeTimer() = cycleStarlightSleepTimer()

    fun release() {
        sleepTimerJob?.cancel()
        stopAudioResources()
    }

    companion object {
        const val DEFAULT_STORYTELLER_KEY = "cb64f9e7"
        const val SAMPLE_RATE_HZ = 22_050

        fun buildStreamUrl(baseUrl: String, trackKey: String = DEFAULT_STORYTELLER_KEY): String {
            val cleanBase = baseUrl.trim().trimEnd('/').ifBlank { "https://lfs-prod.tail420c4d.ts.net" }
            val cleanKey = trackKey.trim().ifBlank { DEFAULT_STORYTELLER_KEY }
            return "$cleanBase/api/lfs/openaudible/stream/$cleanKey"
        }

        fun buildLibraryUrl(baseUrl: String): String {
            val cleanBase = baseUrl.trim().trimEnd('/').ifBlank { "https://lfs-prod.tail420c4d.ts.net" }
            return "$cleanBase/api/lfs/openaudible/library"
        }

        /**
         * Computes the smooth Starlight Sleep-Fade volume multiplier (`0.0f..1.0f`)
         * given active timer minutes (`0` = Off, or `15`, `30`, `45`) and remaining seconds.
         */
        fun computeSleepFadeVolumeMultiplier(sleepTimerMinutes: Int, remainingSeconds: Int): Float {
            if (sleepTimerMinutes <= 0) return 1.0f
            val totalSeconds = sleepTimerMinutes * 60
            if (remainingSeconds <= 0) return 0.0f
            return (remainingSeconds.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f)
        }

        /**
         * Synthesizes a soothing 4-note celesta / music-box arpeggio in 16-bit mono PCM
         * for the offline Sanctuary Storyteller fallback.
         */
        fun synthesizeMusicBoxLullabyPcm(
            phraseIndex: Int = 0,
            volumeScale: Float = 0.5f,
            sampleRateHz: Int = SAMPLE_RATE_HZ,
            durationSec: Int = 0
        ): ShortArray {
            val chords = arrayOf(
                floatArrayOf(523.25f, 659.25f, 783.99f, 987.77f), // Cmaj7 celesta
                floatArrayOf(440.00f, 523.25f, 659.25f, 783.99f), // Am7 starlight
                floatArrayOf(349.23f, 440.00f, 523.25f, 659.25f), // Fmaj7 sanctuary
                floatArrayOf(392.00f, 493.88f, 587.33f, 783.99f)  // G6 resolution
            )
            val notes = chords[phraseIndex.coerceAtLeast(0) % chords.size]
            val totalSamples = if (durationSec > 0) {
                sampleRateHz * durationSec
            } else {
                (sampleRateHz / 3) * notes.size
            }
            val noteSamples = (totalSamples / notes.size).coerceAtLeast(1)
            val out = ShortArray(totalSamples)
            val safeVol = volumeScale.coerceIn(0f, 1f)

            for (idx in 0 until totalSamples) {
                val nIdx = (idx / noteSamples).coerceAtMost(notes.lastIndex)
                val i = idx % noteSamples
                val freq = notes[nIdx].toDouble()
                val t = i.toDouble() / sampleRateHz.toDouble()
                val attack = (i.toDouble() / 180.0).coerceAtMost(1.0)
                val decay = exp(-t * 5.2)
                val fundamental = sin(2.0 * PI * freq * t)
                val overtone = 0.28 * sin(2.0 * PI * (freq * 2.0) * t)
                val bell = 0.12 * sin(2.0 * PI * (freq * 3.0) * t)
                val sample = (fundamental + overtone + bell) * attack * decay * safeVol
                out[idx] = (sample * 24_000.0).roundToInt().coerceIn(-32767, 32767).toShort()
            }
            return out
        }

        fun parseLibraryJson(rawJson: String): List<OpenAudibleChapterTrack> {
            if (rawJson.isBlank()) return emptyList()
            val results = mutableListOf<OpenAudibleChapterTrack>()
            val blocks = Regex("\\{[^{}]*\"(?:key|id|bookTitle|title)\"[^{}]*\\}").findAll(rawJson)
            for (m in blocks) {
                val b = m.value
                val key = Regex("\"key\"\\s*:\\s*\"([^\"]+)\"").find(b)?.groupValues?.getOrNull(1)
                    ?: DEFAULT_STORYTELLER_KEY
                val bookTitle = Regex("\"bookTitle\"\\s*:\\s*\"([^\"]+)\"").find(b)?.groupValues?.getOrNull(1)
                    ?: Regex("\"title\"\\s*:\\s*\"([^\"]+)\"").find(b)?.groupValues?.getOrNull(1)
                    ?: "Chronicles of the Starlight Citadel"
                val chapterTitle = Regex("\"chapterTitle\"\\s*:\\s*\"([^\"]+)\"").find(b)?.groupValues?.getOrNull(1)
                    ?: "Chapter 1: The Hearth Key"
                results.add(
                    OpenAudibleChapterTrack(
                        key = key,
                        bookTitle = bookTitle,
                        chapterTitle = chapterTitle,
                        streamPath = "/api/lfs/openaudible/stream/$key"
                    )
                )
            }
            return results
        }
    }
}
