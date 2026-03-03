package com.d22127059.timekeeperproto.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import kotlin.math.sin


//Generates and plays metronome clicks at a specified BPM
//Uses AudioTrack to synthesise click sounds in realtime
//Dynamically detects device-specific audio latency for accurate timing

class MetronomeEngine(private val context: Context? = null) {
    companion object {
        private const val TAG = "MetronomeEngine"
        private const val CLICK_DURATION_MS = 50
        private const val CLICK_FREQUENCY_HZ = 1000.0

        // Fallback latency for older devices or if measurement fails
        private const val FALLBACK_LATENCY_MS = 50L
    }

    private var audioTrack: AudioTrack? = null
    private var metronomeJob: Job? = null
    private var isPlaying = false
    private var bpm: Int = 120
    private var measuredLatencyMs: Long = FALLBACK_LATENCY_MS
    private var sampleRate: Int = 44100
    private var preGeneratedBeats: MutableList<ShortArray> = mutableListOf()

    // Callback invoked when each click is played (playback time, not write time)
    var onClickPlayed: ((clickTime: Long, beatNumber: Int) -> Unit)? = null

    private val clickSound: ShortArray by lazy {
        generateClickSound()
    }

    fun initialize(): Boolean {
        try {
            // Get the native sample rate for this device - CRITICAL for avoiding underruns
            sampleRate = getNativeSampleRate()
            Log.d(TAG, "Using native sample rate: $sampleRate Hz")

            val minBufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            if (minBufferSize == AudioTrack.ERROR || minBufferSize == AudioTrack.ERROR_BAD_VALUE) {
                Log.e(TAG, "Failed to get minimum buffer size")
                return false
            }

            // Use 4x minimum buffer for physical devices to prevent underruns
            // This is much larger than emulator needs, but necessary for real hardware
            val bufferSize = minBufferSize * 4

            Log.d(TAG, "Min buffer size: $minBufferSize, using: $bufferSize")

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
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .apply {
                    // Enable low latency mode on supported devices
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                    }
                }
                .build()

            if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                Log.e(TAG, "AudioTrack not initialised properly")
                return false
            }

            // Measure actual device latency
            measuredLatencyMs = measureAudioLatency()

