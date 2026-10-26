package com.alanmacdougall.mamejibiki.ui

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alanmacdougall.mamejibiki.data.Dictionary
import com.alanmacdougall.mamejibiki.search.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A history entry. Each screen keeps its own state object, so returning to it restores it exactly. */
sealed interface Screen

/**
 * The app-wide back stack (UI spec §1), kept in a ViewModel so it survives
 * configuration changes such as a dark-mode switch.
 */
class AppModel(application: Application) : AndroidViewModel(application) {
    var dictionary by mutableStateOf<Dictionary?>(null)
        private set

    val stack = mutableStateListOf<Screen>(SearchState(""))

    init {
        // Open on IO but publish on the main thread, so the state write can't
        // race a composition that hasn't been applied yet.
        viewModelScope.launch { dictionary = withContext(Dispatchers.IO) { Dictionary.open(application) } }
    }

    fun push(screen: Screen) {
        stack += screen
    }

    fun pop() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    fun openEntry(result: SearchResult) = push(EntryState(result))

    fun search(text: String) = push(SearchState(text))
}

@Composable
fun App(model: AppModel) {
    BackHandler(enabled = model.stack.size > 1) { model.pop() }
    Scaffold { padding ->
        Box(Modifier.padding(padding).imePadding()) {
            when (val screen = model.stack.last()) {
                is SearchState -> key(screen) { SearchScreen(screen, model.dictionary, onOpen = model::openEntry) }
                is EntryState -> key(screen) {
                    EntryScreen(screen, model.dictionary, onBack = model::pop, onSearch = model::search)
                }
            }
        }
    }
}
