package com.d22127059.timekeeperproto.domain.model

// Categorises drum hit timing accuracy into three levels
// Thresholds are set wider than production values for prototype testing:
// - GREEN: Within ±50ms (perfect timing)
// - YELLOW: Within ±150ms (acceptable timing)
// - RED: Beyond ±150ms (off-beat)
enum class AccuracyCategory {
    GREEN,   // Perfect timing - displayed as circle in UI
    YELLOW,  // Acceptable timing - displayed as diamond in UI
    RED;     // Offbeat - displayed as triangle in UI

    companion object {
        // Prototype thresholds - more forgiving for demonstration purposes
        const val PERFECT_THRESHOLD_MS = 50.0           // ±50ms = GREEN
        const val ACCEPTABLE_EARLY_THRESHOLD_MS = -150.0  // Up to -150ms = YELLOW
        const val ACCEPTABLE_LATE_THRESHOLD_MS = 150.0    // Up to +150ms = YELLOW

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