package com.d22127059.timekeeperproto.domain.model

// Categorises a detected hit into one of three accuracy levels.
// Thresholds are derived from Repp & Su (2013) rhythmic perception research

enum class AccuracyCategory {
    GREEN,   // Perfect timing - circle in UI
    YELLOW,  // Acceptable timing - diamond in UI
    RED;     // Off-beat - triangle in UI

    companion object {
        // ±50ms: perceptual boundary for "on time" (Repp & Su, 2013)
        const val PERFECT_THRESHOLD_MS = 50.0
        // ±150ms: outer limit of acceptable deviation before errors become clearly noticeable to listeners
        const val ACCEPTABLE_EARLY_THRESHOLD_MS = -150.0
        const val ACCEPTABLE_LATE_THRESHOLD_MS = 150.0

        // Categorises a timing error into GREEN/YELLOW/RED.
        // timingErrorMs: Positive = late, negative = early
        fun fromTimingError(timingErrorMs: Double): AccuracyCategory {
            return when (timingErrorMs) {
                in -PERFECT_THRESHOLD_MS..PERFECT_THRESHOLD_MS -> GREEN
                in ACCEPTABLE_EARLY_THRESHOLD_MS..-PERFECT_THRESHOLD_MS -> YELLOW
                in PERFECT_THRESHOLD_MS..ACCEPTABLE_LATE_THRESHOLD_MS -> YELLOW
                else -> RED
            }
        }
    }
}