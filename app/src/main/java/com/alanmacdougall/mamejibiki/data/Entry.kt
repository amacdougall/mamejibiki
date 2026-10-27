package com.alanmacdougall.mamejibiki.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/** A JMdict entry, decoded from `entries.data` (shape documented in pipeline/README.md). */
@Serializable
data class Entry(
    val kanji: List<KanjiForm> = emptyList(),
    val kana: List<KanaForm>,
    val sense: List<Sense>,
) {
    fun text(form: FormRef): String = if (form.kind == FormRef.KANJI) kanji[form.index].text else kana[form.index].text

    /** True when the entry is usually written in kana, so its headword is a reading. */
    val usuallyKana: Boolean get() = kanji.isEmpty() || "uk" in sense.first().misc

    /**
     * The form shown as the entry's headword and carried into Copy and Send to
     * AnkiDroid (UI spec §2): the matched written form; or for a matched
     * reading, that reading if the word is usually written in kana, else the
     * first common written form it applies to. With no match (English search),
     * the first written form, or the first reading for kana words.
     */
    fun headword(matched: FormRef?): FormRef {
        if (matched?.kind == FormRef.KANJI) return matched
        if (matched != null && usuallyKana) return matched
        val reading = matched?.let { kana[it.index] }
        if (matched == null && usuallyKana) return FormRef(FormRef.KANA, 0)
        val candidates = kanji.indices.filter { reading == null || reading.appliesTo(kanji[it].text) }
        val best = candidates.firstOrNull { !kanji[it].isRare } ?: candidates.firstOrNull()
        return if (best != null) FormRef(FormRef.KANJI, best) else matched ?: FormRef(FormRef.KANA, 0)
    }

    /**
     * The reading that goes with [headword] on an Anki card: the headword
     * itself if it's a reading; else the matched reading if it applies to the
     * headword; else the headword's first common reading.
     */
    fun readingFor(headword: FormRef, matched: FormRef?): Int {
        if (headword.kind == FormRef.KANA) return headword.index
        val written = kanji[headword.index].text
        if (matched?.kind == FormRef.KANA && kana[matched.index].appliesTo(written)) return matched.index
        val candidates = kana.indices.filter { kana[it].appliesTo(written) }
        return candidates.firstOrNull { !kana[it].isRare } ?: candidates.firstOrNull() ?: 0
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun decode(data: String): Entry = json.decodeFromString(serializer(), data)
    }
}

@Serializable
data class KanjiForm(
    val text: String,
    val common: Boolean = false,
    val tags: List<String> = emptyList(),
) {
    val isRare: Boolean get() = tags.any { it in RARE_KANJI_TAGS }
}

@Serializable
data class KanaForm(
    val text: String,
    val common: Boolean = false,
    val tags: List<String> = emptyList(),
    val appliesToKanji: List<String>? = null,
) {
    val isRare: Boolean get() = tags.any { it in RARE_KANA_TAGS }

    /** Whether this reading is a reading of the written form [kanji] (a missing list means all forms). */
    fun appliesTo(kanji: String): Boolean = appliesToKanji?.contains(kanji) ?: true
}

/** A written form (kind KANJI, index into Entry.kanji) or reading (kind KANA, index into Entry.kana). */
data class FormRef(val kind: Int, val index: Int) {
    companion object {
        const val KANJI = 0
        const val KANA = 1
    }
}

@Serializable
data class Sense(
    val partOfSpeech: List<String> = emptyList(),
    val appliesToKanji: List<String>? = null,
    val appliesToKana: List<String>? = null,
    val related: List<List<JsonElement>> = emptyList(),
    val antonym: List<List<JsonElement>> = emptyList(),
    val field: List<String> = emptyList(),
    val dialect: List<String> = emptyList(),
    val misc: List<String> = emptyList(),
    val info: List<String> = emptyList(),
    val gloss: List<String>,
    val glossType: List<String?>? = null,
)

private val RARE_KANJI_TAGS = setOf("rK", "iK", "oK", "sK")
private val RARE_KANA_TAGS = setOf("ik", "ok")
