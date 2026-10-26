package com.alanmacdougall.mamejibiki.search

import android.database.sqlite.SQLiteDatabase
import com.alanmacdougall.mamejibiki.data.Entry
import com.alanmacdougall.mamejibiki.data.FormRef
import com.alanmacdougall.mamejibiki.pitch.Accent

enum class Tier { EXACT, FUZZY_EXACT, PREFIX, FUZZY, ENGLISH_EXACT, ENGLISH_PREFIX, ENGLISH_WORD }

/**
 * Which written form (formKind 0, index into Entry.kanji) or reading
 * (formKind 1, index into Entry.kana) a Japanese query matched.
 * [highlightLength] is how many leading characters matched; null means the
 * whole form (fuzzy matches have no character-level alignment).
 */
data class FormMatch(val formKind: Int, val formIndex: Int, val highlightLength: Int?) {
    val form: FormRef get() = FormRef(formKind, formIndex)
}

/** [pitch] holds each reading's accent patterns, indexed like Entry.kana. */
data class SearchResult(
    val id: Long,
    val entry: Entry,
    val tier: Tier,
    val match: FormMatch?,
    val pitch: List<List<Accent>> = emptyList(),
)

/** The search tiers from the UI spec §2, prototyped in pipeline/query.rb. */
class Search(private val db: SQLiteDatabase) {

    fun run(rawQuery: String): List<SearchResult> {
        val query = rawQuery.trim()
        if (query.isEmpty()) return emptyList()
        return if (Kana.hasJapanese(query)) japanese(query) else english(query)
    }

    private fun japanese(query: String): List<SearchResult> {
        val key = globLiteral(Kana.searchKey(query), keepWildcard = true)
        val fuzzy = globLiteral(Kana.fuzzyKey(query), keepWildcard = true)
        // A whole-word fuzzy match (ゆき for ゆうき) ranks with exact matches, above prefix matches.
        val tiers = listOf(
            TierQuery(Tier.EXACT, "k.kind = 0 AND k.key GLOB ?", key),
            TierQuery(Tier.FUZZY_EXACT, "k.kind = 1 AND k.key GLOB ?", fuzzy),
            TierQuery(Tier.PREFIX, "k.kind = 0 AND k.key GLOB ?", "$key*"),
            TierQuery(Tier.FUZZY, "k.kind = 1 AND k.key GLOB ?", "$fuzzy*"),
        )
        return collect(tiers, "jp_keys", "k.form_kind, k.form_index", firstOrder = "") { tier, cursor ->
            val highlight = if (tier == Tier.PREFIX) query.length else null
            FormMatch(cursor.getInt(4), cursor.getInt(5), highlight)
        }
    }

    private fun english(query: String): List<SearchResult> {
        val phrase = English.phraseKey(query)
        if (phrase.isEmpty()) return emptyList()
        val tiers = buildList {
            add(TierQuery(Tier.ENGLISH_EXACT, "k.kind = 0 AND k.key = ?", phrase))
            add(TierQuery(Tier.ENGLISH_PREFIX, "k.kind = 0 AND k.key GLOB ?", globLiteral(phrase, keepWildcard = false) + "*"))
            if (' ' !in phrase) add(TierQuery(Tier.ENGLISH_WORD, "k.kind = 1 AND k.key = ?", phrase))
        }
        // A match in an entry's first sense outranks a match in a later one.
        return collect(tiers, "en_keys", "k.sense_index, 0", firstOrder = "k.sense_index > 0, ") { _, _ -> null }
    }

    private data class TierQuery(val tier: Tier, val where: String, val arg: String)

    private fun collect(
        tiers: List<TierQuery>,
        table: String,
        extraColumns: String,
        firstOrder: String,
        match: (Tier, android.database.Cursor) -> FormMatch?,
    ): List<SearchResult> {
        val seen = HashSet<Long>()
        val results = ArrayList<SearchResult>()
        for (t in tiers) {
            if (results.size >= MAX_RESULTS) break
            val sql = """
                SELECT e.id, e.data, e.common, e.rank, $extraColumns
                FROM $table k JOIN entries e ON e.id = k.entry_id
                WHERE ${t.where}
                ORDER BY ${firstOrder}e.common DESC, e.rank IS NULL, e.rank, length(k.key)
                LIMIT $PER_TIER
            """.trimIndent()
            db.rawQuery(sql, arrayOf(t.arg)).use { cursor ->
                while (cursor.moveToNext() && results.size < MAX_RESULTS) {
                    val id = cursor.getLong(0)
                    if (!seen.add(id)) continue
                    results += SearchResult(id, Entry.decode(cursor.getString(1)), t.tier, match(t.tier, cursor))
                }
            }
        }
        return results
    }

    companion object {
        private const val PER_TIER = 100
        private const val MAX_RESULTS = 100

        /**
         * Escapes GLOB metacharacters. In Japanese queries '?' (and its
         * full-width form ？) stays a single-character wildcard.
         */
        fun globLiteral(text: String, keepWildcard: Boolean): String = buildString {
            for (c in text) when {
                keepWildcard && (c == '?' || c == '？') -> append('?')
                c == '*' || c == '[' || c == '?' -> append('[').append(c).append(']')
                else -> append(c)
            }
        }
    }
}
