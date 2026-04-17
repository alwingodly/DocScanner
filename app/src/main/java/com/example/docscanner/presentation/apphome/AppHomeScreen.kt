package com.example.docscanner.presentation.apphome

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.docscanner.domain.model.ApplicationSession
import com.example.docscanner.domain.model.ApplicationStatus
import com.example.docscanner.domain.model.ApplicationType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── Design tokens ─────────────────────────────────────────────────────────────
private val HeroTop     = Color(0xFF0D1117)
private val HeroBotom   = Color(0xFF1C1E2A)
private val Coral       = Color(0xFFE8603C)
private val CoralSoft   = Color(0x28E8603C)
private val Ink         = Color(0xFF1A1A2E)
private val InkMid      = Color(0xFF6B6878)
private val InkDim      = Color(0xFFA5A3AE)
private val BgBase      = Color(0xFFF6F4F1)
private val BgCard      = Color(0xFFFFFFFF)
private val StrokeLight = Color(0xFFE8E4DF)

private val TypeAccents = mapOf(
    ApplicationType.BANK_ACCOUNT        to Color(0xFF0EA5E9),
    ApplicationType.HOME_LOAN           to Color(0xFFF97316),
    ApplicationType.PERSONAL_LOAN       to Color(0xFF8B5CF6),
    ApplicationType.PASSPORT_APPLICATION to Color(0xFF3B82F6),
    ApplicationType.VISA_APPLICATION    to Color(0xFF6366F1),
    ApplicationType.INSURANCE_CLAIM     to Color(0xFF10B981),
)

private val StatusMeta = mapOf(
    ApplicationStatus.PENDING     to Pair("Pending",     Color(0xFFF59E0B)),
    ApplicationStatus.IN_PROGRESS to Pair("In Progress", Color(0xFF3B82F6)),
    ApplicationStatus.COMPLETED   to Pair("Completed",   Color(0xFF10B981)),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppHomeScreen(
    onSessionClick: (ApplicationSession) -> Unit,
    onProfileClick: () -> Unit,
    viewModel: AppSessionViewModel = hiltViewModel()
) {
    val sessions by viewModel.sessions.collectAsState()
    val uiState  by viewModel.uiState.collectAsState()

    val pending     = sessions.count { it.status == ApplicationStatus.PENDING }
    val inProgress  = sessions.count { it.status == ApplicationStatus.IN_PROGRESS }
    val completed   = sessions.count { it.status == ApplicationStatus.COMPLETED }

    LaunchedEffect(uiState.createdSession) {
        uiState.createdSession?.let { session ->
            viewModel.onSessionNavigated()
            onSessionClick(session)
        }
    }

    val typeSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Box(Modifier.fillMaxSize().background(BgBase)) {

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            // ── Hero header ───────────────────────────────────────────────
            item {
                HeroHeader(
                    total      = sessions.size,
                    pending    = pending,
                    inProgress = inProgress,
                    completed  = completed,
                    onProfile  = onProfileClick
                )
            }

            // ── Section label ─────────────────────────────────────────────
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "All Sessions",
                        color = Ink,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${sessions.size}",
                        color = Coral,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // ── Empty state ───────────────────────────────────────────────
            if (sessions.isEmpty()) {
                item { EmptyState() }
            }

            // ── Session cards ─────────────────────────────────────────────
            itemsIndexed(sessions, key = { _, s -> s.id }) { index, session ->
                var visible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { visible = true }
                AnimatedVisibility(
                    visible = visible,
                    enter   = fadeIn(tween(260, delayMillis = index * 50)) +
                              expandVertically(tween(260, delayMillis = index * 50)),
                    exit    = fadeOut() + shrinkVertically()
                ) {
                    SessionCard(
                        session = session,
                        onClick = { onSessionClick(session) },
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                    )
                }
            }
        }

        // ── Extended FAB ──────────────────────────────────────────────────
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(18.dp)
        ) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Coral)
                    .clickable { viewModel.onFabClick() }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Text("New Application", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // ── Type picker bottom sheet ──────────────────────────────────────
        if (uiState.showTypePicker) {
            ModalBottomSheet(
                onDismissRequest  = viewModel::onDismiss,
                sheetState        = typeSheetState,
                containerColor    = BgCard,
                dragHandle        = {
                    Box(
                        Modifier
                            .padding(top = 12.dp, bottom = 4.dp)
                            .size(width = 36.dp, height = 4.dp)
                            .clip(CircleShape)
                            .background(StrokeLight)
                    )
                }
            ) {
                TypePickerContent(
                    onTypePicked = viewModel::onTypePicked,
                    onDismiss    = viewModel::onDismiss
                )
            }
        }

        // ── Create session dialog ─────────────────────────────────────────
        if (uiState.showCreateDialog && uiState.selectedType != null) {
            CreateSessionDialog(
                type               = uiState.selectedType!!,
                name               = uiState.sessionName,
                referenceId        = uiState.referenceId,
                isCreating         = uiState.isCreating,
                onNameChanged      = viewModel::onSessionNameChanged,
                onReferenceIdChanged = viewModel::onReferenceIdChanged,
                onConfirm          = { viewModel.onCreateSession() },
                onDismiss          = viewModel::onDismiss
            )
        }
    }
}

