package com.moonbench.bifrost.revive

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Persistent revive state. The user setting lives in `bifrost_prefs` with the
 * other settings; runtime state lives in its own file so a backup restore can
 * never resurrect a "service should be running" flag from another day.
 *
 * Flags that must survive an imminent process death are written with commit().
 */
@SuppressLint("ApplySharedPref") // commit() is deliberate, see above.
class ReviveStore(context: Context) {

    private val state: SharedPreferences =
        context.getSharedPreferences(STATE_FILE, Context.MODE_PRIVATE)
    private val settings: SharedPreferences =
        context.getSharedPreferences(SETTINGS_FILE, Context.MODE_PRIVATE)

    var isEnabled: Boolean
        get() = settings.getBoolean(PREF_REVIVE_ENABLED, DEFAULT_REVIVE_ENABLED)
        set(value) { settings.edit { putBoolean(PREF_REVIVE_ENABLED, value) } }

    /** The service is meant to be running (started and not deliberately stopped). */
    val isIntended: Boolean get() = state.getBoolean(KEY_INTENDED, false)
    val hasGivenUp: Boolean get() = state.getBoolean(KEY_GAVE_UP, false)
    /** Wall-clock time of the last start that was not an automatic revive. */
    val lastManualStartAt: Long get() = state.getLong(KEY_LAST_MANUAL_START, 0L)
    var lastImportedExitAt: Long
        get() = state.getLong(KEY_LAST_IMPORTED_EXIT, 0L)
        set(value) { state.edit { putLong(KEY_LAST_IMPORTED_EXIT, value) } }

    fun snapshot(): ReviveSnapshot? = ReviveSnapshot.fromJson(state.getString(KEY_SNAPSHOT, null))

    fun recentRevives(): List<Long> =
        state.getString(KEY_REVIVES, null)
            ?.split(',')
            ?.mapNotNull { it.trim().toLongOrNull() }
            .orEmpty()

    fun history(): List<ReviveEvent> = ReviveHistory.fromJson(state.getString(KEY_HISTORY, null))

    fun markStarted(snapshot: ReviveSnapshot, manual: Boolean, now: Long) {
        state.edit(commit = true) {
            putBoolean(KEY_INTENDED, true)
            putString(KEY_SNAPSHOT, snapshot.toJson())
            if (manual) {
                putLong(KEY_LAST_MANUAL_START, now)
                putBoolean(KEY_GAVE_UP, false)
                remove(KEY_REVIVES)
            }
        }
    }

    /** A live parameter update: cheap and frequent (sliders), so apply(). */
    fun mergeUpdate(updates: Map<String, Any?>) {
        val current = snapshot() ?: return
        state.edit { putString(KEY_SNAPSHOT, current.merge(updates).toJson()) }
    }

    fun markStopped() {
        if (!isIntended) return
        state.edit(commit = true) { putBoolean(KEY_INTENDED, false) }
    }

    fun recordRevive(now: Long) {
        val revives = RevivePolicy.prune(recentRevives() + now, now)
        state.edit(commit = true) { putString(KEY_REVIVES, revives.joinToString(",")) }
    }

    fun markGaveUp() {
        state.edit(commit = true) {
            putBoolean(KEY_GAVE_UP, true)
            putBoolean(KEY_INTENDED, false)
        }
    }

    fun appendHistory(event: ReviveEvent) {
        val updated = ReviveHistory.append(history(), event)
        state.edit(commit = true) { putString(KEY_HISTORY, ReviveHistory.toJson(updated)) }
    }

    fun clearHistory() {
        state.edit { remove(KEY_HISTORY) }
    }

    companion object {
        const val PREF_REVIVE_ENABLED = "revive_after_crash"
        const val DEFAULT_REVIVE_ENABLED = true

        private const val STATE_FILE = "bifrost_revive"
        private const val SETTINGS_FILE = "bifrost_prefs"
        private const val KEY_INTENDED = "intended"
        private const val KEY_GAVE_UP = "gave_up"
        private const val KEY_SNAPSHOT = "snapshot"
        private const val KEY_REVIVES = "revives"
        private const val KEY_HISTORY = "history"
        private const val KEY_LAST_MANUAL_START = "last_manual_start"
        private const val KEY_LAST_IMPORTED_EXIT = "last_imported_exit"
    }
}
