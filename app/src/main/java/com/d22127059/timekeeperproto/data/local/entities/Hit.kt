package com.d22127059.timekeeperproto.data.local.entities


import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey


@Entity(
    tableName = "hits",
    foreignKeys = [
        ForeignKey(
            entity = Session::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE // Delete hits when session is deleted
        )
    ],
    indices = [Index("sessionId")] // Index for faster queries by session
)
data class Hit(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val sessionId: Long,              // Foreign key to parent session
    val hitTimestamp: Long,           // When hit was detected
    val expectedBeatTimestamp: Long,  // When beat was expected
    val timingErrorMs: Double,        // Timing error
    val accuracyCategory: String      // "GREEN", "YELLOW", or "RED"
) {

    val isEarly: Boolean
        get() = timingErrorMs < 0


    val isLate: Boolean
        get() = timingErrorMs > 0


    val isPerfect: Boolean
        get() = accuracyCategory == "GREEN"
}