            Log.d(TAG, "MetronomeEngine initialised successfully")
            Log.d(TAG, "Buffer size: $bufferSize (min: $minBufferSize)")
            Log.d(TAG, "Measured latency: ${measuredLatencyMs}ms")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Error initialising MetronomeEngine", e)
            return false
        }
    }

    /**
     * Gets the native sample rate for this device's audio output
     * Using the native sample rate prevents resampling and reduces underruns
     */
    private fun getNativeSampleRate(): Int {
        return try {
            context?.let {
                val audioManager = it.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                audioManager?.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull()
            } ?: 44100
        } catch (e: Exception) {
            Log.w(TAG, "Could not get native sample rate, using 44100", e)
            44100
        }
    }

    /**
     * Measures the actual audio output latency for this device
     * Uses multiple methods depending on Android version
     */
    private fun measureAudioLatency(): Long {
        audioTrack?.let { track ->
            try {
                // Method 1: Calculate from buffer size and sample rate
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val bufferSize = track.bufferSizeInFrames
                    val calculatedLatencyMs = ((bufferSize.toDouble() / sampleRate) * 1000).toLong()

                    Log.d(TAG, "Calculated latency from buffer: ${calculatedLatencyMs}ms")

                    // Use calculated value if reasonable
                    if (calculatedLatencyMs > 0 && calculatedLatencyMs < 500) {
                        return calculatedLatencyMs
                    }
                } else {
                    // For older APIs, estimate from buffer size in bytes
                    val bufferSizeBytes = AudioTrack.getMinBufferSize(
                        sampleRate,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT
                    )
                    // 2 bytes per sample (16-bit), 1 channel
                    val bufferSizeFrames = bufferSizeBytes / 2
                    val calculatedLatencyMs = ((bufferSizeFrames.toDouble() / sampleRate) * 1000 * 4).toLong()

                    Log.d(TAG, "Calculated latency (pre-M): ${calculatedLatencyMs}ms")

                    if (calculatedLatencyMs > 0 && calculatedLatencyMs < 500) {
                        return calculatedLatencyMs
                    }
                }

            } catch (e: Exception) {
                Log.w(TAG, "Error measuring latency: ${e.message}")
            }
        }

        // Method 2: Device-specific fallback based on known characteristics
        val deviceLatency = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> 40L  // Modern devices
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> 60L  // Android 6+
            else -> 80L  // Older devices
        }

        Log.d(TAG, "Using device-specific fallback latency: ${deviceLatency}ms")
        return deviceLatency
    }


    // Starts the metronome at the specified BPM
    // Returns the timestamp when beat 0 will be heard by the user

    fun start(bpm: Int, coroutineScope: CoroutineScope): Long {
        if (isPlaying) {
            Log.w(TAG, "Metronome already playing")
            return System.currentTimeMillis()
        }

        this.bpm = bpm
        val intervalMs = (60000.0 / bpm).toLong()

        audioTrack?.let { track ->
            try {
                // CRITICAL: Fill the buffer with silence before starting to prevent underruns
                val silenceBuffer = ShortArray(track.bufferSizeInFrames)
                track.write(silenceBuffer, 0, silenceBuffer.size)

                track.play()
                isPlaying = true

                // Write beat 0 immediately
                val writeTime = System.currentTimeMillis()
                playClick(track)

                // Account for output latency to get actual playback time
                val sessionStartTime = writeTime + measuredLatencyMs
                onClickPlayed?.invoke(sessionStartTime, 0)

                Log.d(TAG, "CLICK beat 0 written at ${writeTime}ms, plays at ${sessionStartTime}ms (latency: ${measuredLatencyMs}ms)")

                // Schedule remaining beats in background coroutine
                metronomeJob = coroutineScope.launch(Dispatchers.IO) {
                    var beatNumber = 1
                    var nextClickTime = sessionStartTime + intervalMs

                    while (isPlaying) {
                        val currentTime = System.currentTimeMillis()
                        // Calculate when to write (accounting for output latency)
                        val writeTimestamp = nextClickTime - measuredLatencyMs
                        val timeUntilWrite = writeTimestamp - currentTime

                        if (timeUntilWrite <= 0) {
                            // We're at or past the write time
                            playClick(track)
                            val actualWriteTime = System.currentTimeMillis()

                            // Report playback time (write + latency), not write time
                            val playbackTimestamp = actualWriteTime + measuredLatencyMs
                            val drift = playbackTimestamp - nextClickTime

                            onClickPlayed?.invoke(playbackTimestamp, beatNumber)

                            Log.d(TAG, "CLICK beat $beatNumber written at ${actualWriteTime}ms, plays at ${playbackTimestamp}ms, expected ${nextClickTime}ms, drift: ${drift}ms")

                            beatNumber++
                            nextClickTime += intervalMs
                        } else {
                            // Wait until it's time to write the next beat
                            // Use smaller delays for more precise timing
                            delay(minOf(timeUntilWrite, 10))
                        }
                    }
                }

                Log.d(TAG, "Metronome started at $bpm BPM (${intervalMs}ms interval)")
                return sessionStartTime

            } catch (e: Exception) {
                Log.e(TAG, "Error starting metronome", e)
                isPlaying = false
            }
        } ?: run {
            Log.e(TAG, "AudioTrack not initialised")
        }

        return System.currentTimeMillis()
    }

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

    fun release() {
        stop()
        audioTrack?.release()
        audioTrack = null
        Log.d(TAG, "MetronomeEngine released")
    }

    private fun playClick(track: AudioTrack) {
        try {
            track.write(clickSound, 0, clickSound.size)
        } catch (e: Exception) {
            Log.e(TAG, "Error playing click", e)
        }
    }

    // Generates a 1kHz sine wave with exponential decay envelope
    // Creates a sharp "click" sound suitable for metronome
    private fun generateClickSound(): ShortArray {
        val samples = (sampleRate * CLICK_DURATION_MS / 1000.0).toInt()
        val buffer = ShortArray(samples)

        for (i in 0 until samples) {
            val t = i.toDouble() / sampleRate
            val sine = sin(2.0 * Math.PI * CLICK_FREQUENCY_HZ * t)
            val envelope = Math.exp(-t * 30.0)
            val sample = (sine * envelope * 0.8 * Short.MAX_VALUE).toInt()
            buffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        return buffer
    }

    /**
     * Gets the current measured latency (useful for debugging)
     */
    fun getMeasuredLatency(): Long = measuredLatencyMs
}