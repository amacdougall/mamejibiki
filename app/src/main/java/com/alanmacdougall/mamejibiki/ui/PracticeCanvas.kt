package com.alanmacdougall.mamejibiki.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.alanmacdougall.mamejibiki.practice.Attempt
import com.alanmacdougall.mamejibiki.practice.Grade
import com.alanmacdougall.mamejibiki.practice.PracticeSession
import com.alanmacdougall.mamejibiki.practice.Point
import com.alanmacdougall.mamejibiki.practice.Stroke
import com.alanmacdougall.mamejibiki.practice.StrokeMark
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** KanjiVG's coordinate space is 109 units square. */
private const val UNITS = 109f
/** KanjiVG's own stroke width, in units. */
private const val INK_WIDTH = 3.5f
/** Stroke width for [KanjiStrokes], which is drawn small: a bit heavier, to match a font's regular weight. */
private const val STILL_INK_WIDTH = 5f
/** A drawn point is kept only when it's this far (in units) from the last one. */
private const val POINT_SPACING = 2f

private val WARNING = Color(0xFFF9A825)
private val MISS = Color(0xFFE53935)
private val GOOD = Color(0xFF43A047)

/**
 * A practice canvas's run: the session being graded and the animations on
 * screen. Lives only while the canvas is shown (the drawing isn't kept).
 */
private class PracticeCanvasState(pathData: List<String>, private val scope: CoroutineScope) {
    val paths: List<Path> = pathData.map { PathParser().parsePathString(it).toPath() }
    val polylines: List<List<Point>> = pathData.map(Stroke::parse)
    var session = PracticeSession(polylines)
        private set
    /** Bumped whenever [session] changes, since it isn't Compose state itself. */
    var version by mutableIntStateOf(0)
    /** The stroke being drawn, in units. */
    var drawing by mutableStateOf<List<Point>>(emptyList())

    /** An accepted stroke's drawn line morphing onto the reference as [progress] goes 0 → 1. */
    class Morph(val drawn: List<Point>, val progress: Animatable<Float, *>)
    val morphs = mutableStateListOf<Pair<Int, Morph>>()
    /** A missed stroke fading out. */
    val fading = mutableStateListOf<Pair<List<Point>, Animatable<Float, *>>>()
    /** Strokes being written out by Hint or Show Me: index, how much is written, opacity. */
    class Writing(val index: Int, val progress: Animatable<Float, *>, val alpha: Animatable<Float, *>)
    val writing = mutableStateListOf<Writing>()
    val tint = Animatable(0f)
    private val jobs = mutableListOf<Job>()

    private fun animate(block: suspend CoroutineScope.() -> Unit) {
        jobs += scope.launch(block = block)
    }

    fun submit() {
        val drawn = drawing
        drawing = emptyList()
        when (val attempt = session.submit(drawn)) {
            is Attempt.Accepted -> {
                val morph = Morph(drawn, Animatable(0f))
                morphs += attempt.index to morph
                animate { morph.progress.animateTo(1f, tween(500)) }
                writing.removeAll { it.index == attempt.index }
            }
            is Attempt.Missed -> {
                fade(drawn)
                if (attempt.hint) hint()
            }
            Attempt.Extra -> fade(drawn)
            Attempt.Ignored -> {}
        }
        version++
        if (session.complete) animate { tint.animateTo(1f, tween(400)) }
    }

    private fun fade(drawn: List<Point>) {
        val alpha = Animatable(1f)
        val item = drawn to alpha
        fading += item
        animate {
            alpha.animateTo(0f, tween(600))
            fading.remove(item)
        }
    }

    /** Writes out the next stroke faintly, then fades it. */
    fun hint() {
        if (session.complete) return
        val index = session.next
        writing.removeAll { it.index == index }
        val w = Writing(index, Animatable(0f), Animatable(1f))
        writing += w
        animate {
            w.progress.animateTo(1f, tween(strokeMillis(index), easing = LinearEasing))
            w.alpha.animateTo(0f, tween(800, delayMillis = 400))
            writing.remove(w)
        }
    }

    /** Writes out every remaining stroke in order. */
    fun showMe() {
        if (session.complete) return
        writing.clear()
        val shown = session.showRemaining().map { Writing(it, Animatable(0f), Animatable(1f)) }
        writing += shown
        version++
        animate {
            for (w in shown) w.progress.animateTo(1f, tween(strokeMillis(w.index), easing = LinearEasing))
            tint.animateTo(1f, tween(400))
        }
    }

    fun clear() {
        jobs.forEach { it.cancel() }
        jobs.clear()
        session = PracticeSession(polylines)
        drawing = emptyList()
        morphs.clear()
        fading.clear()
        writing.clear()
        scope.launch { tint.snapTo(0f) }
        version++
    }

    private fun strokeMillis(index: Int) = (Stroke.length(polylines[index]) * 6).toInt().coerceIn(200, 700)
}

/**
 * The practice canvas (UI spec §5): a square at full width with a guide grid.
 * Strokes drawn on it are graded in order against [strokes] (KanjiVG path data).
 * Touches never scroll the page.
 */
