package com.example.docscanner.presentation.camera

import android.Manifest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale

/**
 * Wraps camera content with permission handling.
 *
 * Flow:
 * 1. First launch → auto-requests permission via LaunchedEffect
 * 2. User grants → shows camera content immediately
 * 3. User denies → shows rationale screen with retry button
 * 4. User denies permanently → shows "open settings" message
 *
 * Why Accompanist?
 * - Raw Android permission API is callback-based and messy
 * - Accompanist gives us a Compose-native state: permissionState.status
 * - We can just check .isGranted in a when() block
 *
 * @param onPermissionGranted Content to show when camera access is allowed
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraPermissionHandler(
    onPermissionGranted: @Composable () -> Unit
) {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    // Auto-request on first composition
    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    when {
        // ✅ Permission granted — show camera
        cameraPermissionState.status.isGranted -> {
            onPermissionGranted()
        }

        // ❌ Denied but can ask again — show rationale
        cameraPermissionState.status.shouldShowRationale -> {
            PermissionDeniedContent(
                message = "DocScanner needs camera access to scan documents. Tap below to grant permission.",
                buttonText = "Grant Permission",
                onRequestPermission = { cameraPermissionState.launchPermissionRequest() }
            )
        }

        // ❌ Denied permanently — tell user to open settings
        else -> {
            PermissionDeniedContent(
                message = "Camera permission was denied. Please enable it in your device Settings → Apps → DocScanner → Permissions.",
                buttonText = "Try Again",
                onRequestPermission = { cameraPermissionState.launchPermissionRequest() }
            )
        }
    }
}

/**
 * UI shown when camera permission is not granted.
 * Clean, centered layout with icon + message + action button.
 */
@Composable
private fun PermissionDeniedContent(
    message: String,
    buttonText: String,
    onRequestPermission: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CameraAlt,
            contentDescription = "Camera",
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Camera Access Required",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(onClick = onRequestPermission) {
            Text(text = buttonText)
        }
    }
}