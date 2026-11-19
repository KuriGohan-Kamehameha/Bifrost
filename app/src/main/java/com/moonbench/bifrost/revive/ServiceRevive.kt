package com.moonbench.bifrost.revive

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.ApplicationExitInfo
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.moonbench.bifrost.services.LEDService

/**
 * Brings the LED service back after it dies unexpectedly (low-memory kill,
 * crash, ANR) and restores what it was running.
 *
 * Paths, in the order they normally act:
 * 1. Sticky restart. LEDService returns START_STICKY while it is meant to run,
 *    so Android restarts it in a new process with a null intent after an LMK
 *    kill or a crash. The service asks [prepareRevive] for the saved start
 *    request. This needs no extra permission and is what fixes the low-memory
 *    kills seen on the Thor.
 * 2. Crash safety net. The uncaught-exception hook arms an alarm that revives
 *    the service only if the sticky restart has not happened by then (Android
 *    skips sticky restarts after two crashes within a minute).
 * 3. Watchdog. A 15-minute periodic job revives it if it should be running but
 *    is not (for example when a sticky restart was refused).
 *
 * Starting a foreground service from the background is restricted since
 * Android 12. Exact alarms set with setExactAndAllowWhileIdle by an app that
 * holds SCHEDULE_EXACT_ALARM are exempt, so the alarm and watchdog paths go
 * through such an alarm when the permission is held (pre-granted on Android 13,
 * user-granted on 14+). Without it, a background start is attempted only
 * when the app is exempt from battery optimisation; otherwise the user gets a
 * notification with a Restart action (a notification tap is always exempt).
 *
 * Bifrost never revives after a deliberate stop (Stop action, app toggle,
 * tile, schedule), a force stop, the Android 13+ task-manager Stop, or a
 * reboot (the existing "auto-enable" setting covers boot).
 */
object ServiceRevive {

    private const val TAG = "BifrostRevive"

    const val EXTRA_REVIVE_SOURCE = "bifrost.revive.source"
    const val EXTRA_REVIVE_CAUSE = "bifrost.revive.cause"
    const val ACTION_REVIVE = "com.moonbench.bifrost.action.REVIVE_SERVICE"

    const val SOURCE_STICKY = "system restart"
    const val SOURCE_CRASH_ALARM = "crash alarm"
    const val SOURCE_WATCHDOG = "watchdog"
    /** Second chance for a sticky restart that Android refused to promote. */
    const val SOURCE_RETRY = "retry alarm"
    const val SOURCE_PACKAGE_REPLACED = "app update"

    /** Sources that are a continuation of an already-counted attempt, or not a crash. */
    private val UNCOUNTED_SOURCES = setOf(SOURCE_RETRY, SOURCE_PACKAGE_REPLACED)

    private const val ALARM_REQUEST_CODE = 8431
    private const val WATCHDOG_JOB_ID = 8432
    private const val WATCHDOG_PERIOD_MS = 15 * 60_000L
    private const val WATCHDOG_ALARM_DELAY_MS = 1_000L
    private const val RETRY_ALARM_DELAY_MS = 3_000L
    private const val EXIT_HISTORY_LOOKUP = 8

    fun isReviveIntent(intent: Intent): Boolean = intent.hasExtra(EXTRA_REVIVE_SOURCE)

    // ── Process and service lifecycle ────────────────────────────────────────

    /**
     * Application start. Records how the previous process ended. A force stop
     * or task-manager Stop clears the "should be running" flag here, so a
     * later watchdog run cannot bring back a service the user killed.
     */
    fun onProcessStart(context: Context) {
        val store = ReviveStore(context)
        val lastExit = importExitHistory(context, store)
        if (store.isIntended && lastExit?.userInitiated == true) {
            store.markStopped()
            store.appendHistory(
                ReviveEvent(now(), ReviveEventKind.RESPECTED_STOP, "last exit: ${lastExit.label}")
            )
            cancelScheduled(context)
        } else if (store.isIntended && store.isEnabled && !store.hasGivenUp) {
            scheduleWatchdog(context)
        }
    }

