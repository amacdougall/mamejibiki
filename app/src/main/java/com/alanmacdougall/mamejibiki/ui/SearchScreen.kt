package com.alanmacdougall.mamejibiki.ui

import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.alanmacdougall.mamejibiki.data.DictionaryDatabase
import com.alanmacdougall.mamejibiki.search.FormMatch
import com.alanmacdougall.mamejibiki.search.Search
import com.alanmacdougall.mamejibiki.search.SearchResult
import com.alanmacdougall.mamejibiki.search.Tier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SearchScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var search by remember { mutableStateOf<Search?>(null) }
    // The field's text includes uncommitted IME composition, so results
    // follow the user's typing before kanji conversion.
    var field by remember { mutableStateOf(TextFieldValue("")) }
    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    val focus = remember { FocusRequester() }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        focus.requestFocus()
        search = withContext(Dispatchers.IO) { Search(DictionaryDatabase.open(context.applicationContext)) }
    }

    LaunchedEffect(search, field.text) {
        val s = search ?: return@LaunchedEffect
        val query = field.text
        val started = System.nanoTime()
        results = withContext(Dispatchers.IO) { s.run(query) }
        Log.i("Search", "'$query' → ${results.size} results in ${(System.nanoTime() - started) / 1_000_000} ms")
        listState.scrollToItem(0)
    }

    Column(modifier.fillMaxSize()) {
        OutlinedTextField(
            value = field,
            onValueChange = { field = it },
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
                        if (pasted.isNotBlank()) field = TextFieldValue(pasted, TextRange(pasted.length))
                    }) { Icon(PasteIcon, contentDescription = "Paste") }
                } else {
                    IconButton(onClick = { field = TextFieldValue("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            shape = RoundedCornerShape(28.dp),
        )

        when {
            search == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
            else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(results, key = { it.id }) { result ->
                    ResultRow(result)
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
        Text("Words starting with your input are included, and small slips in long vowels or voicing (ゆき → 勇気) are tolerated.", color = muted)
    }
}

@Composable
private fun ResultRow(result: SearchResult) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                formsLine(result),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (result.entry.kanji.any { it.common } || result.entry.kana.any { it.common }) Badge("common")
            if (result.entry.sense.any { "vs" in it.partOfSpeech }) Badge("する")
        }
        Text(
            glossLine(result),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

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

/** All written forms then all readings; the matched one highlighted, rare ones greyed. */
@Composable
private fun formsLine(result: SearchResult): AnnotatedString {
    val accent = MaterialTheme.colorScheme.primary
    val accentRemainder = accent.copy(alpha = 0.6f)
    val rare = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val forms = result.entry.kanji.mapIndexed { i, k -> Triple(0, i, k.text) to k.isRare } +
        result.entry.kana.mapIndexed { i, r -> Triple(1, i, r.text) to r.isRare }
    return buildAnnotatedString {
        forms.forEachIndexed { n, (form, isRare) ->
            if (n > 0) withStyle(SpanStyle(color = rare)) { append(", ") }
            val (kind, index, text) = form
            val match: FormMatch? = result.match?.takeIf { it.formKind == kind && it.formIndex == index }
            when {
                match != null -> {
                    val length = (match.highlightLength ?: text.length).coerceAtMost(text.length)
                    withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) { append(text.take(length)) }
                    withStyle(SpanStyle(color = accentRemainder, fontWeight = FontWeight.SemiBold)) { append(text.drop(length)) }
                }
                isRare -> withStyle(SpanStyle(color = rare)) { append(text) }
                else -> append(text)
            }
        }
    }
}

private fun glossLine(result: SearchResult): String {
    val senses = result.entry.sense
    if (senses.size == 1) return senses[0].gloss.joinToString(", ")
    return senses.mapIndexed { i, s -> "${i + 1}. ${s.gloss.joinToString(", ")}" }.joinToString("  ")
}

private fun clipboardText(context: Context): String {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    val clip = clipboard.primaryClip ?: return ""
    if (clip.itemCount == 0) return ""
    return clip.getItemAt(0).coerceToText(context).toString().trim()
}
