package com.alanmacdougall.mamejibiki.kanji

import com.alanmacdougall.mamejibiki.data.Entry
import com.alanmacdougall.mamejibiki.data.FormRef
import com.alanmacdougall.mamejibiki.search.Kana

/** A kanji on an entry's Kanji tab, with the written form and reading its highlight comes from. */
data class WordKanji(val literal: String, val form: String, val reading: String)

/** Which of a kanji's ON and KUN readings (indexes into KanjiInfo.on / kun) a word uses. */
data class ReadingHighlight(val on: Set<Int>, val kun: Set<Int>) {
    companion object {
        val NONE = ReadingHighlight(emptySet(), emptySet())
    }
}

/** The Kanji tab's kanji and their reading highlights (UI spec §5). Pure Kotlin. */
object WordKanjiList {
    /** A CJK ideograph, by code point (so 𠮟 counts). The iteration mark 々 isn't one. */
    fun isKanji(codePoint: Int): Boolean =
        codePoint in 0x3400..0x4DBF || codePoint in 0x4E00..0x9FFF || codePoint in 0xF900..0xFAFF || codePoint in 0x20000..0x3FFFF

    /** The kanji in [text], left to right, as strings (a kanji outside the BMP is two chars). */
    fun kanjiIn(text: String): List<String> =
        text.codePoints().toArray().filter { isKanji(it) }.map { String(Character.toChars(it)) }

    /**
     * Every kanji of every written form: the headword's form first, then the
     * others in JMdict order with rare forms last; left to right within a form;
     * each kanji at its first position. Each kanji is paired with the first
     * form it appears in and that form's reading ([Entry.readingFor], using the
     * matched reading for the headword).
     */
    fun list(entry: Entry, headword: FormRef, matched: FormRef?): List<WordKanji> {
        val first = listOfNotNull(headword.index.takeIf { headword.kind == FormRef.KANJI })
        val rest = entry.kanji.indices.filter { it !in first }.sortedBy { entry.kanji[it].isRare }
        val result = LinkedHashMap<String, WordKanji>()
        for (i in first + rest) {
            val form = entry.kanji[i].text
            val ref = FormRef(FormRef.KANJI, i)
            val reading = entry.kana.getOrNull(entry.readingFor(ref, if (ref == headword) matched else null))?.text ?: continue
            for (k in kanjiIn(form)) result.getOrPut(k) { WordKanji(k, form, reading) }
        }
        return result.values.toList()
    }

    /**
     * The readings of [literal] that a word uses, from the word's furigana
     * segments (`[text, reading or null]`). Only segments that are this single
     * kanji count. ON readings match the segment's reading in hiragana; KUN
     * readings match by the part before the `.`. Rendaku (だな → たな) and
     * gemination (がっ → がく) still match. Among matching KUN readings, those
     * with the longest okurigana that begins the kana after the kanji win.
     */
    fun highlight(literal: String, ruby: List<Pair<String, String?>>, on: List<String>, kun: List<String>): ReadingHighlight {
        val onHits = mutableSetOf<Int>()
        val kunHits = mutableSetOf<Int>()
        for ((i, segment) in ruby.withIndex()) {
            val (text, reading) = segment
            if (text != literal || reading.isNullOrEmpty()) continue
            val following = ruby.drop(i + 1).takeWhile { it.second == null }.joinToString("") { it.first }
            val forms = soundVariants(Kana.toHiragana(reading))
            on.forEachIndexed { j, r -> if (Kana.toHiragana(r) in forms) onHits += j }

            val stems = kun.indices.filter { kun[it].replace("-", "").substringBefore('.') in forms }
            val okurigana = stems.associateWith { kun[it].replace("-", "").substringAfter('.', "") }
            val fitting = stems.filter { following.startsWith(okurigana.getValue(it)) }
            val longest = fitting.maxOfOrNull { okurigana.getValue(it).length }
            kunHits += if (longest != null) fitting.filter { okurigana.getValue(it).length == longest } else stems
        }
        return ReadingHighlight(onHits, kunHits)
    }

    /** A segment reading plus the dictionary readings it may be a sound-changed form of. */
    private fun soundVariants(reading: String): Set<String> {
        val unvoiced = setOf(reading, Kana.unvoiced(reading.first()) + reading.drop(1))
        return unvoiced + unvoiced.filter { it.length > 1 && it.last() == 'っ' }
            .flatMap { r -> "くきつち".map { r.dropLast(1) + it } }
    }
}
