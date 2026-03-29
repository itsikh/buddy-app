package com.template.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.template.app.data.models.*

@Database(
    entities = [
        ChildProfile::class,
        Message::class,
        MemoryFact::class,
        VocabularyItem::class,
        SessionLog::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun childProfileDao(): ChildProfileDao
    abstract fun messageDao(): MessageDao
    abstract fun memoryFactDao(): MemoryFactDao
    abstract fun vocabularyItemDao(): VocabularyItemDao
    abstract fun sessionLogDao(): SessionLogDao
}
