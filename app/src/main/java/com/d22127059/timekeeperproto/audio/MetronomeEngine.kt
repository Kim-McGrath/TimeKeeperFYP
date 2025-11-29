package com.d22127059.timekeeperproto.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Generates and plays metronome clicks using AudioTrack.
 */
class MetronomeEngine {
    companion object {
        private const val TAG = "MetronomeEngine"
        private const val SAMPLE_RATE = 44100
        private const val CLICK_FREQUENCY = 1000.0 // 1kHz tone
        private const val CLICK_DURATION_MS = 50
    }

    private var audioTrack: AudioTrack? = null
    private var metronomeScopeJob: Job? = null
    private var isInitialized = false

    // Callback when a click is actually played
    var onClickPlayed: ((clickTime: Long, beatNumber: Int) -> Unit)? = null

    /**
     * Initialize the AudioTrack for metronome playback.
     */
    fun initialize(): Boolean {
        return try {
            val minBufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            if (minBufferSize == AudioTrack.ERROR_BAD_VALUE || minBufferSize == AudioTrack.ERROR) {
                Log.e(TAG, "Invalid buffer size")
                return false
            }

            val bufferSize = minBufferSize * 4

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            isInitialized = true
            Log.d(TAG, "MetronomeEngine initialized successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MetronomeEngine", e)
            false
        }
    }

    /**
     * Start the metronome at the specified BPM.
     * Returns the timestamp when the metronome started (first click time).
     */
    fun start(bpm: Int, scope: CoroutineScope): Long {
        if (!isInitialized || audioTrack == null) {
            Log.e(TAG, "Cannot start - not initialized")
            return System.currentTimeMillis()
        }

        stop() // Stop any existing metronome

        val intervalMs = (60000.0 / bpm).toLong()
        Log.d(TAG, "Starting metronome at $bpm BPM (${intervalMs}ms interval)")

        // Start AudioTrack playback
        audioTrack?.play()

        // Record the start time
        val startTime = System.currentTimeMillis()
        var beatNumber = 0

        metronomeScopeJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                val now = System.currentTimeMillis()
                val expectedClickTime = startTime + (beatNumber * intervalMs)
                val timeUntilClick = expectedClickTime - now

                if (timeUntilClick > 0) {
                    delay(timeUntilClick)
                }

                // Generate and play the click
                val clickTime = System.currentTimeMillis()
                playClick(audioTrack!!)

                // Notify callback
                onClickPlayed?.invoke(clickTime, beatNumber)

                val timingError = clickTime - expectedClickTime
                Log.d(TAG, "Beat $beatNumber - Expected: $expectedClickTime, Actual: $clickTime, Error: ${timingError}ms")

                beatNumber++
            }
        }

        return startTime
    }

    /**
     * Generate and play a single click sound.
     */
    private fun playClick(track: AudioTrack) {
        val clickSamples = (SAMPLE_RATE * CLICK_DURATION_MS / 1000.0).toInt()
        val buffer = ShortArray(clickSamples)

        for (i in 0 until clickSamples) {
            // Generate sine wave
            val angle = 2.0 * PI * CLICK_FREQUENCY * i / SAMPLE_RATE
            val sample = sin(angle)

            // Apply exponential decay envelope
            val decay = exp(-5.0 * i / clickSamples)
            val enveloped = sample * decay

            // Convert to 16-bit PCM
            buffer[i] = (enveloped * Short.MAX_VALUE * 0.8).toInt().toShort()
        }

        // Write to AudioTrack
        track.write(buffer, 0, buffer.size)
    }

    /**
     * Stop the metronome.
     */
    fun stop() {
        metronomeScopeJob?.cancel()
        metronomeScopeJob = null
        audioTrack?.stop()
        audioTrack?.flush() // Add flush to clear buffer
        Log.d(TAG, "Metronome stopped")
    }

    /**
     * Release resources.
     */
    fun release() {
        stop()
        audioTrack?.release()
        audioTrack = null
        isInitialized = false
        Log.d(TAG, "MetronomeEngine released")
    }
}