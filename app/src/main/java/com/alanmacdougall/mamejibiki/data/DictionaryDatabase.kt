package com.alanmacdougall.mamejibiki.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * The dictionary built by pipeline/. SQLite can't open a file compressed
 * inside the APK, so the asset is copied into app storage on first launch and
 * again whenever the pipeline's version stamp (assets/mamejibiki.version)
 * changes.
 */
object DictionaryDatabase {
    private const val ASSET = "mamejibiki.sqlite"
    private const val VERSION_ASSET = "mamejibiki.version"

    @Volatile private var db: SQLiteDatabase? = null

    /** Opens the database, copying it out of the APK first if needed. Call off the main thread. */
    @Synchronized
    fun open(context: Context): SQLiteDatabase {
        db?.let { return it }
        val file = context.getDatabasePath(ASSET)
        val stamp = File(file.path + ".version")
        val bundled = context.assets.open(VERSION_ASSET).bufferedReader().use { it.readText() }
        if (!file.exists() || !stamp.exists() || stamp.readText() != bundled) {
            copyAsset(context, file)
            stamp.writeText(bundled)
        }
        return SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).also { db = it }
    }

    private fun copyAsset(context: Context, target: File) {
        target.parentFile?.mkdirs()
        val temp = File(target.path + ".tmp")
        context.assets.open(ASSET).use { input -> temp.outputStream().use { input.copyTo(it, 1 shl 16) } }
        if (!temp.renameTo(target)) error("Could not install dictionary at $target")
    }
}
