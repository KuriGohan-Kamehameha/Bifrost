package com.moonbench.bifrost.revive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExitKindTest {

    @Test fun mapsApplicationExitInfoReasons() {
        // Values of ApplicationExitInfo.REASON_* (stable public API).
        assertEquals(ExitKind.LOW_MEMORY, ExitKind.fromReason(3))
        assertEquals(ExitKind.CRASH, ExitKind.fromReason(4))
        assertEquals(ExitKind.CRASH_NATIVE, ExitKind.fromReason(5))
        assertEquals(ExitKind.ANR, ExitKind.fromReason(6))
        assertEquals(ExitKind.USER_REQUESTED, ExitKind.fromReason(10))
        assertEquals(ExitKind.OTHER, ExitKind.fromReason(13))
        assertEquals(ExitKind.PACKAGE_UPDATED, ExitKind.fromReason(16))
    }

    @Test fun unknownReasonsFallBackToUnknown() {
        assertEquals(ExitKind.UNKNOWN, ExitKind.fromReason(99))
        assertEquals(ExitKind.UNKNOWN, ExitKind.fromReason(-1))
    }

    @Test fun reasonsAreUnique() {
        assertEquals(ExitKind.entries.size, ExitKind.entries.map { it.reason }.toSet().size)
    }

    @Test fun userActionsAreNeverRevivable() {
        ExitKind.entries.filter { it.userInitiated }.forEach {
            assertFalse("$it must not be revived", it.revivable)
        }
    }

    @Test fun lowMemoryKillWhileLedsRunIsNoteworthy() {
        // As recorded on the Thor: reason=3, importance=125 (foreground service).
        assertTrue(ExitKind.isNoteworthy(3, 125))
    }

    @Test fun cachedProcessReclaimIsNotNoteworthy() {
        // reason=3 / 13 at importance 400: Bifrost was idle in the background.
        assertFalse(ExitKind.isNoteworthy(3, 400))
        assertFalse(ExitKind.isNoteworthy(13, 400))
    }

    @Test fun crashesAndForceStopsAreAlwaysNoteworthy() {
        assertTrue(ExitKind.isNoteworthy(4, 400))
        assertTrue(ExitKind.isNoteworthy(6, 400))
        assertTrue(ExitKind.isNoteworthy(10, 400))
    }
}
