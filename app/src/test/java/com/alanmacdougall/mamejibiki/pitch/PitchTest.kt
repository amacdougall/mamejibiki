package com.alanmacdougall.mamejibiki.pitch

import org.junit.Assert.assertEquals
import org.junit.Test

class PitchTest {
    private fun runs(reading: String, hl: String) = Pitch.highRuns(reading, Pitch.parse(reading, hl))

    @Test fun morae() {
        assertEquals(listOf("ちょ", "っ", "と"), Pitch.morae("ちょっと"))
        assertEquals(listOf("コ", "ー", "ヒ", "ー"), Pitch.morae("コーヒー"))
        assertEquals(listOf("しゅ", "っ", "ぱ", "つ"), Pitch.morae("しゅっぱつ"))
    }

    @Test fun heiban() {
        // 憂鬱: flat, overline to the end with no tick.
        assertEquals(listOf(HighRun(1, 4, false)), runs("ゆううつ", "LHHHH"))
        // 東京: the small ょ's lowercase letter doesn't change the mora's pitch.
        assertEquals(listOf(HighRun(1, 5, false)), runs("とうきょう", "LHHhHH"))
    }

    @Test fun odaka() {
        // 男: high to the end, then the particle drops.
        assertEquals(listOf(HighRun(1, 3, true)), runs("おとこ", "LHHL"))
    }

    @Test fun atamadakaAndNakadaka() {
        assertEquals(listOf(HighRun(0, 2, true)), runs("ちょっと", "HhLLL"))
        assertEquals(listOf(HighRun(1, 3, true)), runs("コーヒー", "LHHLL"))
        assertEquals(listOf(HighRun(1, 2, true)), runs("かける", "LHLL"))
    }

    @Test fun severalPatterns() {
        val accents = Pitch.parseAll("ちょっと", "HhLLL,LlHHH")
        assertEquals(listOf(true, false, false), accents[0].high)
        assertEquals(listOf(false, true, true), accents[1].high)
        assertEquals(false, accents[1].dropAfter)
    }

    @Test fun shortPatternDefaultsLow() {
        assertEquals(listOf(true, false, false), Pitch.parse("あいう", "H").high)
    }
}
