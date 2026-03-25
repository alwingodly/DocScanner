package com.example.docscanner.presentation.apphome

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.docscanner.domain.model.ApplicationSession
import com.example.docscanner.domain.model.ApplicationStatus
import com.example.docscanner.domain.model.ApplicationType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Coral = Color(0xFFE8603C)
private val Ink = Color(0xFF1A1A2E)
private val InkMid = Color(0xFF6B6878)
private val BgBase = Color(0xFFFAF8F5)
private val BgCard = Color(0xFFFFFFFF)
private val StrokeLight = Color(0xFFE5E2DD)

@Composable
fun AppHomeScreen(
    onSessionClick: (ApplicationSession) -> Unit,
    onProfileClick: () -> Unit,
    viewModel: AppSessionViewModel = hiltViewModel()
) {
    val sessions by viewModel.sessions.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.createdSession) {
        uiState.createdSession?.let { session ->
            viewModel.onSessionNavigated()
            onSessionClick(session)   // ← passes full ApplicationSession
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(BgBase)
            .statusBarsPadding()
    ) {
        Column(Modifier.fillMaxSize()) {

            // ── Header ──────────────────────────────────────────────────
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(BgCard)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Applications",
                            color = Ink,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${sessions.size} session${if (sessions.size != 1) "s" else ""}",
                            color = InkMid,
                            fontSize = 13.sp
                        )
                    }
                    IconButton(onClick = onProfileClick) {
                        Icon(
                            Icons.Default.Person,
                            "Profile",
                            tint = Ink,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(StrokeLight)
                )
            }

            // ── Session list ────────────────────────────────────────────
            if (sessions.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No applications yet", color = InkMid, fontSize = 15.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Tap + to start a new one",
                            color = InkMid.copy(0.6f),
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(sessions, key = { it.id }) { session ->
                        SessionCard(session = session, onClick = { onSessionClick(session) })
                    }
                }
            }
        }

        // ── FAB ──────────────────────────────────────────────────────────
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .size(56.dp)
                .clip(CircleShape)
                .background(Coral)
                .clickable { viewModel.onFabClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Add, "New session", tint = Color.White, modifier = Modifier.size(26.dp))
        }

        // ── Type picker bottom sheet ─────────────────────────────────────
        if (uiState.showTypePicker) {
            TypePickerSheet(
                onTypePicked = viewModel::onTypePicked,
                onDismiss = viewModel::onDismiss
            )
        }

        // ── Create session dialog ────────────────────────────────────────
        if (uiState.showCreateDialog && uiState.selectedType != null) {
            CreateSessionDialog(
                type = uiState.selectedType!!,
                name = uiState.sessionName,
                referenceId = uiState.referenceId,
                isCreating = uiState.isCreating,
                onNameChanged = viewModel::onSessionNameChanged,
                onReferenceIdChanged = viewModel::onReferenceIdChanged,
                onConfirm = { viewModel.onCreateSession() },
                onDismiss = viewModel::onDismiss
            )
        }
    }
}

// ── Session card ──────────────────────────────────────────────────────────────

@Composable
private fun SessionCard(session: ApplicationSession, onClick: () -> Unit) {
    val statusColor = when (session.status) {
        ApplicationStatus.PENDING -> Color(0xFFE6A23C)
        ApplicationStatus.IN_PROGRESS -> Color(0xFF2E6BE6)
        ApplicationStatus.COMPLETED -> Color(0xFF27AE60)
    }
    val statusLabel = when (session.status) {
        ApplicationStatus.PENDING -> "Pending"
        ApplicationStatus.IN_PROGRESS -> "In Progress"
        ApplicationStatus.COMPLETED -> "Completed"
    }
    val dateStr = remember(session.createdAt) {
        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(session.createdAt))
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon circle
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Coral.copy(0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Text(session.applicationType.icon, fontSize = 20.sp)
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                session.name,
                color = Ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                session.applicationType.displayName,
                color = InkMid,
                fontSize = 12.sp,
                maxLines = 1
            )
            Spacer(Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Status badge
                Box(
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(statusColor.copy(0.1f))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(statusLabel, color = statusColor, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
                Text(dateStr, color = InkMid.copy(0.6f), fontSize = 11.sp)
            }
        }

        Icon(
            Icons.Default.ChevronRight,
            null,
            tint = InkMid.copy(0.4f),
            modifier = Modifier.size(20.dp)
        )
    }
}

// ── Type picker ───────────────────────────────────────────────────────────────

@Composable
private fun TypePickerSheet(
    onTypePicked: (ApplicationType) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(BgCard)
                .padding(20.dp)
        ) {
            Text(
                "Select application type",
                color = Ink,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(16.dp))
            ApplicationType.entries.forEach { type ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onTypePicked(type) }
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(type.icon, fontSize = 22.sp)
                    Text(type.displayName, color = Ink, fontSize = 14.sp)
                }
                if (type != ApplicationType.entries.last()) {
                    HorizontalDivider(color = StrokeLight, thickness = 0.5.dp)
                }
            }
        }
    }
}

// ── Create session dialog ─────────────────────────────────────────────────────

@Composable
private fun CreateSessionDialog(
    type: ApplicationType,
    name: String,
    referenceId: String,
    isCreating: Boolean,
    onNameChanged: (String) -> Unit,
    onReferenceIdChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(BgCard)
                .padding(20.dp)
        ) {
            // Type badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(type.icon, fontSize = 20.sp)
                Text(type.displayName, color = Coral, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }

            Spacer(Modifier.height(14.dp))

            Text(
                "Name this session",
                color = Ink,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(16.dp))

            // Name field
            DialogInputField(
                value = name,
                onValueChange = onNameChanged,
                label = "Session name  e.g. Alwin Home Loan",
            )

            Spacer(Modifier.height(14.dp))

            // Reference field
            DialogInputField(
                value = referenceId,
                onValueChange = onReferenceIdChanged,
                label = "Reference ID  (optional)"
            )

            Spacer(Modifier.height(24.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Cancel
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(StrokeLight)
                        .clickable(enabled = !isCreating) { onDismiss() }
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Cancel", color = InkMid, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }

                // Create
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (name.isBlank()) Coral.copy(0.4f) else Coral)
                        .clickable(enabled = name.isNotBlank() && !isCreating) { onConfirm() }
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCreating) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Create", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String
) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, color = InkMid, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(BgBase)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            textStyle = TextStyle(color = Ink, fontSize = 14.sp),
            cursorBrush = SolidColor(Coral),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
        )
    }
}