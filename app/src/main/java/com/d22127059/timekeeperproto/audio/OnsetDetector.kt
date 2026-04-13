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
    DRUM_KIT(8.0, 0.03),       // Physical drum kit ~10cm from mic
    PRACTICE_PAD(10.0, 0.08),  // Practice pad, moderate signal
    TABLE(14.0, 0.06),         // Table/quiet surface, weaker transients
    CUSTOM(8.0, 0.05);

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

        // Empirically measured microphone input latency on physical device.
        // Compensates for the delay between a hit occurring and the audio
        // buffer being processed by TarsosDSP.
        private const val INPUT_LATENCY_COMPENSATION_MS = 230L

        // Minimum gap between two accepted onsets.
        // 100ms blocks snare wire resonance (which typically decays within 80ms
        // of the stroke) while leaving the beat window open at all supported BPMs.
        // At 160 BPM the beat interval is 375ms, so 100ms still leaves 275ms open.
        private const val ONSET_DEBOUNCE_MS = 100L
    }

    private var audioRecord: AudioRecord? = null
    private var processingJob: Job? = null
    private var isRecording = false
    private var recordingStartTime: Long = 0L

    @Volatile
    private var lastAcceptedOnsetMs: Long = 0L

    private var currentSurfaceType: SurfaceType = initialSurfaceType
    private var sensitivity: Double = initialSurfaceType.sensitivity
    private var threshold: Double = initialSurfaceType.threshold

    var onOnsetDetected: ((timestamp: Long) -> Unit)? = null

    fun setSurfaceType(surfaceType: SurfaceType) {
        currentSurfaceType = surfaceType
        sensitivity = surfaceType.sensitivity
        threshold = surfaceType.threshold
        Log.d(TAG, "Surface type: $surfaceType (sensitivity=$sensitivity, threshold=$threshold)")
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
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                actualBufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord not initialised properly")
                return false
            }

            lastAcceptedOnsetMs = 0L

            Log.d(TAG, "OnsetDetector initialised: sensitivity=$sensitivity, threshold=$threshold")
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
                recordingStartTime = System.currentTimeMillis()
                lastAcceptedOnsetMs = 0L
                record.startRecording()
                isRecording = true
                processingJob = coroutineScope.launch(Dispatchers.IO) {
                    processAudio(record)
                }
                Log.d(TAG, "Started onset detection at $recordingStartTime")
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
            sampleRate.toFloat(), 16, 1, true, false
        )

        // Snapshot values at detection start so surface type changes
        // mid-session do not affect an active recording
        val activeSensitivity = sensitivity
        val activeThreshold = threshold

        val onsetDetector = PercussionOnsetDetector(
            sampleRate.toFloat(),
            bufferSize,
            OnsetHandler { timeInSeconds, _ ->
                if (isRecording) {
                    val onsetTimeMs = (timeInSeconds * 1000.0).toLong()
                    val rawTimestamp = recordingStartTime + onsetTimeMs + INPUT_LATENCY_COMPENSATION_MS

                    // Debounce: discard onsets arriving within 100ms of the last
                    // accepted hit to suppress snare wire resonance and sympathetic
                    // vibration from other drum components
                    val now = System.currentTimeMillis()
                    if (now - lastAcceptedOnsetMs >= ONSET_DEBOUNCE_MS) {
                        lastAcceptedOnsetMs = now
                        onOnsetDetected?.invoke(rawTimestamp)
                    } else {
                        Log.d(TAG, "Onset debounced: ${now - lastAcceptedOnsetMs}ms after last hit")
                    }
                }
            },
            activeSensitivity,
            activeThreshold
        )

        while (isRecording) {
            val readResult = record.read(audioBuffer, 0, bufferSize)

            if (readResult > 0) {
                for (i in 0 until readResult) floatBuffer[i] = audioBuffer[i] / 32768.0f
                for (i in readResult until bufferSize) floatBuffer[i] = 0.0f
                val audioEvent = AudioEvent(audioFormat).apply {
                    this.floatBuffer = floatBuffer.copyOf()
                }
                onsetDetector.process(audioEvent)
            } else if (readResult == AudioRecord.ERROR_INVALID_OPERATION ||
                readResult == AudioRecord.ERROR_BAD_VALUE) {
                Log.e(TAG, "AudioRecord read error: $readResult")
                break
            }

            yield()
        }
    }

    fun getCurrentSurfaceType(): SurfaceType = currentSurfaceType
}