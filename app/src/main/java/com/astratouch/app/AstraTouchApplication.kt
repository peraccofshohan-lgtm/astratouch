package com.astratouch.app

import android.app.Application
import android.util.Log
import com.astratouch.app.data.repository.MacroRepository
import com.astratouch.app.data.repository.ProfileRepository
import com.astratouch.app.data.prefs.PreferencesStore
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class AstraTouchApplication : Application() {

    @Inject lateinit var profileRepository: ProfileRepository
    @Inject lateinit var macroRepository: MacroRepository
    @Inject lateinit var preferencesStore: PreferencesStore

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // First-run seeding: profiles, themes, gestures, radial layouts and macros.
        // Never overwrites existing rows, so upgrades keep user configuration.
        appScope.launch {
            runCatching { profileRepository.ensureSeeded() }
                .onFailure { Log.e(TAG, "Profile seeding failed", it) }
            runCatching { macroRepository.ensureSeeded() }
                .onFailure { Log.e(TAG, "Macro seeding failed", it) }
        }
    }

    companion object {
        private const val TAG = "AstraTouchApp"
    }
}
