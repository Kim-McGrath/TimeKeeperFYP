package com.d22127059.timekeeperproto.audio

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.annotation.RequiresPermission
import be.tarsos.dsp.AudioEvent
import be.tarsos.dsp.io.TarsosDSPAudioFormat
import be.tarsos.dsp.onsets.OnsetHandler
import be.tarsos.dsp.onsets.PercussionOnsetDetector
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

class OnsetDetector(
    private val sampleRate: Int = 44100,
    private val bufferSize: Int = 2048,
    private val sensitivity: Double = 8.0, // Lower = more sensitive
    private val threshold: Double = 0.3    // Minimum onset strength
) {
    companion object {
        private const val TAG = "OnsetDetector"
    }

    private var audioRecord: AudioRecord? = null
    private var processingJob: Job? = null
    private var isRecording = false
    private var sessionStartTime: Long = 0L

    // Callback for when an onset (hit) is detected
    var onOnsetDetected: ((timestamp: Long) -> Unit)? = null

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun initialize(): Boolean {
        try {
            val minBufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
                Log.e(TAG, "Failed to get minimum buffer size")
                return false
            }

            // Use larger of calculated buffer or minimum buffer
            val actualBufferSize = maxOf(bufferSize * 2, minBufferSize)

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                actualBufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord not initialized properly")
                return false
            }

            Log.d(
                TAG,
                "OnsetDetector initialized: sampleRate=$sampleRate, bufferSize=$actualBufferSize"
            )
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Error initializing OnsetDetector", e)
            return false
        }
    }

    fun startDetection(coroutineScope: CoroutineScope) {
        if (isRecording) {
            Log.w(TAG, "Already recording")
            return
        }

        sessionStartTime = System.currentTimeMillis()

        audioRecord?.let { record ->
            try {
                record.startRecording()
                isRecording = true

                // Launch audio processing on IO dispatcher
                processingJob = coroutineScope.launch(Dispatchers.IO) {
                    processAudio(record)
                }

                Log.d(TAG, "Started onset detection")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting recording", e)
                isRecording = false
            }
        } ?: run {
            Log.e(TAG, "AudioRecord not initialized")
        }
    }

    fun stopDetection() {
        if (!isRecording) return

        isRecording = false
        processingJob?.cancel()

        try {
            audioRecord?.stop()
            Log.d(TAG, "Stopped onset detection")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
        }
    }


    fun release() {
        stopDetection()
        audioRecord?.release()
        audioRecord = null
        Log.d(TAG, "Released OnsetDetector resources")
    }

    private suspend fun processAudio(record: AudioRecord) {
        val audioBuffer = ShortArray(bufferSize)
        val floatBuffer = FloatArray(bufferSize)

        // Create TarsosDSP audio format
        val audioFormat = TarsosDSPAudioFormat(
            sampleRate.toFloat(),
            16, // 16-bit
            1,  // Mono
            true, // Signed
            false // Little endian
        )

        // Create percussion onset detector
        val onsetDetector = PercussionOnsetDetector(
            sampleRate.toFloat(),
            bufferSize,
            OnsetHandler { time, _ ->
                // Onset detected callback
                val timestamp = System.currentTimeMillis()

                // Only trigger if actively recording
                if (isRecording) {
                    onOnsetDetected?.invoke(timestamp)
                    Log.d(TAG, "Onset detected at ${timestamp - sessionStartTime}ms since start")
                }
            },
            sensitivity,
            threshold
        )

        var frameCounter = 0L

        while (isRecording) {
            // Read audio data
            val readResult = record.read(audioBuffer, 0, bufferSize)

            if (readResult > 0) {
                // Convert short samples to float (-1.0 to 1.0 range)
                for (i in 0 until readResult) {
                    floatBuffer[i] = audioBuffer[i] / 32768.0f
                }

                // Create audio event for TarsosDSP
                suspend fun processAudio(record: AudioRecord) {
                    val audioBuffer = ShortArray(bufferSize)
                    val floatBuffer = FloatArray(bufferSize)

                    // Create TarsosDSP audio format
                    val audioFormat = TarsosDSPAudioFormat(
                        sampleRate.toFloat(),
                        16, // 16-bit
                        1,  // Mono
                        true, // Signed
                        false // Little endian
                    )

                    // Create percussion onset detector
                    val onsetDetector = PercussionOnsetDetector(
                        sampleRate.toFloat(),
                        bufferSize,
                        OnsetHandler { time, _ ->
                            // Onset detected callback
                            val timestamp = System.currentTimeMillis()

                            // Only trigger if actively recording
                            if (isRecording) {
                                onOnsetDetected?.invoke(timestamp)
                                Log.d(
                                    TAG,
                                    "Onset detected at ${timestamp - sessionStartTime}ms since start"
                                )
                            }
                        },
                        sensitivity,
                        threshold
                    )

                    while (isRecording) {
                        // Read audio data
                        val readResult = record.read(audioBuffer, 0, bufferSize)

                        if (readResult > 0) {
                            // Convert short samples to float (-1.0 to 1.0 range)
                            for (i in 0 until readResult) {
                                floatBuffer[i] = audioBuffer[i] / 32768.0f
                            }

                            // Create audio event for TarsosDSP
                            val audioEvent = AudioEvent(audioFormat).apply {
                                this.floatBuffer = floatBuffer.copyOf()
                            }

                            // Process through onset detector
                            onsetDetector.process(audioEvent)

                        } else if (readResult == AudioRecord.ERROR_INVALID_OPERATION) {
                            Log.e(TAG, "Invalid operation while reading audio")
                            break
                        } else if (readResult == AudioRecord.ERROR_BAD_VALUE) {
                            Log.e(TAG, "Bad value while reading audio")
                            break
                        }

                        // Yield to prevent blocking
                        yield()
                    }
                }

                /**
                 * Updates sensitivity settings on the fly.
                 * Note: This creates a new detector instance, so there may be a brief interruption.
                 *
                 * @param newSensitivity Lower values = more sensitive
                 * @param newThreshold Minimum onset strength (0.0 to 1.0)
                 */
                fun updateSensitivity(newSensitivity: Double, newThreshold: Double) {
                    // Would need to recreate detector with new settings
                    // For prototype sensitivity is set at initialization
                    Log.d(
                        TAG,
                        "Sensitivity update requested: $newSensitivity, threshold: $newThreshold"
                    )
                }
            }
        }
    }
}