package com.example.android_home_cal.vault

import java.io.File

/**
 * Parsed file contents keyed on (path, mtime, length), so the 10-minute poll costs a few
 * stat calls when nothing changed. In-memory only: a fresh process just re-parses.
 */
object FileParseCache {
    private class Entry(val mtime: Long, val length: Long, val value: Any?)

    private val entries = HashMap<String, Entry>()

    @Synchronized
    @Suppress("UNCHECKED_CAST")
    fun <T> get(kind: String, file: File, parse: (String) -> T): T? {
        val cacheKey = "$kind:${file.path}"
        if (!file.isFile) {
            entries.remove(cacheKey)
            return null
        }
        val mtime = file.lastModified()
        val length = file.length()
        entries[cacheKey]?.let { if (it.mtime == mtime && it.length == length) return it.value as T }
        val text = VaultFiles.readText(file) ?: return null
        val value = parse(text)
        entries[cacheKey] = Entry(mtime, length, value)
        return value
    }

    @Synchronized
    fun clear() = entries.clear()

    @Synchronized
    fun invalidate(file: File) {
        entries.keys.removeAll { it.endsWith(":${file.path}") }
    }
}
