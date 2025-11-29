package com.d22127059.timekeeperproto.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.*
import kotlin.math.sin

/**
 * Simple metronome engine that generates and plays click sounds at a specified BPM.
 * Uses AudioTrack to synthesize click sounds in real-time.
 */
class MetronomeEngine {
    companion object {
        private const val TAG = "MetronomeEngine"
        private const val SAMPLE_RATE = 44100
        private const val CLICK_DURATION_MS = 50
        private const val CLICK_FREQUENCY_HZ = 1000.0 // 1kHz tone for click
    }

    private var audioTrack: AudioTrack? = null
    private var metronomeJob: Job? = null
    private var isPlaying = false
    private var bpm: Int = 120

    // Callback when a click is actually played
    var onClickPlayed: ((clickTime: Long, beatNumber: Int) -> Unit)? = null

    // Pre-generated click sound
    private val clickSound: ShortArray by lazy {
        generateClickSound()
    }

    /**
     * Initializes the AudioTrack for playback.
     */
    fun initialize(): Boolean {
        try {
            val bufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

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

            if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                Log.e(TAG, "AudioTrack not initialized properly")
                return false
            }

            Log.d(TAG, "MetronomeEngine initialized successfully")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Error initializing MetronomeEngine", e)
            return false
        }
    }

    /**
     * Starts the metronome at the specified BPM.
     * Returns the session start time (first beat timestamp).
     */
    fun start(bpm: Int, coroutineScope: CoroutineScope): Long {
        if (isPlaying) {
            Log.w(TAG, "Metronome already playing")
            return System.currentTimeMillis()
        }

        this.bpm = bpm
        val intervalMs = (60000.0 / bpm).toLong()

        // Set the session start time to NOW
        val sessionStartTime = System.currentTimeMillis()

        audioTrack?.let { track ->
            try {
                track.play()
                isPlaying = true

                metronomeJob = coroutineScope.launch(Dispatchers.IO) {
                    var beatNumber = 0
                    var nextClickTime = sessionStartTime + intervalMs

                    while (isPlaying) {
                        val currentTime = System.currentTimeMillis()
                        val timeUntilClick = nextClickTime - currentTime

                        if (timeUntilClick <= 0) {
                            // Time to play click
                            playClick(track)

                            // Invoke callback AFTER click is played
                            onClickPlayed?.invoke(currentTime, beatNumber)

                            Log.d(TAG, "CLICK beat $beatNumber at ${currentTime}ms, expected ${nextClickTime}ms, error: ${currentTime - nextClickTime}ms")

                            // Schedule next click
                            beatNumber++
                            nextClickTime += intervalMs
                        } else {
                            // Wait until next click time (but not too long)
                            delay(minOf(timeUntilClick, 10))
                        }
                    }
                }

                Log.d(TAG, "Metronome started at $bpm BPM (${intervalMs}ms interval), session start: $sessionStartTime")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting metronome", e)
                isPlaying = false
            }
        } ?: run {
            Log.e(TAG, "AudioTrack not initialized")
        }

        return sessionStartTime
    }

    /**
     * Stops the metronome.
     */
    fun stop() {
        if (!isPlaying) return

        isPlaying = false
        metronomeJob?.cancel()

        try {
            audioTrack?.pause()
            audioTrack?.flush()
            Log.d(TAG, "Metronome stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping metronome", e)
        }
    }

    /**
     * Releases all resources.
     */
    fun release() {
        stop()
        audioTrack?.release()
        audioTrack = null
        Log.d(TAG, "MetronomeEngine released")
    }

    /**
     * Plays a single click sound.
     */
    private fun playClick(track: AudioTrack) {
        try {
            track.write(clickSound, 0, clickSound.size)
        } catch (e: Exception) {
            Log.e(TAG, "Error playing click", e)
        }
    }

    /**
     * Generates a short click sound (sine wave with envelope).
     */
    private fun generateClickSound(): ShortArray {
        val samples = (SAMPLE_RATE * CLICK_DURATION_MS / 1000.0).toInt()
        val buffer = ShortArray(samples)

        for (i in 0 until samples) {
            val t = i.toDouble() / SAMPLE_RATE

            // Sine wave
            val sine = sin(2.0 * Math.PI * CLICK_FREQUENCY_HZ * t)

            // Envelope: Quick attack, exponential decay
            val envelope = Math.exp(-t * 30.0)

            // Combine and scale to 16-bit PCM range (INCREASED VOLUME)
            val sample = (sine * envelope * 0.8 * Short.MAX_VALUE).toInt()
            buffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        return buffer
    }
}