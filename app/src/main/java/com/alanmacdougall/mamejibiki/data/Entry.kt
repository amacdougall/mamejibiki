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
