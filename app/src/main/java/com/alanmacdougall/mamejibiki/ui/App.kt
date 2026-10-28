package com.alanmacdougall.mamejibiki.ui

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alanmacdougall.mamejibiki.anki.AnkiDroid
import com.alanmacdougall.mamejibiki.anki.AnkiIndex
import com.alanmacdougall.mamejibiki.anki.SendResult
import com.alanmacdougall.mamejibiki.data.Dictionary
import com.alanmacdougall.mamejibiki.search.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

    val snackbar = SnackbarHostState()
    val anki = AnkiDroid(application)
    /** Every Kaishi note in AnkiDroid, for the long-press menu's "Exists in AnkiDroid". Null until loaded or without permission. */
    var ankiIndex by mutableStateOf<AnkiIndex?>(null)
        private set

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

    fun openKanji(literal: String, original: String?) = push(KanjiState(literal, original))

    /** Shows a message in the snackbar, replacing any message already showing. */
    fun notify(message: String) {
        viewModelScope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(message)
        }
    }

    /**
     * Reloads [ankiIndex] when AnkiDroid's permission is granted. Called each
     * time the app comes to the foreground, since notes may have been added or
     * deleted in AnkiDroid meanwhile.
     */
    fun refreshAnki() {
        if (ankiLoad?.isActive == true || !anki.isInstalled() || !anki.hasPermission()) return
        ankiLoad = viewModelScope.launch {
            val index = withContext(Dispatchers.IO) { runCatching { anki.index() }.getOrNull() }
            if (index != null) ankiIndex = index
        }
    }

    private var ankiLoad: Job? = null

    /** Whether a note for this result's word is already in AnkiDroid, as far as [ankiIndex] knows. */
    fun inAnki(result: SearchResult): Boolean = ankiIndex?.let { AnkiIndex.key(result.entry, result.match?.form) in it } ?: false

    /** Sends a result's entry to AnkiDroid as a Kaishi 1.5k note (UI spec §8). Needs AnkiDroid's permission. */
    fun sendToAnki(result: SearchResult) {
        val d = dictionary ?: return
        viewModelScope.launch {
            val key = AnkiIndex.key(result.entry, result.match?.form)
            val note = withContext(Dispatchers.IO) { d.kaishiNote(result.entry, result.match?.form) }
            val sent = withContext(Dispatchers.IO) { anki.send(note, key, ankiIndex) }
            if (sent !is SendResult.Failed) {
                val index = ankiIndex
                if (index != null) ankiIndex = index.plus(note.word, note.reading) else refreshAnki()
            }
            snackbar.currentSnackbarData?.dismiss()
            when (sent) {
                is SendResult.Added -> snackbar.showSnackbar("Added ${sent.word} to ${AnkiDroid.DECK}")
                is SendResult.Duplicate -> snackbar.showSnackbar("${sent.word} is already in Anki")
                is SendResult.Failed -> snackbar.showSnackbar(sent.message)
            }
        }
    }
}

@Composable
fun App(model: AppModel) {
    BackHandler(enabled = model.stack.size > 1) { model.pop() }
    Scaffold(snackbarHost = { SnackbarHost(model.snackbar, Modifier.imePadding()) }) { padding ->
        Box(Modifier.padding(padding).imePadding()) {
            when (val screen = model.stack.last()) {
                is SearchState -> key(screen) {
                    SearchScreen(
                        screen,
                        model.dictionary,
                        onOpen = model::openEntry,
                        onSend = rememberSendToAnki(model),
                        inAnki = model::inAnki,
                    )
                }
                is EntryState -> key(screen) {
                    EntryScreen(screen, model.dictionary, onBack = model::pop, onSearch = model::search, onOpenKanji = model::openKanji, onOpenEntry = model::openEntry)
                }
                is KanjiState -> key(screen) {
                    KanjiScreen(
                        screen,
                        model.dictionary,
                        onBack = model::pop,
                        onOpenKanji = model::openKanji,
                        onOpenEntry = model::openEntry,
                        onSearch = model::search,
                    )
                }
            }
        }
    }
}
