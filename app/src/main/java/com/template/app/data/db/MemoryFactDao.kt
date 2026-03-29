package com.template.app.data.db

import androidx.room.*
import com.template.app.data.models.MemoryCategory
import com.template.app.data.models.MemoryFact
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryFactDao {

    @Query("SELECT * FROM memory_facts WHERE profileId = :profileId ORDER BY category, key")
    fun observeAll(profileId: String): Flow<List<MemoryFact>>

    @Query("SELECT * FROM memory_facts WHERE profileId = :profileId ORDER BY category, key")
    suspend fun getAll(profileId: String): List<MemoryFact>

    @Query("SELECT * FROM memory_facts WHERE profileId = :profileId AND category = :category")
    suspend fun getByCategory(profileId: String, category: MemoryCategory): List<MemoryFact>

    /** Upsert by (profileId, category, key) to avoid duplicates. */
    @Query("""
        INSERT OR REPLACE INTO memory_facts (id, profileId, category, key, value, updatedAt)
        VALUES (
            COALESCE((SELECT id FROM memory_facts WHERE profileId = :profileId AND category = :category AND key = :key), :newId),
            :profileId, :category, :key, :value, :updatedAt
        )
    """)
    suspend fun upsertFact(
        newId: String,
        profileId: String,
        category: MemoryCategory,
        key: String,
        value: String,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(facts: List<MemoryFact>)

    @Delete
    suspend fun delete(fact: MemoryFact)

    @Query("DELETE FROM memory_facts WHERE profileId = :profileId")
    suspend fun deleteAllForProfile(profileId: String)
}
