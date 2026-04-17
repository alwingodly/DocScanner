package com.example.docscanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.docscanner.data.local.SessionManager
import com.example.docscanner.navigation.DocScannerNavHost
import com.example.docscanner.navigation.Screen
import com.example.docscanner.ui.theme.DocScannerTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applySystemBars()

        setContent {
            DocScannerTheme {
                val startDestination = if (sessionManager.isLoggedIn())
                    Screen.AppHome.route else Screen.Login.route
                DocScannerNavHost(startDestination = startDestination)   // ← pass down
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applySystemBars()
    }

    private fun applySystemBars() {
        // Re-apply after returning from the external scanner, which can temporarily
        // change system UI visibility and leave top insets in a bad state.
        val coralArgb = android.graphics.Color.parseColor("#E8603C")
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(coralArgb),
            navigationBarStyle = SystemBarStyle.light(
                android.graphics.Color.parseColor("#FAF8F5"),
                android.graphics.Color.parseColor("#FAF8F5")
            )
        )
    }
}
