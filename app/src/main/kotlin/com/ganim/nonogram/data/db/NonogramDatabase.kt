package com.ganim.nonogram.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

/** Stores [ProgressState] as its name, so a reordered enum cannot reinterpret old rows. */
class Converters {
    @TypeConverter
    fun toProgressState(value: String): ProgressState = ProgressState.valueOf(value)

    @TypeConverter
    fun fromProgressState(value: ProgressState): String = value.name
}

/**
 * The app's only database (build plan 6.5).
 *
 * `exportSchema` is on deliberately. The schema JSON is committed, which is what makes a
 * migration reviewable and what lets Room verify that a migration actually produces the
 * schema it claims. The plan's acceptance criterion "progress survives app kill, device
 * restart, and **app update**" is really a statement about migrations, and they cannot
 * be got right without the exported schema to diff against.
 *
 * There is only version 1 so far. When a column is added, bump [VERSION], write a
 * `Migration`, and never use `fallbackToDestructiveMigration` - it silently deletes
 * every streak a player has.
 */
@Database(
    entities = [
        PuzzleProgressEntity::class,
        DailyRecordEntity::class,
        UserStatsEntity::class,
    ],
    version = NonogramDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class NonogramDatabase : RoomDatabase() {

    abstract fun puzzleProgressDao(): PuzzleProgressDao
    abstract fun dailyRecordDao(): DailyRecordDao
    abstract fun userStatsDao(): UserStatsDao

    companion object {
        const val VERSION = 1
        const val NAME = "nonogram.db"

        @Volatile
        private var instance: NonogramDatabase? = null

        fun get(context: Context): NonogramDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): NonogramDatabase =
            Room.databaseBuilder(context, NonogramDatabase::class.java, NAME).build()
    }
}
