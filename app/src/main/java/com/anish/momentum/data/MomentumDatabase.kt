package com.anish.momentum.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [HabitEntity::class, CompletionEntity::class],
    version = 2,
    exportSchema = false
)
abstract class MomentumDatabase : RoomDatabase() {

    abstract fun habitDao(): HabitDao

    companion object {
        /** Adds day-of-week scheduling to habits created before it existed. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE habits ADD COLUMN schedule_mask INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        @Volatile
        private var instance: MomentumDatabase? = null

        fun get(context: Context): MomentumDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MomentumDatabase::class.java,
                    "momentum.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build().also { instance = it }
            }
    }
}
