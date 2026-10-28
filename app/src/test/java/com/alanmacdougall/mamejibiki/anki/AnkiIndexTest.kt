package com.alanmacdougall.mamejibiki.anki

import com.alanmacdougall.mamejibiki.data.Entry
import com.alanmacdougall.mamejibiki.data.FormRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnkiIndexTest {
    @Test fun wordStripsRubyAndStrayHtml() {
        assertEquals("様になる", AnkiIndex.word("<ruby>様<rt>よう</rt></ruby>になる"))
        assertEquals("煙草", AnkiIndex.word("<ruby>煙草<rp>(</rp><rt>たばこ</rt><rp>)</rp></ruby>"))
        assertEquals("食べる", AnkiIndex.word("<span style=\"color: red\">食べる</span>&nbsp;"))
        assertEquals("馬鹿", AnkiIndex.word("<ruby>馬<rt>ば</rt></ruby><ruby>鹿<rt>か</rt></ruby>"))
        assertEquals("食べる", AnkiIndex.word("<b>食べる</b><br>"))
        assertEquals("組み込み", AnkiIndex.word("組み込み "))
    }

    @Test fun wordDropsSuruAndAffixMarks() {
        assertEquals("制御", AnkiIndex.word("制御 (する)"))
        assertEquals("コピペ", AnkiIndex.word("コピペ（する）"))
        assertEquals("枚", AnkiIndex.word("〜枚"))
        assertEquals("っぱなし", AnkiIndex.word("～っぱなし"))
    }

    @Test fun readingFoldsKatakanaAndSpace() {
        assertEquals("たべる", AnkiIndex.reading(" たべる　"))
        assertEquals("かしわもち", AnkiIndex.reading("カシワモチ"))
    }

    private val tabako = Entry.decode(
        """{"kanji":[{"text":"煙草"},{"text":"莨","tags":["rK"]}],"kana":[{"text":"たばこ"},{"text":"タバコ"}],"sense":[{"misc":["uk"],"gloss":["tobacco"]}]}""",
    )
    private val kakeru = Entry.decode(
        """{"kanji":[{"text":"掛ける"},{"text":"懸ける"}],"kana":[{"text":"かける"}],"sense":[{"gloss":["to hang"]}]}""",
    )

    @Test fun usuallyKanaWordMatchesItsRubyKanjiNote() {
        val index = AnkiIndex(listOf("<ruby>煙草<rt>たばこ</rt></ruby>" to "たばこ"))
        assertTrue(AnkiIndex.key(tabako, FormRef(FormRef.KANA, 0)) in index)
        assertTrue(AnkiIndex.key(tabako, null) in index)
        assertTrue(AnkiIndex.key(tabako, FormRef(FormRef.KANJI, 0)) in index)
    }

    @Test fun usuallyKanaWordMatchesItsKanaNote() {
        val index = AnkiIndex(listOf("たばこ" to "たばこ"))
        assertTrue(AnkiIndex.key(tabako, FormRef(FormRef.KANJI, 0)) in index)
    }

    @Test fun otherWrittenFormsOfTheSameReadingMatch() {
        val index = AnkiIndex(listOf("掛ける" to "かける"))
        assertTrue(AnkiIndex.key(kakeru, FormRef(FormRef.KANJI, 1)) in index)
    }

    @Test fun kanaNoteDoesNotCoverAWordUsuallyWrittenInKanji() {
        assertFalse(AnkiIndex.key(kakeru, FormRef(FormRef.KANJI, 0)) in AnkiIndex(listOf("かける" to "かける")))
    }

    @Test fun differentReadingIsADifferentWord() {
        assertFalse(AnkiIndex.key(kakeru, null) in AnkiIndex(listOf("掛ける" to "かかる")))
    }

    @Test fun noteWithoutReadingMatchesBySpelling() {
        assertTrue(AnkiIndex.key(kakeru, null) in AnkiIndex(listOf("掛ける" to "")))
    }

    @Test fun plusAddsANote() {
        val index = AnkiIndex(emptyList()).plus("掛ける", "かける")
        assertTrue(AnkiIndex.key(kakeru, null) in index)
    }
}
