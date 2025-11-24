package com.d22127059.timekeeperproto.domain.model


/**
 * Represents the result of analyzing a single drum hit's timing.
 *
 * @property hitTimestamp When the hit was detected (milliseconds since session start)
 * @property expectedBeatTimestamp When the beat was expected (milliseconds since session start)
 * @property timingErrorMs The difference between hit and expected beat (positive = late, negative = early)
 * @property accuracyCategory The categorized accuracy (GREEN/YELLOW/RED)
 */
data class TimingResult(
    val hitTimestamp: Long,
    val expectedBeatTimestamp: Long,
    val timingErrorMs: Double,
    val accuracyCategory: AccuracyCategory
) {
    /**
     * Whether this hit was early (negative timing error)
     */
    val isEarly: Boolean
        get() = timingErrorMs < 0

    /**
     * Whether this hit was late (positive timing error)
     */
    val isLate: Boolean
        get() = timingErrorMs > 0

    /**
     * Whether this hit was perfectly on time (within perfect threshold)
     */
    val isPerfect: Boolean
        get() = accuracyCategory == AccuracyCategory.GREEN
}
