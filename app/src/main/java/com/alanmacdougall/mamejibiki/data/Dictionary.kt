package com.alanmacdougall.mamejibiki.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.alanmacdougall.mamejibiki.anki.Kaishi
import com.alanmacdougall.mamejibiki.anki.KaishiNote
import com.alanmacdougall.mamejibiki.pitch.Accent
import com.alanmacdougall.mamejibiki.pitch.Pitch
import com.alanmacdougall.mamejibiki.search.Kana
import com.alanmacdougall.mamejibiki.search.FormMatch
import com.alanmacdougall.mamejibiki.search.Search
import com.alanmacdougall.mamejibiki.search.SearchResult
import com.alanmacdougall.mamejibiki.search.Tier
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/** A JMdict tag code's English description and Japanese label. */
data class Tag(val description: String, val label: String)

/** Everything the screens read from the database. Methods block, so call them off the main thread. */
class Dictionary private constructor(private val db: SQLiteDatabase) {
    private val searcher = Search(db)

    val tags: Map<String, Tag> = buildMap {
        db.rawQuery("SELECT code, description, label_ja FROM tags", null).use { c ->
            while (c.moveToNext()) put(c.getString(0), Tag(c.getString(1), c.getString(2)))
        }
    }

    /** The Japanese label for a tag code, or the code itself if it's unknown. */
    fun label(code: String): String = tags[code]?.label ?: code

    /**
     * Japanese labels for a list of codes. A noun that takes する (`vs`) folds
     * into its transitivity label: n, vs, vt → 名詞・他動詞（する）.
     */
    fun labels(codes: List<String>): List<String> {
        val suru = "vs" in codes && ("vt" in codes || "vi" in codes)
        return codes.filterNot { suru && it == "vs" }.map { code ->
            if (suru && (code == "vt" || code == "vi")) label(code) + "（する）" else label(code)
        }
    }

    fun search(query: String): List<SearchResult> =
        searcher.run(query).map { it.copy(pitch = readingPitches(it.entry, it.entry.headword(it.match?.form))) }

    /**
     * Each reading's accent patterns, indexed like Entry.kana. Wadoku is keyed
     * by (written form, reading), so this tries the headword first, then the
     * other written forms the reading applies to, then the reading itself.
     */
    fun readingPitches(entry: Entry, headword: FormRef): List<List<Accent>> = entry.kana.map { reading ->
        wadoku(entry, headword, reading)?.let { Pitch.parseAll(reading.text, it) } ?: emptyList()
    }

    /** A reading's Wadoku H/L string (several patterns comma-separated), looked up as in [readingPitches]. */
    private fun wadoku(entry: Entry, headword: FormRef, reading: KanaForm): String? {
        val written = listOfNotNull(entry.kanji.getOrNull(headword.index)?.takeIf { headword.kind == FormRef.KANJI }) + entry.kanji
        val forms = (written.map { it.text }.filter { reading.appliesTo(it) } + reading.text).distinct()
        val key = Kana.toHiragana(reading.text)
        val placeholders = forms.joinToString(",") { "?" }
        val found = HashMap<String, String>()
        db.rawQuery("SELECT form, hl FROM pitch WHERE reading = ? AND form IN ($placeholders)", arrayOf(key) + forms).use { c ->
            while (c.moveToNext()) found[c.getString(0)] = c.getString(1)
        }
        return forms.firstNotNullOfOrNull { found[it] }
    }

    /** The JPDB rank of [form] read as [reading] (a reading headword is ranked by itself), or null. */
    private fun rank(form: String, reading: String, isReading: Boolean): Int? {
        val args = arrayOf(form, if (isReading) "" else Kana.toHiragana(reading))
        return db.rawQuery("SELECT rank FROM freq WHERE form = ? AND reading = ?", args).use { c ->
            if (c.moveToFirst()) c.getInt(0) else null
        }
    }

