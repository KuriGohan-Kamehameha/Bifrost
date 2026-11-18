package com.moonbench.bifrost.suggest

import android.content.Context
import android.content.pm.PackageManager

/** A known Nintendo Switch emulator package and the name shown to the user. */
data class SwitchEmulator(val packageName: String, val displayName: String)

/**
 * The single list of Switch emulators Bifrost recognises. Add new ones here
 * AND as a `<package>` entry in AndroidManifest.xml `<queries>` (a unit test
 * enforces that both lists agree).
 */
object SwitchEmulators {

    val KNOWN: List<SwitchEmulator> = listOf(
        SwitchEmulator("dev.eden.eden_emulator", "Eden"),
        SwitchEmulator("dev.eden.eden_emulator.nightly", "Eden Nightly"),
        SwitchEmulator("dev.eden.nx2", "Eden (nx2)"),
        SwitchEmulator("dev.igawa6.edenduo", "Eden Duo")
    )

    fun displayName(packageName: String): String =
        KNOWN.firstOrNull { it.packageName == packageName }?.displayName ?: packageName

    /** Known emulators for which [isInstalled] is true, in table order. */
    fun detect(isInstalled: (String) -> Boolean): List<SwitchEmulator> =
        KNOWN.filter { isInstalled(it.packageName) }

    /** Installed and enabled known emulators on this device. */
    fun installed(context: Context): List<SwitchEmulator> {
        val pm = context.packageManager
        return detect { pkg ->
            runCatching {
                pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0)).enabled
            }.getOrDefault(false)
        }
    }
}
