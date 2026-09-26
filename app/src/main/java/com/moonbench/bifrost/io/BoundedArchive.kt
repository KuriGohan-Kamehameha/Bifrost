package com.moonbench.bifrost.io

import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream

/** Owns/closes input. Limits actual expanded bytes, including unknown entries. */
object BoundedArchive {
    fun read(
        input: InputStream,
        maxEntryBytes: Int = 8 * 1024 * 1024,
        maxTotalBytes: Int = 32 * 1024 * 1024,
        maxEntries: Int = 256,
        checkCanceled: () -> Unit = {}
    ): Map<String, ByteArray> {
        require(maxEntryBytes > 0 && maxTotalBytes > 0 && maxEntries in 1..10000)
        val files = linkedMapOf<String, ByteArray>()
        val names = hashSetOf<String>()
        var total = 0
        return ZipInputStream(input.buffered()).use { zip ->
            for (index in 0..maxEntries) {
                checkCanceled()
                val entry = zip.nextEntry ?: return@use files
                if (index == maxEntries) throw IOException("Too many archive entries")
                validateName(entry.name)
                if (!names.add(entry.name.removeSuffix("/"))) throw IOException("Duplicate archive entry")
                val data = BoundedInput.read(zip, minOf(maxEntryBytes, maxTotalBytes - total), checkCanceled)
                total += data.size
                if (entry.isDirectory) {
                    if (data.isNotEmpty()) throw IOException("Directory entry contains data")
                } else files[entry.name] = data
                zip.closeEntry()
            }
            throw IOException("Too many archive entries")
        }
    }

    private fun validateName(name: String) {
        val parts = name.removeSuffix("/").split('/')
        if (name.length > 1024 || name.startsWith('/') || '\\' in name || '\u0000' in name || ':' in name ||
            parts.any { it.isEmpty() || it == "." || it == ".." }) {
            throw IOException("Unsafe archive entry name")
        }
    }
}
