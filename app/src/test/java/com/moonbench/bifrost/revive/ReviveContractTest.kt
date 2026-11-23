package com.moonbench.bifrost.revive

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Source-level guards for the parts of revive that unit tests cannot run. */
class ReviveContractTest {

    private val manifest by lazy { locate("src/main/AndroidManifest.xml").readText() }
    private val service by lazy {
        locate("src/main/java/com/moonbench/bifrost/services/LEDService.kt").readText()
    }

    @Test fun serviceIsStickyAndRestoresOnNullIntent() {
        assertTrue("LEDService must ask to be restarted", service.contains("START_STICKY"))
        assertTrue(
            "a null-intent restart must restore the saved request, not just stop",
            service.contains("return handleSystemRestart(flags, startId)")
        )
    }

    @Test fun orderlyStopsClearTheReviveFlag() {
        assertTrue(service.contains("ServiceRevive.onServiceStopped(this)"))
        assertTrue(service.contains("ServiceRevive.onServiceStarted(this, intent)"))
    }

    @Test fun reviveComponentsAreDeclaredAndPrivate() {
        val receiver = manifest.substringAfter(".revive.ReviveReceiver").substringBefore("</receiver>")
        assertTrue(receiver.contains("android:exported=\"false\""))
        val job = manifest.substringAfter(".revive.ReviveWatchdogJob").substringBefore("/>")
        assertTrue(job.contains("android.permission.BIND_JOB_SERVICE"))
        assertFalse(job.contains("android:exported=\"true\""))
    }

    @Test fun exactAlarmPermissionIsDeclared() {
        assertTrue(manifest.contains("android.permission.SCHEDULE_EXACT_ALARM"))
        // USE_EXACT_ALARM is reserved for alarm/calendar apps; do not claim it.
        assertFalse(manifest.contains("android.permission.USE_EXACT_ALARM"))
    }

    private fun locate(rel: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        repeat(5) {
            dir?.let {
                File(it, rel).takeIf(File::exists)?.let { f -> return f }
                File(it, "app/$rel").takeIf(File::exists)?.let { f -> return f }
            }
            dir = dir?.parentFile
        }
        error("could not locate '$rel' from ${System.getProperty("user.dir")}")
    }
}
