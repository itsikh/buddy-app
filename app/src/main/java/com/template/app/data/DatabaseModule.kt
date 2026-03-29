package com.template.app.data

import android.content.Context
import androidx.room.Room
import com.template.app.data.db.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "buddy_db")
            .fallbackToDestructiveMigration() // Safe during early development; add migrations before release
            .build()

    @Provides fun provideChildProfileDao(db: AppDatabase): ChildProfileDao = db.childProfileDao()
    @Provides fun provideMessageDao(db: AppDatabase): MessageDao = db.messageDao()
    @Provides fun provideMemoryFactDao(db: AppDatabase): MemoryFactDao = db.memoryFactDao()
    @Provides fun provideVocabularyItemDao(db: AppDatabase): VocabularyItemDao = db.vocabularyItemDao()
    @Provides fun provideSessionLogDao(db: AppDatabase): SessionLogDao = db.sessionLogDao()
}
