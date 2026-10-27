package com.alanmacdougall.mamejibiki.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alanmacdougall.mamejibiki.data.Dictionary
import com.alanmacdougall.mamejibiki.data.KanjiInfo
import com.alanmacdougall.mamejibiki.data.KanjiNode
import com.alanmacdougall.mamejibiki.kanji.ReadingHighlight
import com.alanmacdougall.mamejibiki.kanji.WordKanji
import com.alanmacdougall.mamejibiki.kanji.WordKanjiList
import com.alanmacdougall.mamejibiki.search.SearchResult

/** How many of the most common entries using the kanji are shown. */
private const val ENTRY_COUNT = 3

/** How many Contained in kanji show before "More". */
private const val CONTAINED_IN_BATCH = 30

/**
 * One kanji block (UI spec §5), loaded. [info] is the kanji's KANJIDIC2 data,
 * or its original's when the kanji is a variant without its own ([variantOf]).
 */
class KanjiBlockState(
    val literal: String,
    val info: KanjiInfo?,
    val variantOf: String?,
    val strokeCount: Int?,
    /** KanjiVG path data for each stroke, or null when KanjiVG doesn't cover the kanji (no Practice button). */
    val strokes: List<String>?,
    val tree: KanjiNode?,
    val nodeInfo: Map<String, KanjiInfo>,
    /** The most common entries with the kanji in a written form. */
    val entries: List<SearchResult>,
    val containedIn: List<String>,
    val highlight: ReadingHighlight,
) {
    var showAllContainedIn by mutableStateOf(false)
    var practiceOpen by mutableStateOf(false)
    /** Entry ids of Entries rows whose senses are expanded. */
    var expandedEntries by mutableStateOf(emptySet<Long>())

    companion object {
        /**
         * Loads a block. [original] is the character [literal] is a variant of,
         * if known; [word] gives the reading highlight on the Kanji tab. Blocks.
         */
        fun load(dictionary: Dictionary, literal: String, original: String? = null, word: WordKanji? = null): KanjiBlockState {
            val vg = dictionary.kanjiVg(literal)
            val nodes = vg?.tree?.all().orEmpty().flatMap { listOfNotNull(it.e, it.o) }
            val infos = dictionary.kanji(nodes + listOfNotNull(literal, original))
            val own = infos[literal]
            val info = own ?: original?.let { infos[it] }
            val ruby = word?.let { dictionary.furigana(it.form, it.reading) }
            return KanjiBlockState(
                literal = literal,
                info = info,
                variantOf = original.takeIf { own == null && info != null },
                strokeCount = own?.strokes ?: vg?.strokes?.size,
                strokes = vg?.strokes?.takeIf { it.isNotEmpty() },
                tree = vg?.tree,
                nodeInfo = infos,
                entries = dictionary.entriesWith(literal, ENTRY_COUNT),
                containedIn = dictionary.containedIn(literal),
                highlight = if (info != null && ruby != null) WordKanjiList.highlight(literal, ruby, info.on, info.kun) else ReadingHighlight.NONE,
            )
        }
    }
}

/** Kanji blocks stacked vertically, divided by rules: the kanji display container. */
@Composable
fun KanjiBlocks(
    blocks: List<KanjiBlockState>,
    onOpenKanji: (String, String?) -> Unit,
    onOpenEntry: (SearchResult) -> Unit,
    onSearch: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        blocks.forEachIndexed { i, block ->
            if (i > 0) HorizontalDivider()
            KanjiBlock(block, onOpenKanji, onOpenEntry, onSearch)
        }
    }
}

