package com.alanmacdougall.mamejibiki.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alanmacdougall.mamejibiki.data.Dictionary
import com.alanmacdougall.mamejibiki.data.Entry
import com.alanmacdougall.mamejibiki.data.FormRef
import com.alanmacdougall.mamejibiki.pitch.Accent
import com.alanmacdougall.mamejibiki.search.Kana
import com.alanmacdougall.mamejibiki.search.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class EntryTab(val title: String) { WORD("Word"), KANJI("Kanji"), FORMS("Forms") }

/** An entry page history entry: the entry, its headword, the selected tab and each tab's scroll position. */
class EntryState(result: SearchResult) : Screen {
    val entry: Entry = result.entry
    val headword: FormRef = entry.headword(result.match?.form)
    val tabs: List<EntryTab> = buildList {
        add(EntryTab.WORD)
        if (entry.kanji.any { form -> form.text.any { Kana.isKanji(it) } }) add(EntryTab.KANJI)
        if (entry.sense.any { s -> s.partOfSpeech.any { it in CONJUGATING_POS || it.startsWith("v5") } }) add(EntryTab.FORMS)
    }
    var tab by mutableStateOf(EntryTab.WORD)
    val scroll: Map<EntryTab, ScrollState> = tabs.associateWith { ScrollState(0) }
    /** Each reading's pitch accent patterns, looked up for this headword; null until loaded. */
    var pitch by mutableStateOf<List<List<Accent>>?>(null)
}

/** POS codes the Forms tab conjugates (UI spec §6), besides the v5 godan family. */
private val CONJUGATING_POS = setOf("v1", "vk", "vs", "vs-i", "vs-s", "adj-i", "adj-ix")

@Composable
fun EntryScreen(
    state: EntryState,
    dictionary: Dictionary?,
    onBack: () -> Unit,
    onSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(dictionary) {
        val d = dictionary ?: return@LaunchedEffect
        if (state.pitch == null) state.pitch = withContext(Dispatchers.IO) { d.readingPitches(state.entry, state.headword) }
    }

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text(
                state.entry.text(state.headword),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (state.tabs.size > 1) {
            PrimaryTabRow(selectedTabIndex = state.tabs.indexOf(state.tab)) {
                state.tabs.forEach { tab ->
                    Tab(selected = tab == state.tab, onClick = { state.tab = tab }, text = { Text(tab.title) })
                }
            }
        }
        Box(Modifier.fillMaxSize().verticalScroll(state.scroll.getValue(state.tab))) {
            when (state.tab) {
                EntryTab.WORD -> if (dictionary != null) WordTab(state, dictionary, onSearch)
                EntryTab.KANJI -> Placeholder("Kanji details are coming soon.")
                EntryTab.FORMS -> Placeholder("The conjugation table is coming soon.")
            }
        }
    }
}

@Composable
private fun Placeholder(text: String) {
    Text(text, Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
}