// ── Hero header ───────────────────────────────────────────────────────────────

@Composable
private fun HeroHeader(
    total: Int,
    pending: Int,
    inProgress: Int,
    completed: Int,
    onProfile: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(HeroTop, HeroBotom)))
    ) {
        // Decorative dot grid (top-right corner)
        Canvas(Modifier.size(100.dp).align(Alignment.TopEnd)) {
            val gap = 14.dp.toPx()
            val dotR = 1.5.dp.toPx()
            for (row in 0..5) {
                for (col in 0..4) {
                    drawCircle(
                        color  = Color.White.copy(alpha = 0.06f),
                        radius = dotR,
                        center = Offset(col * gap + gap / 2, row * gap + gap / 2)
                    )
                }
            }
        }

        // Coral arc ornament bottom-left
        Canvas(Modifier.fillMaxWidth().height(140.dp).align(Alignment.BottomStart)) {
            drawCircle(
                color  = Coral.copy(alpha = 0.07f),
                radius = 120.dp.toPx(),
                center = Offset(-40.dp.toPx(), size.height + 20.dp.toPx())
            )
        }

        Column(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            // Top row: title + profile
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Applications",
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (total == 0) "No sessions yet"
                        else "$total session${if (total != 1) "s" else ""} · Tap to open",
                        color = Color.White.copy(alpha = 0.50f),
                        fontSize = 13.sp
                    )
                }
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(0.10f))
                        .clickable(onClick = onProfile),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Person,
                        "Profile",
                        tint   = Color.White.copy(0.85f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Stat pills
            if (total > 0) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    StatPill(label = "Total",       count = total,      bg = Color.White.copy(0.12f), fg = Color.White)
                    StatPill(label = "Pending",     count = pending,    bg = Color(0x28F59E0B),       fg = Color(0xFFF59E0B))
                    StatPill(label = "Active",      count = inProgress, bg = Color(0x283B82F6),       fg = Color(0xFF3B82F6))
                    StatPill(label = "Done",        count = completed,  bg = Color(0x2810B981),       fg = Color(0xFF10B981))
                }
            }

            Spacer(Modifier.height(6.dp))
        }

        // Bottom wave cut using Canvas path
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .align(Alignment.BottomCenter)
        ) {
            drawPath(
                path = Path().apply {
                    moveTo(0f, 0f)
                    cubicTo(
                        size.width * 0.25f, size.height * 1.8f,
                        size.width * 0.75f, -size.height * 0.8f,
                        size.width, size.height
                    )
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                },
                color = BgBase
            )
        }
    }
}

@Composable
private fun StatPill(label: String, count: Int, bg: Color, fg: Color) {
    Column(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "$count",
            color = fg,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 17.sp
        )
        Text(
            label,
            color = fg.copy(alpha = 0.75f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 12.sp
        )
    }
}

// ── Session card ──────────────────────────────────────────────────────────────

