package com.d22127059.timekeeperproto.data.local


import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.d22127059.timekeeperproto.data.local.dao.HitDao
import com.d22127059.timekeeperproto.data.local.dao.SessionDao
import com.d22127059.timekeeperproto.data.local.entities.Hit
import com.d22127059.timekeeperproto.data.local.entities.Session

/**
 * Room database for TimeKeeper application.
 * Stores sessions and individual hits with relationships.
 *
 * Version 1: Initial schema with Session and Hit entities
 */
@Database(
    entities = [Session::class, Hit::class],
    version = 1,
    exportSchema = false
)
abstract class TimeKeeperDatabase : RoomDatabase() {

    abstract fun sessionDao(): SessionDao
    abstract fun hitDao(): HitDao

    companion object {
        @Volatile
        private var INSTANCE: TimeKeeperDatabase? = null

        private const val DATABASE_NAME = "timekeeper_database"

        /**
         * Gets the singleton database instance.
         * Uses double-checked locking for thread safety.
         */
        fun getDatabase(context: Context): TimeKeeperDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TimeKeeperDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration() // For prototype, allow data loss on schema changes
                    .build()

                INSTANCE = instance
                instance
            }
        }

        /**
         * Clears the database instance (useful for testing).
         */
        fun clearInstance() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}