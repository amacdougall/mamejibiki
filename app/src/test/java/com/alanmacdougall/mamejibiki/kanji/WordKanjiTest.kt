package com.alanmacdougall.mamejibiki.kanji

import com.alanmacdougall.mamejibiki.data.Entry
import com.alanmacdougall.mamejibiki.data.FormRef
import org.junit.Assert.assertEquals
import org.junit.Test

class WordKanjiTest {
    private val yuuutsu = Entry.decode(
        """{"kanji":[{"text":"憂鬱","common":true},{"text":"幽鬱"},{"text":"悒鬱","tags":["rK"]}],
           "kana":[{"text":"ゆううつ","common":true}],"sense":[{"gloss":["depression"]}]}""",
    )
    private val kakeru = Entry.decode(
        """{"kanji":[{"text":"掛ける","common":true},{"text":"懸ける"}],"kana":[{"text":"かける"}],"sense":[{"gloss":["to hang"]}]}""",
    )

    private fun literals(entry: Entry, headword: FormRef) = WordKanjiList.list(entry, headword, null).map { it.literal }

    @Test fun order() {
        assertEquals(listOf("憂", "鬱", "幽", "悒"), literals(yuuutsu, FormRef(FormRef.KANJI, 0)))
        assertEquals(listOf("懸", "掛"), literals(kakeru, FormRef(FormRef.KANJI, 1)))
        // A reading headword (a word usually written in kana): JMdict order.
        assertEquals(listOf("掛", "懸"), literals(kakeru, FormRef(FormRef.KANA, 0)))
    }

    @Test fun rareFormsComeLast() {
        assertEquals(listOf("悒", "鬱", "憂", "幽"), literals(yuuutsu, FormRef(FormRef.KANJI, 2)))
        val entry = Entry.decode(
            """{"kanji":[{"text":"甲","tags":["rK"]},{"text":"乙"}],"kana":[{"text":"x"}],"sense":[{"gloss":["x"]}]}""",
        )
        assertEquals(listOf("乙", "甲"), literals(entry, FormRef(FormRef.KANA, 0)))
    }

    @Test fun formsAndReadings() {
        val list = WordKanjiList.list(yuuutsu, FormRef(FormRef.KANJI, 0), null)
        assertEquals(WordKanji("幽", "幽鬱", "ゆううつ"), list[2])
    }

    @Test fun kanjiByCodePoint() {
        assertEquals(listOf("𠮟", "責"), WordKanjiList.kanjiIn("𠮟責"))
        assertEquals(listOf("人"), WordKanjiList.kanjiIn("人々"))
    }

    private val kake = listOf("カイ", "ケイ") to listOf("か.ける", "か.け", "-が.け", "か.かる", "-が.かる", "か.かり", "-が.かり", "かかり", "-がかり")

    private fun highlight(literal: String, ruby: List<Pair<String, String?>>, readings: Pair<List<String>, List<String>>) =
        WordKanjiList.highlight(literal, ruby, readings.first, readings.second)

    @Test fun kunWithLongestOkurigana() {
        assertEquals(ReadingHighlight(emptySet(), setOf(0)), highlight("掛", listOf("掛" to "か", "ける" to null), kake))
        assertEquals(ReadingHighlight(emptySet(), setOf(1)), highlight("掛", listOf("掛" to "か", "け" to null), kake))
        assertEquals(ReadingHighlight(emptySet(), setOf(5)), highlight("掛", listOf("掛" to "か", "かり" to null), kake))
        // Rendaku: が matches both か.け and -が.け.
        assertEquals(ReadingHighlight(emptySet(), setOf(1, 2)), highlight("掛", listOf("心" to "こころ", "掛" to "が", "け" to null), kake))
    }

    @Test fun on() {
        val ruby = listOf("憂" to "ゆう", "鬱" to "うつ")
        assertEquals(ReadingHighlight(setOf(0), emptySet()), highlight("憂", ruby, listOf("ユウ") to listOf("うれ.える", "うれ.い", "う.い")))
        assertEquals(ReadingHighlight(setOf(0), emptySet()), highlight("鬱", ruby, listOf("ウツ") to listOf("ふさ.ぐ", "しげ.る")))
    }

    @Test fun rendakuAndGemination() {
        assertEquals(ReadingHighlight(emptySet(), setOf(0, 1)), highlight("棚", listOf("本" to "ほん", "棚" to "だな"), listOf("ホウ") to listOf("たな", "-だな")))
        assertEquals(ReadingHighlight(setOf(0), emptySet()), highlight("学", listOf("学" to "がっ", "校" to "こう"), listOf("ガク") to listOf("まな.ぶ")))
        assertEquals(ReadingHighlight(setOf(0), emptySet()), highlight("発", listOf("出" to "しゅっ", "発" to "ぱつ"), listOf("ハツ", "ホツ") to listOf("た.つ")))
    }

    @Test fun jukujikunHighlightsNothing() {
        assertEquals(ReadingHighlight.NONE, highlight("今", listOf("今日" to "きょう"), listOf("コン", "キン") to listOf("いま")))
    }
}
