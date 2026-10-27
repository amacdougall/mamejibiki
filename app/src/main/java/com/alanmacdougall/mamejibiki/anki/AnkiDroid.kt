package com.alanmacdougall.mamejibiki.anki

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.ichi2.anki.FlashCardsContract
import com.ichi2.anki.api.AddContentApi

/** What happened when a note was sent. */
sealed interface SendResult {
    data class Added(val word: String) : SendResult
    /** A Kaishi note with the same (word, reading) already exists, anywhere in the collection. */
    data class Duplicate(val word: String, val noteId: Long) : SendResult
    data class Failed(val message: String) : SendResult
}

/**
 * Adds Kaishi 1.5k notes through AnkiDroid's ContentProvider (UI spec §8,
 * research doc §1). Methods block, so call them off the main thread.
 */
class AnkiDroid(private val context: Context) {
    fun isInstalled(): Boolean = AddContentApi.getAnkiDroidPackageName(context) != null

    fun hasPermission(): Boolean =
        context.checkSelfPermission(AddContentApi.READ_WRITE_PERMISSION) == PackageManager.PERMISSION_GRANTED

    fun send(note: KaishiNote): SendResult {
        val result = try {
            sendOrThrow(note)
        } catch (e: Exception) {
            Log.i("Anki", "Send failed", e)
            SendResult.Failed("AnkiDroid error: ${e.message}")
        }
        Log.i("Anki", "${note.word} (${note.reading}) → $result")
        return result
    }

    private fun sendOrThrow(note: KaishiNote): SendResult {
        if (!isInstalled()) return SendResult.Failed("AnkiDroid isn't installed")
        val api = AddContentApi(context)
        val modelId = api.modelList?.entries?.firstOrNull { it.value == Kaishi.NOTE_TYPE }?.key
            ?: return SendResult.Failed("AnkiDroid has no ${Kaishi.NOTE_TYPE} note type")
        if (api.getFieldList(modelId)?.toList() != Kaishi.FIELD_NAMES) {
            return SendResult.Failed("${Kaishi.NOTE_TYPE}'s fields aren't the expected 14")
        }
        val deckId = api.deckList?.entries?.firstOrNull { it.value == DECK }?.key
            ?: return SendResult.Failed("AnkiDroid has no deck named $DECK")
        duplicate(note)?.let { return SendResult.Duplicate(note.word, it) }
        api.addNote(modelId, deckId, note.fields.toTypedArray(), note.tags)
            ?: return SendResult.Failed("AnkiDroid didn't add the note")
        return SendResult.Added(note.word)
    }

    /**
     * The id of a Kaishi note with the same Word (ruby furigana stripped) and
     * Word Reading in any deck, like anki-scripts' DedupChecker.
     */
    private fun duplicate(note: KaishiNote): Long? {
        val search = "\"note:${Kaishi.NOTE_TYPE}\" \"Word Reading:${searchLiteral(note.reading)}\""
        val projection = arrayOf(FlashCardsContract.Note._ID, FlashCardsContract.Note.FLDS)
        context.contentResolver.query(FlashCardsContract.Note.CONTENT_URI, projection, search, null, null)?.use { c ->
            while (c.moveToNext()) {
                val fields = c.getString(1).split('\u001f')
                if (Kaishi.stripRuby(fields[Kaishi.WORD]) == note.word && fields.getOrNull(Kaishi.WORD_READING) == note.reading) {
                    return c.getLong(0)
                }
            }
        }
        return null
    }

    companion object {
        /** The deck notes are added to. Hardcoded until there's a settings screen. */
        const val DECK = "色んな言葉"
        const val PERMISSION = AddContentApi.READ_WRITE_PERMISSION

        /** Escapes Anki search wildcards and quotes. */
        private fun searchLiteral(text: String) = text.replace(Regex("""[\\"*_:]""")) { "\\" + it.value }
    }
}
