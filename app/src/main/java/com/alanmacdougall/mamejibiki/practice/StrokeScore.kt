package com.alanmacdougall.mamejibiki.practice

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2

enum class Verdict { CORRECT, WARNING, WRONG }

/** Grades a drawn stroke against the expected one (research doc §3). Pure Kotlin. */
object StrokeScore {
    /** Drawn strokes shorter than this (in 109-unit space) are ignored. */
    const val MIN_LENGTH = 2f
    const val CORRECT_MAX = 2.0
    const val WARNING_MAX = 4.0

    /**
     * The score P = L + O + G: mean tangent-angle difference ÷ 15°, the squared
     * distance between the strokes' chord midpoints ÷ 20, and the length ratio
     * error. Lower is better.
     */
    fun score(user: List<Point>, expected: List<Point>): Double {
        val expectedLength = Stroke.length(expected)
        val userLength = Stroke.length(user)
        val n = maxOf(3, (expectedLength / 2).toInt())
        val u = Stroke.resample(user, n)
        val e = Stroke.resample(expected, n)

        var angleSum = 0.0
        for (k in 0 until n) {
            var diff = tangent(u, k) - tangent(e, k)
            while (diff > 180) diff -= 360
            while (diff < -180) diff += 360
            angleSum += abs(diff)
        }
        val shape = angleSum / n / 15

        val um = Point((user.first().x + user.last().x) / 2, (user.first().y + user.last().y) / 2)
        val em = Point((expected.first().x + expected.last().x) / 2, (expected.first().y + expected.last().y) / 2)
        val offset = um.distanceTo(em) / 20.0
        val position = offset * offset

        val length = abs(1 - userLength / expectedLength.toDouble())
        return shape + position + length
    }

    fun verdict(score: Double): Verdict = when {
        score <= CORRECT_MAX -> Verdict.CORRECT
        score <= WARNING_MAX -> Verdict.WARNING
        else -> Verdict.WRONG
    }

    /** The direction at sample [k] in degrees, by central difference (one-sided at the ends). */
    private fun tangent(points: List<Point>, k: Int): Double {
        val a = points[maxOf(0, k - 1)]
        val b = points[minOf(points.size - 1, k + 1)]
        return atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble()) * 180 / PI
    }
}
