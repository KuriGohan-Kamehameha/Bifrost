package com.moonbench.bifrost.io

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

class BoundedArchiveTest {
    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray =
        ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { z ->
                for ((name, data) in entries) {
                    z.putNextEntry(ZipEntry(name)); z.write(data); z.closeEntry()
                }
            }
        }.toByteArray()
    private fun input(vararg entries: Pair<String, ByteArray>) = ByteArrayInputStream(zip(*entries))

    @Test fun validManifestIconsAndEmptyEntries() {
        val data = BoundedArchive.read(input("manifest.json" to "{}".toByteArray(), "icons/" to byteArrayOf(), "icons/a" to byteArrayOf()))
        assertEquals(2, data.size)
        assertArrayEquals("{}".toByteArray(), data["manifest.json"])
    }
    @Test fun rejectsCompressedBombByActualSize() {
        assertThrows(IOException::class.java) { BoundedArchive.read(input("x" to ByteArray(10000)), maxEntryBytes = 100) }
    }
    @Test fun exactLimitsAreAllowed() {
        assertEquals(1, BoundedArchive.read(input("x" to ByteArray(10)), maxEntryBytes = 10, maxTotalBytes = 10).size)
    }
    @Test fun totalLimitCoversUnknownEntries() {
        assertThrows(IOException::class.java) {
            BoundedArchive.read(input("a" to ByteArray(6), "b" to ByteArray(6)), maxEntryBytes = 10, maxTotalBytes = 10)
        }
    }
    @Test fun directoriesCountTowardEntryLimit() {
        assertThrows(IOException::class.java) {
            BoundedArchive.read(input("a/" to byteArrayOf(), "b/" to byteArrayOf()), maxEntries = 1)
        }
    }
    @Test fun directoryPayloadCannotBypassSizeLimits() {
        assertThrows(IOException::class.java) { BoundedArchive.read(input("a/" to ByteArray(1000)), maxEntryBytes = 10) }
    }
    @Test fun rejectsUnsafePaths() {
        for (name in listOf("../x", "/x", "a/../x", "a\\x", "./x", "C:/x", "a//b")) {
            assertThrows(name, IOException::class.java) { BoundedArchive.read(input(name to byteArrayOf())) }
        }
    }
    @Test fun cancellationPropagatesDuringDecompression() {
        var calls = 0
        assertThrows(InterruptedException::class.java) {
            BoundedArchive.read(input("x" to ByteArray(20000)), checkCanceled = { if (++calls == 3) throw InterruptedException() })
        }
    }
    @Test fun duplicateEntriesAreRejected() {
        val bytes = zip("first1" to byteArrayOf(), "second" to byteArrayOf())
        val from = "second".toByteArray(); val to = "first1".toByteArray()
        for (i in 0..bytes.size - from.size) {
            if (from.indices.all { bytes[i + it] == from[it] }) to.copyInto(bytes, i)
        }
        assertThrows(IOException::class.java) { BoundedArchive.read(ByteArrayInputStream(bytes)) }
    }
}
