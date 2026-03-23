package com.example.docscanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.docscanner.navigation.DocScannerNavHost
import com.example.docscanner.ui.theme.DocScannerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Coral primary color (#E8603C) as status bar background with light (white) icons
        val coralArgb = android.graphics.Color.parseColor("#E8603C")
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(coralArgb),
            navigationBarStyle = SystemBarStyle.light(
                android.graphics.Color.parseColor("#FAF8F5"),
                android.graphics.Color.parseColor("#FAF8F5")
            )
        )

        setContent {
            DocScannerTheme {
                DocScannerNavHost()
            }
        }
    }
}