@Composable
private fun SessionCard(
    session: ApplicationSession,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = TypeAccents[session.applicationType] ?: Coral
    val (statusLabel, statusColor) = StatusMeta[session.status] ?: ("Unknown" to InkMid)
    val dateStr = remember(session.createdAt) {
        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(session.createdAt))
    }

    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BgCard)
            .clickable(onClick = onClick)
    ) {
        // Left accent strip
        Box(
            Modifier
                .width(4.dp)
                .height(84.dp)
                .background(
                    Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0.4f)))
                )
        )

        Spacer(Modifier.width(12.dp))

        // Icon
        Box(
            Modifier
                .padding(vertical = 16.dp)
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(accent.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Text(session.applicationType.icon, fontSize = 22.sp)
        }

        Spacer(Modifier.width(12.dp))

        // Content
        Column(
            Modifier
                .weight(1f)
                .padding(vertical = 14.dp)
        ) {
            Text(
                session.name,
                color = Ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                buildString {
                    append(session.applicationType.displayName)
                    session.referenceNumber?.takeIf { it.isNotBlank() }?.let { append("  ·  $it") }
                },
                color = InkMid,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Status dot
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Text(statusLabel, color = statusColor, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                Text("·", color = InkDim, fontSize = 11.sp)
                Text(dateStr, color = InkDim, fontSize = 11.sp)
            }
        }

        // Arrow
        Box(
            Modifier
                .padding(end = 14.dp)
                .align(Alignment.CenterVertically)
        ) {
            Icon(
                Icons.Default.KeyboardArrowRight,
                null,
                tint   = InkDim,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ── Empty state ────────────────────────────────────────────────────────────────

@Composable
private fun EmptyState() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Scattered emoji cluster
        Box(
            Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(BgCard),
            contentAlignment = Alignment.Center
        ) {
            // Dashed border via Canvas
            Canvas(Modifier.fillMaxSize()) {
                drawRoundRect(
                    color       = Coral.copy(alpha = 0.20f),
                    style       = Stroke(width = 1.5.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(28.dp.toPx())
                )
            }
            // Emoji grid
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("🏦", fontSize = 26.sp)
                    Text("🏠", fontSize = 26.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("✈️", fontSize = 26.sp)
                    Text("🛡️", fontSize = 26.sp)
                }
            }
        }

        Spacer(Modifier.height(22.dp))

        Text(
            "Nothing here yet",
            color = Ink,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Tap \"New Application\" to create your first session and start scanning documents.",
            color = InkMid,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
    }
}

// ── Type picker bottom sheet ───────────────────────────────────────────────────

@Composable
private fun TypePickerContent(
    onTypePicked: (ApplicationType) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp)
    ) {
        Text(
            "Select Application Type",
            color = Ink,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp)
        )

        HorizontalDivider(color = StrokeLight, thickness = 0.5.dp)
        Spacer(Modifier.height(8.dp))

        ApplicationType.entries.forEach { type ->
            val accent = TypeAccents[type] ?: Coral
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onTypePicked(type) }
                    .padding(horizontal = 10.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(type.icon, fontSize = 22.sp)
                }
                Column(Modifier.weight(1f)) {
                    Text(type.displayName, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(accent)
                )
            }
        }
    }
}

// ── Create session dialog ──────────────────────────────────────────────────────

@Composable
private fun CreateSessionDialog(
    type: ApplicationType,
    name: String,
    referenceId: String,
    isCreating: Boolean,
    onNameChanged: (String) -> Unit,
    onReferenceIdChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val accent = TypeAccents[type] ?: Coral
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(BgCard)
        ) {
            // Colored top bar
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(accent, accent.copy(0.70f))))
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(type.icon, fontSize = 22.sp)
                Column {
                    Text("New Session", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(type.displayName, color = Color.White.copy(0.75f), fontSize = 12.sp)
                }
            }

            Column(Modifier.padding(20.dp)) {
                DialogInputField(
                    value        = name,
                    onValueChange = onNameChanged,
                    label        = "Session name",
                    hint         = "e.g. Alwin Home Loan",
                    accent       = accent
                )

                Spacer(Modifier.height(14.dp))

                DialogInputField(
                    value        = referenceId,
                    onValueChange = onReferenceIdChanged,
                    label        = "Reference ID",
                    hint         = "Optional",
                    accent       = accent
                )

                Spacer(Modifier.height(24.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(BgBase)
                            .clickable(enabled = !isCreating) { onDismiss() }
                            .padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Cancel", color = InkMid, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }

                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (name.isBlank()) accent.copy(0.40f) else accent)
                            .clickable(enabled = name.isNotBlank() && !isCreating) { onConfirm() }
                            .padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isCreating) {
                            CircularProgressIndicator(
                                Modifier.size(18.dp),
                                color       = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Create", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
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
    label: String,
    hint: String,
    accent: Color,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            label.uppercase(),
            color = InkDim,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(BgBase)
                .padding(horizontal = 14.dp, vertical = 13.dp)
        ) {
            BasicTextField(
                value        = value,
                onValueChange = onValueChange,
                modifier     = Modifier.fillMaxWidth(),
                textStyle    = TextStyle(color = Ink, fontSize = 14.sp),
                cursorBrush  = SolidColor(accent),
                singleLine   = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(hint, color = InkDim, fontSize = 14.sp)
                    }
                    inner()
                }
            )
        }
    }
}
