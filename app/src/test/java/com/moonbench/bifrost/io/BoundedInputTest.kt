package com.moonbench.bifrost.io

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.*
import org.junit.Test

class BoundedInputTest {
    @Test fun noBytesBeyondLimitReachDestination() {
        val output = ByteArrayOutputStream()
        assertThrows(IOException::class.java) { BoundedInput.copy(ByteArrayInputStream(ByteArray(100)), output, 10) }
        assertTrue(output.size() <= 10)
    }
    @Test fun emptyInputIsAllowedAtZeroLimit() {
        assertArrayEquals(byteArrayOf(), BoundedInput.read(ByteArrayInputStream(byteArrayOf()), 0))
        assertThrows(IOException::class.java) { BoundedInput.read(ByteArrayInputStream(byteArrayOf(1)), 0) }
    }
    @Test fun shortAndZeroLengthReadsCannotSpin() {
        val source = object : InputStream() {
            var remaining = 5
            override fun read(b: ByteArray, off: Int, len: Int): Int = if (remaining > 0) 0 else -1
            override fun read(): Int = if (remaining-- > 0) 42 else -1
        }
        assertArrayEquals(ByteArray(5) { 42 }, BoundedInput.read(source, 5))
    }
    @Test fun cancellationIsNotSwallowed() {
        assertThrows(InterruptedException::class.java) {
            BoundedInput.read(ByteArrayInputStream(ByteArray(20)), 20) { throw InterruptedException() }
        }
    }
}
