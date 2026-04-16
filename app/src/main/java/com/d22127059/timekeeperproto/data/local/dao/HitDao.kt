package com.d22127059.timekeeperproto.data.local.dao


import androidx.room.*
import com.d22127059.timekeeperproto.data.local.entities.Hit
import kotlinx.coroutines.flow.Flow

// Data Access Object for the Hit entity
// Provides queries for inserting and retrieving individual hit records
// Hit data is used by SessionDetailScreen to render the full timing analysis
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

}