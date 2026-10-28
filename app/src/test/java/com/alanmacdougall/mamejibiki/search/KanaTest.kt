package com.alanmacdougall.mamejibiki.search

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KanaTest {
    @Test fun hasJapanese() {
        assertTrue(Kana.hasJapanese("たべる"))
        assertTrue(Kana.hasJapanese("コーヒー"))
        assertTrue(Kana.hasJapanese("食"))
        assertTrue(Kana.hasJapanese("々"))
        // Outside the BMP: a surrogate pair.
        assertTrue(Kana.hasJapanese("𠮟"))
        assertFalse(Kana.hasJapanese("taberu"))
        assertFalse(Kana.hasJapanese("??"))
    }
}
