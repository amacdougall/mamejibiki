package com.alanmacdougall.mamejibiki.pitch

/**
 * One pitch accent pattern for a reading: whether each mora is high, and
 * whether the pitch drops on the following particle (尾高 words, which
 * otherwise look the same as 平板 ones).
 */
data class Accent(val high: List<Boolean>, val dropAfter: Boolean)

/** A run of high morae as a character range of the reading, with a downstep tick at its end if [drop]. */
data class HighRun(val start: Int, val end: Int, val drop: Boolean)

/**
 * Reads Wadoku H/L strings, following anki-scripts' pitch_accent_renderer.rb:
 * the string has one letter per character (lowercase for small kana), and
 * may have one extra letter for a following particle.
 */
object Pitch {
    private const val COMBINERS = "ゃゅょャュョぁぃぅぇぉァィゥェォゎヮゕゖヵヶ"

    /** Splits a reading into morae: small ゃゅょぁぃぅぇぉゎ attach to the kana before them. */
    fun morae(reading: String): List<String> {
        val morae = ArrayList<String>()
        var i = 0
        while (i < reading.length) {
            val combined = i + 1 < reading.length && reading[i + 1] in COMBINERS
            val size = if (combined) 2 else 1
            morae += reading.substring(i, i + size)
            i += size
        }
        return morae
    }

    /** Parses every comma-separated pattern in a `pitch.hl` value. */
    fun parseAll(reading: String, hl: String): List<Accent> = hl.split(',').map { parse(reading, it.trim()) }

    fun parse(reading: String, hl: String): Accent {
        val high = ArrayList<Boolean>()
        var charIndex = 0
        for (mora in morae(reading)) {
            val letters = hl.drop(charIndex).take(mora.length)
            // The regular kana's uppercase letter sets the mora's pitch; default low.
            val letter = letters.firstOrNull { it.isUpperCase() } ?: letters.firstOrNull() ?: 'L'
            high += letter.uppercaseChar() == 'H'
            charIndex += mora.length
        }
        val particle = hl.getOrNull(charIndex)
        return Accent(high, high.lastOrNull() == true && particle?.uppercaseChar() == 'L')
    }

    /** The high runs to overline, as character ranges of [reading]. */
    fun highRuns(reading: String, accent: Accent): List<HighRun> {
        val morae = morae(reading)
        val runs = ArrayList<HighRun>()
        var charIndex = 0
        var runStart = -1
        morae.forEachIndexed { i, mora ->
            val high = accent.high.getOrElse(i) { false }
            if (high && runStart < 0) runStart = charIndex
            charIndex += mora.length
            val nextHigh = accent.high.getOrElse(i + 1) { false }
            if (high && (i == morae.lastIndex || !nextHigh)) {
                val drop = if (i == morae.lastIndex) accent.dropAfter else true
                runs += HighRun(runStart, charIndex, drop)
                runStart = -1
            }
        }
        return runs
    }
}
