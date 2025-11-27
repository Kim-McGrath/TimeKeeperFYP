package com.d22127059.timekeeperproto.domain


import android.util.Log
import com.d22127059.timekeeperproto.domain.model.AccuracyCategory
import com.d22127059.timekeeperproto.domain.model.TimingResult
import kotlin.math.abs
import kotlin.math.round

/**
 * Core business logic for analyzing drum hit timing accuracy.
 * Compares detected hits against expected metronome beats and categorizes accuracy.
 *
 * Based on research showing listeners judge early deviations more harshly than late ones
 * (Repp & Su, 2013), asymmetric thresholds are applied.
 */
class TimingAnalyzer(
    private val bpm: Int,
    private val systemLatencyMs: Long = 0L
) {
    companion object {
        private const val TAG = "TimingAnalyzer"
    }

    // Calculate milliseconds between beats based on BPM
    private val msBetweenBeats: Double = 60000.0 / bpm

    fun analyzeHit(hitTimestamp: Long, sessionStartTime: Long): TimingResult {
        // Compensate for system latency
        val compensatedHitTime = hitTimestamp - systemLatencyMs

        // Calculate which beat this hit is closest to
        // Note: First click is at sessionStartTime + msBetweenBeats (beat 0)
        val timeSinceStart = (compensatedHitTime - sessionStartTime).toDouble()

        // Use round() to find the NEAREST beat
        val nearestBeatNumber = round(timeSinceStart / msBetweenBeats).toLong()

        // Calculate the expected timestamp for this beat
        // Beat 0 is at sessionStartTime + msBetweenBeats
        val expectedBeatTimestamp = sessionStartTime + (nearestBeatNumber * msBetweenBeats).toLong()

        // Calculate timing error (positive = late, negative = early)
        val timingErrorMs = (compensatedHitTime - expectedBeatTimestamp).toDouble()

        // Categorize based on thresholds
        val category = AccuracyCategory.fromTimingError(timingErrorMs)

        Log.d(TAG, "Hit Analysis: timeSinceStart=${timeSinceStart.toLong()}ms, " +
                "nearestBeat=$nearestBeatNumber, " +
                "expectedBeatTime=${expectedBeatTimestamp}ms, " +
                "error=${timingErrorMs.toInt()}ms, " +
                "category=$category")

        return TimingResult(
            hitTimestamp = compensatedHitTime,
            expectedBeatTimestamp = expectedBeatTimestamp,
            timingErrorMs = timingErrorMs,
            accuracyCategory = category
        )
    }

    /**
     * Generates expected beat timestamps for the entire session.
     * Useful for metronome visualization and pre-calculating beat positions.
     *
     * @param sessionStartTime Session start timestamp
     * @param durationMs Session duration in milliseconds
     * @return List of expected beat timestamps
     */
    fun generateExpectedBeats(sessionStartTime: Long, durationMs: Long): List<Long> {
        val beats = mutableListOf<Long>()
        var currentBeatTime = sessionStartTime
        val sessionEndTime = sessionStartTime + durationMs

        while (currentBeatTime <= sessionEndTime) {
            beats.add(currentBeatTime)
            currentBeatTime += msBetweenBeats.toLong()
        }

        return beats
    }

    /**
     * Calculates aggregate statistics from a list of timing results.
     * Used for post-session reporting.
     *
     * @param results List of all hits in the session
     * @return Map containing accuracy percentage, hit counts by category, and timing patterns
     */
    fun calculateSessionStats(results: List<TimingResult>): SessionStats {
        if (results.isEmpty()) {
            return SessionStats(
                totalHits = 0,
                accuracyPercentage = 0.0,
                greenHits = 0,
                yellowHits = 0,
                redHits = 0,
                averageTimingError = 0.0,
                tendencyToRush = false,
                tendencyToDrag = false
            )
        }

        val greenCount = results.count { it.accuracyCategory == AccuracyCategory.GREEN }
        val yellowCount = results.count { it.accuracyCategory == AccuracyCategory.YELLOW }
        val redCount = results.count { it.accuracyCategory == AccuracyCategory.RED }

        // Accuracy percentage: green + yellow are "acceptable"
        val acceptableHits = greenCount + yellowCount
        val accuracyPercentage = (acceptableHits.toDouble() / results.size) * 100

        // Calculate average timing error to detect rushing/dragging
        val avgTimingError = results.map { it.timingErrorMs }.average()

        Log.d(TAG, "Session Stats: total=$results.size, green=$greenCount, yellow=$yellowCount, " +
                "red=$redCount, accuracy=${accuracyPercentage.toInt()}%, avgError=${avgTimingError.toInt()}ms")

        return SessionStats(
            totalHits = results.size,
            accuracyPercentage = accuracyPercentage,
            greenHits = greenCount,
            yellowHits = yellowCount,
            redHits = redCount,
            averageTimingError = avgTimingError,
            tendencyToRush = avgTimingError < -10.0, // Consistently early
            tendencyToDrag = avgTimingError > 10.0   // Consistently late
        )
    }
}

/**
 * Data class representing aggregated session statistics.
 */
data class SessionStats(
    val totalHits: Int,
    val accuracyPercentage: Double,
    val greenHits: Int,
    val yellowHits: Int,
    val redHits: Int,
    val averageTimingError: Double,
    val tendencyToRush: Boolean,
    val tendencyToDrag: Boolean
)