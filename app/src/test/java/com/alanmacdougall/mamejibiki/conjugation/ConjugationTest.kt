package com.alanmacdougall.mamejibiki.conjugation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConjugationTest {
    private fun table(word: String, pos: String): Map<String, Pair<String, String>> =
        Conjugation.table(word, pos)!!.associate { row ->
            row.label to (row.affirmative.joinToString(", ").ifEmpty { "—" } to row.negative.joinToString(", ").ifEmpty { "—" })
        }

    private fun assertRows(expected: List<Triple<String, String, String>>, actual: Map<String, Pair<String, String>>) {
        assertEquals(expected.map { it.first }, actual.keys.toList())
        for ((label, affirmative, negative) in expected) {
            assertEquals("$label, affirmative", affirmative, actual.getValue(label).first)
            assertEquals("$label, negative", negative, actual.getValue(label).second)
        }
    }

    /** Checks only the listed rows. */
    private fun assertCells(word: String, pos: String, vararg expected: Triple<String, String, String>) {
        val actual = table(word, pos)
        for ((label, affirmative, negative) in expected) {
            assertEquals("$word $label, affirmative", affirmative, actual.getValue(label).first)
            assertEquals("$word $label, negative", negative, actual.getValue(label).second)
        }
    }

    private fun row(label: String, affirmative: String, negative: String) = Triple(label, affirmative, negative)

    @Test fun hiku() = assertRows(
        listOf(
            row("Non-past", "引く", "引かない"),
            row("Non-past, polite", "引きます", "引きません"),
            row("Past", "引いた", "引かなかった"),
            row("Past, polite", "引きました", "引きませんでした"),
            row("Te-form", "引いて", "引かないで, 引かなくて"),
            row("Progressive", "引いている", "引いていない"),
            row("Progressive, polite", "引いています", "引いていません"),
            row("Volitional", "引こう", "引くまい"),
            row("Volitional, polite", "引きましょう", "—"),
            row("Desiderative", "引きたい", "引きたくない"),
            row("Conditional (ば)", "引けば", "引かなければ"),
            row("Conditional (たら)", "引いたら", "引かなかったら"),
            row("Alternative (たり)", "引いたり", "引かなかったり"),
            row("Potential", "引ける", "引けない"),
            row("Passive", "引かれる", "引かれない"),
            row("Causative", "引かせる", "引かせない"),
            row("Causative passive", "引かせられる, 引かされる", "引かせられない, 引かされない"),
            row("Imperative", "引け", "引くな"),
            row("Request", "引いてください", "引かないでください"),
            row("Stem (連用形)", "引き", "—"),
        ),
        table("引く", "v5k"),
    )

    @Test fun takai() = assertRows(
        listOf(
            row("Non-past", "高い", "高くない"),
            row("Non-past, polite", "高いです", "高くないです, 高くありません"),
            row("Past", "高かった", "高くなかった"),
            row("Past, polite", "高かったです", "高くなかったです"),
            row("Te-form", "高くて", "高くなくて"),
            row("Adverbial", "高く", "—"),
            row("Conditional (ば)", "高ければ", "高くなければ"),
            row("Conditional (たら)", "高かったら", "高くなかったら"),
        ),
        table("高い", "adj-i"),
    )

    @Test fun ichidan() = assertCells(
        "食べる", "v1",
        row("Non-past", "食べる", "食べない"),
        row("Past", "食べた", "食べなかった"),
        row("Te-form", "食べて", "食べないで, 食べなくて"),
        row("Volitional", "食べよう", "食べまい"),
        row("Conditional (ば)", "食べれば", "食べなければ"),
        row("Potential", "食べられる", "食べられない"),
        row("Passive", "食べられる", "食べられない"),
        row("Causative passive", "食べさせられる", "食べさせられない"),
        row("Imperative", "食べろ", "食べるな"),
        row("Stem (連用形)", "食べ", "—"),
    )

    @Test fun kureru() = assertCells("くれる", "v1-s", row("Imperative", "くれ", "くれるな"))

    @Test fun godanEndings() {
        val cases = listOf(
            // word, class, past, negative, polite, potential, volitional
            listOf("書く", "v5k", "書いた", "書かない", "書きます", "書ける", "書こう"),
            listOf("泳ぐ", "v5g", "泳いだ", "泳がない", "泳ぎます", "泳げる", "泳ごう"),
            listOf("話す", "v5s", "話した", "話さない", "話します", "話せる", "話そう"),
            listOf("待つ", "v5t", "待った", "待たない", "待ちます", "待てる", "待とう"),
            listOf("死ぬ", "v5n", "死んだ", "死なない", "死にます", "死ねる", "死のう"),
            listOf("遊ぶ", "v5b", "遊んだ", "遊ばない", "遊びます", "遊べる", "遊ぼう"),
            listOf("読む", "v5m", "読んだ", "読まない", "読みます", "読める", "読もう"),
            listOf("帰る", "v5r", "帰った", "帰らない", "帰ります", "帰れる", "帰ろう"),
            listOf("買う", "v5u", "買った", "買わない", "買います", "買える", "買おう"),
        )
        for (case in cases) {
            val (word, pos, past, negative, polite) = case
            val (potential, volitional) = case.drop(5)
            assertCells(
                word, pos,
                row("Non-past", word, negative),
                row("Past", past, negative.dropLast(1) + "かった"),
                row("Non-past, polite", polite, polite.dropLast(1) + "せん"),
                row("Potential", potential, potential.dropLast(1) + "ない"),
                row("Volitional", volitional, word + "まい"),
            )
        }
        assertCells("泳ぐ", "v5g", row("Te-form", "泳いで", "泳がないで, 泳がなくて"))
        assertCells("話す", "v5s", row("Causative passive", "話させられる", "話させられない"))
        assertCells("死ぬ", "v5n", row("Te-form", "死んで", "死なないで, 死ななくて"))
    }

    @Test fun godanExceptions() {
        assertCells(
            "行く", "v5k-s",
            row("Past", "行った", "行かなかった"),
            row("Te-form", "行って", "行かないで, 行かなくて"),
            row("Conditional (たら)", "行ったら", "行かなかったら"),
            row("Non-past, polite", "行きます", "行きません"),
        )
        assertCells(
            "ある", "v5r-i",
            row("Non-past", "ある", "ない"),
            row("Past", "あった", "なかった"),
            row("Te-form", "あって", "ないで, なくて"),
        )
        assertCells("恩が有る", "v5r-i", row("Non-past", "恩が有る", "恩がない"))
        assertCells(
            "くださる", "v5aru",
            row("Non-past, polite", "くださいます", "くださいません"),
            row("Imperative", "ください", "くださるな"),
            row("Past", "くださった", "くださらなかった"),
        )
        assertCells(
            "問う", "v5u-s",
            row("Past", "問うた", "問わなかった"),
            row("Te-form", "問うて", "問わないで, 問わなくて"),
            row("Non-past", "問う", "問わない"),
        )
    }

    @Test fun kuru() {
        assertCells(
            "来る", "vk",
            row("Non-past", "来る", "来ない"),
            row("Non-past, polite", "来ます", "来ません"),
            row("Past", "来た", "来なかった"),
            row("Volitional", "来よう", "来まい"),
            row("Potential", "来られる", "来られない"),
            row("Imperative", "来い", "来るな"),
            row("Conditional (ば)", "来れば", "来なければ"),
        )
        assertCells(
            "くる", "vk",
            row("Non-past", "くる", "こない"),
            row("Non-past, polite", "きます", "きません"),
            row("Past", "きた", "こなかった"),
            row("Volitional", "こよう", "こまい"),
            row("Potential", "こられる", "こられない"),
            row("Imperative", "こい", "くるな"),
            row("Conditional (ば)", "くれば", "こなければ"),
        )
    }

    @Test fun suru() {
        assertCells(
            "する", "vs-i",
            row("Non-past", "する", "しない"),
            row("Past", "した", "しなかった"),
            row("Volitional", "しよう", "するまい"),
            row("Potential", "できる", "できない"),
            row("Passive", "される", "されない"),
            row("Causative", "させる", "させない"),
            row("Causative passive", "させられる", "させられない"),
            row("Imperative", "しろ", "するな"),
        )
        assertCells(
            "勉強", "vs",
            row("Non-past", "勉強する", "勉強しない"),
            row("Past, polite", "勉強しました", "勉強しませんでした"),
            row("Potential", "勉強できる", "勉強できない"),
            row("Progressive", "勉強している", "勉強していない"),
        )
        assertCells("愛する", "vs-s", row("Non-past", "愛する", "愛さない"), row("Potential", "愛せる", "愛せない"))
    }

    @Test fun ii() {
        for (word in listOf("いい", "良い")) {
            val stem = if (word == "いい") "よ" else "良"
            assertCells(
                word, "adj-ix",
                row("Non-past", word, "${stem}くない"),
                row("Non-past, polite", "${word}です", "${stem}くないです, ${stem}くありません"),
                row("Past", "${stem}かった", "${stem}くなかった"),
                row("Adverbial", "${stem}く", "—"),
                row("Conditional (ば)", "${stem}ければ", "${stem}くなければ"),
            )
        }
    }

    @Test fun unconjugatableSpelling() {
        assertNull(Conjugation.table("引く", "v1"))
        assertNull(Conjugation.table("引く", "n"))
    }
}
