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
        // Empirically measured compensation for Android touch input processing delay
        // Touch events are stamped when the system processes them, not when the finger makes contact
        // Calibrated on the test device (Google Pixel 7a)
        private const val TAP_LATENCY_COMPENSATION_MS = 208L
    }

    private val _uiState = MutableStateFlow<PracticeUiState>(PracticeUiState.Idle)
    val uiState: StateFlow<PracticeUiState> = _uiState.asStateFlow()

    // Debug event tracking
    // These fields were used during development to visualise the audio pipeline in real time via DebugTimingVisualization
    // The debug UI is no longer exposed to users (the toggle button has been removed from PracticeScreen) but the
    // event tracking is retained here as a record of the testing infrastructure
    private val _debugEvents = MutableStateFlow<List<DebugEvent>>(emptyList())

    // sessionOriginTime is the fixed reference for all timing calculations.
    // It is set once when the session begins and never changes across pause/resume cycles.
    // This ensures hits recorded before and after a pause are all compared against the
    // same beat grid.
    private var sessionOriginTime: Long = 0L

    // metronomeStartTime tracks when the metronome was most recently started
    // After a resume, this differs from sessionOriginTime because the beat grid must be reconstructed from where it left off
    private var metronomeStartTime: Long = 0L

    private var pauseStartTime: Long = 0L
    private var totalPausedMs: Long = 0L
    private var currentSessionId: Long? = null
    private var timingAnalyzer: TimingAnalyzer? = null
    private val hitResults = mutableListOf<TimingResult>()
    private var timerJob: Job? = null
    private val actualBeatTimes = java.util.concurrent.CopyOnWriteArrayList<Long>()
    private var intervalMs: Long = 0

    private var currentSurfaceType: SurfaceType = SurfaceType.DRUM_KIT
    private var bpm: Int = 120
    private var durationMs: Long = 300000

    // Tap mode - replaces microphone with screen tap
    private var tapModeEnabled: Boolean = false

    private fun addDebugEvent(event: DebugEvent) {
        _debugEvents.value += event
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

    fun startCountdown(bpm: Int = 100, durationMinutes: Int = 5, tapMode: Boolean = false) {
        Log.d(TAG, "Starting countdown, BPM: $bpm, tapMode: $tapMode")

        this.bpm = bpm
        this.tapModeEnabled = tapMode
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

        // metronomeStartTime and sessionOriginTime are both set here at session start
        // They only diverge after a pause/resume cycle
        metronomeStartTime = metronomeEngine.start(bpm, viewModelScope)
        sessionOriginTime = metronomeStartTime

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

        if (!tapModeEnabled) {
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
            onsetDetector.startDetection(sessionOriginTime, viewModelScope)
        }

        _uiState.value = PracticeUiState.Active(
            currentCategory = null,
            hitCount = 0,
            elapsedTimeMs = 0L,
            bpm = bpm,
            durationMs = durationMs,
            sessionStartTime = sessionOriginTime,
            surfaceType = currentSurfaceType,
            isPaused = false,
            tapModeEnabled = tapModeEnabled
        )

        startTimer()

        viewModelScope.launch {
            val session = Session(
                timestamp = sessionOriginTime,
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

    // Handles a screen tap in tap mode. 200ms compensation constant is added to the touch event timestamp to
    // account for Android touch input processing delay, calibrated empirically in the same way as the microphone input constant
    fun onTapHit() {
        val currentState = _uiState.value
        if (currentState is PracticeUiState.Active && !currentState.isPaused && tapModeEnabled) {
            val timestamp = System.currentTimeMillis() + TAP_LATENCY_COMPENSATION_MS
            addDebugEvent(DebugEvent(
                timestamp = timestamp,
                type = EventType.HIT_DETECTED,
                label = "TAP HIT",
                details = "Screen tap at: $timestamp"
            ))
            handleOnsetDetected(timestamp)
        }
    }

    // Polls every 100ms to update elapsed session time in the UI state
    // Elapsed time calculated from sessionOriginTime minus accumulated pause duration, ensuring paused periods are excluded from the session length
    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(100)
                val currentState = _uiState.value
                if (currentState is PracticeUiState.Active && !currentState.isPaused) {
                    // Elapsed time is measured from the fixed session origin, minus however long has been spent paused
                    val elapsed = System.currentTimeMillis() - sessionOriginTime - totalPausedMs
                    _uiState.value = currentState.copy(elapsedTimeMs = elapsed)
                    if (durationMs != Long.MAX_VALUE && elapsed >= durationMs) {
                        endSession()
                    }
                }
            }
        }
    }

    // Returns true if the given timestamp falls within 30ms of a known metronome beat
    // Used to suppress false positive detections caused by the metronome click bleeding through the device speaker into the microphone
    private fun isMetronomeClick(timestamp: Long): Boolean {
        if (actualBeatTimes.isEmpty()) return false
        val filterWindowMs = 30L
        return actualBeatTimes.any { beatTime ->
            kotlin.math.abs(timestamp - beatTime) < filterWindowMs
        }
    }

    private fun handleOnsetDetected(timestamp: Long) {
        val analyzer = timingAnalyzer ?: return
        val currentState = _uiState.value
        if (currentState is PracticeUiState.Active && currentState.isPaused) return

        // All hits are analysed against the fixed sessionOriginTime so that pausing does not corrupt the beat grid reference
        val result = analyzer.analyzeHit(timestamp, sessionOriginTime)
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
        pauseStartTime = System.currentTimeMillis()
        if (!tapModeEnabled) onsetDetector.stopDetection()
        metronomeEngine.stop()
        _uiState.value = currentState.copy(isPaused = true)
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun resumeSession() {
        val currentState = _uiState.value as? PracticeUiState.Active ?: return
        if (!currentState.isPaused) return

        val pauseDuration = System.currentTimeMillis() - pauseStartTime
        totalPausedMs += pauseDuration

        // Re-initialise audio hardware
        metronomeEngine.initialize()

        // Restart the metronome. metronomeStartTime updates to the new start,
        // but sessionOriginTime stays fixed so the beat grid reference is preserved
        // The TimingAnalyzer still uses sessionOriginTime for all hit calculations
        metronomeStartTime = metronomeEngine.start(bpm, viewModelScope)

        if (!tapModeEnabled) {
            onsetDetector.initialize()
            onsetDetector.setSurfaceType(currentSurfaceType)
            onsetDetector.startDetection(metronomeStartTime, viewModelScope)
        }

        _uiState.value = currentState.copy(isPaused = false)
    }


     // Resets the ViewModel back to Idle so the user can start a new session
     // Called when navigating away from the Completed screen.
    fun resetSession() {
        hitResults.clear()
        actualBeatTimes.clear()
        _debugEvents.value = emptyList()
        timingAnalyzer = null
        currentSessionId = null
        totalPausedMs = 0L
        _uiState.value = PracticeUiState.Idle
    }

    fun endSession() {
        timerJob?.cancel()
        if (!tapModeEnabled) onsetDetector.stopDetection()
        metronomeEngine.stop()

        val analyzer = timingAnalyzer ?: run {
            _uiState.value = PracticeUiState.Idle
            return
        }

        // If no hits were recorded, delete the placeholder session and return to idle
        if (hitResults.isEmpty()) {
            currentSessionId?.let { sessionId ->
                viewModelScope.launch {
                    repository.getSession(sessionId)?.let { repository.deleteSession(it) }
                    _uiState.value = PracticeUiState.Idle
                }
            } ?: run { _uiState.value = PracticeUiState.Idle }
            return
        }

        val stats = analyzer.calculateSessionStats(hitResults)

        currentSessionId?.let { sessionId ->
            viewModelScope.launch {
                val session = repository.getSession(sessionId)
                session?.let {
                    val updatedSession = it.copy(
                        actualDurationMs = System.currentTimeMillis() - sessionOriginTime - totalPausedMs,
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
        val isPaused: Boolean = false,
        val tapModeEnabled: Boolean = false
    ) : PracticeUiState()
    data class Completed(val stats: SessionStats) : PracticeUiState()
    data class Error(val message: String) : PracticeUiState()
}