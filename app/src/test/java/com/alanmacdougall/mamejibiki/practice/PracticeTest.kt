package com.alanmacdougall.mamejibiki.practice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeTest {
    // KanjiVG r20260714 strokes.
    private val hi = listOf(
        "M31.5,24.5c1.12,1.12,1.74,2.75,1.74,4.75c0,1.6-0.16,38.11-0.09,53.5c0.02,3.82,0.05,6.35,0.09,6.75",
        "M33.48,26c0.8-0.05,37.67-3.01,40.77-3.25c3.19-0.25,5,1.75,5,4.25c0,4-0.22,40.84-0.23,56c0,3.48,0,5.72,0,6",
        "M34.22,55.25c7.78-0.5,35.9-2.5,44.06-2.75",
        "M34.23,86.5c10.52-0.75,34.15-2.12,43.81-2.25",
    )
    private val you = listOf(
        "M15.92,18.17c1.46,0.71,3.08,0.72,3.84,0.62c5.12-0.66,9.74-1.41,12.91-2.12c3.09-0.69,4.27,1.63,3.17,4.08C34.38,24,30.5,32.38,28,37",
        "M27.75,37.5c14.12,9.75,9.12,35.5-0.58,26.75",
        "M17.12,18.75c0.63,0.63,1.21,1.73,1.21,2.75c0,2.84,0.02,48.88,0.03,67.12c0,3.44,0,5.88,0,6.88",
        "M50.82,15.51c0.84,0.84,1.43,1.62,1.56,2.83c0.57,5.28,1.27,10.88,2.38,18.91c0.1,0.77,0.21,1.56,0.33,2.37",
        "M52.57,16.66c4.86-0.65,18.44-2.69,26.08-3.46c3.1-0.31,4.54,1.08,4.15,3.69c-0.92,6.11-2.27,12.97-3.63,18.15c-0.24,0.92-0.47,1.78-0.69,2.56",
        "M54.65,26.85c3.72-0.48,18.6-2.35,25.91-2.68",
        "M55.85,37.78c4.37-0.55,16.64-1.95,22.08-2.42",
        "M42.84,49.6c2.8,0.45,5.65,0.52,8.92,0.02c8.87-1.37,25.62-3.12,35.85-3.88c2.56-0.19,5.23-0.32,7.77,0.17",
        "M56.88,52.12c-0.04,0.49-0.07,1.29-0.43,1.97c-1.96,3.64-8.75,12.41-18.01,17.74",
        "M57.3,59.95c0.99,0.57,2.77,0.66,3.76,0.6c2.81-0.18,24.14-2.57,27.72-2.68c3.58-0.11,4.23,2.57,3.28,5.82c-1.41,4.82-4.02,17.2-11.36,28.36c-3.13,4.75-5.36,5.45-9.33,1.18",
        "M61.76,63.86c-0.02,0.41-0.04,1.06-0.38,1.63c-3.33,5.6-11.15,13.36-21.25,17.89",
        "M77.03,62.32c-0.05,0.86-0.16,2.22-0.77,3.45c-3.57,7.25-14.39,20.38-30.19,28.08",
    )
    // ！'s dot: implicit repeated relative curves, separated by spaces.
    private val dot = "M54.5,88 c -0.83,0 -1.5,0.67 -1.5,1.5 0,0.83 0.67,1.5 1.5,1.5 0.83,0 1.5,-0.67 1.5,-1.5 0,-0.83 -0.67,-1.5 -1.5,-1.5"

    private fun List<Point>.shifted(dx: Float, dy: Float = 0f) = map { Point(it.x + dx, it.y + dy) }

    private fun near(a: Point, b: Point) = a.distanceTo(b) < 0.01f

    @Test fun parsesAbsoluteRelativeAndSmooth() {
        val first = Stroke.parse(you[0])
        assertTrue(near(Point(15.92f, 18.17f), first.first()))
        assertTrue(near(Point(28f, 37f), first.last()))
        val down = Stroke.parse(hi[0])
        assertTrue(near(Point(33.24f, 89.5f), down.last()))
    }

    @Test fun parsesImplicitRepeats() {
        val points = Stroke.parse(dot)
        assertEquals(1 + 4 * 16, points.size)
        assertTrue(near(points.first(), points.last()))
        assertTrue(near(Point(54.5f, 91f), points[2 * 16]))
    }

    @Test fun resampleSpacesPointsEvenly() {
        val line = listOf(Point(0f, 0f), Point(10f, 0f), Point(10f, 10f))
        val r = Stroke.resample(line, 5)
        assertEquals(listOf(Point(0f, 0f), Point(5f, 0f), Point(10f, 0f), Point(10f, 5f), Point(10f, 10f)), r)
        assertEquals(Point(10f, 0f), Stroke.prefix(line, 0.5f).last())
    }

    @Test fun everyStrokeMatchesItself() {
        for (d in hi + you) {
            val s = Stroke.parse(d)
            assertEquals(d, 0.0, StrokeScore.score(s, s), 1e-6)
        }
    }

    @Test fun reversedStrokeFails() {
        for (d in hi + you) {
            val s = Stroke.parse(d)
            assertEquals(d, Verdict.WRONG, StrokeScore.verdict(StrokeScore.score(s.reversed(), s)))
        }
    }

    @Test fun strokeFarAwayFails() {
        val s = Stroke.parse(hi[2])
        assertEquals(Verdict.WRONG, StrokeScore.verdict(StrokeScore.score(s.shifted(0f, 45f), s)))
    }

    @Test fun halfStrokeFails() {
        // The top-and-right stroke of 日, stopped before it turns down.
        val s = Stroke.parse(hi[1])
        assertEquals(Verdict.WRONG, StrokeScore.verdict(StrokeScore.score(Stroke.prefix(s, 0.5f), s)))
    }

    @Test fun slightlyOffStrokePassesWithWarning() {
        val s = Stroke.parse(hi[2])
        assertEquals(Verdict.CORRECT, StrokeScore.verdict(StrokeScore.score(s.shifted(5f, 5f), s)))
        assertEquals(Verdict.WARNING, StrokeScore.verdict(StrokeScore.score(s.shifted(0f, 30f), s)))
    }

    @Test fun cleanSessionIsGreen() {
        val strokes = hi.map(Stroke::parse)
        val session = PracticeSession(strokes)
        strokes.forEachIndexed { i, s -> assertEquals(Attempt.Accepted(i, StrokeMark.CORRECT), session.submit(s)) }
        assertTrue(session.complete)
        assertEquals(Grade.GREEN, session.grade)
        assertEquals(Attempt.Extra, session.submit(strokes[0]))
        assertEquals(Grade.YELLOW, session.grade)
    }

    @Test fun strokesMustBeInOrder() {
        val strokes = hi.map(Stroke::parse)
        val session = PracticeSession(strokes)
        assertEquals(Attempt.Missed(hint = false), session.submit(strokes[2]))
        assertEquals(0, session.next)
    }

    @Test fun thirdMissHintsAndCorrectAfterMissIsMarked() {
        val strokes = hi.map(Stroke::parse)
        val session = PracticeSession(strokes)
        val wrong = strokes[0].reversed()
        assertEquals(Attempt.Missed(hint = false), session.submit(wrong))
        assertEquals(Attempt.Missed(hint = false), session.submit(wrong))
        assertEquals(Attempt.Missed(hint = true), session.submit(wrong))
        assertEquals(Attempt.Accepted(0, StrokeMark.CORRECT_AFTER_MISS), session.submit(strokes[0]))
        assertEquals(Attempt.Accepted(1, StrokeMark.CORRECT), session.submit(strokes[1]))
    }

    @Test fun shortStrokesAreIgnored() {
        val session = PracticeSession(hi.map(Stroke::parse))
        assertEquals(Attempt.Ignored, session.submit(listOf(Point(50f, 50f), Point(51f, 50.5f))))
        assertEquals(0, session.misses)
    }

    @Test fun warningsAndShowMe() {
        val strokes = hi.map(Stroke::parse)
        val session = PracticeSession(strokes)
        assertEquals(Attempt.Accepted(0, StrokeMark.WARNING), session.submit(strokes[0].shifted(30f)))
        assertEquals(1..3, session.showRemaining())
        assertEquals(Grade.RED, session.grade)
    }
}
