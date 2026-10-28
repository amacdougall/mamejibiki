package com.alanmacdougall.mamejibiki.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.alanmacdougall.mamejibiki.data.Dictionary
import com.alanmacdougall.mamejibiki.kanji.WordKanjiList
import com.alanmacdougall.mamejibiki.search.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The page for a search with no results (UI spec §2): the typed [text] in the
 * top bar and a block for each of its kanji, so similar kanji can be compared.
 */
class LiteralState(val text: String) : Screen {
    /** The kanji in [text], deduplicated, left to right. */
    val kanji: List<String> = WordKanjiList.kanjiIn(text).distinct()
    val scroll = ScrollState(0)
    /** null until loaded. */
    var kanjiBlocks by mutableStateOf<List<KanjiBlockState>?>(null)
}

@Composable
fun LiteralScreen(
    state: LiteralState,
    dictionary: Dictionary?,
    onBack: () -> Unit,
    onSearch: (String) -> Unit,
    onOpenKanji: (String, String?) -> Unit,
    onOpenEntry: (SearchResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(dictionary) {
        val d = dictionary ?: return@LaunchedEffect
        if (state.kanjiBlocks == null) state.kanjiBlocks = withContext(Dispatchers.IO) { state.kanji.map { KanjiBlockState.load(d, it) } }
    }

    EntryFrame(state.text, listOf(EntryTab.KANJI), EntryTab.KANJI, {}, state.scroll, onBack, modifier) {
        state.kanjiBlocks?.let { KanjiBlocks(it, onOpenKanji, onOpenEntry, onSearch) }
    }
}
