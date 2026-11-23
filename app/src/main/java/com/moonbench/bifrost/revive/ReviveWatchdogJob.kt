package com.moonbench.bifrost.revive

import android.app.job.JobParameters
import android.app.job.JobService

/**
 * Periodic check (15 min, Doze-friendly) that the LED service is running when
 * it should be. Scheduled only while the service is meant to run; cancelled
 * on every deliberate stop.
 */
class ReviveWatchdogJob : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        runCatching { ServiceRevive.onWatchdog(applicationContext) }
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean = false
}
