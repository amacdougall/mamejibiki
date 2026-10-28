package com.alanmacdougall.mamejibiki.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.alanmacdougall.mamejibiki.anki.AnkiDroid
import com.alanmacdougall.mamejibiki.search.SearchResult

/**
 * Send to a deck with AnkiDroid's runtime permission: the first send asks for
 * AnkiDroid's database permission, then sends if it's granted.
 */
@Composable
fun rememberSendToAnki(model: AppModel): (SearchResult, String) -> Unit {
    var pending by remember { mutableStateOf<Pair<SearchResult, String>?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val send = pending
        pending = null
        if (!granted) model.notify("Mamejibiki needs AnkiDroid's permission to add notes")
        else if (send != null) model.sendToAnki(send.first, send.second)
    }
    return { result, deck ->
        when {
            !model.anki.isInstalled() -> model.notify("AnkiDroid isn't installed")
            model.anki.hasPermission() -> model.sendToAnki(result, deck)
            else -> {
                pending = result to deck
                permission.launch(AnkiDroid.PERMISSION)
            }
        }
    }
}

/** Asks for AnkiDroid's permission (the Settings deck list needs it), then loads AnkiDroid's decks and notes. */
@Composable
fun rememberAnkiPermission(model: AppModel): () -> Unit {
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) model.refreshAnki() else model.notify("Mamejibiki needs AnkiDroid's permission to list decks")
    }
    return { permission.launch(AnkiDroid.PERMISSION) }
}
