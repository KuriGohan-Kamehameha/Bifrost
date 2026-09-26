package com.moonbench.bifrost.io

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

object BoundedInput {
    fun read(input: InputStream, limit: Int, checkCanceled: () -> Unit = {}): ByteArray =
        ByteArrayOutputStream().use { out ->
            copy(input, out, limit, checkCanceled)
            out.toByteArray()
        }

    fun copy(input: InputStream, output: OutputStream, limit: Int, checkCanceled: () -> Unit = {}) {
        require(limit >= 0)
        val buffer = ByteArray(8192)
        var total = 0L
        while (total <= limit.toLong()) {
            checkCanceled()
            val requested = minOf(buffer.size.toLong(), limit.toLong() - total + 1).toInt()
            val count = input.read(buffer, 0, requested)
            if (count < 0) return
            if (count == 0) {
                val byte = input.read()
                if (byte < 0) return
                if (++total > limit) throw IOException("Input exceeds $limit bytes")
                output.write(byte)
            } else {
                total += count
                if (total > limit) throw IOException("Input exceeds $limit bytes")
                output.write(buffer, 0, count)
            }
        }
    }
}
