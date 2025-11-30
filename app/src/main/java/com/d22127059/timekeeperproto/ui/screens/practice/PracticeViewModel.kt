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

/**
 * ViewModel for the practice session screen.
 * Manages the state of an active practice session and coordinates between
 * audio processing, timing analysis, and UI updates.
 */
class PracticeViewModel(
    private val onsetDetector: OnsetDetector,
    private val metronomeEngine: MetronomeEngine,
    private val repository: SessionRepository
) : ViewModel() {

    companion object {
        private const val TAG = "PracticeViewModel"
    }

    // UI State
    private val _uiState = MutableStateFlow<PracticeUiState>(PracticeUiState.Idle)
    val uiState: StateFlow<PracticeUiState> = _uiState.asStateFlow()

    // Debug events
    private val _debugEvents = MutableStateFlow<List<DebugEvent>>(emptyList())
    val debugEvents: StateFlow<List<DebugEvent>> = _debugEvents.asStateFlow()

    // Current session data
    private var sessionStartTime: Long = 0L
    private var currentSessionId: Long? = null
    private var timingAnalyzer: TimingAnalyzer? = null
    private val hitResults = mutableListOf<TimingResult>()
    private var timerJob: Job? = null
    private val actualBeatTimes = mutableListOf<Long>()
    private var intervalMs: Long = 0

    // Session configuration
    private var bpm: Int = 120
    private var durationMs: Long = 300000 // 5 minutes default

    private fun addDebugEvent(event: DebugEvent) {
        _debugEvents.value = _debugEvents.value + event
        // Keep only last 100 events to prevent memory issues
        if (_debugEvents.value.size > 100) {
            _debugEvents.value = _debugEvents.value.takeLast(100)
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun initializeDetector() {
        Log.d(TAG, "Initializing detector and metronome")

        val detectorInit = onsetDetector.initialize()
        val metronomeInit = metronomeEngine.initialize()

        if (detectorInit && metronomeInit) {
            _uiState.value = PracticeUiState.Ready
            Log.d(TAG, "Detector and metronome initialized successfully")
        } else {
            val errorMsg = when {
                !detectorInit && !metronomeInit -> "Failed to initialize audio and metronome"
                !detectorInit -> "Failed to initialize audio"
                else -> "Failed to initialize metronome"
            }
            _uiState.value = PracticeUiState.Error(errorMsg)
            Log.e(TAG, errorMsg)
        }
    }

    /**
     * Starts countdown before session begins.
     * Metronome plays during countdown but hits aren't registered yet.
     */
    fun startCountdown(bpm: Int = 100, durationMinutes: Int = 5) {
        Log.d(TAG, "Starting countdown before session")

        this.bpm = bpm
        this.durationMs = durationMinutes * 60 * 1000L
        this.intervalMs = (60000.0 / bpm).toLong()

        hitResults.clear()
        actualBeatTimes.clear()
        _debugEvents.value = emptyList()

        // Set up metronome click callback early
        metronomeEngine.onClickPlayed = { clickTime, beatNumber ->
            actualBeatTimes.add(clickTime)
            addDebugEvent(DebugEvent(
                timestamp = clickTime,
                type = EventType.METRONOME_CLICK,
                label = "METRONOME CLICK $beatNumber",
                details = "Actual time: $clickTime"
            ))
        }

        // ✅ Start metronome - it plays beat 0 immediately
        sessionStartTime = metronomeEngine.start(bpm, viewModelScope)

        addDebugEvent(DebugEvent(
            timestamp = sessionStartTime,
            type = EventType.ANALYSIS_RESULT,
            label = "COUNTDOWN START",
            details = "Metronome started, BPM: $bpm, Beat 0 at: $sessionStartTime"
        ))

        Log.d(TAG, "Metronome started, beat 0 at $sessionStartTime")

        // Run countdown synchronized with metronome beats
        viewModelScope.launch {
            // ✅ Beat 0 just played, wait for beat 1
            delay(intervalMs)
            _uiState.value = PracticeUiState.Countdown(3)

            // Beat 1 → show "3"
            delay(intervalMs)
            _uiState.value = PracticeUiState.Countdown(2)

            // Beat 2 → show "2"
            delay(intervalMs)
            _uiState.value = PracticeUiState.Countdown(1)

            // Beat 3 → show "1"
            delay(intervalMs)
            _uiState.value = PracticeUiState.Countdown(0)

            // Beat 4 → show "GO!"
            delay(500)

            startSessionAfterCountdown(bpm, durationMinutes)
        }
    }

    /**
     * Starts the actual practice session after countdown completes.
     * Metronome is already playing at this point.
     */
    private fun startSessionAfterCountdown(bpm: Int, durationMinutes: Int) {
        Log.d(TAG, "Starting active session after countdown")

        // Create timing analyzer
        timingAnalyzer = TimingAnalyzer(bpm)

        // Set up onset detection callback with filtering
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

        // ✅ Start detecting hits - ONLY ONCE with sessionStartTime
        onsetDetector.startDetection(sessionStartTime, viewModelScope)

        addDebugEvent(DebugEvent(
            timestamp = System.currentTimeMillis(),
            type = EventType.ANALYSIS_RESULT,
            label = "SESSION ACTIVE",
            details = "Hit detection enabled"
        ))

        // Update UI to Active state
        _uiState.value = PracticeUiState.Active(
            currentCategory = AccuracyCategory.GREEN,
            hitCount = 0,
            elapsedTimeMs = 0L,
            bpm = bpm,
            sessionStartTime = sessionStartTime
        )

        // Start timer to update elapsed time
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

    /**
     * Starts a coroutine to update the timer display every 100ms
     */
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

    /**
     * Helper to find closest beat distance
     */
    private fun findClosestBeat(timestamp: Long): Long {
        if (actualBeatTimes.isEmpty()) return Long.MAX_VALUE
        return actualBeatTimes.minByOrNull { kotlin.math.abs(it - timestamp) }
            ?.let { kotlin.math.abs(it - timestamp) } ?: Long.MAX_VALUE
    }

    /**
     * Checks if a detected onset is likely a metronome click.
     * Uses ACTUAL click times, not expected times.
     */
    private fun isMetronomeClick(timestamp: Long): Boolean {
        if (actualBeatTimes.isEmpty()) return false

        // ✅ PROTOTYPE: Reduced to 15ms to allow perfect hits through
        val threshold = 15L  // 15ms - only filters metronome clicks from speakers, allows perfect drum hits

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

    /**
     * Handles when an onset (hit) is detected.
     * Analyzes timing and updates UI.
     */
    private fun handleOnsetDetected(timestamp: Long) {
        val analyzer = timingAnalyzer ?: return

        val timeSinceStart = timestamp - sessionStartTime

        // Find closest actual beat
        val closestBeat = actualBeatTimes.minByOrNull { kotlin.math.abs(it - timestamp) }
        val closestBeatDiff = closestBeat?.let { kotlin.math.abs(it - timestamp) } ?: Long.MAX_VALUE
        val closestBeatNumber = actualBeatTimes.indexOf(closestBeat)

        Log.d(TAG, "=== HIT DETECTED ===")
        Log.d(TAG, "Timestamp: $timestamp")
        Log.d(TAG, "Session Start: $sessionStartTime")
        Log.d(TAG, "Time since start: ${timeSinceStart}ms")
        Log.d(TAG, "Closest actual beat: $closestBeat (beat #$closestBeatNumber)")
        Log.d(TAG, "Distance from closest beat: ${closestBeatDiff}ms")

        // Analyze the hit
        val result = analyzer.analyzeHit(timestamp, sessionStartTime)
        hitResults.add(result)

        val beatNumber = findNearestBeatNumber(timestamp)

        // Debug: Log analysis result
        addDebugEvent(DebugEvent(
            timestamp = timestamp,
            type = EventType.ANALYSIS_RESULT,
            label = "ANALYSIS: ${result.accuracyCategory}",
            details = "Error: ${result.timingErrorMs.toInt()}ms, Beat: $beatNumber, ClosestActual: ${closestBeatDiff}ms"
        ))

        Log.d(TAG, "Analyzer result - category=${result.accuracyCategory}, error=${result.timingErrorMs.toInt()}ms, beat=$beatNumber")
        Log.d(TAG, "Expected beat timestamp: ${result.expectedBeatTimestamp}")
        Log.d(TAG, "==================")

        // Save hit to database
        currentSessionId?.let { sessionId ->
            viewModelScope.launch {
                repository.saveTimingResult(sessionId, result)
            }
        }

        // Update UI with current category and hit count
        val currentState = _uiState.value
        if (currentState is PracticeUiState.Active) {
            _uiState.value = currentState.copy(
                currentCategory = result.accuracyCategory,
                hitCount = hitResults.size
            )
        }

    }

    /**
     * Helper to find beat number
     */
    private fun findNearestBeatNumber(timestamp: Long): Int {
        val timeSinceStart = (timestamp - sessionStartTime).toDouble()
        val msBetweenBeats = 60000.0 / bpm
        return kotlin.math.round(timeSinceStart / msBetweenBeats).toInt()
    }

    /**
     * Ends the current session and generates final report.
     */
    fun endSession() {
        Log.d(TAG, "Ending session")

        timerJob?.cancel()
        onsetDetector.stopDetection()
        metronomeEngine.stop()

        val analyzer = timingAnalyzer ?: return
        val stats = analyzer.calculateSessionStats(hitResults)

        Log.d(TAG, "Session stats: $stats")

        // Update session in database with final stats
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

    /**
     * Pauses the current session.
     */
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

/**
 * Sealed class representing the different states of the practice screen.
 */
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