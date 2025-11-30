package com.d22127059.timekeeperproto.data.local.dao

import androidx.room.*
import com.d22127059.timekeeperproto.data.local.entities.Session
import kotlinx.coroutines.flow.Flow

// Data Access Object for session entity
// Provides methods for CRUD operations on sessions
// Much of this is not yet implemented, will be for session reports later on
@Dao
interface SessionDao {

    // Inserts a new session into the database
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: Session): Long

    // Updates an existing session
    @Update
    suspend fun updateSession(session: Session)

    //  Deletes a session from the database
    @Delete
    suspend fun deleteSession(session: Session)

    // Gets a session by its ID
    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: Long): Session?

    // Gets all sessions ordered by timestamp (most recent first)
    // Returns a flow for reactive updates
    @Query("SELECT * FROM sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<Session>>

    // Gets sessions within a date range
    @Query("SELECT * FROM sessions WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    fun getSessionsInRange(startTime: Long, endTime: Long): Flow<List<Session>>

    // Gets the most recent N sessions
    @Query("SELECT * FROM sessions ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentSessions(limit: Int): Flow<List<Session>>

    // Calculates the average accuracy across all sessions
    @Query("SELECT AVG(accuracyPercentage) FROM sessions")
    suspend fun getAverageAccuracy(): Double?

    // Gets total number of sessions
    @Query("SELECT COUNT(*) FROM sessions")
    suspend fun getTotalSessionCount(): Int

    // Gets sessions with accuracy above a threshold
    @Query("SELECT * FROM sessions WHERE accuracyPercentage >= :minAccuracy ORDER BY timestamp DESC")
    fun getSessionsAboveAccuracy(minAccuracy: Double): Flow<List<Session>>

    // Deletes all sessions (for testing or data reset)
    @Query("DELETE FROM sessions")
    suspend fun deleteAllSessions()
}