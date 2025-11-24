package com.d22127059.timekeeperproto.domain


import com.d22127059.timekeeperproto.domain.model.AccuracyCategory
import com.d22127059.timekeeperproto.domain.model.TimingResult
import kotlin.math.abs


class TimingAnalyzer(
    private val bpm: Int,
    private val systemLatencyMs: Long = 0L
) {
    // Calculate milliseconds between beats based on BPM
    private val msBetweenBeats: Double = 60000.0 / bpm

    fun analyzeHit(hitTimestamp: Long, sessionStartTime: Long): TimingResult {
        // Compensate for system latency
        val compensatedHitTime = hitTimestamp - systemLatencyMs

        // Calculate which beat this hit is closest to
        val timeSinceStart = compensatedHitTime - sessionStartTime
        val nearestBeatNumber = (timeSinceStart / msBetweenBeats).toLong()
        val expectedBeatTimestamp = sessionStartTime + (nearestBeatNumber * msBetweenBeats).toLong()

        // Calculate timing error
        val timingErrorMs = (compensatedHitTime - expectedBeatTimestamp).toDouble()

        // Categorize based on thresholds
        val category = AccuracyCategory.fromTimingError(timingErrorMs)

        return TimingResult(
            hitTimestamp = compensatedHitTime,
            expectedBeatTimestamp = expectedBeatTimestamp,
            timingErrorMs = timingErrorMs,
            accuracyCategory = category
        )
    }


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