    /** A full start request has been applied and the service is foreground. */
    fun onServiceStarted(context: Context, intent: Intent) {
        val store = ReviveStore(context)
        val revive = isReviveIntent(intent)
        store.markStarted(ReviveSnapshot.fromExtras(extrasOf(intent)), manual = !revive, now = now())
        if (revive) {
            val source = intent.getStringExtra(EXTRA_REVIVE_SOURCE).orEmpty()
            val cause = intent.getStringExtra(EXTRA_REVIVE_CAUSE) ?: ExitKind.UNKNOWN.label
            store.appendHistory(ReviveEvent(now(), ReviveEventKind.REVIVED, "after $cause (via $source)"))
        }
        ReviveNotifier.cancel(context)
        if (store.isEnabled) scheduleWatchdog(context) else cancelScheduled(context)
    }

    fun onParamsUpdated(context: Context, intent: Intent) {
        runCatching { ReviveStore(context).mergeUpdate(extrasOf(intent)) }
    }

    /** Deliberate stop: nothing may bring the service back by itself. */
    fun onServiceStopped(context: Context) {
        ReviveStore(context).markStopped()
        cancelScheduled(context)
    }

    /** Reboot is not a crash; the existing auto-enable setting covers boot. */
    fun onBoot(context: Context) {
        ReviveStore(context).markStopped()
        cancelScheduled(context)
    }

    fun onPackageReplaced(context: Context) {
        reviveFromBackground(context, SOURCE_PACKAGE_REPLACED)
    }

    fun onSettingChanged(context: Context, enabled: Boolean) {
        val store = ReviveStore(context)
        store.isEnabled = enabled
        if (enabled && LEDService.isRunning) scheduleWatchdog(context) else if (!enabled) cancelScheduled(context)
    }

    // ── Crash hook ───────────────────────────────────────────────────────────

    /** Runs on the crashing thread just before the process dies: keep it short. */
    fun onUncaughtException(context: Context, thread: Thread, error: Throwable) {
        if (!LEDService.isRunning) return
        val store = ReviveStore(context)
        if (!store.isEnabled || !store.isIntended) return
        val message = error.message?.take(80)?.let { ": $it" }.orEmpty()
        store.appendHistory(
            ReviveEvent(now(), ReviveEventKind.CRASH, "${error.javaClass.simpleName}$message (thread ${thread.name})")
        )
        val revives = RevivePolicy.revivesInWindow(store.recentRevives(), now())
        scheduleAlarm(context, RevivePolicy.safetyNetDelayMs(revives), SOURCE_CRASH_ALARM)
    }

    // ── Decisions ────────────────────────────────────────────────────────────

    /**
     * Decides whether to revive and performs the bookkeeping for that decision.
     * Returns the start request to run, or null when the service must stay off.
     */
    fun prepareRevive(context: Context, running: Boolean, source: String): Intent? {
        val store = ReviveStore(context)
        val lastExit = runCatching { importExitHistory(context, store) }.getOrNull()
        val now = now()
        val decision = RevivePolicy.decide(
            ReviveInput(
                enabled = store.isEnabled,
                intended = store.isIntended,
                running = running,
                gaveUp = store.hasGivenUp,
                lastExit = lastExit,
                recentRevives = store.recentRevives(),
                now = now
            )
        )
        Log.i(TAG, "prepareRevive: source=$source lastExit=$lastExit decision=$decision")
        val cause = lastExit?.label ?: "an unexpected stop"
        return when (decision) {
            ReviveDecision.REVIVE -> {
                val intent = buildReviveIntent(context, store, source, cause)
                if (intent == null) {
                    store.markStopped()
                    return null
                }
                if (source !in UNCOUNTED_SOURCES) store.recordRevive(now)
                intent
            }
            ReviveDecision.GIVE_UP -> {
                store.markGaveUp()
                store.appendHistory(
                    ReviveEvent(
                        now, ReviveEventKind.GAVE_UP,
                        "${RevivePolicy.MAX_CRASHES_IN_WINDOW} stops within " +
                            "${RevivePolicy.WINDOW_MS / 60_000} minutes; last: $cause"
                    )
                )
                cancelScheduled(context)
                ReviveNotifier.showGaveUp(context, cause, store.snapshot())
                null
            }
            ReviveDecision.USER_STOPPED, ReviveDecision.NOT_REVIVABLE -> {
                store.markStopped()
                store.appendHistory(ReviveEvent(now, ReviveEventKind.RESPECTED_STOP, "last exit: $cause"))
                cancelScheduled(context)
                null
            }
            ReviveDecision.DISABLED -> {
                store.markStopped()
                store.appendHistory(
                    ReviveEvent(now, ReviveEventKind.RESPECTED_STOP, "last exit: $cause; automatic revive is off")
                )
                cancelScheduled(context)
                null
            }
            ReviveDecision.ALREADY_RUNNING,
            ReviveDecision.NOT_INTENDED,
            ReviveDecision.GAVE_UP_EARLIER -> null
        }
    }

