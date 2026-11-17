package com.moonbench.bifrost.revive

import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial
import com.moonbench.bifrost.R
import com.moonbench.bifrost.ui.BifrostAlertDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Settings-panel controls: the revive toggle and the crash/revive history. */
object ReviveSettingsUi {

    fun bind(activity: AppCompatActivity, toggle: SwitchMaterial, historyButton: View) {
        val store = ReviveStore(activity)
        toggle.isChecked = store.isEnabled
        toggle.setOnCheckedChangeListener { _, isChecked ->
            ServiceRevive.onSettingChanged(activity, isChecked)
        }
        historyButton.setOnClickListener { showHistory(activity) }
    }

    fun showHistory(activity: AppCompatActivity) {
        val store = ReviveStore(activity)
        runCatching { ServiceRevive.importExitHistory(activity, store) }
        val events = store.history()
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val body = if (events.isEmpty()) {
            activity.getString(R.string.revive_history_empty)
        } else {
            ReviveHistory.format(events) { format.format(Date(it)) }
        }
        val status = activity.getString(
            R.string.revive_history_status,
            activity.getString(if (store.isEnabled) R.string.revive_on else R.string.revive_off),
            activity.getString(
                if (ServiceRevive.canScheduleExactAlarms(activity)) R.string.revive_allowed else R.string.revive_not_allowed
            ),
            activity.getString(
                if (ServiceRevive.isIgnoringBatteryOptimizations(activity)) {
                    R.string.revive_battery_unrestricted
                } else {
                    R.string.revive_battery_optimized
                }
            )
        )
        BifrostAlertDialog().show(
            activity = activity,
            title = activity.getString(R.string.revive_history_title),
            subtitle = status,
            body = body,
            positiveLabelResId = R.string.revive_history_close,
            negativeLabelResId = null,
            cancelable = true,
            onConfirm = {}
        )
    }
}
