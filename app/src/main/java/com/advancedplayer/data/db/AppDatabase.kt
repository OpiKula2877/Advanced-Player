package com.advancedplayer.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Track::class, Playlist::class, PlaylistEntry::class, PlayEvent::class, Bookmark::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tracks(): TrackDao
    abstract fun playlists(): PlaylistDao
    abstract fun stats(): StatsDao
    abstract fun bookmarks(): BookmarkDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "advanced_player.db")
                .addMigrations(MIGRATION_1_2)
                .build()

        /**
         * 1.1 counts every listen of 5 s or more as a play. Re-count the history recorded by
         * 1.0 with the same rule so old listens no longer show as 0 plays.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE play_events SET counted = 1 WHERE listenedMs >= 5000")
                db.execSQL(
                    "UPDATE tracks SET " +
                        "playCount = (SELECT COUNT(*) FROM play_events e WHERE e.trackId = tracks.id AND e.counted = 1), " +
                        "lastPlayed = MAX(lastPlayed, COALESCE((SELECT MAX(timestamp) FROM play_events e WHERE e.trackId = tracks.id), 0))",
                )
            }
        }
    }
}