    /** KANJIDIC2 data for each of [literals] that has an entry. */
    fun kanji(literals: Collection<String>): Map<String, KanjiInfo> {
        if (literals.isEmpty()) return emptyMap()
        val placeholders = literals.joinToString(",") { "?" }
        return buildMap {
            db.rawQuery("SELECT literal, data FROM kanji WHERE literal IN ($placeholders)", literals.toTypedArray()).use { c ->
                while (c.moveToNext()) put(c.getString(0), KanjiInfo.decode(c.getString(1)))
            }
        }
    }

    /** A character's KanjiVG strokes and component tree, or null if KanjiVG doesn't have it. */
    fun kanjiVg(literal: String): KanjiVg? =
        db.rawQuery("SELECT strokes, tree FROM kanji_vg WHERE literal = ?", arrayOf(literal)).use { c ->
            if (!c.moveToFirst()) return null
            KanjiVg(Json.decodeFromString(ListSerializer(String.serializer()), c.getString(0)), KanjiNode.decode(c.getString(1)))
        }

    /** The kanji that contain [component] anywhere in their component tree, in Contained in order (UI spec §5). */
    fun containedIn(component: String): List<String> = buildList {
        db.rawQuery("SELECT kanji FROM kanji_parts WHERE component = ? ORDER BY ord", arrayOf(component)).use { c ->
            while (c.moveToNext()) add(c.getString(0))
        }
    }

    /**
     * The most common entries with [literal] in a written form (UI spec §5),
     * as search results matching that form. Entries whose headword contains the
     * kanji come first; entries that only use it in another form fill the rest.
     */
    fun entriesWith(literal: String, limit: Int): List<SearchResult> {
        val sql = """
            SELECT e.id, e.data FROM jp_keys k JOIN entries e ON e.id = k.entry_id
            WHERE k.kind = 0 AND k.form_kind = 0 AND k.key GLOB ?
            GROUP BY e.id
            ORDER BY e.rank IS NULL, e.rank, e.common DESC
            LIMIT 60
        """.trimIndent()
        val headed = ArrayList<SearchResult>()
        val others = ArrayList<SearchResult>()
        db.rawQuery(sql, arrayOf("*${Search.globLiteral(literal, keepWildcard = false)}*")).use { c ->
            while (c.moveToNext() && headed.size < limit) {
                val entry = Entry.decode(c.getString(1))
                val headword = entry.headword(null)
                val form = if (headword.kind == FormRef.KANJI && literal in entry.kanji[headword.index].text) {
                    headword.index
                } else {
                    val withKanji = entry.kanji.indices.filter { literal in entry.kanji[it].text }
                    withKanji.firstOrNull { !entry.kanji[it].isRare } ?: withKanji.firstOrNull() ?: continue
                }
                val result = SearchResult(c.getLong(0), entry, Tier.EXACT, FormMatch(FormRef.KANJI, form, null))
                if (form == headword.index && headword.kind == FormRef.KANJI) headed += result else others += result
            }
        }
        return (headed + others).take(limit).map { it.copy(pitch = readingPitches(it.entry, it.entry.headword(it.match?.form))) }
    }

    /** JmdictFurigana's segments for a written form and reading: (text, reading), reading null for kana. */
    fun furigana(form: String, reading: String): List<Pair<String, String?>>? =
        db.rawQuery("SELECT ruby FROM furigana WHERE form = ? AND reading = ?", arrayOf(form, reading)).use { c ->
            if (!c.moveToFirst()) return null
            Json.parseToJsonElement(c.getString(0)).jsonArray.map { segment ->
                val (text, rt) = segment.jsonArray.map { it.jsonPrimitive.contentOrNull }
                text.orEmpty() to rt
            }
        }

    /** The Kaishi 1.5k note for an entry, with the headword chosen from [matched] (UI spec §8). */
    fun kaishiNote(entry: Entry, matched: FormRef?): KaishiNote {
        val headword = entry.headword(matched)
        val word = entry.text(headword)
        val reading = entry.kana[entry.readingFor(headword, matched)]
        return Kaishi.note(entry, word, reading.text, wadoku(entry, headword, reading), rank(word, reading.text, headword.kind == FormRef.KANA))
    }

    companion object {
        fun open(context: Context) = Dictionary(DictionaryDatabase.open(context))
    }
}
