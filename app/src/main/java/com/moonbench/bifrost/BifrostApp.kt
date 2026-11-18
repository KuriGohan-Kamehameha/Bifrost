package com.moonbench.bifrost

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import com.moonbench.bifrost.presets.PresetMigration
import com.moonbench.bifrost.suggest.SuggestionStore
import com.moonbench.bifrost.tools.CrashReporter

class BifrostApp : Application() {

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        runCatching {
            PresetMigration.run(getSharedPreferences("bifrost_prefs", Context.MODE_PRIVATE))
        }
    }

    /**
     * A wallpaper/theme change re-generates the system palette and arrives as a
     * configuration change; keep the "Cocoon Match" preset in step with it.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        runCatching { SuggestionStore.refreshCocoonMatch(this, SuggestionStore.prefs(this)) }
    }
}
