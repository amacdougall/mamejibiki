package com.alanmacdougall.mamejibiki.search

import android.database.sqlite.SQLiteDatabase
import com.alanmacdougall.mamejibiki.conjugation.Deinflector
import com.alanmacdougall.mamejibiki.conjugation.Inflection
import com.alanmacdougall.mamejibiki.data.Entry
import com.alanmacdougall.mamejibiki.data.FormRef
import com.alanmacdougall.mamejibiki.pitch.Accent

enum class Tier { EXACT, FUZZY_EXACT, DEINFLECTED, PREFIX, FUZZY, ENGLISH_EXACT, ENGLISH_PREFIX, ENGLISH_WORD }

/**
 * Which written form (formKind 0, index into Entry.kanji) or reading
 * (formKind 1, index into Entry.kana) a Japanese query matched.
 * [highlightLength] is how many leading characters matched; null means the
 * whole form (fuzzy matches have no character-level alignment).
 */
data class FormMatch(val formKind: Int, val formIndex: Int, val highlightLength: Int?) {
    val form: FormRef get() = FormRef(formKind, formIndex)
}

/**
 * [pitch] holds each reading's accent patterns, indexed like Entry.kana.
 * [inflection] describes what a deinflected match undid ("past, negative").
 */
data class SearchResult(
    val id: Long,
    val entry: Entry,
    val tier: Tier,
    val match: FormMatch?,
    val pitch: List<List<Accent>> = emptyList(),
    val inflection: String? = null,
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
        // A whole-word fuzzy match (ゆき for ゆうき) ranks with exact matches,
        // then deinflected matches, then prefix matches.
        val whole = listOf(
            TierQuery(Tier.EXACT, "k.kind = 0 AND k.key GLOB ?", key),
            TierQuery(Tier.FUZZY_EXACT, "k.kind = 1 AND k.key GLOB ?", fuzzy),
        )
        val prefix = listOf(
            TierQuery(Tier.PREFIX, "k.kind = 0 AND k.key GLOB ?", "$key*"),
            TierQuery(Tier.FUZZY, "k.kind = 1 AND k.key GLOB ?", "$fuzzy*"),
        )
        val seen = HashSet<Long>()
        val results = ArrayList<SearchResult>()
        val match = { tier: Tier, cursor: android.database.Cursor ->
            FormMatch(cursor.getInt(4), cursor.getInt(5), if (tier == Tier.PREFIX) query.length else null)
        }
        collect(whole, "jp_keys", "k.form_kind, k.form_index", firstOrder = "", seen, results, match)
        if ('?' !in query && '？' !in query) deinflected(Kana.searchKey(query), seen, results)
        collect(prefix, "jp_keys", "k.form_kind, k.form_index", firstOrder = "", seen, results, match)
        return results
    }

    /**
     * The deinflected tier: each candidate dictionary form is looked up as an
     * exact key, and kept only if the entry has the candidate's POS class. A
     * する verb candidate (勉強する) also finds the `vs` noun (勉強).
     */
    private fun deinflected(key: String, seen: HashSet<Long>, results: ArrayList<SearchResult>) {
        // Each lookup key's (required POS, inflections) readings.
        val wanted = HashMap<String, MutableList<Pair<String, List<Inflection>>>>()
        for (d in Deinflector.deinflect(key)) {
            if (d.text == key) continue
            wanted.getOrPut(d.text) { ArrayList() } += d.wordClass to d.inflections
            val noun = d.text.removeSuffix("する")
            if (d.wordClass == "vs-i" && noun != d.text && noun.isNotEmpty()) {
                wanted.getOrPut(noun) { ArrayList() } += "vs" to d.inflections
            }
        }
        if (wanted.isEmpty()) return

        class Hit(val id: Long, val data: String, val common: Int, val rank: Int?, val match: FormMatch, val chains: MutableList<List<Inflection>>)
        val hits = LinkedHashMap<Long, Hit>()
        for (keys in wanted.keys.chunked(500)) {
            val sql = """
                SELECT e.id, e.data, e.common, e.rank, e.pos, k.key, k.form_kind, k.form_index
                FROM jp_keys k JOIN entries e ON e.id = k.entry_id
                WHERE k.kind = 0 AND k.key IN (${keys.joinToString(",") { "?" }})
            """.trimIndent()
            db.rawQuery(sql, keys.toTypedArray()).use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    if (id in seen) continue
                    val pos = c.getString(4).split(' ').toSet()
                    val chains = wanted.getValue(c.getString(5)).filter { it.first in pos }.map { it.second }
                    if (chains.isEmpty()) continue
                    hits[id]?.let { it.chains += chains; continue }
                    val rank = if (c.isNull(3)) null else c.getInt(3)
                    hits[id] = Hit(id, c.getString(1), c.getInt(2), rank, FormMatch(c.getInt(6), c.getInt(7), null), chains.toMutableList())
                }
            }
        }
        val ordered = hits.values.sortedWith(compareByDescending<Hit> { it.common }.thenBy { it.rank ?: Int.MAX_VALUE })
        for (hit in ordered) {
            if (results.size >= MAX_RESULTS) break
            seen += hit.id
            results += SearchResult(hit.id, Entry.decode(hit.data), Tier.DEINFLECTED, hit.match, inflection = Deinflector.label(hit.chains))
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
        val results = ArrayList<SearchResult>()
        collect(tiers, "en_keys", "k.sense_index, 0", firstOrder = "k.sense_index > 0, ", HashSet(), results) { _, _ -> null }
        return results
    }

    private data class TierQuery(val tier: Tier, val where: String, val arg: String)

    private fun collect(
        tiers: List<TierQuery>,
        table: String,
        extraColumns: String,
        firstOrder: String,
        seen: HashSet<Long>,
        results: ArrayList<SearchResult>,
        match: (Tier, android.database.Cursor) -> FormMatch?,
    ) {
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
