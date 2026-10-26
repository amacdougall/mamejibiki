package com.alanmacdougall.mamejibiki.search

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/** Checks the Kotlin normalization against the same vectors the pipeline tests use. */
class SharedVectorsTest {
    private fun vectors(name: String): JsonObject =
        Json.parseToJsonElement(File(System.getProperty("sharedDir"), name).readText()).jsonObject

    private fun pairs(json: JsonObject, key: String): List<Pair<String, JsonArray>> =
        json.getValue(key).jsonArray.map { it.jsonArray[0].jsonPrimitive.content to it.jsonArray }

    @Test
    fun kanaSearchKey() = pairs(vectors("kana-vectors.json"), "search_key").forEach { (input, row) ->
        assertEquals(input, row[1].jsonPrimitive.content, Kana.searchKey(input))
    }

    @Test
    fun kanaFuzzyKey() = pairs(vectors("kana-vectors.json"), "fuzzy_key").forEach { (input, row) ->
        assertEquals(input, row[1].jsonPrimitive.content, Kana.fuzzyKey(input))
    }

    @Test
    fun englishPhraseKey() = pairs(vectors("english-vectors.json"), "phrase_key").forEach { (input, row) ->
        assertEquals(input, row[1].jsonPrimitive.content, English.phraseKey(input))
    }

    @Test
    fun englishWordKeys() = pairs(vectors("english-vectors.json"), "word_keys").forEach { (input, row) ->
        assertEquals(input, row[1].jsonArray.map { it.jsonPrimitive.content }, English.wordKeys(input))
    }
}
