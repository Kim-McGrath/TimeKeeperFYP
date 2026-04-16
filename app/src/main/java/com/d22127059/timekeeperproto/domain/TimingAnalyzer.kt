package com.d22127059.timekeeperproto.domain

import android.util.Log
import com.d22127059.timekeeperproto.domain.model.AccuracyCategory
import com.d22127059.timekeeperproto.domain.model.TimingResult
import kotlin.math.abs
import kotlin.math.floor

// Analyses drum hit timing accuracy by comparing detected hit timestamps against expected metronome beat positions
// Latency compensation is handled upstream in MetronomeEngine and OnsetDetector before timestamps reach here

class TimingAnalyzer(private val bpm: Int) {
    companion object {
        private const val TAG = "TimingAnalyzer"
    }

    private val msBetweenBeats: Double = 60000.0 / bpm

    // Compare against both the previous and next expected beat, use whichever produces the smaller absolute error
    // Prevents midpoint misclassification where a hit arriving just past the halfway point between two beats would otherwise be incorrectly assigned to the wrong beat
    // Analyses a single hit and determines its timing accuracy
    // hitTimestamp: When the hit was detected (absolute time)
    // sessionStartTime: When the session started (absolute time)
    fun analyzeHit(hitTimestamp: Long, sessionStartTime: Long): TimingResult {
        val timeSinceStart = (hitTimestamp - sessionStartTime).toDouble()

        val prevBeatNumber = floor(timeSinceStart / msBetweenBeats).toLong()
        val nextBeatNumber = prevBeatNumber + 1

        val prevBeatTime = sessionStartTime + (prevBeatNumber * msBetweenBeats).toLong()
        val nextBeatTime = sessionStartTime + (nextBeatNumber * msBetweenBeats).toLong()

        val errorToPrev = abs(hitTimestamp - prevBeatTime).toDouble()
        val errorToNext = abs(hitTimestamp - nextBeatTime).toDouble()

        val (expectedBeatTimestamp, timingErrorMs) = if (errorToPrev <= errorToNext) {
            Pair(prevBeatTime, (hitTimestamp - prevBeatTime).toDouble())
        } else {
            Pair(nextBeatTime, (hitTimestamp - nextBeatTime).toDouble())
        }

        val category = AccuracyCategory.fromTimingError(timingErrorMs)
        return TimingResult(
            hitTimestamp = hitTimestamp,
            expectedBeatTimestamp = expectedBeatTimestamp,
            timingErrorMs = timingErrorMs,
            accuracyCategory = category
        )
    }

    // Calculates aggregate statistics from a list of timing results
    // Used for post-session reporting and identifying timing tendencies
    // Accuracy is defined as the percentage of hits that are GREEN or YELLOW
    // A mean error below -10ms indicates rushing; above +10ms indicates dragging
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

        // Accuracy: percentage of hits that are green or yellow
        val acceptableHits = greenCount + yellowCount
        val accuracyPercentage = (acceptableHits.toDouble() / results.size) * 100

        // Average timing error indicates rushing (negative) or dragging (positive)
        val avgTimingError = results.map { it.timingErrorMs }.average()

        Log.d(TAG, "Session Stats: total=${results.size}, green=$greenCount, yellow=$yellowCount, " +
                "red=$redCount, accuracy=${accuracyPercentage.toInt()}%, avgError=${avgTimingError.toInt()}ms")

        return SessionStats(
            totalHits = results.size,
            accuracyPercentage = accuracyPercentage,
            greenHits = greenCount,
            yellowHits = yellowCount,
            redHits = redCount,
            averageTimingError = avgTimingError,
            tendencyToRush = avgTimingError < -30.0,
            tendencyToDrag = avgTimingError > 30.0
        )
    }
}

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