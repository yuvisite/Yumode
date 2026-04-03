package com.myapp.data.cache

import android.content.Context
import java.io.File
import java.security.MessageDigest

class DiskCacheStore(context: Context) {
    private val cacheDirectory = File(context.filesDir, "yumode_cache").apply {
        mkdirs()
    }

    fun readFresh(
        key: String,
        maxAgeMs: Long,
    ): String? {
        val file = fileFor(key)
        if (!file.exists()) {
            return null
        }
        val ageMs = System.currentTimeMillis() - file.lastModified()
        if (ageMs > maxAgeMs) {
            return null
        }
        return file.readText()
    }

    fun readAny(key: String): String? {
        val file = fileFor(key)
        if (!file.exists()) {
            return null
        }
        return file.readText()
    }

    fun write(
        key: String,
        value: String,
    ) {
        fileFor(key).writeText(value)
    }

    private fun fileFor(key: String): File =
        File(cacheDirectory, "${key.sha256Hex()}.json")

    private fun String.sha256Hex(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray())
        return buildString(digest.size * 2) {
            digest.forEach { byte ->
                append("%02x".format(byte))
            }
        }
    }
}
