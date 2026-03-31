package com.itsikh.buddy.voice

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.itsikh.buddy.R
import com.itsikh.buddy.logging.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves ambiguous Hebrew homographs before TTS synthesis by injecting niqqud
 * (vowel diacritics) into words that have multiple possible pronunciations.
 *
 * ## Why this helps
 * Chirp3-HD infers vowels from context but gets common homographs wrong —
 * e.g. "ספר" read as "sapar" (barber) instead of "séfer" (book).
 * Injecting the correct niqqud form (סֵפֶר vs סָפַר) removes all ambiguity.
 *
 * ## Heuristics (no ML — deterministic rules on surrounding tokens)
 * 1. **ל prefix** on the word → verb (infinitive marker: "לכתוב" = to write)
 * 2. **ה prefix** on the word → noun (definite article: "הספר" = the book)
 * 3. **ב / מ prefix** on the word → noun (prepositional: "בדרך" = on the way)
 * 4. Previous token is a **personal pronoun** → verb ("הוא כתב" = he wrote)
 * 5. Previous token is **יש / אין** → noun ("יש ספר" = there is a book)
 * 6. Previous token is a **demonstrative** (זה/זו/זאת) → noun ("זה ספר" = this is a book)
 * 7. Next token is **את** → verb (direct-object marker signals a transitive verb)
 * 8. **Default**: the statistically dominant reading in modern Israeli Hebrew
 *
 * ## Extending the dictionary
 * Add entries to `res/raw/hebrew_homographs.json`. Each entry has:
 * - `bare`    — unvocalized word exactly as it appears in text (consonants only)
 * - `default` — niqqud to use when no heuristic fires
 * - `noun`    — niqqud to use in noun context (null → fall back to default)
 * - `verb`    — niqqud to use in verb context (null → fall back to default)
 *
 * Niqqud strings should be reviewed by a native Hebrew speaker.
 */
@Singleton
class HomographResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) {
    companion object {
        private const val TAG = "HomographResolver"

        // Hebrew prefixes that attach directly to a word (no space)
        // Checked in order: longer first to avoid partial matches
        private val PREFIXES = listOf("וב", "ול", "ומ", "וכ", "שב", "של", "שמ", "ה", "ו", "ב", "ל", "מ", "כ", "ש")

        // Prefixes that indicate a NOUN reading (prepositional / definite)
        private val NOUN_PREFIXES  = setOf("ה", "ב", "מ", "וב", "ומ", "שב", "שמ")

        // Prefixes that indicate a VERB reading (infinitive marker)
        private val VERB_PREFIXES  = setOf("ל", "ול", "של")

        // Personal pronouns — if the previous token is one of these, the word is a verb
        private val PERSONAL_PRONOUNS = setOf(
            "אני", "אתה", "את", "הוא", "היא",
            "אנחנו", "אנו", "אתם", "אתן", "הם", "הן"
        )

        // Demonstratives — if the previous token is one of these, the word is a noun
        private val DEMONSTRATIVES = setOf("זה", "זו", "זאת", "הזה", "הזו", "הזאת")

        // Existential particles — if the previous token is one of these, the word is a noun
        private val EXISTENTIALS = setOf("יש", "אין")
    }

    // ── Data model ─────────────────────────────────────────────────────────

    private data class HomographEntry(
        @SerializedName("bare")    val bare: String,
        @SerializedName("default") val default: String,
        @SerializedName("noun")    val noun: String?,
        @SerializedName("verb")    val verb: String?
    )

    private data class HomographDictionary(
        @SerializedName("entries") val entries: List<HomographEntry>
    )

    // ── Dictionary (loaded once at startup) ────────────────────────────────

    private val dictionary: Map<String, HomographEntry> by lazy { loadDictionary() }

