package com.alanmacdougall.mamejibiki.practice

/** How an expected stroke ended up on the canvas. */
enum class StrokeMark {
    CORRECT,
    WARNING,
    /** Correct, but drawn after one or more misses on the same stroke. */
    CORRECT_AFTER_MISS,
    /** Revealed by Show Me. */
    SHOWN,
}

/** The finished character's tint. */
enum class Grade { GREEN, YELLOW, RED }

/** What happened to one drawn stroke. */
sealed interface Attempt {
    /** Too short to count; nothing changes. */
    data object Ignored : Attempt
    /** Accepted as stroke [index]. */
    data class Accepted(val index: Int, val mark: StrokeMark) : Attempt
    /** Rejected; [hint] is true when this miss triggers the automatic Hint. */
    data class Missed(val hint: Boolean) : Attempt
    /** Drawn after the character was finished; counts as a miss. */
    data object Extra : Attempt
}

/**
 * One practice run over a kanji's [strokes] (polylines in drawing order):
 * strokes must be drawn in order, and each drawn stroke is graded against
 * the next expected one (research doc §3). Pure Kotlin.
 */
class PracticeSession(val strokes: List<List<Point>>) {
    /** The mark of each accepted stroke, in order. Its size is the next expected stroke's index. */
    val marks = mutableListOf<StrokeMark>()
    var misses = 0
        private set
    var warnings = 0
        private set
    private var consecutiveMisses = 0

    val next: Int get() = marks.size
    val complete: Boolean get() = marks.size == strokes.size

    fun submit(drawn: List<Point>): Attempt {
        if (drawn.size < 2 || Stroke.length(drawn) < StrokeScore.MIN_LENGTH) return Attempt.Ignored
        if (complete) {
            misses++
            return Attempt.Extra
        }
        return when (StrokeScore.verdict(StrokeScore.score(drawn, strokes[next]))) {
            Verdict.WRONG -> {
                misses++
                consecutiveMisses++
                Attempt.Missed(hint = consecutiveMisses % 3 == 0)
            }
            Verdict.WARNING -> accept(StrokeMark.WARNING).also { warnings++ }
            Verdict.CORRECT -> accept(if (consecutiveMisses > 0) StrokeMark.CORRECT_AFTER_MISS else StrokeMark.CORRECT)
        }
    }

    private fun accept(mark: StrokeMark): Attempt.Accepted {
        consecutiveMisses = 0
        marks += mark
        return Attempt.Accepted(marks.size - 1, mark)
    }

    /** Show Me: marks every remaining stroke as shown and returns their indexes. */
    fun showRemaining(): IntRange {
        val from = next
        while (!complete) marks += StrokeMark.SHOWN
        consecutiveMisses = 0
        return from until strokes.size
    }

    /** Green when clean, red after Show Me or three or more misses, otherwise yellow. Null until complete. */
    val grade: Grade?
        get() = when {
            !complete -> null
            StrokeMark.SHOWN in marks || misses >= 3 -> Grade.RED
            misses > 0 || warnings > 0 -> Grade.YELLOW
            else -> Grade.GREEN
        }
}
