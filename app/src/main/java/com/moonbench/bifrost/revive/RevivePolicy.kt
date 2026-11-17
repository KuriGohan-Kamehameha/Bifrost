package com.moonbench.bifrost.revive

enum class ReviveDecision {
    /** Bring the service back now. */
    REVIVE,
    ALREADY_RUNNING,
    /** The service was stopped on purpose (or never started). */
    NOT_INTENDED,
    /** The previous process was force-stopped / stopped by the user. */
    USER_STOPPED,
    /** "Revive automatically after a crash" is off. */
    DISABLED,
    /** The previous process died in a way that reviving would only repeat. */
    NOT_REVIVABLE,
    /** This would be one crash too many: stop retrying and tell the user. */
    GIVE_UP,
    /** Already gave up earlier; waiting for the user to restart manually. */
    GAVE_UP_EARLIER
}

data class ReviveInput(
    val enabled: Boolean,
    val intended: Boolean,
    val running: Boolean,
    val gaveUp: Boolean,
    /** How the previous process ended, if it ended after the last manual start. */
    val lastExit: ExitKind?,
    /** Wall-clock times of earlier automatic revives. */
    val recentRevives: List<Long>,
    val now: Long
)

/** Pure decision logic for reviving the LED service; no Android dependencies. */
object RevivePolicy {

    /** Crashes counted together when deciding to give up. */
    const val WINDOW_MS = 5 * 60_000L

    /** The crash that reaches this count inside [WINDOW_MS] is not revived. */
    const val MAX_CRASHES_IN_WINDOW = 3

    fun decide(input: ReviveInput): ReviveDecision {
        if (input.running) return ReviveDecision.ALREADY_RUNNING
        if (!input.intended) return ReviveDecision.NOT_INTENDED
        if (input.lastExit?.userInitiated == true) return ReviveDecision.USER_STOPPED
        if (!input.enabled) return ReviveDecision.DISABLED
        if (input.lastExit != null && !input.lastExit.revivable) return ReviveDecision.NOT_REVIVABLE
        if (input.gaveUp) return ReviveDecision.GAVE_UP_EARLIER
        // This crash plus the ones already revived inside the window.
        val crashes = revivesInWindow(input.recentRevives, input.now) + 1
        if (crashes >= MAX_CRASHES_IN_WINDOW) return ReviveDecision.GIVE_UP
        return ReviveDecision.REVIVE
    }

    /**
     * Revives inside the window ending at [now]. Timestamps in the future (the
     * clock was set back) are ignored rather than counted forever.
     */
    fun revivesInWindow(times: List<Long>, now: Long): Int =
        times.count { it in (now - WINDOW_MS + 1)..now }

    /** Keeps only the timestamps that can still affect a decision. */
    fun prune(times: List<Long>, now: Long): List<Long> =
        times.filter { it in (now - WINDOW_MS + 1)..now }
            .sorted()
            .takeLast(MAX_CRASHES_IN_WINDOW)

    /**
     * Delay of the safety-net alarm armed when a crash is caught in-process.
     * Android's own sticky restart normally comes first (about a second); the
     * alarm only matters when it does not, and it backs off as crashes repeat.
     */
    fun safetyNetDelayMs(revivesInWindow: Int): Long = when {
        revivesInWindow <= 0 -> 10_000L
        revivesInWindow == 1 -> 30_000L
        else -> 60_000L
    }
}
