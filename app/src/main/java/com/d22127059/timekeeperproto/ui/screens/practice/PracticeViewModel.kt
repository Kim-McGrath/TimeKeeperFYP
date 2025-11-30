package com.d22127059.timekeeperproto.ui.screens.practice

import android.Manifest
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.d22127059.timekeeperproto.audio.MetronomeEngine
import com.d22127059.timekeeperproto.audio.OnsetDetector
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

// Manages the practice session lifecycle and coordinates audio processing, timing analysis, and UI updates
// Session flow:
// 1. Idle -> initialize audio
// 2. Ready -> start countdown
// 3. Countdown (beats 0-4) -> metronome plays but hits aren't registered
// 4. Active (beat 5+) -> hits are detected and analysed
// 5. Completed -> display final statistics
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
    private var currentSessionId: Long? = null
    private var timingAnalyzer: TimingAnalyzer? = null
    private val hitResults = mutableListOf<TimingResult>()
    private var timerJob: Job? = null
    private val actualBeatTimes = mutableListOf<Long>()
    private var intervalMs: Long = 0

    private var bpm: Int = 120
    private var durationMs: Long = 300000 // 5 minutes default

    private fun addDebugEvent(event: DebugEvent) {
        _debugEvents.value = _debugEvents.value + event
        // Keep only last 100 events to prevent memory bloat
        if (_debugEvents.value.size > 100) {
            _debugEvents.value = _debugEvents.value.takeLast(100)
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun initializeDetector() {
        Log.d(TAG, "Initialising detector and metronome")

        val detectorInit = onsetDetector.initialize()
        val metronomeInit = metronomeEngine.initialize()

        if (detectorInit && metronomeInit) {
            _uiState.value = PracticeUiState.Ready
            Log.d(TAG, "Detector and metronome initialised successfully")
        } else {
            val errorMsg = when {
                !detectorInit && !metronomeInit -> "Failed to initialise audio and metronome"
                !detectorInit -> "Failed to initialise audio"
                else -> "Failed to initialise metronome"
            }
            _uiState.value = PracticeUiState.Error(errorMsg)
            Log.e(TAG, errorMsg)
        }
    }

    // Starts the countdown sequence before the active session begins
    // Metronome plays during countdown (beats 0-4) but hits aren't registered yet
    fun startCountdown(bpm: Int = 100, durationMinutes: Int = 5) {
        Log.d(TAG, "Starting countdown before session")

        this.bpm = bpm
        this.durationMs = durationMinutes * 60 * 1000L
        this.intervalMs = (60000.0 / bpm).toLong()

        hitResults.clear()
        actualBeatTimes.clear()
        _debugEvents.value = emptyList()

        // Set up callback to record actual metronome click times
        metronomeEngine.onClickPlayed = { clickTime, beatNumber ->
            actualBeatTimes.add(clickTime)
            addDebugEvent(DebugEvent(
                timestamp = clickTime,
                type = EventType.METRONOME_CLICK,
                label = "METRONOME CLICK $beatNumber",
                details = "Actual time: $clickTime"
            ))
        }

        // Start metronome - it plays beat 0 immediately and returns when that beat will be heard
        sessionStartTime = metronomeEngine.start(bpm, viewModelScope)

        addDebugEvent(DebugEvent(
            timestamp = sessionStartTime,
            type = EventType.ANALYSIS_RESULT,
            label = "COUNTDOWN START",
            details = "Metronome started, BPM: $bpm, Beat 0 at: $sessionStartTime"
        ))

        Log.d(TAG, "Metronome started, beat 0 at $sessionStartTime")

        // Countdown displays: 3, 2, 1, GO!
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

    // Transitions to active session after countdown completes
    // Hit detection begins at this point (beat 5 onwards)
    private fun startSessionAfterCountdown(bpm: Int, durationMinutes: Int) {
        Log.d(TAG, "Starting active session after countdown")

        timingAnalyzer = TimingAnalyzer(bpm)

        // Set up onset detection with filtering to remove metronome clicks
        onsetDetector.onOnsetDetected = { timestamp ->
            val filtered = isMetronomeClick(timestamp)
            val closestBeatDiff = findClosestBeat(timestamp)

            Log.d(TAG, "Onset at $timestamp, closest beat: ${closestBeatDiff}ms away, filtered: $filtered")

            if (filtered) {
                addDebugEvent(DebugEvent(
                    timestamp = timestamp,
                    type = EventType.HIT_FILTERED,
                    label = "FILTERED",
                    details = "Within ${closestBeatDiff}ms of beat"
                ))
                Log.d(TAG, "Filtered onset at $timestamp (${closestBeatDiff}ms from beat)")
            } else {
                addDebugEvent(DebugEvent(
                    timestamp = timestamp,
                    type = EventType.HIT_DETECTED,
                    label = "HIT DETECTED",
                    details = "Raw timestamp: $timestamp"
                ))
                handleOnsetDetected(timestamp)
            }
        }

        // Start detecting hits
        onsetDetector.startDetection(sessionStartTime, viewModelScope)

        addDebugEvent(DebugEvent(
            timestamp = System.currentTimeMillis(),
            type = EventType.ANALYSIS_RESULT,
            label = "SESSION ACTIVE",
            details = "Hit detection enabled"
        ))

        _uiState.value = PracticeUiState.Active(
            currentCategory = AccuracyCategory.GREEN,
            hitCount = 0,
            elapsedTimeMs = 0L,
            bpm = bpm,
            sessionStartTime = sessionStartTime
        )

        startTimer()

        // Create session in database
        viewModelScope.launch {
            val session = Session(
                timestamp = sessionStartTime,
                durationMs = durationMs,
                actualDurationMs = 0L,
                bpm = bpm,
                surfaceType = "DRUM_KIT",
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
            Log.d(TAG, "Session created in DB with ID: $currentSessionId")
        }
    }

    // Updates elapsed time display every 100ms
    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(100)

                val currentState = _uiState.value
                if (currentState is PracticeUiState.Active) {
                    val elapsed = System.currentTimeMillis() - sessionStartTime
                    _uiState.value = currentState.copy(
                        elapsedTimeMs = elapsed
                    )
                }
            }
        }
    }

    private fun findClosestBeat(timestamp: Long): Long {
        if (actualBeatTimes.isEmpty()) return Long.MAX_VALUE
        return actualBeatTimes.minByOrNull { kotlin.math.abs(it - timestamp) }
            ?.let { kotlin.math.abs(it - timestamp) } ?: Long.MAX_VALUE
    }

//    Filters out detected onsets that are likely metronome clicks from the speaker
//    Not currently functional but for future implementation and work
    private fun isMetronomeClick(timestamp: Long): Boolean {
        if (actualBeatTimes.isEmpty()) return false

        // 15ms threshold filters metronome clicks whilst allowing perfect drum hits through
        val threshold = 15L

        val isFiltered = actualBeatTimes.any { actualBeat ->
            val difference = kotlin.math.abs(timestamp - actualBeat)
            difference < threshold
        }

        if (isFiltered) {
            val closest = actualBeatTimes.minByOrNull { kotlin.math.abs(it - timestamp) }
            val diff = closest?.let { kotlin.math.abs(it - timestamp) } ?: 0
            Log.d(TAG, "FILTERING hit at $timestamp (${diff}ms from nearest beat)")
        }

        return isFiltered
    }

    // Analyses a detected hit, updates UI, and saves to database
    private fun handleOnsetDetected(timestamp: Long) {
        val analyzer = timingAnalyzer ?: return

        val timeSinceStart = timestamp - sessionStartTime

        val closestBeat = actualBeatTimes.minByOrNull { kotlin.math.abs(it - timestamp) }
        val closestBeatDiff = closestBeat?.let { kotlin.math.abs(it - timestamp) } ?: Long.MAX_VALUE
        val closestBeatNumber = actualBeatTimes.indexOf(closestBeat)

        Log.d(TAG, "=== HIT DETECTED ===")
        Log.d(TAG, "Timestamp: $timestamp")
        Log.d(TAG, "Session Start: $sessionStartTime")
        Log.d(TAG, "Time since start: ${timeSinceStart}ms")
        Log.d(TAG, "Closest actual beat: $closestBeat (beat #$closestBeatNumber)")
        Log.d(TAG, "Distance from closest beat: ${closestBeatDiff}ms")

        val result = analyzer.analyzeHit(timestamp, sessionStartTime)
        hitResults.add(result)

        val beatNumber = findNearestBeatNumber(timestamp)

        addDebugEvent(DebugEvent(
            timestamp = timestamp,
            type = EventType.ANALYSIS_RESULT,
            label = "ANALYSIS: ${result.accuracyCategory}",
            details = "Error: ${result.timingErrorMs.toInt()}ms, Beat: $beatNumber, ClosestActual: ${closestBeatDiff}ms"
        ))

        Log.d(TAG, "Analyser result - category=${result.accuracyCategory}, error=${result.timingErrorMs.toInt()}ms, beat=$beatNumber")
        Log.d(TAG, "Expected beat timestamp: ${result.expectedBeatTimestamp}")
        Log.d(TAG, "==================")

        // Save to database
        currentSessionId?.let { sessionId ->
            viewModelScope.launch {
                repository.saveTimingResult(sessionId, result)
            }
        }

        // Update UI
        val currentState = _uiState.value
        if (currentState is PracticeUiState.Active) {
            _uiState.value = currentState.copy(
                currentCategory = result.accuracyCategory,
                hitCount = hitResults.size
            )
        }
    }

    private fun findNearestBeatNumber(timestamp: Long): Int {
        val timeSinceStart = (timestamp - sessionStartTime).toDouble()
        val msBetweenBeats = 60000.0 / bpm
        return kotlin.math.round(timeSinceStart / msBetweenBeats).toInt()
    }

    fun endSession() {
        Log.d(TAG, "Ending session")

        timerJob?.cancel()
        onsetDetector.stopDetection()
        metronomeEngine.stop()

        val analyzer = timingAnalyzer ?: return
        val stats = analyzer.calculateSessionStats(hitResults)

        Log.d(TAG, "Session stats: $stats")

        // Update database with final statistics
        currentSessionId?.let { sessionId ->
            viewModelScope.launch {
                val session = repository.getSession(sessionId)
                session?.let {
                    val updatedSession = it.copy(
                        actualDurationMs = System.currentTimeMillis() - sessionStartTime,
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
                    Log.d(TAG, "Session updated in DB")
                }

                _uiState.value = PracticeUiState.Completed(stats)
            }
        }
    }

    fun pauseSession() {
        Log.d(TAG, "Pausing session")
        timerJob?.cancel()
        onsetDetector.stopDetection()
        metronomeEngine.stop()
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        onsetDetector.release()
        metronomeEngine.release()
        Log.d(TAG, "ViewModel cleared")
    }
}

// UI states for the practice screen
sealed class PracticeUiState {
    object Idle : PracticeUiState()
    object Ready : PracticeUiState()
    data class Countdown(val countdownValue: Int) : PracticeUiState()
    data class Active(
        val currentCategory: AccuracyCategory,
        val hitCount: Int,
        val elapsedTimeMs: Long,
        val bpm: Int,
        val sessionStartTime: Long = 0L
    ) : PracticeUiState()
    data class Completed(val stats: SessionStats) : PracticeUiState()
    data class Error(val message: String) : PracticeUiState()
}