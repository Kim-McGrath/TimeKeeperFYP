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

 // Detects drum hits using microphone input and TarsosDSPs percussion onset detection.

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
    private var recordingStartTime: Long = 0L

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


     // Starts onset detection.
     // @param sessionStartTime When the session started (for logging/debugging only)
    fun startDetection(sessionStartTime: Long, coroutineScope: CoroutineScope) {
        if (isRecording) {
            Log.w(TAG, "Already recording")
            return
        }

        audioRecord?.let { record ->
            try {
                // Record the actual time when recording starts
                val actualRecordingStart = System.currentTimeMillis()
                recordingStartTime = actualRecordingStart

                record.startRecording()
                isRecording = true

                processingJob = coroutineScope.launch(Dispatchers.IO) {
                    processAudio(record)
                }

                Log.d(TAG, "Started onset detection at $actualRecordingStart (session started at $sessionStartTime, delay=${actualRecordingStart - sessionStartTime}ms)")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting recording", e)
                isRecording = false
            }
        } ?: run {
            Log.e(TAG, "AudioRecord not initialised")
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
                    // TarsosDSP timestamps already include all processing latency.
                    // Simply convert to milliseconds and add to recording start time.
                    val onsetTimeMs = (timeInSeconds * 1000.0).toLong()
                    val actualTimestamp = recordingStartTime + onsetTimeMs

                    onOnsetDetected?.invoke(actualTimestamp)

                    Log.d(TAG, "Onset detected: " +
                            "tarsosDSP=${String.format("%.3f", timeInSeconds)}s, " +
                            "onsetTimeMs=${onsetTimeMs}ms, " +
                            "recordingStart=$recordingStartTime, " +
                            "actualTimestamp=$actualTimestamp")
                }
            },
            sensitivity,
            threshold
        )

        while (isRecording) {
            val readResult = record.read(audioBuffer, 0, bufferSize)

            if (readResult > 0) {
                // Convert 16-bit samples to float range [-1.0, 1.0]
                for (i in 0 until readResult) {
                    floatBuffer[i] = audioBuffer[i] / 32768.0f
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
}