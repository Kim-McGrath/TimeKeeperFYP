package com.d22127059.timekeeperproto.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.*
import kotlin.math.sin


 //Generates and plays metronome clicks at a specified BPM
 //Uses AudioTrack to synthesise click sounds in realtime

class MetronomeEngine {
    companion object {
        private const val TAG = "MetronomeEngine"
        private const val SAMPLE_RATE = 44100
        private const val CLICK_DURATION_MS = 50
        private const val CLICK_FREQUENCY_HZ = 1000.0

        // AudioTrack output latency - time between write() call and actual speaker playback
        // Adjust for different devices? 280 for emulator
        private const val AUDIO_OUTPUT_LATENCY_MS = 280L
    }

    private var audioTrack: AudioTrack? = null
    private var metronomeJob: Job? = null
    private var isPlaying = false
    private var bpm: Int = 120

    // Callback invoked when each click is played (playback time, not write time)
    var onClickPlayed: ((clickTime: Long, beatNumber: Int) -> Unit)? = null

    private val clickSound: ShortArray by lazy {
        generateClickSound()
    }

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
                Log.e(TAG, "AudioTrack not initialised properly")
                return false
            }

            Log.d(TAG, "MetronomeEngine initialised successfully")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Error initialising MetronomeEngine", e)
            return false
        }
    }


     // Starts the metronome at the specified BPM (100 for demo)
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
                track.play()
                isPlaying = true

                // Write beat 0 immediately
                val writeTime = System.currentTimeMillis()
                playClick(track)

                // Account for output latency to get actual playback time
                val sessionStartTime = writeTime + AUDIO_OUTPUT_LATENCY_MS
                onClickPlayed?.invoke(sessionStartTime, 0)

                Log.d(TAG, "CLICK beat 0 written at ${writeTime}ms, plays at ${sessionStartTime}ms (latency: ${AUDIO_OUTPUT_LATENCY_MS}ms)")

                // Schedule remaining beats in background coroutine
                metronomeJob = coroutineScope.launch(Dispatchers.IO) {
                    var beatNumber = 1
                    var nextClickTime = sessionStartTime + intervalMs

                    while (isPlaying) {
                        val currentTime = System.currentTimeMillis()
                        // Calculate when to write (accounting for output latency)
                        val timeUntilWrite = nextClickTime - AUDIO_OUTPUT_LATENCY_MS - currentTime

                        if (timeUntilWrite <= 0) {
                            val writeTimestamp = System.currentTimeMillis()
                            playClick(track)

                            // Report playback time (write + latency), not write time
                            val playbackTimestamp = writeTimestamp + AUDIO_OUTPUT_LATENCY_MS
                            onClickPlayed?.invoke(playbackTimestamp, beatNumber)

                            Log.d(TAG, "CLICK beat $beatNumber written at ${writeTimestamp}ms, plays at ${playbackTimestamp}ms, expected ${nextClickTime}ms, error: ${playbackTimestamp - nextClickTime}ms")

                            beatNumber++
                            nextClickTime += intervalMs
                        } else {
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
        val samples = (SAMPLE_RATE * CLICK_DURATION_MS / 1000.0).toInt()
        val buffer = ShortArray(samples)

        for (i in 0 until samples) {
            val t = i.toDouble() / SAMPLE_RATE
            val sine = sin(2.0 * Math.PI * CLICK_FREQUENCY_HZ * t)
            val envelope = Math.exp(-t * 30.0)
            val sample = (sine * envelope * 0.8 * Short.MAX_VALUE).toInt()
            buffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        return buffer
    }
}