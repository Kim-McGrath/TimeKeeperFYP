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

/**
 * Handles real-time audio processing for detecting drum hit onsets.
 * Uses TarsosDSP's PercussionOnsetDetector to identify transient audio events.
 */
class OnsetDetector(
    private val sampleRate: Int = 44100,
    private val bufferSize: Int = 2048,
    private val sensitivity: Double = 8.0,
    private val threshold: Double = 0.3
) {
    companion object {
        private const val TAG = "OnsetDetector"
    }

    private var audioRecord: AudioRecord? = null
    private var processingJob: Job? = null
    private var isRecording = false
    private var audioStartTime: Long = 0L  // ✅ ADD: Track when audio capture started
    private var totalSamplesProcessed: Long = 0  // ✅ ADD: Track sample position

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

            Log.d(TAG, "OnsetDetector initialized: sampleRate=$sampleRate, bufferSize=$actualBufferSize")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Error initializing OnsetDetector", e)
            return false
        }
    }

    /**
     * Starts recording and processing audio for onset detection.
     */
    fun startDetection(coroutineScope: CoroutineScope) {
        if (isRecording) {
            Log.w(TAG, "Already recording")
            return
        }

        audioStartTime = System.currentTimeMillis()  // ✅ Record start time
        totalSamplesProcessed = 0  // ✅ Reset sample counter

        audioRecord?.let { record ->
            try {
                record.startRecording()
                isRecording = true

                processingJob = coroutineScope.launch(Dispatchers.IO) {
                    processAudio(record)
                }

                Log.d(TAG, "Started onset detection at $audioStartTime")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting recording", e)
                isRecording = false
            }
        } ?: run {
            Log.e(TAG, "AudioRecord not initialized")
        }
    }

    /**
     * Stops recording and releases audio resources.
     */
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

    /**
     * Releases all resources.
     */
    fun release() {
        stopDetection()
        audioRecord?.release()
        audioRecord = null
        Log.d(TAG, "Released OnsetDetector resources")
    }

    /**
     * Main audio processing loop.
     */
    private suspend fun processAudio(record: AudioRecord) {
        val audioBuffer = ShortArray(bufferSize)
        val floatBuffer = FloatArray(bufferSize)

        val audioFormat = TarsosDSPAudioFormat(
            sampleRate.toFloat(),
            16,
            1,
            true,
            false
        )

        val onsetDetector = PercussionOnsetDetector(
            sampleRate.toFloat(),
            bufferSize,
            OnsetHandler { timeInSeconds, _ ->
                // ✅ FIXED: Calculate actual timestamp from audio position
                val samplePosition = totalSamplesProcessed + (timeInSeconds * sampleRate).toLong()
                val audioElapsedMs = (samplePosition * 1000.0 / sampleRate).toLong()
                val actualTimestamp = audioStartTime + audioElapsedMs

                if (isRecording) {
                    onOnsetDetected?.invoke(actualTimestamp)
                    Log.d(TAG, "Onset detected: audioTime=${timeInSeconds}s, " +
                            "samplePos=$samplePosition, " +
                            "timestamp=$actualTimestamp")
                }
            },
            sensitivity,
            threshold
        )

        while (isRecording) {
            val readResult = record.read(audioBuffer, 0, bufferSize)

            if (readResult > 0) {
                // Convert short samples to float
                for (i in 0 until readResult) {
                    floatBuffer[i] = audioBuffer[i] / 32768.0f
                }

                // ✅ Track total samples processed
                totalSamplesProcessed += readResult

                val audioEvent = AudioEvent(audioFormat).apply {
                    this.floatBuffer = floatBuffer.copyOf()
                }

                onsetDetector.process(audioEvent)

            } else if (readResult == AudioRecord.ERROR_INVALID_OPERATION) {
                Log.e(TAG, "Invalid operation while reading audio")
                break
            } else if (readResult == AudioRecord.ERROR_BAD_VALUE) {
                Log.e(TAG, "Bad value while reading audio")
                break
            }

            yield()
        }
    }

    /**
     * Updates sensitivity settings.
     */
    fun updateSensitivity(newSensitivity: Double, newThreshold: Double) {
        Log.d(TAG, "Sensitivity update requested: $newSensitivity, threshold: $newThreshold")
    }
}