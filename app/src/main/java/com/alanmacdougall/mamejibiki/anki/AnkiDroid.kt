package com.alanmacdougall.mamejibiki.anki

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.ichi2.anki.FlashCardsContract
import com.ichi2.anki.api.AddContentApi

/** What happened when a note was sent. */
sealed interface SendResult {
    data class Added(val word: String, val deck: String) : SendResult
    /** A Kaishi note for the same word already exists, anywhere in the collection ([AnkiIndex]). */
    data class Duplicate(val word: String) : SendResult
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

    /** AnkiDroid's deck names, sorted, or null when they can't be read. */
    fun decks(): List<String>? = runCatching { AddContentApi(context).deckList?.values?.sorted() }.getOrNull()

    /**
     * Adds [note] to [deck] unless a note matching [key] already exists,
     * checked against [known], or a freshly read index when that's null.
     */
    fun send(note: KaishiNote, key: AnkiKey, known: AnkiIndex?, deck: String): SendResult {
        val result = try {
            sendOrThrow(note, key, known, deck)
        } catch (e: Exception) {
            Log.i("Anki", "Send failed", e)
            SendResult.Failed("AnkiDroid error: ${e.message}")
        }
        Log.i("Anki", "${note.word} (${note.reading}) → $result")
        return result
    }

    private fun sendOrThrow(note: KaishiNote, key: AnkiKey, known: AnkiIndex?, deck: String): SendResult {
        if (!isInstalled()) return SendResult.Failed("AnkiDroid isn't installed")
        val api = AddContentApi(context)
        val modelId = api.modelList?.entries?.firstOrNull { it.value == Kaishi.NOTE_TYPE }?.key
            ?: return SendResult.Failed("AnkiDroid has no ${Kaishi.NOTE_TYPE} note type")
        if (api.getFieldList(modelId)?.toList() != Kaishi.FIELD_NAMES) {
            return SendResult.Failed("${Kaishi.NOTE_TYPE}'s fields aren't the expected 14")
        }
        val deckId = api.deckList?.entries?.firstOrNull { it.value == deck }?.key
            ?: return SendResult.Failed("AnkiDroid has no deck named $deck")
        if (key in (known ?: index())) return SendResult.Duplicate(note.word)
        api.addNote(modelId, deckId, note.fields.toTypedArray(), note.tags)
            ?: return SendResult.Failed("AnkiDroid didn't add the note")
        return SendResult.Added(note.word, deck)
    }

    /**
     * Every Kaishi note's Word and Word Reading, in any deck, like anki-scripts'
     * DedupChecker. Reads every Kaishi note: over a second for 9,000 notes.
     */
    fun index(): AnkiIndex {
        val start = System.currentTimeMillis()
        val notes = ArrayList<Pair<String, String>>()
        val projection = arrayOf(FlashCardsContract.Note.FLDS)
        context.contentResolver.query(FlashCardsContract.Note.CONTENT_URI, projection, "\"note:${Kaishi.NOTE_TYPE}\"", null, null)?.use { c ->
            while (c.moveToNext()) {
                val fields = c.getString(0).split('\u001f')
                notes += fields[Kaishi.WORD] to fields.getOrElse(Kaishi.WORD_READING) { "" }
            }
        }
        return AnkiIndex(notes).also { Log.i("Anki", "Indexed ${notes.size} notes in ${System.currentTimeMillis() - start} ms") }
    }

    companion object {
        const val PERMISSION = AddContentApi.READ_WRITE_PERMISSION
    }
}
