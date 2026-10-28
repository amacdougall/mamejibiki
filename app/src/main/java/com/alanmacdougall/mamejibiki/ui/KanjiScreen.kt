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
 * A kanji page history entry: a block for each of [kanji], with no word
 * context, under [title]. Each kanji is paired with the character it's a
 * variant of, if known.
 */
class KanjiState private constructor(val title: String, val kanji: List<Pair<String, String?>>) : Screen {
    val scroll = ScrollState(0)
    /** null until loaded. */
    var blocks by mutableStateOf<List<KanjiBlockState>?>(null)

    companion object {
        /** One kanji's page (UI spec §5), opened from a component or a Contained in kanji. */
        fun single(literal: String, original: String?) = KanjiState(literal, listOf(literal to original))

        /**
         * The kanji comparison page for a search with no results (UI spec §2):
         * the typed [text] as the title, and each of its kanji once, left to right.
         */
        fun comparing(text: String) = KanjiState(text, WordKanjiList.kanjiIn(text).distinct().map { it to null })
    }
}

@Composable
fun KanjiScreen(
    state: KanjiState,
    dictionary: Dictionary?,
    onBack: () -> Unit,
    onOpenKanji: (String, String?) -> Unit,
    onOpenEntry: (SearchResult) -> Unit,
    onSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(dictionary) {
        val d = dictionary ?: return@LaunchedEffect
        if (state.blocks == null) {
            state.blocks = withContext(Dispatchers.IO) { state.kanji.map { (literal, original) -> KanjiBlockState.load(d, literal, original) } }
        }
    }

    Page(state.title, onBack, state.scroll, modifier) {
        state.blocks?.let { KanjiBlocks(it, onOpenKanji, onOpenEntry, onSearch) }
    }
}
