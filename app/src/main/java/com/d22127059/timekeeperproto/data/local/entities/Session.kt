package com.d22127059.timekeeperproto.data.local.entities


import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class Session(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // Session configuration
    val timestamp: Long,           // When session started
    val durationMs: Long,          // Intended duration ms
    val actualDurationMs: Long,    // Actual duration
    val bpm: Int,                  // Metronome tempo
    val surfaceType: String,       // "DRUM_KIT", "PRACTICE_PAD", "TABLE"

    // Session results
    val totalHits: Int,
    val greenHits: Int,            // Perfect timing hits
    val yellowHits: Int,           // Acceptable timing hits
    val redHits: Int,              // Off-time hits
    val accuracyPercentage: Double,
    val averageTimingError: Double, // Average timing error in ms (+ = late, - = early)

    // Patterns
    val tendencyToRush: Boolean,   // True if consistently early
    val tendencyToDrag: Boolean    // True if consistently late
) {

    val accuracyRate: Double
        get() = accuracyPercentage / 100.0


    val timingTendencyDescription: String
        get() = when {
            tendencyToRush -> "Tendency to rush (play early)"
            tendencyToDrag -> "Tendency to drag (play late)"
            else -> "Consistent timing"
        }
}