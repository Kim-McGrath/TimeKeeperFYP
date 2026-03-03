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

enum class SurfaceType(val sensitivity: Double, val threshold: Double) {
    DRUM_KIT(8.0, 0.3),
    PRACTICE_PAD(12.0, 0.25),
    TABLE(15.0, 0.2),
    CUSTOM(8.0, 0.3);

    companion object {
        fun fromString(name: String): SurfaceType {
            return when (name.uppercase()) {
                "DRUM_KIT" -> DRUM_KIT
                "PRACTICE_PAD" -> PRACTICE_PAD
                "TABLE" -> TABLE
                else -> CUSTOM
            }
        }
    }
}

class OnsetDetector(
    private val sampleRate: Int = 44100,
    private val bufferSize: Int = 2048,
    initialSurfaceType: SurfaceType = SurfaceType.DRUM_KIT
) {
    companion object {
        private const val TAG = "OnsetDetector"
    }

    private var audioRecord: AudioRecord? = null
    private var processingJob: Job? = null
    private var isRecording = false
    private var recordingStartTime: Long = 0L

    private var currentSurfaceType: SurfaceType = initialSurfaceType
    private var sensitivity: Double = initialSurfaceType.sensitivity
    private var threshold: Double = initialSurfaceType.threshold

    var onOnsetDetected: ((timestamp: Long) -> Unit)? = null

    fun setSurfaceType(surfaceType: SurfaceType) {
        currentSurfaceType = surfaceType
        sensitivity = surfaceType.sensitivity
        threshold = surfaceType.threshold
        Log.d(TAG, "Surface type changed to $surfaceType (sensitivity=$sensitivity, threshold=$threshold)")
    }

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
                MediaRecorder.AudioSource.VOICE_COMMUNICATION, // Enables hardware AEC
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                actualBufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord not initialised properly")
                return false
            }

            Log.d(TAG, "OnsetDetector initialised: sampleRate=$sampleRate, bufferSize=$actualBufferSize")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Error initialising OnsetDetector", e)
            return false
        }
    }

    fun startDetection(sessionStartTime: Long, coroutineScope: CoroutineScope) {
        if (isRecording) {
            Log.w(TAG, "Already recording")
            return
        }

        audioRecord?.let { record ->
            try {
                val actualRecordingStart = System.currentTimeMillis()
                recordingStartTime = actualRecordingStart

                record.startRecording()
                isRecording = true

                processingJob = coroutineScope.launch(Dispatchers.IO) {
                    processAudio(record)
                }

                Log.d(TAG, "Started onset detection at $actualRecordingStart")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting recording", e)
                isRecording = false
            }
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
                if (isRecording) {
                    val onsetTimeMs = (timeInSeconds * 1000.0).toLong()
                    val actualTimestamp = recordingStartTime + onsetTimeMs
                    onOnsetDetected?.invoke(actualTimestamp)
                }
            },
            sensitivity,
            threshold
        )

        while (isRecording) {
            val readResult = record.read(audioBuffer, 0, bufferSize)

            if (readResult > 0) {
                for (i in 0 until readResult) {
                    floatBuffer[i] = audioBuffer[i] / 32768.0f
                }

                for (i in readResult until bufferSize) {
                    floatBuffer[i] = 0.0f
                }

                val audioEvent = AudioEvent(audioFormat).apply {
                    this.floatBuffer = floatBuffer.copyOf()
                }

                onsetDetector.process(audioEvent)

            } else if (readResult == AudioRecord.ERROR_INVALID_OPERATION) {
                Log.e(TAG, "Invalid operation whilst reading audio")
                break
            } else if (readResult == AudioRecord.ERROR_BAD_VALUE) {
                Log.e(TAG, "Bad value whilst reading audio")
                break
            }

            yield()
        }
    }

    fun getCurrentSurfaceType(): SurfaceType = currentSurfaceType
}