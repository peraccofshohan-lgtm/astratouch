package com.astratouch.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val initialRoute = intent?.getStringExtra(EXTRA_ROUTE)
        // Tapping the "Bubble is ready" notification opens the app in the
        // foreground, which is the only legal moment to start the overlay's
        // specialUse foreground service after a reboot on Android 12+.
        if (intent?.getBooleanExtra(EXTRA_RESTORE_OVERLAY, false) == true) {
            com.astratouch.app.service.FloatingOverlayService.start(this)
        }
        setContent {
            AstraTouchApp(initialRoute = initialRoute)
        }
    }

    companion object {
        const val EXTRA_ROUTE = "com.astratouch.app.extra.ROUTE"
        const val EXTRA_RESTORE_OVERLAY = "com.astratouch.app.extra.RESTORE_OVERLAY"
    }
}
