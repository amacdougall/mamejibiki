package com.alanmacdougall.mamejibiki.anki

import com.alanmacdougall.mamejibiki.data.Entry
import com.alanmacdougall.mamejibiki.data.FormRef
import java.text.Normalizer

/**
 * What the "exists in AnkiDroid" check compares for one result (UI spec §8):
 * the note's reading, and every Word spelling that counts as the same word.
 */
data class AnkiKey(val reading: String, val spellings: Set<String>)

/**
 * The Word and Word Reading of every Kaishi 1.5k note in the collection, in
 * any deck, normalised by [word] and [reading]: a map from reading to the
 * Words noted with it. Pure Kotlin.
 */
class AnkiIndex private constructor(private val words: Map<String, Set<String>>) {
    constructor(notes: List<Pair<String, String>>) : this(
        notes.groupBy({ reading(it.second) }, { word(it.first) }).mapValues { it.value.toSet() },
    )

    /** A note with the key's reading and one of its spellings, or one of its spellings and no reading at all. */
    operator fun contains(key: AnkiKey): Boolean {
        val known = words[reading(key.reading)].orEmpty() + words[""].orEmpty()
        return key.spellings.any { word(it) in known }
    }

    /** This index with a newly added note. */
    fun plus(word: String, reading: String): AnkiIndex {
        val r = reading(reading)
        return AnkiIndex(words + (r to (words[r].orEmpty() + word(word))))
    }

    companion object {
        private val RUBY_TEXT = Regex("<(rt|rp)\\b[^>]*>.*?</\\1>", RegexOption.IGNORE_CASE)
        private val TAG = Regex("<[^>]+>")
        private val SPACE = Regex("[\\s\\u3000]+")
        private val SURU = Regex("[(（]する[)）]$")
        private val AFFIX = Regex("^[〜～~]+|[〜～~]+$")

        /**
         * A Word field as plain text. anki-scripts writes usually-kana words as
         * `<ruby>煙草<rt>たばこ</rt></ruby>` (per kanji, so 馬鹿 can be two ruby
         * runs), and its own ruby stripping misses `<rp>`. Cards mined with
         * reader tools can carry stray HTML and `&nbsp;` until strip-word-html.rb
         * runs. Hand-made cards mark する verbs as `制御 (する)` and affixes as
         * `〜枚`, and sometimes leave a trailing space.
         */
        fun word(field: String): String {
            val text = field.replace(RUBY_TEXT, "").replace(TAG, "")
                .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&amp;", "&")
                .replace(SPACE, "")
                .replace(SURU, "")
                .replace(AFFIX, "")
            return Normalizer.normalize(text, Normalizer.Form.NFC)
        }

        /**
         * A Word Reading field cleaned like [word], in hiragana: cards keep a
         * loanword's katakana inside a hiragana reading (かくとうゲーム).
         */
        fun reading(field: String): String = buildString {
            for (c in word(field)) append(if (c in 'ァ'..'ヶ') c - 0x60 else c)
        }

        /**
         * The key for sending [entry] with [matched] (the headword and reading
         * rules of UI spec §2 and §8). Any written form the reading applies to
         * counts as the same word, so a note for 掛ける covers 懸ける, and the
         * kanji in a usually-kana word's ruby Word field matches its kana
         * headword. The reading itself counts only for usually-kana words.
         */
        fun key(entry: Entry, matched: FormRef?): AnkiKey {
            val headword = entry.headword(matched)
            val reading = entry.kana[entry.readingFor(headword, matched)]
            val spellings = buildSet {
                add(entry.text(headword))
                entry.kanji.filter { reading.appliesTo(it.text) }.forEach { add(it.text) }
                if (entry.usuallyKana) add(reading.text)
            }
            return AnkiKey(reading.text, spellings)
        }
    }
}