    /** Alarm, watchdog and package-replaced path: start the service from the background. */
    fun reviveFromBackground(context: Context, source: String) {
        if (LEDService.isRunning) return
        val intent = prepareRevive(context, running = false, source = source) ?: return
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (e: Exception) {
            // ForegroundServiceStartNotAllowedException (API 31+) or SecurityException.
            onReviveBlocked(context, intent, e)
        }
    }

    /**
     * Android refused to start or promote the revived service. A sticky restart
     * gets a second chance through an exact alarm (exempt); anything else asks
     * the user, because only a user action is guaranteed to be allowed.
     */
    fun onReviveBlocked(context: Context, intent: Intent, error: Throwable) {
        val store = ReviveStore(context)
        val source = intent.getStringExtra(EXTRA_REVIVE_SOURCE).orEmpty()
        val cause = intent.getStringExtra(EXTRA_REVIVE_CAUSE) ?: ExitKind.UNKNOWN.label
        Log.w(TAG, "revive via $source blocked", error)
        val retry = source == SOURCE_STICKY && canScheduleExactAlarms(context)
        store.appendHistory(
            ReviveEvent(
                now(), ReviveEventKind.BLOCKED,
                "background start refused (via $source)" + if (retry) "; retrying with an exact alarm" else ""
            )
        )
        if (retry) {
            scheduleAlarm(context, RETRY_ALARM_DELAY_MS, SOURCE_RETRY)
        } else {
            // The notification is now the way back; stop background attempts so
            // the watchdog does not re-post it every period.
            val snapshot = store.snapshot()
            store.markStopped()
            cancelScheduled(context)
            ReviveNotifier.showBlocked(context, cause, snapshot)
        }
    }

    /** Periodic watchdog: the service should be running but this process has none. */
    fun onWatchdog(context: Context) {
        if (LEDService.isRunning) return
        val store = ReviveStore(context)
        if (!store.isIntended || !store.isEnabled || store.hasGivenUp) {
            cancelScheduled(context)
            return
        }
        when {
            // An exact alarm grants the foreground-service start exemption.
            canScheduleExactAlarms(context) -> scheduleAlarm(context, WATCHDOG_ALARM_DELAY_MS, SOURCE_WATCHDOG)
            isIgnoringBatteryOptimizations(context) -> reviveFromBackground(context, SOURCE_WATCHDOG)
            else -> {
                // Starting now would throw; decide (and count) first, then ask the user.
                val intent = prepareRevive(context, running = false, source = SOURCE_WATCHDOG) ?: return
                onReviveBlocked(context, intent, IllegalStateException("no background start exemption"))
            }
        }
    }

    // ── Exit history ─────────────────────────────────────────────────────────

    /**
     * Copies new noteworthy ApplicationExitInfo records into the history and
     * returns how the most recent process ended after the last manual start
     * (null if none did, i.e. nothing to explain).
     */
    fun importExitHistory(context: Context, store: ReviveStore = ReviveStore(context)): ExitKind? {
        val manager = context.getSystemService(ActivityManager::class.java) ?: return null
        val exits = runCatching {
            manager.getHistoricalProcessExitReasons(context.packageName, 0, EXIT_HISTORY_LOOKUP)
        }.getOrNull().orEmpty()
        if (exits.isEmpty()) return null

        val importedUpTo = store.lastImportedExitAt
        exits.filter { it.timestamp > importedUpTo }
            .sortedBy { it.timestamp }
            .filter { ExitKind.isNoteworthy(it.reason, it.importance) }
            .forEach { store.appendHistory(ReviveEvent(it.timestamp, ReviveEventKind.EXIT, describe(it))) }
        val newest = exits.maxOf { it.timestamp }
        if (newest > importedUpTo) store.lastImportedExitAt = newest

        val lastStart = store.lastManualStartAt
        return exits.filter { it.timestamp > lastStart }
            .maxByOrNull { it.timestamp }
            ?.let { ExitKind.fromReason(it.reason) }
    }

