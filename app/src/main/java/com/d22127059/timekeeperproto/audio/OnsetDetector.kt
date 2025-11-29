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

class OnsetDetector(
    private val sampleRate: Int = 44100,
    private val bufferSize: Int = 2048,
    private val sensitivity: Double = 8.0,
    private val threshold: Double = 0.3
) {
    companion object {
        private const val TAG = "OnsetDetector"

        // ✅ CRITICAL: Audio processing latency compensation
        // This represents the delay from audio capture to onset detection
        private const val AUDIO_PROCESSING_LATENCY_MS = 160L
    }

    private var audioRecord: AudioRecord? = null
    private var processingJob: Job? = null
    private var isRecording = false
    private var recordingStartTime: Long = 0L

    var onOnsetDetected: ((timestamp: Long) -> Unit)? = null

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun initialize(): Boolean {  // ✅ FIXED: Added this method
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

    // ✅ FIXED: Renamed from startDetection to start
    fun start(sessionStartTime: Long, coroutineScope: CoroutineScope) {
        if (isRecording) {
            Log.w(TAG, "Already recording")
            return
        }

        recordingStartTime = sessionStartTime

        audioRecord?.let { record ->
            try {
                record.startRecording()
                isRecording = true

                processingJob = coroutineScope.launch(Dispatchers.IO) {
                    processAudio(record)
                }

                Log.d(TAG, "Started onset detection, sessionStart=$sessionStartTime, audioLatencyCompensation=${AUDIO_PROCESSING_LATENCY_MS}ms")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting recording", e)
                isRecording = false
            }
        } ?: run {
            Log.e(TAG, "AudioRecord not initialized")
        }
    }

    // ✅ FIXED: Renamed from stopDetection to stop
    fun stop() {
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

    fun release() {  // ✅ This method exists and matches
        stop()
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
                    // ✅ REVERSED: ADD latency instead of subtracting
                    val onsetTimeMs = (timeInSeconds * 1000.0).toLong()
                    val compensatedOnsetTimeMs = onsetTimeMs + AUDIO_PROCESSING_LATENCY_MS
                    val actualTimestamp = recordingStartTime + compensatedOnsetTimeMs

                    onOnsetDetected?.invoke(actualTimestamp)

                    Log.d(TAG, "Onset detected: " +
                            "tarsosDSP=${String.format("%.3f", timeInSeconds)}s, " +
                            "rawOnsetTimeMs=${onsetTimeMs}ms, " +
                            "compensatedOnsetTimeMs=${compensatedOnsetTimeMs}ms, " +
                            "sessionStart=$recordingStartTime, " +
                            "actualTimestamp=$actualTimestamp")
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
}