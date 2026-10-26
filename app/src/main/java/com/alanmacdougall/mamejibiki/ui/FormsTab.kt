package com.alanmacdougall.mamejibiki.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.alanmacdougall.mamejibiki.data.Dictionary

/**
 * The Forms tab (UI spec §6): a conjugation table per conjugating class,
 * titled with the class's Japanese label. A cell with several forms lists
 * one per line.
 */
@Composable
fun FormsTab(state: EntryState, dictionary: Dictionary) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val divider = MaterialTheme.colorScheme.outlineVariant
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
        for (table in state.formsTables) {
            Column {
                Text(
                    dictionary.label(table.pos),
                    Modifier.padding(bottom = 8.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                TableRow("", listOf("Affirmative"), listOf("Negative"), muted, header = true)
                for (row in table.rows) {
                    HorizontalDivider(color = divider)
                    TableRow(row.label, row.affirmative, row.negative, muted, header = false)
                }
            }
        }
    }
}

@Composable
private fun TableRow(label: String, affirmative: List<String>, negative: List<String>, muted: Color, header: Boolean) {
    val cellStyle = if (header) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyLarge
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = muted)
        for (forms in listOf(affirmative, negative)) {
            Column(Modifier.weight(1f)) {
                if (forms.isEmpty()) Text("—", style = cellStyle, color = muted)
                for (form in forms) Text(form, style = cellStyle, color = if (header) muted else Color.Unspecified)
            }
        }
    }
}
