package com.alanmacdougall.mamejibiki.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alanmacdougall.mamejibiki.data.Dictionary
import com.alanmacdougall.mamejibiki.search.FormMatch
import com.alanmacdougall.mamejibiki.search.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One search history entry. The field's text includes uncommitted IME
 * composition, so results follow the user's typing before kanji conversion.
 */
class SearchState(initialQuery: String) : Screen {
    var field by mutableStateOf(TextFieldValue(initialQuery, TextRange(initialQuery.length)))
    var results by mutableStateOf<List<SearchResult>>(emptyList())
    /** The query [results] belong to, so returning to this screen doesn't search again. */
    var resultsQuery: String? = null
    val listState = LazyListState()
    /** Entry ids of rows whose senses are expanded. Cleared by each new search. */
    var expanded by mutableStateOf(emptySet<Long>())
    /**
     * A search pushed by a See also link first shows its results with the
     * keyboard down. Every other time the screen is shown, the field is focused.
     */
    var focusOnShow = initialQuery.isEmpty()
}

@Composable
fun SearchScreen(
    state: SearchState,
    dictionary: Dictionary?,
    onOpen: (SearchResult) -> Unit,
    onSend: (SearchResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    val field = state.field
    val results = state.results

    LaunchedEffect(Unit) {
        if (state.focusOnShow) {
            state.field = state.field.copy(selection = TextRange(state.field.text.length))
            focus.requestFocus()
        }
        state.focusOnShow = true
    }

    LaunchedEffect(dictionary, field.text) {
        val d = dictionary ?: return@LaunchedEffect
        val query = field.text
        if (query == state.resultsQuery) return@LaunchedEffect
        val started = System.nanoTime()
        state.results = withContext(Dispatchers.IO) { d.search(query) }
        state.resultsQuery = query
        state.expanded = emptySet()
        Log.i("Search", "'$query' → ${state.results.size} results in ${(System.nanoTime() - started) / 1_000_000} ms")
        state.listState.scrollToItem(0)
    }

    Column(modifier.fillMaxSize()) {
        OutlinedTextField(
            value = field,
            onValueChange = { state.field = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .focusRequester(focus),
            placeholder = { Text("Search Japanese or English") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (field.text.isEmpty()) {
                    IconButton(onClick = {
                        val pasted = clipboardText(context)
                        if (pasted.isNotBlank()) state.field = TextFieldValue(pasted, TextRange(pasted.length))
                    }) { Icon(PasteIcon, contentDescription = "Paste") }
                } else {
                    IconButton(onClick = { state.field = TextFieldValue("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            shape = RoundedCornerShape(28.dp),
        )

        when {
            dictionary == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text("Preparing dictionary…", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
            field.text.isBlank() -> EmptyHelp()
            results.isEmpty() -> Text(
                "No results",
                Modifier.padding(24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> LazyColumn(state = state.listState, modifier = Modifier.fillMaxSize()) {
                items(results, key = { it.id }) { result ->
                    ResultRow(
                        result,
                        expanded = result.id in state.expanded,
                        onToggle = { state.expanded = state.expanded.toggle(result.id) },
                        onClick = {
                            keyboard?.hide()
                            onOpen(result)
                        },
                        onCopy = { copyText(context, result.entry.text(result.entry.headword(result.match?.form))) },
                        onSend = { onSend(result) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun EmptyHelp() {
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val muted = MaterialTheme.colorScheme.onSurfaceVariant
        Text("Search by kanji, kana or English.", color = muted)
        Text("? matches any one character: 日?, ??語", color = muted)
        Text("* matches any run of characters: *食 finds words ending in 食, *食* words containing it.", color = muted)
        Text("Words starting with your input are included, and small slips in long vowels or voicing (ゆき → 勇気) are tolerated.", color = muted)
        Text("Conjugated words find their dictionary form: 食べられない → 食べる.", color = muted)
    }
}

/** A result row. Tap opens the entry; long-press shows Copy and Send to AnkiDroid (UI spec §2). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ResultRow(
    result: SearchResult,
    expanded: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    onCopy: () -> Unit,
    onSend: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        ResultRowContent(
            result,
            expanded,
            onToggle,
            Modifier.combinedClickable(onClick = onClick, onLongClick = { menu = true }),
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, offset = DpOffset(16.dp, 0.dp)) {
            DropdownMenuItem(text = { Text("Copy") }, onClick = {
                menu = false
                onCopy()
            })
            DropdownMenuItem(text = { Text("Send to AnkiDroid") }, onClick = {
                menu = false
                onSend()
            })
        }
    }
}

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
    val forms = result.entry.kanji.mapIndexed { i, k -> Triple(0, i, k.text) to k.isRare } +
        result.entry.kana.mapIndexed { i, r -> Triple(1, i, r.text) to r.isRare }
    val pitch = ArrayList<PitchLine>()
    val text = buildAnnotatedString {
        forms.forEachIndexed { n, (form, isRare) ->
            if (n > 0) withStyle(SpanStyle(color = rare)) { append(", ") }
            val (kind, index, text) = form
            result.pitch.getOrNull(index)?.firstOrNull()?.takeIf { kind == 1 }?.let { accent ->
                pitch += pitchLines(text, accent, offset = length, color = if (isRare) rare else Color.Unspecified)
            }
            val match: FormMatch? = result.match?.takeIf { it.formKind == kind && it.formIndex == index }
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

private fun copyText(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(text, text))
}

private fun clipboardText(context: Context): String {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    val clip = clipboard.primaryClip ?: return ""
    if (clip.itemCount == 0) return ""
    return clip.getItemAt(0).coerceToText(context).toString().trim()
}
