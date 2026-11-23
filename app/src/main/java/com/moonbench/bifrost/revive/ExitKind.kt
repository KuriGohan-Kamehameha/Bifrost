package com.moonbench.bifrost.revive

/**
 * Why the previous Bifrost process ended, mapped from the integer
 * `ApplicationExitInfo.REASON_*` constants (kept as plain ints so the mapping
 * is unit-testable without the Android framework).
 *
 * [revivable]: dying this way is an accident the service should recover from.
 * [userInitiated]: the user (or a user-facing system action such as Force stop
 * or the Android 13 foreground-service task manager's Stop) ended the app.
 * Bifrost must not come back by itself after that.
 */
enum class ExitKind(
    val reason: Int,
    val label: String,
    val revivable: Boolean,
    val userInitiated: Boolean
) {
    UNKNOWN(0, "unknown cause", revivable = true, userInitiated = false),
    EXIT_SELF(1, "exited", revivable = true, userInitiated = false),
    SIGNALED(2, "killed by a signal", revivable = true, userInitiated = false),
    LOW_MEMORY(3, "low memory", revivable = true, userInitiated = false),
    CRASH(4, "crash", revivable = true, userInitiated = false),
    CRASH_NATIVE(5, "native crash", revivable = true, userInitiated = false),
    ANR(6, "not responding (ANR)", revivable = true, userInitiated = false),
    // Dying during start-up again would only loop.
    INITIALIZATION_FAILURE(7, "failed to initialise", revivable = false, userInitiated = false),
    PERMISSION_CHANGE(8, "a permission was revoked", revivable = false, userInitiated = true),
    EXCESSIVE_RESOURCE_USAGE(9, "excessive resource use", revivable = true, userInitiated = false),
    USER_REQUESTED(10, "stopped by the user", revivable = false, userInitiated = true),
    USER_STOPPED(11, "user profile stopped", revivable = false, userInitiated = true),
    DEPENDENCY_DIED(12, "a dependency died", revivable = true, userInitiated = false),
    OTHER(13, "killed by the system", revivable = true, userInitiated = false),
    FREEZER(14, "killed while frozen", revivable = true, userInitiated = false),
    PACKAGE_STATE_CHANGE(15, "app disabled", revivable = false, userInitiated = true),
    PACKAGE_UPDATED(16, "app updated", revivable = true, userInitiated = false);

    companion object {
        /** ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE */
        const val IMPORTANCE_FOREGROUND_SERVICE = 125

        fun fromReason(reason: Int): ExitKind =
            entries.firstOrNull { it.reason == reason } ?: UNKNOWN

        /**
         * Whether an exit belongs in the user-visible history. Cached/empty
         * processes are reclaimed all the time and say nothing about the LED
         * service, so only exits of a process at foreground-service importance
         * or better, crashes/ANRs, and user stops are kept.
         */
        fun isNoteworthy(reason: Int, importance: Int): Boolean {
            val kind = fromReason(reason)
            if (kind == CRASH || kind == CRASH_NATIVE || kind == ANR) return true
            if (kind.userInitiated) return true
            return importance in 1..IMPORTANCE_FOREGROUND_SERVICE
        }
    }
}
