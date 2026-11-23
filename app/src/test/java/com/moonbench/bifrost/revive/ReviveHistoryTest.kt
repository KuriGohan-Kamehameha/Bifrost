package com.moonbench.bifrost.revive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviveHistoryTest {

    @Test fun keepsOnlyTheNewestEvents() {
        var events = emptyList<ReviveEvent>()
        repeat(ReviveHistory.MAX_EVENTS + 5) { i ->
            events = ReviveHistory.append(events, ReviveEvent(i.toLong(), ReviveEventKind.REVIVED, "#$i"))
        }
        assertEquals(ReviveHistory.MAX_EVENTS, events.size)
        assertEquals("#5", events.first().detail)
        assertEquals("#${ReviveHistory.MAX_EVENTS + 4}", events.last().detail)
    }

    @Test fun importedExitsSortIntoPlace() {
        val events = ReviveHistory.append(
            listOf(ReviveEvent(200, ReviveEventKind.REVIVED, "after low memory")),
            ReviveEvent(100, ReviveEventKind.EXIT, "low memory")
        )
        assertEquals(listOf(100L, 200L), events.map { it.at })
    }

    @Test fun roundTripsThroughJson() {
        val events = listOf(
            ReviveEvent(1, ReviveEventKind.EXIT, "low memory (LEDs running, 51 MB)"),
            ReviveEvent(2, ReviveEventKind.REVIVED, "after low memory (via system restart)"),
            ReviveEvent(3, ReviveEventKind.GAVE_UP, "3 stops")
        )
        assertEquals(events, ReviveHistory.fromJson(ReviveHistory.toJson(events)))
    }

    @Test fun unreadableHistoryIsEmptyNotFatal() {
        assertTrue(ReviveHistory.fromJson(null).isEmpty())
        assertTrue(ReviveHistory.fromJson("{oops").isEmpty())
        val partlyBad = """[{"at":1,"kind":"NOPE","detail":"x"},{"at":2,"kind":"CRASH","detail":"y"}]"""
        assertEquals(listOf(ReviveEvent(2, ReviveEventKind.CRASH, "y")), ReviveHistory.fromJson(partlyBad))
    }

    @Test fun formatsNewestFirst() {
        val text = ReviveHistory.format(
            listOf(
                ReviveEvent(1, ReviveEventKind.EXIT, "low memory"),
                ReviveEvent(2, ReviveEventKind.REVIVED, "")
            )
        ) { "t$it" }
        assertEquals("t2  Revived\nt1  Ended: low memory", text)
    }

    @Test fun longDetailsAreTrimmed() {
        val events = ReviveHistory.append(emptyList(), ReviveEvent(1, ReviveEventKind.CRASH, "x".repeat(1000)))
        assertTrue(events.single().detail.length <= 160)
    }
}