/**
 * A kanji block: header row, practice area, component breakdown, entries and
 * Contained in. Taps open kanji pages and entries; "See more" searches for the kanji.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KanjiBlock(
    state: KanjiBlockState,
    onOpenKanji: (String, String?) -> Unit,
    onOpenEntry: (SearchResult) -> Unit,
    onSearch: (String) -> Unit,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(
                    Modifier.size(104.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(state.literal, style = MaterialTheme.typography.displayLarge, fontSize = 72.sp)
                }
                val info = state.info
                if (info != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Readings(info.on, state.highlight.on)
                        Readings(info.kun, state.highlight.kun)
                        if (info.meanings.isNotEmpty()) Text(info.meanings.joinToString(", "), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            val stats = listOfNotNull(
                state.strokeCount?.let { if (it == 1) "1 stroke" else "$it strokes" },
                // A variant's borrowed data lends meanings and readings, not its levels.
                state.info?.takeIf { state.variantOf == null }?.jlpt?.let { "N$it" },
                state.info?.takeIf { state.variantOf == null }?.grade?.let(::gradeLabel),
            )
            if (stats.isNotEmpty()) Text(stats.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = muted)
            state.variantOf?.let { original ->
                Text(
                    "Variant of $original",
                    Modifier.clickable { onOpenKanji(original, null) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            state.strokes?.let { strokes ->
                FilledTonalButton(onClick = { state.practiceOpen = !state.practiceOpen }) {
                    Text("Practice")
                    Icon(
                        if (state.practiceOpen) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        Modifier.padding(start = 4.dp),
                    )
                }
                if (state.practiceOpen) PracticeCanvas(strokes, Modifier.fillMaxWidth())
            }
        }

        val components = state.tree?.c.orEmpty()
        if (components.isNotEmpty()) {
            Section("Components") {
                Column { for (node in components) ComponentNode(node, 0, state.nodeInfo, onOpenKanji) }
            }
        }

        if (state.entries.isNotEmpty()) {
            Section("Entries") {
                Column {
                    for (result in state.entries) {
                        ResultRowContent(
                            result,
                            expanded = result.id in state.expandedEntries,
                            onToggle = { state.expandedEntries = state.expandedEntries.toggle(result.id) },
                            Modifier.clickable { onOpenEntry(result) },
                            horizontalPadding = 0.dp,
                        )
                    }
                }
                TextButton(onClick = { onSearch("*${state.literal}*") }) { Text("See more") }
            }
        }

        if (state.containedIn.isNotEmpty()) {
            Section("Contained in") {
                val shown = if (state.showAllContainedIn) state.containedIn else state.containedIn.take(CONTAINED_IN_BATCH)
                FlowRow {
                    for (k in shown) {
                        Text(
                            k,
                            Modifier.clickable { onOpenKanji(k, null) }.padding(horizontal = 6.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.headlineMedium,
                        )
                    }
                }
                if (shown.size < state.containedIn.size) {
                    TextButton(onClick = { state.showAllContainedIn = true }) {
                        Text("More (${state.containedIn.size - shown.size})")
                    }
                }
            }
        }
    }
}

/** A line of readings in KANJIDIC2 order, with the word's own readings highlighted. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Readings(readings: List<String>, highlighted: Set<Int>) {
    if (readings.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        readings.forEachIndexed { i, reading ->
            val on = i in highlighted
            Text(
                reading,
                Modifier
                    .background(if (on) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp),
                style = MaterialTheme.typography.titleMedium,
                color = if (on) MaterialTheme.colorScheme.onPrimaryContainer else Color.Unspecified,
            )
        }
    }
}

/** A component and, indented below it, its own components. */
@Composable
private fun ComponentNode(node: KanjiNode, depth: Int, infos: Map<String, KanjiInfo>, onOpenKanji: (String, String?) -> Unit) {
    val info = infos[node.e] ?: node.o?.let { infos[it] }
    Row(
        Modifier.fillMaxWidth().clickable { onOpenKanji(node.e, node.o) }.padding(start = (depth * 28).dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(node.e, Modifier.width(48.dp), style = MaterialTheme.typography.headlineMedium)
        Column {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (info != null && info.meanings.isNotEmpty()) {
                    Text(info.meanings.take(3).joinToString(", "), style = MaterialTheme.typography.bodyLarge)
                }
                if (node.phon) {
                    Text(
                        "phonetic",
                        Modifier.background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
            val readings = info?.let { it.on.take(3) + it.kun.take(3) }.orEmpty()
            if (readings.isNotEmpty()) {
                Text(readings.joinToString("、"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    for (child in node.c) ComponentNode(child, depth + 1, infos, onOpenKanji)
}

/** KANJIDIC2's school grade in words (UI spec §5). */
private fun gradeLabel(grade: Int): String? = when (grade) {
    in 1..6 -> "Grade $grade"
    8 -> "Jōyō"
    9, 10 -> "Jinmeiyō"
    else -> null
}
