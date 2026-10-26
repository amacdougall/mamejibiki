package com.alanmacdougall.mamejibiki.conjugation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeinflectorTest {
    /** The label for [word] deinflected to [dictionary] as a [pos] word, or null if it isn't a candidate. */
    private fun labelFor(word: String, dictionary: String, pos: String): String? {
        val chains = Deinflector.deinflect(word).filter { it.text == dictionary && it.wordClass == pos }.map { it.inflections }
        return if (chains.isEmpty()) null else Deinflector.label(chains)
    }

    @Test fun specExamples() {
        assertEquals("past", labelFor("掛けた", "掛ける", "v1"))
        assertEquals("past, negative", labelFor("引かなかった", "引く", "v5k"))
        assertEquals("potential or passive, negative", labelFor("食べられない", "食べる", "v1"))
        assertEquals("potential or passive", labelFor("食べられる", "食べる", "v1"))
        assertEquals("negative", labelFor("食べられない", "食べられる", "v1"))
    }

    @Test fun labels() {
        assertEquals("past, polite, negative", labelFor("引きませんでした", "引く", "v5k"))
        assertEquals("volitional, polite", labelFor("引きましょう", "引く", "v5k"))
        assertEquals("progressive, polite", labelFor("引いています", "引く", "v5k"))
        assertEquals("request, negative", labelFor("引かないでください", "引く", "v5k"))
        assertEquals("te-form, negative", labelFor("引かないで", "引く", "v5k"))
        assertEquals("causative passive", labelFor("引かされる", "引く", "v5k"))
        assertEquals("causative passive, negative", labelFor("引かせられない", "引く", "v5k"))
        assertEquals("desiderative, past, negative", labelFor("引きたくなかった", "引く", "v5k"))
        assertEquals("polite, negative", labelFor("高くありません", "高い", "adj-i"))
        assertEquals("past, polite, negative", labelFor("高くなかったです", "高い", "adj-i"))
        assertEquals("potential", labelFor("勉強できる", "勉強する", "vs-i"))
        assertEquals("negative", labelFor("よくない", "いい", "adj-ix"))
        assertEquals("negative", labelFor("こない", "くる", "vk"))
    }

    @Test fun eachClassPastAndNegative() {
        val words = listOf(
            "食べる" to "v1", "くれる" to "v1-s", "書く" to "v5k", "泳ぐ" to "v5g", "話す" to "v5s", "待つ" to "v5t",
            "死ぬ" to "v5n", "遊ぶ" to "v5b", "読む" to "v5m", "帰る" to "v5r", "買う" to "v5u", "行く" to "v5k-s",
            "ある" to "v5r-i", "くださる" to "v5aru", "問う" to "v5u-s", "来る" to "vk", "くる" to "vk",
            "する" to "vs-i", "愛する" to "vs-s", "高い" to "adj-i", "いい" to "adj-ix", "良い" to "adj-ix",
        )
        for ((word, pos) in words) {
            val table = Conjugation.table(word, pos)!!.associateBy { it.label }
            assertEquals("$word past", "past", labelFor(table.getValue("Past").affirmative.single(), word, pos))
            assertEquals("$word negative", "negative", labelFor(table.getValue("Non-past").negative.single(), word, pos))
        }
    }

    /** Every cell of every table deinflects back to its dictionary form. */
    @Test fun reverseRegeneratesTables() {
        val words = listOf(
            "引く" to "v5k", "高い" to "adj-i", "食べる" to "v1", "行く" to "v5k-s", "ある" to "v5r-i",
            "くださる" to "v5aru", "問う" to "v5u-s", "来る" to "vk", "くる" to "vk", "する" to "vs-i",
            "愛する" to "vs-s", "いい" to "adj-ix", "泳ぐ" to "v5g", "死ぬ" to "v5n",
        )
        for ((word, pos) in words) {
            for (row in Conjugation.table(word, pos)!!) {
                val forms = row.affirmative + row.negative
                for (form in forms) {
                    if (form == word) continue
                    // The ichidan stem is the one form deinflection deliberately skips.
                    if (pos == "v1" && row.label.startsWith("Stem")) continue
                    assertTrue("$form (${row.label}) → $word", Deinflector.deinflect(form).any { it.text == word && it.wordClass == pos })
                }
            }
        }
    }

    @Test fun suruNoun() {
        assertTrue(Deinflector.deinflect("勉強した").any { it.text == "勉強する" && it.wordClass == "vs-i" })
    }
}
