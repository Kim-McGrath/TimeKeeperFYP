package com.d22127059.timekeeperproto.domain.model

// Represents the result of analysing a single hit's timing against the nearest beat
// timingErrorMs is signed: positive = late, negative = early
data class TimingResult(
    val hitTimestamp: Long,
    val expectedBeatTimestamp: Long,
    val timingErrorMs: Double,
    val accuracyCategory: AccuracyCategory
) {
    // Whether this hit was early (negative timing error)
    val isEarly: Boolean
        get() = timingErrorMs < 0

    // Whether this hit was late (positive timing error)
    val isLate: Boolean
        get() = timingErrorMs > 0

    // Whether this hit was perfectly on time (within perfect threshold)
    val isPerfect: Boolean
        get() = accuracyCategory == AccuracyCategory.GREEN
}