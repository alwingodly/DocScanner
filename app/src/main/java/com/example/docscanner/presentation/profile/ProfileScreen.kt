package com.example.docscanner.presentation.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

private val Coral = Color(0xFFE8603C)
private val Ink = Color(0xFF1A1A2E)
private val InkMid = Color(0xFF6B6878)
private val BgBase = Color(0xFFFAF8F5)
private val BgCard = Color(0xFFFFFFFF)
private val StrokeLight = Color(0xFFE5E2DD)

@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showLogoutDialog by remember { mutableStateOf(false) }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Log out?", fontWeight = FontWeight.SemiBold) },
            text = { Text("You'll need to log in again to access your sessions.") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    viewModel.logout(onLogout)
                }) {
                    Text("Log out", color = Color(0xFFD94040), fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel", color = InkMid)
                }
            }
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(BgBase)
            .systemBarsPadding()
    ) {
        // ── Header ──────────────────────────────────────────────────────
        Box(
            Modifier
                .fillMaxWidth()
                .background(BgCard)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Default.ArrowBack, "Back",
                        tint = Ink, modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    "Profile & Settings",
                    color = Ink,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(StrokeLight)
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // ── User info card ───────────────────────────────────────────
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(BgCard)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Avatar circle
                Box(
                    Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Coral),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        state.username.take(1).uppercase(),
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column {
                    Text(
                        state.username,
                        color = Ink,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "DocScanner User",
                        color = InkMid,
                        fontSize = 12.sp
                    )
                }
            }

            // ── App settings ─────────────────────────────────────────────
            SectionCard(title = "App Settings") {
                ToggleRow(
                    icon = Icons.Default.Notifications,
                    label = "Notifications",
                    checked = state.notificationsEnabled,
                    onToggle = { viewModel.toggleNotifications() }
                )
                HorizontalDivider(color = StrokeLight, thickness = 0.5.dp)
                ToggleRow(
                    icon = Icons.Default.DarkMode,
                    label = "Dark Theme",
                    checked = state.darkTheme,
                    onToggle = { viewModel.toggleDarkTheme() }
                )
            }

            // ── About ────────────────────────────────────────────────────
            SectionCard(title = "About") {
                InfoRow(icon = Icons.Default.Info, label = "App Version", value = "1.0.0")
                HorizontalDivider(color = StrokeLight, thickness = 0.5.dp)
                InfoRow(icon = Icons.Default.Business, label = "Developer", value = "Ospyn Technologies")
            }

            // ── Logout ───────────────────────────────────────────────────
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = { showLogoutDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFD94040)
                )
            ) {
                Icon(
                    Icons.Default.Logout, null,
                    modifier = Modifier.size(18.dp),
                    tint = Color.White
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Log Out",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }
}

// ── Reusable components ──────────────────────────────────────────────────────

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BgCard)
    ) {
        Text(
            title,
            color = InkMid,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
        HorizontalDivider(color = StrokeLight, thickness = 0.5.dp)
        content()
    }
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = InkMid, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = Ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Coral)
        )
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = InkMid, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = Ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = InkMid, fontSize = 13.sp)
    }
}