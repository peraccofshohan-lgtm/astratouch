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
        setContent {
            AstraTouchApp(initialRoute = initialRoute)
        }
    }

    companion object {
        const val EXTRA_ROUTE = "com.astratouch.app.extra.ROUTE"
    }
}
