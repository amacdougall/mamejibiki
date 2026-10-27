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
import com.alanmacdougall.mamejibiki.conjugation.Conjugation
import com.alanmacdougall.mamejibiki.conjugation.FormsRow
import com.alanmacdougall.mamejibiki.data.Dictionary
import com.alanmacdougall.mamejibiki.data.Entry
import com.alanmacdougall.mamejibiki.data.FormRef
import com.alanmacdougall.mamejibiki.pitch.Accent
import com.alanmacdougall.mamejibiki.kanji.WordKanji
import com.alanmacdougall.mamejibiki.kanji.WordKanjiList
import com.alanmacdougall.mamejibiki.search.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class EntryTab(val title: String) { WORD("Word"), KANJI("Kanji"), FORMS("Forms") }

/** An entry page history entry: the entry, its headword, the selected tab and each tab's scroll position. */
class EntryState(result: SearchResult) : Screen {
    val entry: Entry = result.entry
    val headword: FormRef = entry.headword(result.match?.form)
    /** The Kanji tab's kanji, in order (UI spec §5). */
    val wordKanji: List<WordKanji> = WordKanjiList.list(entry, headword, result.match?.form)
    /** One conjugation table per conjugating POS class, spelled with the headword (UI spec §6). */
    val formsTables: List<FormsTable> = entry.sense.flatMap { it.partOfSpeech }.distinct().mapNotNull { pos ->
        Conjugation.table(entry.text(headword), pos)?.let { FormsTable(pos, it) }
    }
    val tabs: List<EntryTab> = buildList {
        add(EntryTab.WORD)
        if (wordKanji.isNotEmpty()) add(EntryTab.KANJI)
        if (formsTables.isNotEmpty()) add(EntryTab.FORMS)
    }
    var tab by mutableStateOf(EntryTab.WORD)
    val scroll: Map<EntryTab, ScrollState> = tabs.associateWith { ScrollState(0) }
    /** Each reading's pitch accent patterns, looked up for this headword; null until loaded. */
    var pitch by mutableStateOf<List<List<Accent>>?>(null)
    /** The Kanji tab's blocks; null until the tab is first shown. */
    var kanjiBlocks by mutableStateOf<List<KanjiBlockState>?>(null)
}

data class FormsTable(val pos: String, val rows: List<FormsRow>)

@Composable
fun EntryScreen(
    state: EntryState,
    dictionary: Dictionary?,
    onBack: () -> Unit,
    onSearch: (String) -> Unit,
    onOpenKanji: (String, String?) -> Unit,
    onOpenEntry: (SearchResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(dictionary) {
        val d = dictionary ?: return@LaunchedEffect
        if (state.pitch == null) state.pitch = withContext(Dispatchers.IO) { d.readingPitches(state.entry, state.headword) }
    }
    LaunchedEffect(dictionary, state.tab) {
        val d = dictionary ?: return@LaunchedEffect
        if (state.tab == EntryTab.KANJI && state.kanjiBlocks == null) {
            state.kanjiBlocks = withContext(Dispatchers.IO) { state.wordKanji.map { KanjiBlockState.load(d, it.literal, word = it) } }
        }
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
                EntryTab.KANJI -> state.kanjiBlocks?.let { KanjiBlocks(it, onOpenKanji, onOpenEntry, onSearch) }
                EntryTab.FORMS -> if (dictionary != null) FormsTab(state, dictionary)
            }
        }
    }
}
