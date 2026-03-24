package com.d22127059.timekeeperproto.ui.screens.practice

import android.Manifest
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.d22127059.timekeeperproto.audio.MetronomeEngine
import com.d22127059.timekeeperproto.audio.OnsetDetector
import com.d22127059.timekeeperproto.audio.SurfaceType
import com.d22127059.timekeeperproto.data.local.entities.Session
import com.d22127059.timekeeperproto.data.repository.SessionRepository
import com.d22127059.timekeeperproto.domain.SessionStats
import com.d22127059.timekeeperproto.domain.TimingAnalyzer
import com.d22127059.timekeeperproto.domain.model.AccuracyCategory
import com.d22127059.timekeeperproto.domain.model.TimingResult
import com.d22127059.timekeeperproto.ui.components.DebugEvent
import com.d22127059.timekeeperproto.ui.components.EventType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PracticeViewModel(
    private val onsetDetector: OnsetDetector,
    private val metronomeEngine: MetronomeEngine,
    private val repository: SessionRepository
) : ViewModel() {

    companion object {
        private const val TAG = "PracticeViewModel"
    }

    private val _uiState = MutableStateFlow<PracticeUiState>(PracticeUiState.Idle)
    val uiState: StateFlow<PracticeUiState> = _uiState.asStateFlow()

    private val _debugEvents = MutableStateFlow<List<DebugEvent>>(emptyList())
    val debugEvents: StateFlow<List<DebugEvent>> = _debugEvents.asStateFlow()

    private var sessionStartTime: Long = 0L
    private var pauseStartTime: Long = 0L
    private var totalPausedMs: Long = 0L
    private var currentSessionId: Long? = null
    private var timingAnalyzer: TimingAnalyzer? = null
    private val hitResults = mutableListOf<TimingResult>()
    private var timerJob: Job? = null
    private val actualBeatTimes = mutableListOf<Long>()
    private var intervalMs: Long = 0

    private var currentSurfaceType: SurfaceType = SurfaceType.DRUM_KIT
    private var bpm: Int = 120
    private var durationMs: Long = 300000

    private fun addDebugEvent(event: DebugEvent) {
        _debugEvents.value = _debugEvents.value + event
        if (_debugEvents.value.size > 100) {
            _debugEvents.value = _debugEvents.value.takeLast(100)
        }
    }

    fun updateSurfaceType(surfaceType: SurfaceType) {
        currentSurfaceType = surfaceType
        onsetDetector.setSurfaceType(surfaceType)
        Log.d(TAG, "Surface type updated to: $surfaceType")
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun initializeDetector() {
        Log.d(TAG, "Initialising detector and metronome")
        val detectorInit = onsetDetector.initialize()
        val metronomeInit = metronomeEngine.initialize()

        if (detectorInit && metronomeInit) {
            _uiState.value = PracticeUiState.Ready
            Log.d(TAG, "Initialised successfully, latency: ${metronomeEngine.getMeasuredLatency()}ms")
        } else {
            val errorMsg = when {
                !detectorInit && !metronomeInit -> "Failed to initialise audio and metronome"
                !detectorInit -> "Failed to initialise audio"
                else -> "Failed to initialise metronome"
            }
            _uiState.value = PracticeUiState.Error(errorMsg)
        }
    }

    // durationMinutes: 0 means unlimited (user ends manually)
    fun startCountdown(bpm: Int = 100, durationMinutes: Int = 5) {
        Log.d(TAG, "Starting countdown, BPM: $bpm, duration: ${if (durationMinutes == 0) "unlimited" else "${durationMinutes}min"}")

        this.bpm = bpm
        this.durationMs = if (durationMinutes == 0) Long.MAX_VALUE else durationMinutes * 60 * 1000L
        this.intervalMs = (60000.0 / bpm).toLong()
        this.totalPausedMs = 0L

        hitResults.clear()
        actualBeatTimes.clear()
        _debugEvents.value = emptyList()

        metronomeEngine.onClickPlayed = { clickTime, beatNumber ->
            actualBeatTimes.add(clickTime)
            addDebugEvent(DebugEvent(
                timestamp = clickTime,
                type = EventType.METRONOME_CLICK,
                label = "METRONOME CLICK $beatNumber",
                details = "Actual time: $clickTime"
            ))
        }

        sessionStartTime = metronomeEngine.start(bpm, viewModelScope)

        viewModelScope.launch {
            delay(intervalMs)
            _uiState.value = PracticeUiState.Countdown(3)
            delay(intervalMs)
            _uiState.value = PracticeUiState.Countdown(2)
            delay(intervalMs)
            _uiState.value = PracticeUiState.Countdown(1)
            delay(intervalMs)
            _uiState.value = PracticeUiState.Countdown(0)
            delay(500)
            startSessionAfterCountdown(bpm, durationMinutes)
        }
    }

    private fun startSessionAfterCountdown(bpm: Int, durationMinutes: Int) {
        timingAnalyzer = TimingAnalyzer(bpm)

        onsetDetector.onOnsetDetected = { timestamp ->
            val filtered = isMetronomeClick(timestamp)
            if (!filtered) {
                addDebugEvent(DebugEvent(
                    timestamp = timestamp,
                    type = EventType.HIT_DETECTED,
                    label = "HIT DETECTED",
                    details = "Raw timestamp: $timestamp"
                ))
                handleOnsetDetected(timestamp)
            }
        }

        onsetDetector.startDetection(sessionStartTime, viewModelScope)

        _uiState.value = PracticeUiState.Active(
            currentCategory = null,
            hitCount = 0,
            elapsedTimeMs = 0L,
            bpm = bpm,
            durationMs = durationMs,
            sessionStartTime = sessionStartTime,
            surfaceType = currentSurfaceType,
            isPaused = false
        )

        startTimer()

        viewModelScope.launch {
            val session = Session(
                timestamp = sessionStartTime,
                durationMs = durationMs,
                actualDurationMs = 0L,
                bpm = bpm,
                surfaceType = currentSurfaceType.name,
                totalHits = 0,
                greenHits = 0,
                yellowHits = 0,
                redHits = 0,
                accuracyPercentage = 0.0,
                averageTimingError = 0.0,
                tendencyToRush = false,
                tendencyToDrag = false
            )
            currentSessionId = repository.createSession(session)
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(100)
                val currentState = _uiState.value
                if (currentState is PracticeUiState.Active && !currentState.isPaused) {
                    val elapsed = System.currentTimeMillis() - sessionStartTime - totalPausedMs
                    _uiState.value = currentState.copy(elapsedTimeMs = elapsed)

                    // Auto-end if duration is set and reached
                    if (durationMs != Long.MAX_VALUE && elapsed >= durationMs) {
                        endSession()
                    }
                }
            }
        }
    }

    private fun isMetronomeClick(timestamp: Long): Boolean {
        if (actualBeatTimes.isEmpty()) return false
        val filterWindowMs = 30L
        return actualBeatTimes.any { beatTime ->
            kotlin.math.abs(timestamp - beatTime) < filterWindowMs
        }
    }

    private fun handleOnsetDetected(timestamp: Long) {
        val analyzer = timingAnalyzer ?: return

        // Don't register hits while paused
        val currentState = _uiState.value
        if (currentState is PracticeUiState.Active && currentState.isPaused) return

        val result = analyzer.analyzeHit(timestamp, sessionStartTime)
        hitResults.add(result)

        currentSessionId?.let { sessionId ->
            viewModelScope.launch {
                repository.saveTimingResult(sessionId, result)
            }
        }

        if (currentState is PracticeUiState.Active) {
            _uiState.value = currentState.copy(
                currentCategory = result.accuracyCategory,
                hitCount = hitResults.size
            )
        }
    }

    fun pauseSession() {
        val currentState = _uiState.value as? PracticeUiState.Active ?: return
        if (currentState.isPaused) return

        Log.d(TAG, "Pausing session")
        pauseStartTime = System.currentTimeMillis()
        onsetDetector.stopDetection()
        metronomeEngine.stop()

        _uiState.value = currentState.copy(isPaused = true)
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun resumeSession() {
        val currentState = _uiState.value as? PracticeUiState.Active ?: return
        if (!currentState.isPaused) return

        Log.d(TAG, "Resuming session")

        // Accumulate paused time so elapsed timer stays accurate
        totalPausedMs += System.currentTimeMillis() - pauseStartTime

        // Restart metronome and detection
        metronomeEngine.initialize()
        sessionStartTime = metronomeEngine.start(bpm, viewModelScope)

        onsetDetector.initialize()
        onsetDetector.setSurfaceType(currentSurfaceType)
        onsetDetector.startDetection(sessionStartTime, viewModelScope)

        _uiState.value = currentState.copy(isPaused = false)
    }

    fun endSession() {
        Log.d(TAG, "Ending session")
        timerJob?.cancel()
        onsetDetector.stopDetection()
        metronomeEngine.stop()

        val analyzer = timingAnalyzer ?: return
        val stats = analyzer.calculateSessionStats(hitResults)

        currentSessionId?.let { sessionId ->
            viewModelScope.launch {
                val session = repository.getSession(sessionId)
                session?.let {
                    val updatedSession = it.copy(
                        actualDurationMs = System.currentTimeMillis() - sessionStartTime - totalPausedMs,
                        totalHits = stats.totalHits,
                        greenHits = stats.greenHits,
                        yellowHits = stats.yellowHits,
                        redHits = stats.redHits,
                        accuracyPercentage = stats.accuracyPercentage,
                        averageTimingError = stats.averageTimingError,
                        tendencyToRush = stats.tendencyToRush,
                        tendencyToDrag = stats.tendencyToDrag
                    )
                    repository.updateSession(updatedSession)
                }
                _uiState.value = PracticeUiState.Completed(stats)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        onsetDetector.release()
        metronomeEngine.release()
    }
}

sealed class PracticeUiState {
    object Idle : PracticeUiState()
    object Ready : PracticeUiState()
    data class Countdown(val countdownValue: Int) : PracticeUiState()
    data class Active(
        val currentCategory: AccuracyCategory?,
        val hitCount: Int,
        val elapsedTimeMs: Long,
        val bpm: Int,
        val durationMs: Long,
        val sessionStartTime: Long = 0L,
        val surfaceType: SurfaceType = SurfaceType.DRUM_KIT,
        val isPaused: Boolean = false
    ) : PracticeUiState()
    data class Completed(val stats: SessionStats) : PracticeUiState()
    data class Error(val message: String) : PracticeUiState()
}