package com.moonbench.bifrost.revive

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Receives the revive alarm (crash safety net, watchdog hand-off, retry). */
class ReviveReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ServiceRevive.ACTION_REVIVE) return
        val source = intent.getStringExtra(ServiceRevive.EXTRA_REVIVE_SOURCE) ?: ServiceRevive.SOURCE_WATCHDOG
        ServiceRevive.reviveFromBackground(context, source)
    }
}
