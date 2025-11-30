package com.d22127059.timekeeperproto.domain

import android.util.Log
import com.d22127059.timekeeperproto.domain.model.AccuracyCategory
import com.d22127059.timekeeperproto.domain.model.TimingResult
import kotlin.math.abs
import kotlin.math.round

// Analyses drum hit timing accuracy by comparing detected hits against expected metronome beats
// The systemLatencyMs parameter exists for potential future use with differing device latency
// but is currently unused, as latency is already properly handled in MetronomeEngine and OnsetDetector

class TimingAnalyzer(
    private val bpm: Int,
    private val systemLatencyMs: Long = 0L
) {
    companion object {
        private const val TAG = "TimingAnalyzer"
    }

    private val msBetweenBeats: Double = 60000.0 / bpm

    // Analyses a single hit and determines its timing accuracy
    // hitTimestamp: When the hit was detected (absolute time)
    // sessionStartTime: When the session started (absolute time)
    fun analyzeHit(hitTimestamp: Long, sessionStartTime: Long): TimingResult {
        val timeSinceStart = (hitTimestamp - sessionStartTime).toDouble()

        // Find which beat this hit is closest to
        val nearestBeatNumber = round(timeSinceStart / msBetweenBeats).toLong()
        val expectedBeatTimestamp = sessionStartTime + (nearestBeatNumber * msBetweenBeats).toLong()

        // Calculate timing error (positive = late, negative = early)
        val timingErrorMs = (hitTimestamp - expectedBeatTimestamp).toDouble()

        val category = AccuracyCategory.fromTimingError(timingErrorMs)

        Log.d(TAG, "Hit Analysis: timeSinceStart=${timeSinceStart.toLong()}ms, " +
                "nearestBeat=$nearestBeatNumber, " +
                "expectedBeatTime=${expectedBeatTimestamp}ms, " +
                "error=${timingErrorMs.toInt()}ms, " +
                "category=$category")

        return TimingResult(
            hitTimestamp = hitTimestamp,
            expectedBeatTimestamp = expectedBeatTimestamp,
            timingErrorMs = timingErrorMs,
            accuracyCategory = category
        )
    }

    // Generates a list of expected beat timestamps for the entire session
    // for metronome visualisation and precalculating beat positions
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

    // Calculates aggregate statistics from a list of timing results
    // Used for post-session reporting and identifying timing tendencies
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
            tendencyToRush = avgTimingError < -10.0,
            tendencyToDrag = avgTimingError > 10.0
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