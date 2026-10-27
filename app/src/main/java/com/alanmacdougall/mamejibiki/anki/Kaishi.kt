package com.alanmacdougall.mamejibiki.anki

import com.alanmacdougall.mamejibiki.data.Entry

/**
 * A Kaishi 1.5k note ready to add: [fields] in the note type's order, plus
 * the anki-scripts tags for the fields filled.
 */
data class KaishiNote(val fields: List<String>, val tags: Set<String>) {
    val word: String get() = fields[Kaishi.WORD]
    val reading: String get() = fields[Kaishi.WORD_READING]
}

/**
 * The Kaishi 1.5k field formats, matching ../anki-scripts exactly (UI spec
 * §8). Checked against shared/anki-vectors.json, which anki-scripts' own
 * code generates (pipeline/anki_vectors.rb).
 */
object Kaishi {
    const val NOTE_TYPE = "Kaishi 1.5k"
    val FIELD_NAMES = listOf(
        "Word", "Word Reading", "Word Meaning", "Word Furigana", "Word Audio", "Sentence", "Sentence Meaning",
        "Sentence Furigana", "Sentence Audio", "Notes", "Pitch Accent", "Pitch Accent Notes", "Frequency", "Picture",
    )
    const val WORD = 0
    const val WORD_READING = 1
    const val WORD_MEANING = 2
    const val NOTES = 9
    const val PITCH_ACCENT = 10
    const val FREQUENCY = 12

    const val PITCH_ACCENT_TAG = "generated::pitch-accent"
    const val PITCH_ACCENT_UNAVAILABLE_TAG = "pitch-accent::unavailable"
    const val GRAMMAR_TAG = "generated::grammar"

    /**
     * The fields the app fills from its own data. Furigana, sentences and
     * audio are left for anki-scripts. [pitch] is the Wadoku H/L string for
     * (word, reading); [rank] the JPDB rank, left blank when unknown.
     */
    fun note(entry: Entry, word: String, reading: String, pitch: String?, rank: Int?): KaishiNote {
        val fields = MutableList(FIELD_NAMES.size) { "" }
        fields[WORD] = word
        fields[WORD_READING] = reading
        fields[WORD_MEANING] = meaning(entry)
        fields[NOTES] = grammarNotes(entry).joinToString("、")
        if (pitch != null) fields[PITCH_ACCENT] = pitchHtml(reading, pitch)
        if (rank != null) fields[FREQUENCY] = rank.toString()
        val tags = setOf(if (pitch != null) PITCH_ACCENT_TAG else PITCH_ACCENT_UNAVAILABLE_TAG, GRAMMAR_TAG)
        return KaishiNote(fields, tags)
    }

    /**
     * WordLookup.extract_definitions: one line per sense, numbered when there
     * are several. Lines are joined with a bare `<br>`, without the script's
     * newline: AnkiDroid renders the newline as a second line break.
     */
    fun meaning(entry: Entry): String {
        val lines = entry.sense.map { it.gloss.joinToString(", ") }.filter { it.isNotEmpty() }
        return if (lines.size > 1) lines.mapIndexed { i, line -> "${i + 1}. $line" }.joinToString("<br>") else lines.firstOrNull().orEmpty()
    }

    /** extract_grammar_notes in add-jmdict-grammar-notes.rb, over every sense's POS and misc codes. */
    fun grammarNotes(entry: Entry): List<String> {
        val pos = entry.sense.flatMap { it.partOfSpeech }.toSet()
        val misc = entry.sense.flatMap { it.misc }.toSet()
        val transitive = "vt" in pos
        val intransitive = "vi" in pos
        val suru = pos.any { it in setOf("vs", "vs-s", "vs-i") }
        return buildList {
            when {
                "n" in pos && suru -> if (transitive) add("他動詞（する）") else if (intransitive) add("自動詞（する）")
                transitive && !suru -> add("他動詞")
                intransitive && !suru -> add("自動詞")
            }
            if ("adj-na" in pos) add("形容動詞")
            if ("adj-i" in pos) add("形容詞")
            if ("adv-to" in pos) add("副詞（と）")
            if ("adv" in pos && "adv-to" !in pos) add("副詞")
            if ("ctr" in pos) add("助数詞")
            if ("pref" in pos) add("接頭辞")
            if ("suf" in pos) add("接尾辞")
            if ("on-mim" in misc) add("オノマトペ")
            if ("hon" in misc) add("尊敬語")
            if ("hum" in misc) add("謙譲語")
            if ("pol" in misc) add("丁寧語")
        }
    }

    /** CardCreator.strip_ruby_tags: the Word field without its <ruby> furigana, for duplicate checks. */
    fun stripRuby(text: String): String = text.replace(Regex("<rt>[^<]*</rt>"), "").replace(Regex("</?ruby>"), "")

    private val COMBINERS = "ゃゅょャュョぁぃぅぇぉァィゥェォゎヮゕゖヵヶ".toSet()

    /** PitchAccentRenderer.to_kaishi_html: the reading in hiragana, each high run under an overline. */
    fun pitchHtml(reading: String, pattern: String): String {
        val hiragana = buildString { for (c in reading) append(if (c in 'ァ'..'ン') c - 0x60 else c) }
        val morae = ArrayList<String>()
        var c = 0
        while (c < hiragana.length) {
            if (c + 1 < hiragana.length && hiragana[c + 1] in COMBINERS) {
                morae += hiragana.substring(c, c + 2)
                c += 2
            } else {
                morae += hiragana.substring(c, c + 1)
                c += 1
            }
        }
        // Each mora's pitch is the uppercase letter among its characters' letters.
        var charIndex = 0
        val high = morae.map { mora ->
            val letters = pattern.drop(charIndex).take(mora.length).map { it.toString() }
            charIndex += mora.length
            val letter = letters.firstOrNull { it == it.uppercase() } ?: letters.firstOrNull()
            letter?.uppercase() == "H"
        }
        return buildString {
            var i = 0
            while (i < morae.size) {
                if (!high[i]) {
                    append(morae[i++])
                    continue
                }
                var j = i
                while (j < morae.size && high[j]) j++
                append(overline(morae.subList(i, j).joinToString(""), drop = j < morae.size))
                i = j
            }
        }
    }

    private fun overline(text: String, drop: Boolean): String = if (drop) {
        """<span style="display:inline-block;position:relative;padding-right:0.1em;margin-right:0.1em;"><span style="display:inline;">$text</span><span style="border-color:currentColor;display:block;user-select:none;pointer-events:none;position:absolute;top:0.1em;left:0;right:0;height:0;border-top-width:0.1em;border-top-style:solid;right:-0.1em;height:0.4em;border-right-width:0.1em;border-right-style:solid;"></span></span>"""
    } else {
        """<span style="display:inline-block;position:relative;"><span style="display:inline;">$text</span><span style="border-color:currentColor;display:block;user-select:none;pointer-events:none;position:absolute;top:0.1em;left:0;right:0;height:0;border-top-width:0.1em;border-top-style:solid;"></span></span>"""
    }
}
