package com.d22127059.timekeeperproto.ui.screens.practice


import android.util.Log
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

    // Current session data
    private var sessionStartTime: Long = 0L
    private var currentSessionId: Long? = null
    private var timingAnalyzer: TimingAnalyzer? = null
    private val hitResults = mutableListOf<TimingResult>()
    private var timerJob: Job? = null

    // Session configuration
    private var bpm: Int = 120
    private var durationMs: Long = 300000 // 5 minutes default

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
     * Starts a new practice session.
     *
     * @param bpm Metronome tempo
     * @param durationMinutes Session duration in minutes
     */
    fun startSession(bpm: Int = 100, durationMinutes: Int = 5) {
        Log.d(TAG, "Starting session: BPM=$bpm, duration=$durationMinutes min")

        this.bpm = bpm
        this.durationMs = durationMinutes * 60 * 1000L
        hitResults.clear()

        // Start metronome and get the session start time
        sessionStartTime = metronomeEngine.start(bpm, viewModelScope)

        Log.d(TAG, "Session start time: $sessionStartTime")

        // Create the timing analyzer
        timingAnalyzer = TimingAnalyzer(bpm)

        // Set up onset detection callback
        onsetDetector.onOnsetDetected = { timestamp ->
            handleOnsetDetected(timestamp)
        }

        // Start detecting
        onsetDetector.startDetection(viewModelScope)

        _uiState.value = PracticeUiState.Active(
            currentCategory = AccuracyCategory.GREEN,
            hitCount = 0,
            elapsedTimeMs = 0L,
            bpm = bpm
        )

        // Start timer to update elapsed time
        startTimer()

        // Create session in database
        viewModelScope.launch {
            val session = Session(
                timestamp = sessionStartTime,
                durationMs = durationMs,
                actualDurationMs = 0L, // Will update when session ends
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
                delay(100) // Update every 100ms

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
     * Handles when an onset (hit) is detected.
     * Analyzes timing and updates UI.
     */
    private fun handleOnsetDetected(timestamp: Long) {
        val analyzer = timingAnalyzer ?: return

        val timeSinceStart = timestamp - sessionStartTime
        Log.d(TAG, "=== HIT DETECTED ===")
        Log.d(TAG, "Timestamp: $timestamp")
        Log.d(TAG, "Session Start: $sessionStartTime")
        Log.d(TAG, "Time since start: ${timeSinceStart}ms")

        // Analyze the hit
        val result = analyzer.analyzeHit(timestamp, sessionStartTime)
        hitResults.add(result)

        Log.d(TAG, "Result - category=${result.accuracyCategory}, error=${result.timingErrorMs.toInt()}ms")
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
        // Could implement resume functionality
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

    data class Active(
        val currentCategory: AccuracyCategory,
        val hitCount: Int,
        val elapsedTimeMs: Long,
        val bpm: Int
    ) : PracticeUiState()

    data class Completed(val stats: SessionStats) : PracticeUiState()
    data class Error(val message: String) : PracticeUiState()
}