@Composable
fun PracticeCanvas(strokes: List<String>, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val state = remember(strokes) { PracticeCanvasState(strokes, scope) }
    val ink = MaterialTheme.colorScheme.onSurface
    val guide = MaterialTheme.colorScheme.outlineVariant
    val hintInk = MaterialTheme.colorScheme.primary
    // Opaque, so overlapping strokes don't darken where they cross.
    val shownInk = lerp(MaterialTheme.colorScheme.surface, ink, 0.45f)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .border(1.dp, guide, RoundedCornerShape(8.dp))
                .pointerInput(state) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        val unit = UNITS / size.width
                        state.drawing = listOf(Point(down.position.x * unit, down.position.y * unit))
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            change.consume()
                            if (!change.pressed) break
                            val p = Point(change.position.x * unit, change.position.y * unit)
                            if (p.distanceTo(state.drawing.last()) > POINT_SPACING) state.drawing = state.drawing + p
                        }
                        state.submit()
                    }
                },
        ) {
            state.version // Redraw when the session changes.
            val grade = state.session.grade
            val gradeColor = when (grade) {
                Grade.GREEN -> GOOD
                Grade.YELLOW -> WARNING
                Grade.RED -> MISS
                null -> null
            }
            gradeColor?.let { drawRect(it.copy(alpha = 0.08f * state.tint.value)) }
            scale(size.width / UNITS, pivot = Offset.Zero) {
                drawGuides(guide)
                state.session.marks.forEachIndexed { i, mark ->
                    if (mark == StrokeMark.SHOWN) return@forEachIndexed
                    var color = when (mark) {
                        StrokeMark.CORRECT -> ink
                        StrokeMark.WARNING -> WARNING
                        else -> MISS
                    }
                    if (gradeColor != null) color = lerp(color, gradeColor, state.tint.value)
                    val morph = state.morphs.firstOrNull { it.first == i }?.second
                    val t = morph?.progress?.value ?: 1f
                    if (morph != null && t < 1f) drawPolyline(morphed(morph.drawn, state.polylines[i], t), ink.copy(alpha = 1f - t))
                    drawPath(state.paths[i], color.copy(alpha = t), style = inkStyle())
                }
                for (w in state.writing) {
                    if (w.progress.value == 0f) continue
                    val shown = state.session.marks.getOrNull(w.index) == StrokeMark.SHOWN
                    var color = if (shown) shownInk else hintInk.copy(alpha = 0.5f * w.alpha.value)
                    if (shown && gradeColor != null) color = lerp(color, lerp(shownInk, gradeColor, 0.7f), state.tint.value)
                    drawPolyline(Stroke.prefix(state.polylines[w.index], w.progress.value), color)
                }
                for ((drawn, alpha) in state.fading) drawPolyline(drawn, MISS.copy(alpha = alpha.value))
                drawPolyline(state.drawing, ink)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = state::hint) { Text("Hint") }
            OutlinedButton(onClick = state::showMe) { Text("Show Me") }
            OutlinedButton(onClick = state::clear) { Text("Clear") }
        }
    }
}

/** The kanji's KanjiVG strokes drawn whole and still, filling a square. */
@Composable
fun KanjiStrokes(strokes: List<String>, modifier: Modifier = Modifier) {
    val paths = remember(strokes) { strokes.map { PathParser().parsePathString(it).toPath() } }
    val ink = MaterialTheme.colorScheme.onSurface
    Canvas(modifier.aspectRatio(1f)) {
        scale(size.width / UNITS, pivot = Offset.Zero) {
            for (path in paths) drawPath(path, ink, style = inkStyle(STILL_INK_WIDTH))
        }
    }
}

private fun inkStyle(width: Float = INK_WIDTH) = DrawStroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)

/** The guide grid: centre lines and diagonals, dashed. */
private fun DrawScope.drawGuides(color: Color) {
    val dash = PathEffect.dashPathEffect(floatArrayOf(2f, 2f))
    val w = 0.4f
    drawLine(color, Offset(UNITS / 2, 0f), Offset(UNITS / 2, UNITS), w, pathEffect = dash)
    drawLine(color, Offset(0f, UNITS / 2), Offset(UNITS, UNITS / 2), w, pathEffect = dash)
    drawLine(color, Offset(0f, 0f), Offset(UNITS, UNITS), w, pathEffect = dash)
    drawLine(color, Offset(UNITS, 0f), Offset(0f, UNITS), w, pathEffect = dash)
}

private fun DrawScope.drawPolyline(points: List<Point>, color: Color) {
    if (points.isEmpty() || color.alpha == 0f) return
    val path = Path().apply {
        moveTo(points[0].x, points[0].y)
        if (points.size == 1) lineTo(points[0].x, points[0].y)
        for (p in points.drop(1)) lineTo(p.x, p.y)
    }
    drawPath(path, color, style = inkStyle())
}

/**
 * [drawn] moved part of the way ([t]) onto [target]: its chord's midpoint,
 * angle and length are interpolated toward the target chord's.
 */
private fun morphed(drawn: List<Point>, target: List<Point>, t: Float): List<Point> {
    val (dm, da, dl) = chord(drawn)
    val (tm, ta, tl) = chord(target)
    var turn = ta - da
    while (turn > Math.PI) turn -= (2 * Math.PI).toFloat()
    while (turn < -Math.PI) turn += (2 * Math.PI).toFloat()
    val angle = turn * t
    val scale = if (dl < 1f) 1f else 1f + (tl / dl - 1f) * t
    val cx = dm.x + (tm.x - dm.x) * t
    val cy = dm.y + (tm.y - dm.y) * t
    val c = cos(angle)
    val s = sin(angle)
    return drawn.map {
        val x = (it.x - dm.x) * scale
        val y = (it.y - dm.y) * scale
        Point(cx + x * c - y * s, cy + x * s + y * c)
    }
}

/** A polyline's chord: midpoint, angle (radians) and length. */
private fun chord(points: List<Point>): Triple<Point, Float, Float> {
    val a = points.first()
    val b = points.last()
    return Triple(Point((a.x + b.x) / 2, (a.y + b.y) / 2), atan2(b.y - a.y, b.x - a.x), a.distanceTo(b))
}
