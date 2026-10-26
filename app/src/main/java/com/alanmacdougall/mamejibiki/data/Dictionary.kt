package com.alanmacdougall.mamejibiki.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.alanmacdougall.mamejibiki.pitch.Accent
import com.alanmacdougall.mamejibiki.pitch.Pitch
import com.alanmacdougall.mamejibiki.search.Kana
import com.alanmacdougall.mamejibiki.search.Search
import com.alanmacdougall.mamejibiki.search.SearchResult

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
        val written = listOfNotNull(entry.kanji.getOrNull(headword.index)?.takeIf { headword.kind == FormRef.KANJI }) + entry.kanji
        val forms = (written.map { it.text }.filter { reading.appliesTo(it) } + reading.text).distinct()
        val key = Kana.toHiragana(reading.text)
        val placeholders = forms.joinToString(",") { "?" }
        val found = HashMap<String, String>()
        db.rawQuery("SELECT form, hl FROM pitch WHERE reading = ? AND form IN ($placeholders)", arrayOf(key) + forms).use { c ->
            while (c.moveToNext()) found[c.getString(0)] = c.getString(1)
        }
        forms.firstNotNullOfOrNull { found[it] }?.let { Pitch.parseAll(reading.text, it) } ?: emptyList()
    }

    companion object {
        fun open(context: Context) = Dictionary(DictionaryDatabase.open(context))
    }
}
