package com.alanmacdougall.mamejibiki.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.alanmacdougall.mamejibiki.data.Dictionary
import com.alanmacdougall.mamejibiki.search.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A kanji page history entry (UI spec §5): one kanji, with no word context. [original] is the character it's a variant of, if known. */
class KanjiState(val literal: String, val original: String?) : Screen {
    var block by mutableStateOf<KanjiBlockState?>(null)
    val scroll = ScrollState(0)
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
        if (state.block == null) state.block = withContext(Dispatchers.IO) { KanjiBlockState.load(d, state.literal, state.original) }
    }

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text(state.literal, style = MaterialTheme.typography.titleLarge)
        }
        Box(Modifier.fillMaxSize().verticalScroll(state.scroll)) {
            state.block?.let { KanjiBlocks(listOf(it), onOpenKanji, onOpenEntry, onSearch) }
        }
    }
}
