package com.itsikh.buddy.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.itsikh.buddy.data.models.*

@Database(
    entities = [
        ChildProfile::class,
        Message::class,
        MemoryFact::class,
        VocabularyItem::class,
        SessionLog::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun childProfileDao(): ChildProfileDao
    abstract fun messageDao(): MessageDao
    abstract fun memoryFactDao(): MemoryFactDao
    abstract fun vocabularyItemDao(): VocabularyItemDao
    abstract fun sessionLogDao(): SessionLogDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE child_profiles ADD COLUMN gender TEXT NOT NULL DEFAULT 'BOY'")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE child_profiles ADD COLUMN namePhonetic TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE child_profiles ADD COLUMN coins INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
