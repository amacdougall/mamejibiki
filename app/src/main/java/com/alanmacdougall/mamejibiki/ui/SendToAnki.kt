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
 * Send to AnkiDroid with its runtime permission: the first send asks for
 * AnkiDroid's database permission, then sends if it's granted.
 */
@Composable
fun rememberSendToAnki(model: AppModel): (SearchResult) -> Unit {
    var pending by remember { mutableStateOf<SearchResult?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val result = pending
        pending = null
        if (!granted) model.notify("Mamejibiki needs AnkiDroid's permission to add notes")
        else if (result != null) model.sendToAnki(result)
    }
    return { result ->
        when {
            !model.anki.isInstalled() -> model.notify("AnkiDroid isn't installed")
            model.anki.hasPermission() -> model.sendToAnki(result)
            else -> {
                pending = result
                permission.launch(AnkiDroid.PERMISSION)
            }
        }
    }
}
