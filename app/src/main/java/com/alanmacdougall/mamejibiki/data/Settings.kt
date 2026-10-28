package com.alanmacdougall.mamejibiki.data

import android.content.Context

/** The user's settings (UI spec §1 Settings), kept in SharedPreferences. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Names of the AnkiDroid decks the long-press menu offers to send to. */
    var ankiDecks: Set<String>
        get() = prefs.getStringSet(ANKI_DECKS, null)?.toSet() ?: setOf(DEFAULT_DECK)
        set(value) = prefs.edit().putStringSet(ANKI_DECKS, value).apply()

    private companion object {
        const val ANKI_DECKS = "ankiDecks"
        /** Enabled until the user chooses decks. */
        const val DEFAULT_DECK = "色んな言葉"
    }
}
