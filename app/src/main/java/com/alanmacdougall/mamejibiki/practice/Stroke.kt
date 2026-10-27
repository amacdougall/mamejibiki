package com.alanmacdougall.mamejibiki.practice

import kotlin.math.hypot

/** A point in KanjiVG's 109 × 109 space. */
data class Point(val x: Float, val y: Float) {
    fun distanceTo(other: Point): Float = hypot(x - other.x, y - other.y)
}

/** A stroke as a polyline, with the helpers grading and drawing need. Pure Kotlin. */
object Stroke {
    /** Line segments per cubic Bézier when flattening. */
    private const val CURVE_STEPS = 16

    /**
     * Flattens a KanjiVG stroke's SVG path data to a polyline. Handles the
     * commands KanjiVG uses: `M m C c S s`, including implicit repeats (extra moveto pairs act as linetos).
     */
    fun parse(d: String): List<Point> {
        val tokens = tokenize(d)
        val points = mutableListOf<Point>()
        var x = 0f
        var y = 0f
        // The previous curve's second control point, for S's reflection.
        var c2x = 0f
        var c2y = 0f
        var command = ' '
        var i = 0
        fun num(): Float = (tokens[i++] as Float)
        while (i < tokens.size) {
            val t = tokens[i]
            if (t is Char) {
                command = t
                i++
                continue
            }
            val rel = command.isLowerCase()
            val ox = if (rel) x else 0f
            val oy = if (rel) y else 0f
            when (command.lowercaseChar()) {
                'm' -> {
                    x = ox + num(); y = oy + num()
                    c2x = x; c2y = y
                    points += Point(x, y)
                }
                'c' -> {
                    val x1 = ox + num(); val y1 = oy + num()
                    val x2 = ox + num(); val y2 = oy + num()
                    val ex = ox + num(); val ey = oy + num()
                    cubic(points, x, y, x1, y1, x2, y2, ex, ey)
                    c2x = x2; c2y = y2; x = ex; y = ey
                }
                's' -> {
                    val x1 = 2 * x - c2x; val y1 = 2 * y - c2y
                    val x2 = ox + num(); val y2 = oy + num()
                    val ex = ox + num(); val ey = oy + num()
                    cubic(points, x, y, x1, y1, x2, y2, ex, ey)
                    c2x = x2; c2y = y2; x = ex; y = ey
                }
                else -> throw IllegalArgumentException("Unsupported path command '$command' in $d")
            }
        }
        return points
    }

    private fun cubic(out: MutableList<Point>, x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
        for (step in 1..CURVE_STEPS) {
            val t = step.toFloat() / CURVE_STEPS
            val u = 1 - t
            val a = u * u * u
            val b = 3 * u * u * t
            val c = 3 * u * t * t
            val e = t * t * t
            out += Point(a * x0 + b * x1 + c * x2 + e * x3, a * y0 + b * y1 + c * y2 + e * y3)
        }
    }

    /** Command letters as Chars and numbers as Floats. Numbers may run together ("1.5-2", "0.5.5"). */
    private fun tokenize(d: String): List<Any> {
        val out = mutableListOf<Any>()
        var i = 0
        while (i < d.length) {
            val ch = d[i]
            when {
                ch.isLetter() -> { out += ch; i++ }
                ch == '-' || ch == '.' || ch.isDigit() -> {
                    val start = i
                    if (ch == '-') i++
                    var dot = false
                    while (i < d.length && (d[i].isDigit() || (d[i] == '.' && !dot))) {
                        if (d[i] == '.') dot = true
                        i++
                    }
                    out += d.substring(start, i).toFloat()
                }
                else -> i++
            }
        }
        return out
    }

    fun length(points: List<Point>): Float = points.zipWithNext { a, b -> a.distanceTo(b) }.sum()

    /** [n] points spaced evenly along the polyline, from its start to its end. */
    fun resample(points: List<Point>, n: Int): List<Point> {
        require(n >= 2 && points.isNotEmpty())
        val total = length(points)
        if (total == 0f) return List(n) { points.first() }
        val out = ArrayList<Point>(n)
        var seg = 0
        var segStart = 0f
        for (k in 0 until n) {
            val target = total * k / (n - 1)
            while (seg < points.size - 2 && segStart + points[seg].distanceTo(points[seg + 1]) < target) {
                segStart += points[seg].distanceTo(points[seg + 1])
                seg++
            }
            val a = points[seg]
            val b = points[minOf(seg + 1, points.size - 1)]
            val segLen = a.distanceTo(b)
            val f = if (segLen == 0f) 0f else ((target - segStart) / segLen).coerceIn(0f, 1f)
            out += Point(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f)
        }
        return out
    }

    /** The first [fraction] of the polyline by length, for drawing a stroke being written. */
    fun prefix(points: List<Point>, fraction: Float): List<Point> {
        if (fraction >= 1f || points.size < 2) return points
        val target = length(points) * fraction.coerceAtLeast(0f)
        val out = mutableListOf(points.first())
        var walked = 0f
        for ((a, b) in points.zipWithNext()) {
            val len = a.distanceTo(b)
            if (walked + len >= target) {
                val f = if (len == 0f) 0f else (target - walked) / len
                out += Point(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f)
                return out
            }
            walked += len
            out += b
        }
        return out
    }
}
