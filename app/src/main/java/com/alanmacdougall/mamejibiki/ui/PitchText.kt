package com.alanmacdougall.mamejibiki.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.alanmacdougall.mamejibiki.pitch.Accent
import com.alanmacdougall.mamejibiki.pitch.Pitch

/** An overline over characters [start, end) of a text, with a downstep tick at its right end if [drop]. */
data class PitchLine(val start: Int, val end: Int, val drop: Boolean, val color: Color = Color.Unspecified)

/** The overlines for [reading] drawn at character [offset] of a larger text. */
fun pitchLines(reading: String, accent: Accent, offset: Int = 0, color: Color = Color.Unspecified): List<PitchLine> =
    Pitch.highRuns(reading, accent).map { PitchLine(offset + it.start, offset + it.end, it.drop, color) }

/**
 * Text with pitch accent overlines (UI spec §7). Lines are drawn per
 * character, so a reading that wraps keeps its marks. The overline sits just
 * above the kana (clearing dakuten), measured from the baseline, so wrapped
 * text needs a line height with room for it.
 */
@Composable
fun PitchText(
    text: AnnotatedString,
    lines: List<PitchLine>,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val defaultColor = if (color.isSpecified) color else LocalContentColor.current
    Text(
        text,
        modifier.drawWithContent {
            drawContent()
            val l = layout ?: return@drawWithContent
            val stroke = 1.2.dp.toPx()
            val em = l.layoutInput.style.fontSize.toPx()
            for (line in lines) {
                val lineColor = if (line.color.isSpecified) line.color else defaultColor
                for (i in line.start until line.end.coerceAtMost(l.layoutInput.text.length)) {
                    val box = l.getBoundingBox(i)
                    val y = l.getLineBaseline(l.getLineForOffset(i)) - em * OVERLINE_HEIGHT
                    drawLine(lineColor, Offset(box.left, y), Offset(box.right, y), stroke)
                    if (line.drop && i == line.end - 1) {
                        drawLine(lineColor, Offset(box.right, y), Offset(box.right, y + em * TICK_LENGTH), stroke)
                    }
                }
            }
        },
        color = color,
        style = style,
        onTextLayout = { layout = it },
    )
}

/** Overline height above the baseline, and the downstep tick's length, in ems. */
private const val OVERLINE_HEIGHT = 1.0f
private const val TICK_LENGTH = 0.3f
