package com.alanmacdougall.mamejibiki.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.alanmacdougall.mamejibiki.data.Dictionary
import com.alanmacdougall.mamejibiki.data.FormRef
import com.alanmacdougall.mamejibiki.data.Sense
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/** The Word tab (UI spec §4): written forms, readings with pitch accent, and senses. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordTab(state: EntryState, dictionary: Dictionary, onSearch: (String) -> Unit) {
    val entry = state.entry
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        // Written forms: the headword, then the others.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(entry.text(state.headword), style = MaterialTheme.typography.displaySmall)
            val others = entry.kanji.indices.filter { FormRef(FormRef.KANJI, it) != state.headword }
            if (others.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (i in others) {
                        val form = entry.kanji[i]
                        FormWithTags(form.text, form.tags, form.isRare, dictionary) { color ->
                            Text(form.text, style = MaterialTheme.typography.headlineMedium, color = color)
                        }
                    }
                }
            }
        }

        // Readings, each with all its pitch accent patterns.
        Section("Readings") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                entry.kana.forEachIndexed { i, reading ->
                    FormWithTags(reading.text, reading.tags, reading.isRare, dictionary) { color ->
                        val accents = state.pitch?.getOrNull(i).orEmpty()
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            if (accents.isEmpty()) {
                                Text(reading.text, style = MaterialTheme.typography.headlineSmall, color = color)
                            }
                            for (accent in accents) {
                                PitchText(
                                    AnnotatedString(reading.text),
                                    pitchLines(reading.text, accent),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = color,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        // Meaning: senses grouped by part of speech, each group headed by its
        // shared grammar labels. Senses keep their JMdict numbers, so a gap
        // (1, 2, 4) shows that sense 3 is in a later group.
        Section("Meaning") {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                for (group in entry.sense.indices.groupBy { entry.sense[it].partOfSpeech.toSet() }.values) {
                    val senses = group.map { entry.sense[it] }
                    val shared = sharedCodes(senses)
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (shared.isNotEmpty()) {
                            Text(
                                dictionary.labels(shared).joinToString("・"),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        for (i in group) {
                            Row {
                                if (entry.sense.size > 1) {
                                    Text("${i + 1}.", Modifier.width(28.dp), style = MaterialTheme.typography.bodyLarge, color = muted)
                                }
                                SenseBody(entry.sense[i], shared, dictionary, onSearch)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

/** A form, greyed if rare, with its form tags spelled out beneath. */
@Composable
private fun FormWithTags(
    text: String,
    tags: List<String>,
    isRare: Boolean,
    dictionary: Dictionary,
    content: @Composable (Color) -> Unit,
) {
    val color = if (isRare) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else Color.Unspecified
    Column {
        content(color)
        if (tags.isNotEmpty()) {
            Text(
                tags.joinToString(", ") { dictionary.tags[it]?.description ?: it },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SenseBody(sense: Sense, shared: List<String>, dictionary: Dictionary, onSearch: (String) -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val own = sense.misc.filter { it !in shared } + sense.field + sense.dialect
        if (own.isNotEmpty()) {
            Text(dictionary.labels(own).joinToString("・"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
        }
        Text(sense.gloss.joinToString(", "), style = MaterialTheme.typography.bodyLarge)
        if (sense.info.isNotEmpty()) {
            Text(sense.info.joinToString("; "), style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = muted)
        }
        for ((label, refs) in listOf("See also" to sense.related, "Antonym" to sense.antonym)) {
            if (refs.isEmpty()) continue
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("$label:", style = MaterialTheme.typography.bodyMedium, color = muted)
                for (ref in refs) {
                    val (display, query) = crossRef(ref) ?: continue
                    Text(
                        display,
                        Modifier.clickable { onSearch(query) },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/** POS and misc codes present in every one of [senses], in the first sense's order. */
private fun sharedCodes(senses: List<Sense>): List<String> {
    val first = senses.firstOrNull() ?: return emptyList()
    return (first.partOfSpeech + first.misc).filter { code -> senses.all { code in it.partOfSpeech || code in it.misc } }
}

/**
 * A jmdict-simplified cross-reference, `[kanji, kana?, senseIndex?]` or
 * `[kana, senseIndex?]`, as display text and the text to search for.
 */
private fun crossRef(ref: List<JsonElement>): Pair<String, String>? {
    val parts = ref.filterIsInstance<JsonPrimitive>()
    val words = parts.filter { it.isString }.map { it.content }
    val senseNumber = parts.firstOrNull { !it.isString }?.content
    val query = words.firstOrNull() ?: return null
    return (words.joinToString("・") + (senseNumber?.let { " ($it)" } ?: "")) to query
}
