package com.example.docscanner.presentation.alldocuments

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.docscanner.domain.model.Document
import com.example.docscanner.domain.model.DocumentDetail

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditDetailsScreen(
    document: Document,
    onSave  : (List<DocumentDetail>) -> Unit,
    onBack  : () -> Unit
) {
    var fields by remember(document.id) {
        mutableStateOf(
            document.groupDetailRows().ifEmpty { listOf(DocumentDetail("Field", "")) }
        )
    }
    val originalFields = remember(document.id) {
        document.groupDetailRows().ifEmpty { listOf(DocumentDetail("Field", "")) }
    }
    val isDirty = fields != originalFields

    // Field pending deletion — drives the confirmation dialog
    var pendingDeleteIndex by remember { mutableStateOf<Int?>(null) }

    // Unsaved-changes back guard
    var showDiscardDialog by remember { mutableStateOf(false) }

    BackHandler {
        if (isDirty) showDiscardDialog = true else onBack()
    }

    // ── Discard-changes confirmation ──────────────────────────────────────
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            containerColor   = BgCard,
            shape            = RoundedCornerShape(20.dp),
            title = {
                Text(
                    "Discard changes?",
                    color      = Ink,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 17.sp
                )
            },
            text = {
                Text(
                    "You have unsaved changes. Leave without saving?",
                    color    = InkMid,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(DangerRed)
                        .clickable { showDiscardDialog = false; onBack() }
                        .padding(horizontal = 20.dp, vertical = 11.dp)
                ) {
                    Text(
                        "Discard",
                        color      = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize   = 14.sp
                    )
                }
            },
            dismissButton = {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(BgSurface)
                        .border(1.dp, StrokeLight, RoundedCornerShape(12.dp))
                        .clickable { showDiscardDialog = false }
                        .padding(horizontal = 20.dp, vertical = 11.dp)
                ) {
                    Text("Keep editing", color = InkMid, fontSize = 14.sp)
                }
            }
        )
    }

    // ── Delete-field confirmation ─────────────────────────────────────────
    if (pendingDeleteIndex != null) {
        val idx   = pendingDeleteIndex!!
        val label = fields.getOrNull(idx)?.label?.takeIf { it.isNotBlank() } ?: "this field"
        AlertDialog(
            onDismissRequest = { pendingDeleteIndex = null },
            containerColor   = BgCard,
            shape            = RoundedCornerShape(20.dp),
            title = {
                Text(
                    "Delete field?",
                    color      = Ink,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 17.sp
                )
            },
            text = {
                Text(
                    "\"$label\" will be removed from this document.",
                    color    = InkMid,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(DangerRed)
                        .clickable {
                            fields = fields.toMutableList().apply { removeAt(idx) }
                            pendingDeleteIndex = null
                        }
                        .padding(horizontal = 20.dp, vertical = 11.dp)
                ) {
                    Text(
                        "Delete",
                        color      = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize   = 14.sp
                    )
                }
            },
            dismissButton = {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(BgSurface)
                        .border(1.dp, StrokeLight, RoundedCornerShape(12.dp))
                        .clickable { pendingDeleteIndex = null }
                        .padding(horizontal = 20.dp, vertical = 11.dp)
                ) {
                    Text("Cancel", color = InkMid, fontSize = 14.sp)
                }
            }
        )
    }

    // ── Screen scaffold ───────────────────────────────────────────────────
    Column(
        Modifier
            .fillMaxSize()
            .background(BgBase)
    ) {
        // Status bar
        Spacer(
            Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBarsIgnoringVisibility)
                .background(BgCard)
        )

        // ── Top bar ───────────────────────────────────────────────────────
        Box(
            Modifier
                .fillMaxWidth()
                .background(BgCard)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { if (isDirty) showDiscardDialog = true else onBack() }) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint     = Ink,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        "Edit details",
                        color      = Ink,
                        fontSize   = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        document.name,
                        color    = InkDim,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // Dirty indicator dot
                if (isDirty) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Coral)
                    )
                    Spacer(Modifier.width(8.dp))
                }
            }
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(StrokeLight)
            )
        }

        // ── Field list ────────────────────────────────────────────────────
        LazyColumn(
            modifier       = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                start  = 16.dp, end    = 16.dp,
                top    = 20.dp, bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {

            // Document chip
            item(key = "preview") {
                DocPreviewChip(document = document)
                Spacer(Modifier.height(20.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        "FIELDS",
                        color         = InkDim,
                        fontSize      = 10.sp,
                        fontWeight    = FontWeight.SemiBold,
                        letterSpacing = 0.8.sp
                    )
                    if (fields.isNotEmpty()) {
                        Text(
                            "${fields.size} field${if (fields.size != 1) "s" else ""}",
                            color    = InkDim,
                            fontSize = 11.sp
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            // Empty state
            if (fields.isEmpty()) {
                item(key = "empty") {
                    EmptyFieldsPlaceholder()
                    Spacer(Modifier.height(12.dp))
                }
            }

            // Field rows — rendered as a single rounded card
            if (fields.isNotEmpty()) {
                item(key = "fields_card") {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(BgCard)
                            .border(1.dp, StrokeLight, RoundedCornerShape(16.dp))
                    ) {
                        fields.forEachIndexed { index, field ->
                            FieldRow(
                                field         = field,
                                index         = index,
                                total         = fields.size,
                                onLabelChange = { newLabel ->
                                    fields = fields.toMutableList().apply {
                                        this[index] = this[index].copy(label = newLabel)
                                    }
                                },
                                onValueChange = { newValue ->
                                    fields = fields.toMutableList().apply {
                                        this[index] = this[index].copy(
                                            value     = newValue,
                                            multiline = newValue.length > 42 || newValue.contains("\n")
                                        )
                                    }
                                },
                                onDelete = { pendingDeleteIndex = index }
                            )
                            if (index < fields.lastIndex) {
                                HorizontalDivider(
                                    color     = StrokeLight,
                                    thickness = 0.5.dp
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }

            // Add field row
            item(key = "add") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(BgCard)
                        .border(1.dp, StrokeLight, RoundedCornerShape(14.dp))
                        .clickable {
                            fields = fields + DocumentDetail("Field", "")
                        }
                        .padding(horizontal = 16.dp, vertical = 15.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(CoralSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            tint     = Coral,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        "Add a field",
                        color      = Ink,
                        fontSize   = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // ── Bottom bar ────────────────────────────────────────────────────
        Box(
            Modifier
                .fillMaxWidth()
                .background(BgCard)
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(StrokeLight)
                    .align(Alignment.TopCenter)
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Cancel
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(BgSurface)
                        .border(1.dp, StrokeLight, RoundedCornerShape(14.dp))
                        .clickable { if (isDirty) showDiscardDialog = true else onBack() }
                        .padding(vertical = 15.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Cancel",
                        color      = InkMid,
                        fontSize   = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                // Save
                Box(
                    Modifier
                        .weight(2f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isDirty) Coral else BgSurface)
                        .border(
                            1.dp,
                            if (isDirty) Color.Transparent else StrokeLight,
                            RoundedCornerShape(14.dp)
                        )
                        .clickable(enabled = isDirty) {
                            onSave(fields)
                            onBack()
                        }
                        .padding(vertical = 15.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Save changes",
                        color      = if (isDirty) Color.White else InkDim,
                        fontSize   = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Doc chip at the top of the list
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun DocPreviewChip(document: Document) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BgCard)
            .border(1.dp, StrokeLight, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(BgSurface)
                .border(1.dp, StrokeLight, RoundedCornerShape(10.dp))
        ) {
            if (document.thumbnailPath != null) {
                AsyncImage(
                    model              = document.thumbnailPath,
                    contentDescription = null,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    Icons.Default.Image, null,
                    tint     = InkDim,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(20.dp)
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                document.name,
                color      = Ink,
                fontSize   = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(TypeColors[document.docClassLabel ?: "Other"] ?: InkMid)
                )
                Text(
                    document.docClassLabel ?: "Other",
                    color    = InkDim,
                    fontSize = 11.sp
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Single field row inside the card
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun FieldRow(
    field        : DocumentDetail,
    index        : Int,
    total        : Int,
    onLabelChange: (String) -> Unit,
    onValueChange: (String) -> Unit,
    onDelete     : () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    // Highlight the whole row when any child is focused
    Box(
        Modifier
            .fillMaxWidth()
            .background(
                if (isFocused) Coral.copy(alpha = 0.03f) else Color.Transparent
            )
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Left: label + value stacked
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Label
                BasicTextField(
                    value         = field.label,
                    onValueChange = onLabelChange,
                    singleLine    = true,
                    cursorBrush   = SolidColor(Coral),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    textStyle     = TextStyle(
                        color      = InkMid,
                        fontSize   = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.3.sp
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { isFocused = it.isFocused },
                    decorationBox = { inner ->
                        if (field.label.isEmpty()) {
                            Text(
                                "Label",
                                color    = InkDim,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        inner()
                    }
                )

                // Value
                BasicTextField(
                    value           = field.value,
                    onValueChange   = onValueChange,
                    cursorBrush     = SolidColor(Coral),
                    textStyle       = TextStyle(
                        color      = Ink,
                        fontSize   = 15.sp,
                        fontWeight = FontWeight.Normal,
                        lineHeight = 22.sp
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { isFocused = it.isFocused },
                    decorationBox = { inner ->
                        if (field.value.isEmpty()) {
                            Text(
                                "Enter value…",
                                color    = InkDim,
                                fontSize = 15.sp
                            )
                        }
                        inner()
                    }
                )
            }

            Spacer(Modifier.width(4.dp))

            // Delete button — always visible
            IconButton(
                onClick  = onDelete,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = "Delete field",
                    tint     = DangerRed.copy(alpha = 0.70f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Left accent bar when focused
        if (isFocused) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .width(3.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp))
                    .background(Coral)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Empty state
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyFieldsPlaceholder() {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BgCard)
            .border(1.dp, StrokeLight, RoundedCornerShape(16.dp))
            .padding(vertical = 36.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(BgSurface)
                    .border(1.dp, StrokeLight, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.NoteAdd,
                    contentDescription = null,
                    tint     = InkDim,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                "No fields",
                color      = Ink,
                fontSize   = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Tap \"Add a field\" to create one",
                color    = InkDim,
                fontSize = 12.sp
            )
        }
    }
}
