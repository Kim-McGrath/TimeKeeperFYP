package com.d22127059.timekeeperproto.data.repository

import com.d22127059.timekeeperproto.data.local.dao.HitDao
import com.d22127059.timekeeperproto.data.local.dao.SessionDao
import com.d22127059.timekeeperproto.data.local.entities.Hit
import com.d22127059.timekeeperproto.data.local.entities.Session
import com.d22127059.timekeeperproto.domain.model.TimingResult
import kotlinx.coroutines.flow.Flow


// Repository providing a single access point for session and hit data
// Wraps the local Room DAOs. Firestore sync is handled separately in AuthViewModel, triggered from MainActivity after a session completes
class SessionRepository(
    private val sessionDao: SessionDao,
    private val hitDao: HitDao
) {

    // Saves a new session to the database. Return The ID of the newly created session
    suspend fun createSession(session: Session): Long {
        return sessionDao.insertSession(session)
    }

    // Updates an existing session
    suspend fun updateSession(session: Session) {
        sessionDao.updateSession(session)
    }

    // Gets a specific session by ID
    suspend fun getSession(sessionId: Long): Session? {
        return sessionDao.getSessionById(sessionId)
    }

    // Gets all sessions as a flow
    fun getAllSessions(): Flow<List<Session>> {
        return sessionDao.getAllSessions()
    }

    // Aggregates total session count and average accuracy across all sessions
    // Used by the home screen stats chips and the account screen
    suspend fun getUserStatistics(): UserStatistics {
        val totalSessions = sessionDao.getTotalSessionCount()
        val avgAccuracy = sessionDao.getAverageAccuracy() ?: 0.0

        return UserStatistics(
            totalSessions = totalSessions,
            averageAccuracy = avgAccuracy
        )
    }

    // Deletes a session and all its hits (cascade delete)
    suspend fun deleteSession(session: Session) {
        sessionDao.deleteSession(session)
    }

    // Removes any incomplete session records left by app crashes.
    // Sessions with zero hits were created at session start but never finalised.
    suspend fun deleteZeroHitSessions() {
        sessionDao.deleteZeroHitSessions()
    }

    // Converts a TimingResult from the domain layer into a Hit entity and persists it
    // Called after each detected hit during an active session
    // sessionId: The session this hit belongs to
    // result: The timing analysis result
    suspend fun saveTimingResult(sessionId: Long, result: TimingResult): Long {
        val hit = Hit(
            sessionId = sessionId,
            hitTimestamp = result.hitTimestamp,
            expectedBeatTimestamp = result.expectedBeatTimestamp,
            timingErrorMs = result.timingErrorMs,
            accuracyCategory = result.accuracyCategory.name
        )
        return hitDao.insertHit(hit)
    }

    // Gets all hits for a specific session
    suspend fun getHitsForSession(sessionId: Long): List<Hit> {
        return hitDao.getHitsForSession(sessionId)
    }
}

// Data class representing overall user statistics
data class UserStatistics(
    val totalSessions: Int,
    val averageAccuracy: Double
)
