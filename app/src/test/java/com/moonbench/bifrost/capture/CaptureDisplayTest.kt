package com.moonbench.bifrost.capture

import org.junit.Assert.*
import org.junit.Test

class CaptureDisplayTest {
    private class FakeDisplay {
        var creates = 0
        var updates = 0
        var detaches = 0
        var releases = 0
        val owner = CaptureDisplay<Any, Any>(
            create = { _, _, _, _ -> creates++; Any() },
            update = { _, _, _, _, _ -> updates++ },
            detach = { detaches++ },
            release = { releases++ }
        )
    }
    @Test fun animationRestartReusesTheOnlyVirtualDisplay() {
        val f = FakeDisplay(); val first = Any(); val next = Any()
        f.owner.attach(first, 2, 1, 320)
        f.owner.detach(first)
        f.owner.attach(next, 32, 16, 320)
        assertEquals(1, f.creates)
        assertEquals(1, f.updates)
        assertEquals(0, f.releases)
    }
    @Test fun lateOldStopCannotDetachTheNewSurface() {
        val f = FakeDisplay(); val first = Any(); val next = Any()
        f.owner.attach(first, 2, 1, 320)
        f.owner.attach(next, 2, 1, 320)
        f.owner.detach(first)
        assertEquals(0, f.detaches)
        f.owner.detach(next)
        assertEquals(1, f.detaches)
    }
    @Test fun revokedSessionNeverCreatesAnotherDisplay() {
        val f = FakeDisplay()
        f.owner.attach(Any(), 2, 1, 320)
        f.owner.close(); f.owner.close()
        assertEquals(1, f.releases)
        assertThrows(IllegalStateException::class.java) { f.owner.attach(Any(), 2, 1, 320) }
        assertEquals(1, f.creates)
    }
    @Test fun failedCreationCannotSpendConsentAgain() {
        var attempts = 0
        val owner = CaptureDisplay<Any, Any>(
            create = { _, _, _, _ -> attempts++; throw SecurityException("revoked") },
            update = { _, _, _, _, _ -> }, detach = {}, release = {})
        assertThrows(SecurityException::class.java) { owner.attach(Any(), 2, 1, 320) }
        assertThrows(IllegalStateException::class.java) { owner.attach(Any(), 2, 1, 320) }
        assertEquals(1, attempts)
    }
}
