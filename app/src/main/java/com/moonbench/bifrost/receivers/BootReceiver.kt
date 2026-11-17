package com.moonbench.bifrost.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.moonbench.bifrost.revive.ServiceRevive
import com.moonbench.bifrost.schedule.ScheduleApplier
import com.moonbench.bifrost.services.HeimdallStartupManager

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val isStartupSignal = action == Intent.ACTION_BOOT_COMPLETED ||
                action == Intent.ACTION_MY_PACKAGE_REPLACED

        if (!isStartupSignal) return

        val prefs = context.getSharedPreferences("bifrost_prefs", Context.MODE_PRIVATE)

        // A reboot is not a crash: forget "was running" so no revive path
        // brings the service back unless auto-enable starts it below.
        if (action == Intent.ACTION_BOOT_COMPLETED) ServiceRevive.onBoot(context)

        ScheduleApplier.apply(context)

        if (!HeimdallStartupManager.isAutoStartEnabled(prefs)) {
            // An update kills the running service; bring it back if it was running.
            if (action == Intent.ACTION_MY_PACKAGE_REPLACED) ServiceRevive.onPackageReplaced(context)
            return
        }

        val serviceIntent = HeimdallStartupManager.buildStartupDecision(context, prefs).serviceIntent ?: return
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}
