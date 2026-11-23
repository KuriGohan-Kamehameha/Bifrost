package com.moonbench.bifrost.revive

import org.junit.Assert.assertEquals
import org.junit.Test

class RevivePolicyTest {

    private val now = 1_800_000_000_000L
    private val minute = 60_000L

    private fun input(
        enabled: Boolean = true,
        intended: Boolean = true,
        running: Boolean = false,
        gaveUp: Boolean = false,
        lastExit: ExitKind? = ExitKind.LOW_MEMORY,
        revives: List<Long> = emptyList()
    ) = ReviveInput(enabled, intended, running, gaveUp, lastExit, revives, now)

    @Test fun lowMemoryKillOfARunningServiceIsRevived() {
        assertEquals(ReviveDecision.REVIVE, RevivePolicy.decide(input()))
    }

    @Test fun crashesAnrsAndUnknownExitsAreRevived() {
        listOf(ExitKind.CRASH, ExitKind.CRASH_NATIVE, ExitKind.ANR, ExitKind.OTHER, null).forEach {
            assertEquals("exit $it", ReviveDecision.REVIVE, RevivePolicy.decide(input(lastExit = it)))
        }
    }

    @Test fun aRunningServiceIsNotRevivedTwice() {
        assertEquals(ReviveDecision.ALREADY_RUNNING, RevivePolicy.decide(input(running = true)))
    }

    @Test fun aDeliberatelyStoppedServiceStaysStopped() {
        assertEquals(ReviveDecision.NOT_INTENDED, RevivePolicy.decide(input(intended = false)))
    }

    @Test fun forceStopAndTaskManagerStopAreRespected() {
        assertEquals(ReviveDecision.USER_STOPPED, RevivePolicy.decide(input(lastExit = ExitKind.USER_REQUESTED)))
        assertEquals(ReviveDecision.USER_STOPPED, RevivePolicy.decide(input(lastExit = ExitKind.USER_STOPPED)))
        assertEquals(ReviveDecision.USER_STOPPED, RevivePolicy.decide(input(lastExit = ExitKind.PERMISSION_CHANGE)))
    }

    @Test fun userStopWinsEvenWhenTheSettingIsOff() {
        // Both mean "stay off", but the history should name the user's action.
        assertEquals(
            ReviveDecision.USER_STOPPED,
            RevivePolicy.decide(input(enabled = false, lastExit = ExitKind.USER_REQUESTED))
        )
    }

    @Test fun settingOffDisablesRevive() {
        assertEquals(ReviveDecision.DISABLED, RevivePolicy.decide(input(enabled = false)))
    }

    @Test fun startupFailureIsNotRetried() {
        assertEquals(
            ReviveDecision.NOT_REVIVABLE,
            RevivePolicy.decide(input(lastExit = ExitKind.INITIALIZATION_FAILURE))
        )
    }

    @Test fun afterGivingUpOnlyAManualStartHelps() {
        assertEquals(ReviveDecision.GAVE_UP_EARLIER, RevivePolicy.decide(input(gaveUp = true)))
    }

    @Test fun secondCrashInTheWindowIsStillRevived() {
        val decision = RevivePolicy.decide(input(revives = listOf(now - 2 * minute)))
        assertEquals(ReviveDecision.REVIVE, decision)
    }

    @Test fun thirdCrashWithinFiveMinutesGivesUp() {
        val decision = RevivePolicy.decide(input(revives = listOf(now - 4 * minute, now - 1 * minute)))
        assertEquals(ReviveDecision.GIVE_UP, decision)
    }

    @Test fun crashesOutsideTheWindowDoNotCount() {
        val decision = RevivePolicy.decide(input(revives = listOf(now - 6 * minute, now - 5 * minute)))
        assertEquals(ReviveDecision.REVIVE, decision)
    }

    @Test fun timestampsFromAClockSetBackAreIgnored() {
        val decision = RevivePolicy.decide(input(revives = listOf(now + 10 * minute, now + 20 * minute)))
        assertEquals(ReviveDecision.REVIVE, decision)
    }

    @Test fun pruneKeepsOnlyRelevantRecentTimes() {
        val pruned = RevivePolicy.prune(
            listOf(now - 10 * minute, now - 3 * minute, now + minute, now - minute, now - 2 * minute, now),
            now
        )
        assertEquals(listOf(now - 2 * minute, now - minute, now), pruned)
    }

    @Test fun safetyNetBacksOffAsCrashesRepeat() {
        val first = RevivePolicy.safetyNetDelayMs(0)
        val second = RevivePolicy.safetyNetDelayMs(1)
        val third = RevivePolicy.safetyNetDelayMs(2)
        org.junit.Assert.assertTrue(first < second && second < third)
        // Long enough for Android's own sticky restart (about a second) to win.
        org.junit.Assert.assertTrue(first >= 5_000L)
    }
}
