package com.alanmacdougall.mamejibiki.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alanmacdougall.mamejibiki.data.FormKind
import com.alanmacdougall.mamejibiki.data.FormRef
import com.alanmacdougall.mamejibiki.search.SearchResult

/**
 * A result row's forms, badges and senses. Also used for a kanji block's entries.
 * Collapsed, the senses run together on two lines; expanded, each sense gets its
 * own line. The chevron that toggles [expanded] shows only when there's more to see.
 */
@Composable
fun ResultRowContent(
    result: SearchResult,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier,
    horizontalPadding: Dp = 16.dp,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val (forms, pitch) = formsLine(result)
            PitchText(
                forms,
                pitch,
                style = MaterialTheme.typography.titleMedium.copy(lineHeight = 30.sp),
                modifier = Modifier.weight(1f, fill = false),
            )
            if (result.entry.kanji.any { it.common } || result.entry.kana.any { it.common }) Badge("common")
            if (result.entry.sense.any { "vs" in it.partOfSpeech }) Badge("する")
        }
        result.inflection?.let {
            Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
        }
        var overflows by remember(result.id) { mutableStateOf(false) }
        val glossStyle = MaterialTheme.typography.bodyMedium
        val glossColor = MaterialTheme.colorScheme.onSurfaceVariant
        // The chevron sits beside the collapsed text's second line in both states,
        // so tapping the same spot twice expands then collapses.
        val chevronTop = with(LocalDensity.current) { (glossStyle.lineHeight * 2).toDp() - CHEVRON_SIZE }.coerceAtLeast(0.dp)
        Row(verticalAlignment = Alignment.Top) {
            if (expanded) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (line in senseLines(result)) Text(line, style = glossStyle, color = glossColor)
                }
            } else {
                Text(
                    glossLine(result),
                    Modifier.weight(1f),
                    style = glossStyle,
                    color = glossColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { if (it.hasVisualOverflow) overflows = true },
                )
            }
            if (expanded || overflows) {
                Box(
                    Modifier
                        .padding(start = 4.dp, top = chevronTop)
                        .size(CHEVRON_SIZE)
                        .clip(CircleShape)
                        .clickable(onClick = onToggle),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse senses" else "Expand senses",
                        tint = glossColor,
                    )
                }
            }
        }
    }
}

private val CHEVRON_SIZE = 36.dp

fun Set<Long>.toggle(id: Long): Set<Long> = if (id in this) this - id else this + id

@Composable
private fun Badge(label: String) {
    Text(
        label,
        Modifier
            .padding(start = 6.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
    )
}

/**
 * All written forms then all readings; the matched one highlighted, rare ones
 * greyed. Readings carry their first pitch accent pattern.
 */
@Composable
private fun formsLine(result: SearchResult): Pair<AnnotatedString, List<PitchLine>> {
    val accent = MaterialTheme.colorScheme.primary
    val accentRemainder = accent.copy(alpha = 0.6f)
    val rare = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val forms = result.entry.kanji.mapIndexed { i, k -> Triple(FormRef(FormKind.KANJI, i), k.text, k.isRare) } +
        result.entry.kana.mapIndexed { i, r -> Triple(FormRef(FormKind.KANA, i), r.text, r.isRare) }
    val pitch = ArrayList<PitchLine>()
    val text = buildAnnotatedString {
        forms.forEachIndexed { n, (ref, text, isRare) ->
            if (n > 0) withStyle(SpanStyle(color = rare)) { append(", ") }
            result.pitch.getOrNull(ref.index)?.firstOrNull()?.takeIf { ref.kind == FormKind.KANA }?.let { accent ->
                pitch += pitchLines(text, accent, offset = length, color = if (isRare) rare else Color.Unspecified)
            }
            val match = result.match?.takeIf { it.form == ref }
            when {
                match != null -> {
                    val ranges = match.highlight ?: listOf(text.indices)
                    text.forEachIndexed { i, c ->
                        val color = if (ranges.any { i in it }) accent else accentRemainder
                        withStyle(SpanStyle(color = color, fontWeight = FontWeight.SemiBold)) { append(c) }
                    }
                }
                isRare -> withStyle(SpanStyle(color = rare)) { append(text) }
                else -> append(text)
            }
        }
    }
    return text to pitch
}

private fun glossLine(result: SearchResult): String = senseLines(result).joinToString("  ")

/** Each sense's glosses, comma-joined, numbered when there's more than one sense. */
private fun senseLines(result: SearchResult): List<String> {
    val senses = result.entry.sense
    if (senses.size == 1) return listOf(senses[0].gloss.joinToString(", "))
    return senses.mapIndexed { i, s -> "${i + 1}. ${s.gloss.joinToString(", ")}" }
}

internal fun copyText(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(text, text))
}

