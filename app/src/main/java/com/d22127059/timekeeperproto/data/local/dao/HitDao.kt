package com.d22127059.timekeeperproto.data.local.dao


import androidx.room.*
import com.d22127059.timekeeperproto.data.local.entities.Hit
import kotlinx.coroutines.flow.Flow

// Data Access Object for hit entity
// Provides methods for CRUD operations on individual hits
// Much of this is not yet implemented, will be for session reports later on
@Dao
interface HitDao {

    // Inserts a new hit into the database. return The ID of the newly inserted hit
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHit(hit: Hit): Long


    // Inserts multiple hits at once (more efficient for batch operations)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHits(hits: List<Hit>)

    // Deletes a hit from the database
    @Delete
    suspend fun deleteHit(hit: Hit)

    // Gets all hits for a specific session
    @Query("SELECT * FROM hits WHERE sessionId = :sessionId ORDER BY hitTimestamp ASC")
    suspend fun getHitsForSession(sessionId: Long): List<Hit>

    // Gets all hits for a specific session as flow
    @Query("SELECT * FROM hits WHERE sessionId = :sessionId ORDER BY hitTimestamp ASC")
    fun getHitsForSessionFlow(sessionId: Long): Flow<List<Hit>>

    // Gets hits by accuracy category for a session
    @Query("SELECT * FROM hits WHERE sessionId = :sessionId AND accuracyCategory = :category ORDER BY hitTimestamp ASC")
    suspend fun getHitsByCategory(sessionId: Long, category: String): List<Hit>

    // Counts hits by category for a session
    @Query("SELECT COUNT(*) FROM hits WHERE sessionId = :sessionId AND accuracyCategory = :category")
    suspend fun countHitsByCategory(sessionId: Long, category: String): Int

    // Gets the average timing error for a session
    @Query("SELECT AVG(timingErrorMs) FROM hits WHERE sessionId = :sessionId")
    suspend fun getAverageTimingError(sessionId: Long): Double?

    // Deletes all hits for a specific session
    @Query("DELETE FROM hits WHERE sessionId = :sessionId")
    suspend fun deleteHitsForSession(sessionId: Long)

    // Deletes all hits (for testing or data reset)
    @Query("DELETE FROM hits")
    suspend fun deleteAllHits()
}