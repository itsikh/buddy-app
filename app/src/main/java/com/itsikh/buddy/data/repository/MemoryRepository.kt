package com.itsikh.buddy.data.repository

import com.itsikh.buddy.data.db.MemoryFactDao
import com.itsikh.buddy.data.models.MemoryCategory
import com.itsikh.buddy.data.models.MemoryFact
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryRepository @Inject constructor(
    private val dao: MemoryFactDao
) {
    fun observeAll(profileId: String): Flow<List<MemoryFact>> = dao.observeAll(profileId)

    suspend fun getAll(profileId: String): List<MemoryFact> = dao.getAll(profileId)

    /** Upserts a fact — if (profileId, category, key) already exists it's updated. */
    suspend fun saveFact(profileId: String, category: MemoryCategory, key: String, value: String) {
        dao.upsertFact(
            newId     = UUID.randomUUID().toString(),
            profileId = profileId,
            category  = category,
            key       = key,
            value     = value
        )
    }

    suspend fun saveAll(facts: List<MemoryFact>) = dao.insertAll(facts)

    suspend fun deleteFact(fact: MemoryFact) = dao.delete(fact)

    suspend fun deleteAllForProfile(profileId: String) = dao.deleteAllForProfile(profileId)

    /**
     * Returns a compact string representation of all known facts, suitable for injection
     * into the AI system prompt. Grouped by category.
     */
    suspend fun toPromptString(profileId: String): String {
        val facts = dao.getAll(profileId)
        if (facts.isEmpty()) return ""
        return buildString {
            appendLine("What Buddy knows about this child:")
            MemoryCategory.entries.forEach { cat ->
                val catFacts = facts.filter { it.category == cat }
                if (catFacts.isNotEmpty()) {
                    appendLine("  ${cat.name.lowercase().replaceFirstChar { it.uppercase() }}:")
                    catFacts.forEach { appendLine("    - ${it.key}: ${it.value}") }
                }
            }
        }.trimEnd()
    }
}
