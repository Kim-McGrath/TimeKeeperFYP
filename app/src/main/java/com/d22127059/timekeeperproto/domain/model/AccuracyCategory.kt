package com.d22127059.timekeeperproto.domain.model



enum class AccuracyCategory {
    GREEN,   // Perfect timing - Circle shape in UI
    YELLOW,  // Acceptable timing - Diamond shape in UI
    RED;     // Significantly off-time - Triangle shape in UI

    companion object {
        // ✅ PROTOTYPE: More forgiving thresholds for testing
        const val PERFECT_THRESHOLD_MS = 50.0           // ±50ms = GREEN (was ±25ms)
        const val ACCEPTABLE_EARLY_THRESHOLD_MS = -150.0  // Up to -150ms = YELLOW (was -70ms)
        const val ACCEPTABLE_LATE_THRESHOLD_MS = 150.0    // Up to +150ms = YELLOW (was +100ms)

        fun fromTimingError(timingErrorMs: Double): AccuracyCategory {
            return when (timingErrorMs) {
                // Perfect timing: within ±50ms
                in -PERFECT_THRESHOLD_MS..PERFECT_THRESHOLD_MS -> GREEN

                // Early hits: -150ms to -50ms
                // Late hits: +50ms to +150ms
                in ACCEPTABLE_EARLY_THRESHOLD_MS..-PERFECT_THRESHOLD_MS -> YELLOW
                in PERFECT_THRESHOLD_MS..ACCEPTABLE_LATE_THRESHOLD_MS -> YELLOW

                // Everything else is significantly off-time
                else -> RED
            }
        }
    }
}