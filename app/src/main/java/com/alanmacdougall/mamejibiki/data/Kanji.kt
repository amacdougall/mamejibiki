package com.alanmacdougall.mamejibiki.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A KANJIDIC2 kanji, decoded from `kanji.data` (shape documented in pipeline/lib/schema.sql). */
@Serializable
data class KanjiInfo(
    val on: List<String> = emptyList(),
    val kun: List<String> = emptyList(),
    val meanings: List<String> = emptyList(),
    val strokes: Int? = null,
    val grade: Int? = null,
    val freq: Int? = null,
    val jlpt: Int? = null,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun decode(data: String): KanjiInfo = json.decodeFromString(serializer(), data)
    }
}

/**
 * A node of a KanjiVG component tree (`kanji_vg.tree`): the component, the
 * character it's a variant of, whether it's the phonetic component, and its
 * own components.
 */
@Serializable
data class KanjiNode(
    val e: String,
    val o: String? = null,
    val phon: Boolean = false,
    val c: List<KanjiNode> = emptyList(),
) {
    /** This node and every node below it. */
    fun all(): List<KanjiNode> = listOf(this) + c.flatMap { it.all() }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun decode(data: String): KanjiNode = json.decodeFromString(serializer(), data)
    }
}

/** A KanjiVG character: SVG path data for each stroke in drawing order (109 × 109 space), and its component tree. */
data class KanjiVg(val strokes: List<String>, val tree: KanjiNode)
