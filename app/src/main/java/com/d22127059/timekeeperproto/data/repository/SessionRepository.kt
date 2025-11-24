package com.d22127059.timekeeperproto.data.repository

import com.d22127059.timekeeperproto.data.local.dao.HitDao
import com.d22127059.timekeeperproto.data.local.dao.SessionDao
import com.d22127059.timekeeperproto.data.local.entities.Hit
import com.d22127059.timekeeperproto.data.local.entities.Session
import com.d22127059.timekeeperproto.domain.model.TimingResult
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing session and hit data.
 * Provides a clean API for the ViewModel layer to interact with data sources.
 *
 * In the prototype, this only uses local database. In production, could add
 * cloud sync, caching strategies, etc.
 */
class SessionRepository(
    private val sessionDao: SessionDao,
    private val hitDao: HitDao
) {

    // ========== Session Operations ==========

    /**
     * Saves a new session to the database.
     * @return The ID of the newly created session
     */
    suspend fun createSession(session: Session): Long {
        return sessionDao.insertSession(session)
    }

    /**
     * Updates an existing session (e.g., when session ends and final stats are calculated).
     */
    suspend fun updateSession(session: Session) {
        sessionDao.updateSession(session)
    }

    /**
     * Gets a specific session by ID.
     */
    suspend fun getSession(sessionId: Long): Session? {
        return sessionDao.getSessionById(sessionId)
    }

    /**
     * Gets all sessions as a Flow (reactive updates).
     */
    fun getAllSessions(): Flow<List<Session>> {
        return sessionDao.getAllSessions()
    }

    /**
     * Gets recent sessions (limit to N most recent).
     */
    fun getRecentSessions(limit: Int = 10): Flow<List<Session>> {
        return sessionDao.getRecentSessions(limit)
    }

    /**
     * Gets sessions within a date range.
     */
    fun getSessionsInDateRange(startTime: Long, endTime: Long): Flow<List<Session>> {
        return sessionDao.getSessionsInRange(startTime, endTime)
    }

    /**
     * Calculates overall user statistics across all sessions.
     */
    suspend fun getUserStatistics(): UserStatistics {
        val totalSessions = sessionDao.getTotalSessionCount()
        val avgAccuracy = sessionDao.getAverageAccuracy() ?: 0.0

        return UserStatistics(
            totalSessions = totalSessions,
            averageAccuracy = avgAccuracy
        )
    }

    /**
     * Deletes a session and all its hits (cascade delete).
     */
    suspend fun deleteSession(session: Session) {
        sessionDao.deleteSession(session)
    }

    // ========== Hit Operations ==========

    /**
     * Saves a single hit to the database.
     */
    suspend fun saveHit(hit: Hit): Long {
        return hitDao.insertHit(hit)
    }

    /**
     * Saves multiple hits at once (more efficient).
     * Useful for saving all hits at end of session.
     */
    suspend fun saveHits(hits: List<Hit>) {
        hitDao.insertHits(hits)
    }

    /**
     * Converts TimingResult to Hit entity and saves it.
     * @param sessionId The session this hit belongs to
     * @param result The timing analysis result
     */
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

    /**
     * Gets all hits for a specific session.
     */
    suspend fun getHitsForSession(sessionId: Long): List<Hit> {
        return hitDao.getHitsForSession(sessionId)
    }

    /**
     * Gets hits for a session as a Flow (reactive).
     */
    fun getHitsForSessionFlow(sessionId: Long): Flow<List<Hit>> {
        return hitDao.getHitsForSessionFlow(sessionId)
    }

    /**
     * Gets detailed statistics for a specific session including hit breakdown.
     */
    suspend fun getSessionDetails(sessionId: Long): SessionDetails? {
        val session = sessionDao.getSessionById(sessionId) ?: return null
        val hits = hitDao.getHitsForSession(sessionId)

        return SessionDetails(
            session = session,
            hits = hits
        )
    }
}

/**
 * Data class representing overall user statistics.
 */
data class UserStatistics(
    val totalSessions: Int,
    val averageAccuracy: Double
)

/**
 * Data class representing detailed information about a session.
 */
data class SessionDetails(
    val session: Session,
    val hits: List<Hit>
)