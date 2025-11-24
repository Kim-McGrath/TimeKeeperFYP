package com.d22127059.timekeeperproto.domain.model


/**
 * Represents the accuracy category for a drum hit based on timing error.
 * Uses asymmetric thresholds based on rhythmic perception research (Repp & Su, 2013).
 *
 * GREEN (Perfect): Within ±25ms of expected beat
 * YELLOW (Acceptable): Early: -70ms to -25ms, Late: +25ms to +100ms
 * RED (Off-time): Beyond acceptable thresholds
 */
enum class AccuracyCategory {
    GREEN,   // Perfect timing - Circle shape in UI
    YELLOW,  // Acceptable timing - Triangle shape in UI
    RED;     // Significantly off-time - Triangle shape in UI

    companion object {
        // Threshold constants based on research
        const val PERFECT_THRESHOLD_MS = 25.0
        const val ACCEPTABLE_EARLY_THRESHOLD_MS = -70.0
        const val ACCEPTABLE_LATE_THRESHOLD_MS = 100.0

        fun fromTimingError(timingErrorMs: Double): AccuracyCategory {
            return when (// Perfect timing: within +-25ms
                timingErrorMs) {
                in -PERFECT_THRESHOLD_MS..PERFECT_THRESHOLD_MS -> GREEN
                // Early hits: -70ms to -25ms
                // Late hits: +25ms to +100ms
                in ACCEPTABLE_EARLY_THRESHOLD_MS..-PERFECT_THRESHOLD_MS -> YELLOW
                in PERFECT_THRESHOLD_MS..ACCEPTABLE_LATE_THRESHOLD_MS -> YELLOW

                // Everything else is offtime
                else -> RED
            }
        }
    }
}