    private fun describe(info: ApplicationExitInfo): String {
        val kind = ExitKind.fromReason(info.reason)
        val state = when {
            info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> "in use"
            info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE -> "LEDs running"
            else -> "in background"
        }
        val pssMb = info.pss / 1024
        val detail = info.description
            ?.takeIf { kind == ExitKind.CRASH || kind == ExitKind.CRASH_NATIVE || kind == ExitKind.ANR }
            ?.take(80)
            ?.let { ", $it" }
            .orEmpty()
        return "${kind.label} ($state, ${pssMb} MB$detail)"
    }

    // ── Scheduling ───────────────────────────────────────────────────────────

    fun canScheduleExactAlarms(context: Context): Boolean =
        runCatching { context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true }
            .getOrDefault(false)

    fun isIgnoringBatteryOptimizations(context: Context): Boolean =
        runCatching {
            context.getSystemService(PowerManager::class.java)
                ?.isIgnoringBatteryOptimizations(context.packageName) == true
        }.getOrDefault(false)

    fun scheduleAlarm(context: Context, delayMs: Long, source: String) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerAt = SystemClock.elapsedRealtime() + delayMs
        val pending = alarmIntent(context, source)
        runCatching {
            if (alarms.canScheduleExactAlarms()) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pending)
            } else {
                // Inexact alarms carry no foreground-start exemption; the receiver
                // still tries and falls back to the restart notification.
                alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pending)
            }
        }.onFailure { Log.w(TAG, "could not arm revive alarm", it) }
    }

    fun scheduleWatchdog(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        if (scheduler.getPendingJob(WATCHDOG_JOB_ID) != null) return
        val job = JobInfo.Builder(WATCHDOG_JOB_ID, ComponentName(context, ReviveWatchdogJob::class.java))
            .setPeriodic(WATCHDOG_PERIOD_MS)
            .setPersisted(false)
            .build()
        runCatching { scheduler.schedule(job) }
            .onFailure { Log.w(TAG, "could not schedule revive watchdog", it) }
    }

    fun cancelScheduled(context: Context) {
        runCatching { context.getSystemService(JobScheduler::class.java)?.cancel(WATCHDOG_JOB_ID) }
        runCatching { context.getSystemService(AlarmManager::class.java)?.cancel(alarmIntent(context, null)) }
    }

    private fun alarmIntent(context: Context, source: String?): PendingIntent {
        val intent = Intent(context, ReviveReceiver::class.java).apply {
            action = ACTION_REVIVE
            if (source != null) putExtra(EXTRA_REVIVE_SOURCE, source)
        }
        return PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun buildReviveIntent(context: Context, store: ReviveStore, source: String, cause: String): Intent? {
        val snapshot = store.snapshot() ?: return null
        return snapshotIntent(context, snapshot).apply {
            putExtra(EXTRA_REVIVE_SOURCE, source)
            putExtra(EXTRA_REVIVE_CAUSE, cause)
        }
    }

    /** A plain (manual) start request rebuilt from a snapshot. */
    fun snapshotIntent(context: Context, snapshot: ReviveSnapshot): Intent =
        Intent(context, LEDService::class.java).apply {
            for ((key, value) in snapshot.values) {
                when (value) {
                    is Boolean -> putExtra(key, value)
                    is Int -> putExtra(key, value)
                    is Long -> putExtra(key, value)
                    is Float -> putExtra(key, value)
                    is Double -> putExtra(key, value)
                    is String -> putExtra(key, value)
                }
            }
        }

    @Suppress("DEPRECATION")
    private fun extrasOf(intent: Intent): Map<String, Any?> {
        val bundle = intent.extras ?: return emptyMap()
        return bundle.keySet().associateWith { key -> runCatching { bundle.get(key) }.getOrNull() }
    }

    private fun now(): Long = System.currentTimeMillis()
}
