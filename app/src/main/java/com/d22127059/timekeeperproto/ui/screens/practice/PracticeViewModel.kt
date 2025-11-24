package com.d22127059.timekeeperproto.ui.screens.practice


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.d22127059.timekeeperproto.audio.OnsetDetector
import com.d22127059.timekeeperproto.data.local.entities.Session
import com.d22127059.timekeeperproto.data.repository.SessionRepository
import com.d22127059.timekeeperproto.domain.SessionStats
import com.d22127059.timekeeperproto.domain.TimingAnalyzer
import com.d22127059.timekeeperproto.domain.model.AccuracyCategory
import com.d22127059.timekeeperproto.domain.model.TimingResult
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
    private val repository: SessionRepository
) : ViewModel() {

    // UI State
    private val _uiState = MutableStateFlow<PracticeUiState>(PracticeUiState.Idle)
    val uiState: StateFlow<PracticeUiState> = _uiState.asStateFlow()

    // Current session data
    private var sessionStartTime: Long = 0L
    private var currentSessionId: Long? = null
    private var timingAnalyzer: TimingAnalyzer? = null
    private val hitResults = mutableListOf<TimingResult>()

    // Session configuration
    private var bpm: Int = 120
    private var durationMs: Long = 300000 // 5 minutes default

    fun initializeDetector() {
        if (onsetDetector.initialize()) {
            _uiState.value = PracticeUiState.Ready
        } else {
            _uiState.value = PracticeUiState.Error("Failed to initialize audio")
        }
    }

    /**
     * Starts a new practice session.
     *
     * @param bpm Metronome tempo
     * @param durationMinutes Session duration in minutes
     */
    fun startSession(bpm: Int = 120, durationMinutes: Int = 5) {
        this.bpm = bpm
        this.durationMs = durationMinutes * 60 * 1000L

        sessionStartTime = System.currentTimeMillis()
        timingAnalyzer = TimingAnalyzer(bpm)
        hitResults.clear()

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
        }
    }

    /**
     * Handles when an onset (hit) is detected.
     * Analyzes timing and updates UI.
     */
    private fun handleOnsetDetected(timestamp: Long) {
        val analyzer = timingAnalyzer ?: return

        // Analyze the hit
        val result = analyzer.analyzeHit(timestamp, sessionStartTime)
        hitResults.add(result)

        // Save hit to database
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
                hitCount = hitResults.size,
                elapsedTimeMs = System.currentTimeMillis() - sessionStartTime
            )
        }
    }

    /**
     * Ends the current session and generates final report.
     */
    fun endSession() {
        onsetDetector.stopDetection()

        val analyzer = timingAnalyzer ?: return
        val stats = analyzer.calculateSessionStats(hitResults)

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
                }

                _uiState.value = PracticeUiState.Completed(stats)
            }
        }
    }

    /**
     * Pauses the current session.
     */
    fun pauseSession() {
        onsetDetector.stopDetection()
        // Could implement resume functionality
    }

    override fun onCleared() {
        super.onCleared()
        onsetDetector.release()
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