    private fun loadDictionary(): Map<String, HomographEntry> {
        return try {
            val json = context.resources.openRawResource(R.raw.hebrew_homographs)
                .bufferedReader()
                .use { it.readText() }
            val dict = gson.fromJson(json, HomographDictionary::class.java)
            dict.entries.associateBy { it.bare }.also {
                AppLogger.d(TAG, "Loaded ${it.size} homograph entries")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to load homograph dictionary", e)
            emptyMap<String, HomographEntry>()
        }
    }

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Scans [text] word by word and replaces known homographs with their
     * context-appropriate niqqud form. Non-homograph words are returned unchanged.
     *
     * Must be called AFTER [GoogleCloudTtsManager.cleanForTts] (which strips any
     * existing niqqud from input) and BEFORE [GoogleCloudTtsManager.buildSsml].
     */
    fun resolve(text: String): String {
        if (dictionary.isEmpty()) return text
        val tokens = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return text

        return tokens.mapIndexed { index, token ->
            resolveToken(
                token  = token,
                prev   = tokens.getOrNull(index - 1)?.bareLetters(),
                next   = tokens.getOrNull(index + 1)?.bareLetters()
            )
        }.joinToString(" ")
    }

    // ── Internal resolution ────────────────────────────────────────────────

    private fun resolveToken(token: String, prev: String?, next: String?): String {
        // Separate leading/trailing punctuation so lookup targets letters only
        val leadPunct  = token.takeWhile  { !it.isLetter() }
        val trailPunct = token.takeLastWhile { !it.isLetter() }
        val begin = leadPunct.length
        val end   = token.length - trailPunct.length
        val word  = if (begin >= end) "" else token.substring(begin, end)
        if (word.isEmpty()) return token

        // Find the entry: try exact match first, then with prefix stripped
        val (prefix, entry) = findEntry(word) ?: return token

        val niqqud = pickNiqqud(prefix, entry, prev, next)
        return leadPunct + prefix + niqqud + trailPunct
    }

    /**
     * Returns (prefix, entry) if [word] or [word] minus a known prefix is in
     * the dictionary; null otherwise.
     */
    private fun findEntry(word: String): Pair<String, HomographEntry>? {
        // Exact match (no prefix)
        dictionary[word]?.let { return Pair("", it) }

        // Try stripping each known prefix (longest first)
        for (prefix in PREFIXES) {
            if (word.startsWith(prefix) && word.length > prefix.length) {
                val bare = word.substring(prefix.length)
                dictionary[bare]?.let { return Pair(prefix, it) }
            }
        }
        return null
    }

    /**
     * Picks the correct niqqud form based on the detected [prefix] and
     * surrounding tokens [prev] / [next].
     */
    private fun pickNiqqud(
        prefix: String,
        entry: HomographEntry,
        prev: String?,
        next: String?
    ): String {
        val wordContext = when {
            // Prefix signals
            prefix in VERB_PREFIXES                      -> WordContext.VERB
            prefix in NOUN_PREFIXES                      -> WordContext.NOUN

            // Previous-token signals
            prev in PERSONAL_PRONOUNS                    -> WordContext.VERB
            prev in EXISTENTIALS                         -> WordContext.NOUN
            prev in DEMONSTRATIVES                       -> WordContext.NOUN

            // Next-token signals
            next == "את"                                 -> WordContext.VERB

            else                                         -> WordContext.DEFAULT
        }

        return when (wordContext) {
            WordContext.VERB    -> entry.verb    ?: entry.default
            WordContext.NOUN    -> entry.noun    ?: entry.default
            WordContext.DEFAULT -> entry.default
        }
    }

    private enum class WordContext { VERB, NOUN, DEFAULT }

    // ── Helpers ────────────────────────────────────────────────────────────

    /**
     * Strips punctuation from a token and returns only its letters, lowercased.
     * Used for clean comparison against pronoun/demonstrative sets.
     */
    private fun String.bareLetters(): String =
        filter { it.isLetter() }.lowercase()
}
