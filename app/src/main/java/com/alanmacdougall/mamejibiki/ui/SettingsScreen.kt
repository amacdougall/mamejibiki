package com.alanmacdougall.mamejibiki.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** The settings screen's history entry (UI spec §1 Settings). */
class SettingsState : Screen {
    val scroll = ScrollState(0)
}

/**
 * Settings: the AnkiDroid decks the long-press menu offers, each with a
 * checkbox. [decks] is AnkiDroid's deck list, null until loaded.
 */
@Composable
fun SettingsScreen(
    state: SettingsState,
    installed: Boolean,
    hasPermission: Boolean,
    decks: List<String>?,
    enabled: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    onRequestPermission: () -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Decks may have been added or renamed in AnkiDroid.
    LaunchedEffect(Unit) { onRefresh() }

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Settings", style = MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.fillMaxSize().verticalScroll(state.scroll).padding(vertical = 8.dp)) {
            Text(
                "Anki decks",
                Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            val muted = MaterialTheme.colorScheme.onSurfaceVariant
            Text(
                "Long-pressing a result offers to send it to each checked deck.",
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = muted,
            )
            when {
                !installed -> Text("AnkiDroid isn't installed.", Modifier.padding(16.dp), color = muted)
                !hasPermission -> Column(Modifier.padding(16.dp)) {
                    Text("Mamejibiki needs AnkiDroid's permission to list its decks.", color = muted)
                    FilledTonalButton(onClick = onRequestPermission, Modifier.padding(top = 12.dp)) { Text("Allow access to AnkiDroid") }
                }
                decks == null -> CircularProgressIndicator(Modifier.padding(16.dp))
                else -> for (deck in decks) {
                    val checked = deck in enabled
                    Row(
                        Modifier.fillMaxWidth().clickable { onToggle(deck, !checked) }.padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = { onToggle(deck, it) })
                        Text(deck, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}
