package com.example.docscanner.presentation.alldocuments

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.docscanner.domain.model.DocClassType
import com.example.docscanner.domain.model.Document
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.nativeCanvas
import com.example.docscanner.domain.model.Folder
import com.example.docscanner.presentation.shared.ScanSaveFeedback
import com.example.docscanner.presentation.shared.ScannedMismatch
import kotlinx.coroutines.delay

// ── Design tokens ─────────────────────────────────────────────────────────────
internal val BgBase = Color(0xFFF7F5F2)
internal val BgSurface = Color(0xFFEFECE8)
internal val BgCard = Color(0xFFFFFFFF)
internal val StrokeLight = Color(0xFFE8E4DF)
internal val StrokeMid = Color(0xFFCDC8C0)
internal val Coral = Color(0xFFE8603C)
internal val CoralDark = Color(0xFFD94860)
internal val Ink = Color(0xFF18181B)
internal val InkMid = Color(0xFF71717A)
internal val InkDim = Color(0xFFA1A1AA)
internal val DangerRed = Color(0xFFDC2626)
private val PdfBadgeBg = Color(0xFFDC2626)

internal val GreenAccent = Color(0xFF16A34A)

internal val CoralSoft = Color(0x1AE8603C)
internal val TypeColors = mapOf(
    "Aadhaar" to Color(0xFF2563EB),
    "PAN Card" to Color(0xFFD97706),
    "Passport" to Color(0xFF7C3AED),
    "Voter ID" to Color(0xFF059669),
    "DL" to Color(0xFFDC2626),
    "Other" to Color(0xFF71717A)
)

private val ALL_TYPES = DocClassType.entries.map { it.displayName }

private data class GroupMoveTarget(
    val title: String,
    val subtitle: String,
    val currentLabel: String?,
    val documents: List<Document>
)

private fun normalizeSectionLabel(label: String?): String = when (label ?: "Other") {
    "Aadhaar Front", "Aadhaar Back" -> "Aadhaar"
    else -> label ?: "Other"
}

private fun resolveSectionLabel(documents: List<Document>): String? =
    documents
        .map { normalizeSectionLabel(it.docClassLabel) }
        .distinct()
        .singleOrNull()

private fun Document.aadhaarSideLabel(): String = when {
    aadhaarSide == "FRONT" || docClassLabel == "Aadhaar Front" -> "Front"
    aadhaarSide == "BACK" || docClassLabel == "Aadhaar Back" -> "Back"
    docClassLabel == "Aadhaar" -> "Aadhaar"
    else -> "Type"
}

private fun Document.isMergeable() = pdfPath == null && !isMergedSource

private fun supportsGenericGrouping(label: String?): Boolean {
    val normalized = normalizeSectionLabel(label)
    return !normalized.equals("Aadhaar", ignoreCase = true) &&
        !normalized.equals("Passport", ignoreCase = true)
}

enum class DocumentTab { ALL, UNCLASSIFIED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllDocumentsScreen(
    isSelectMode: Boolean,
    selectAllTrigger: Int,
    onSelectToggle: () -> Unit,
    onEnterSelectMode: () -> Unit = {},
    onDocumentClick: (Document) -> Unit,
    onScanClick: () -> Unit,
    onScanToFolder: (Folder) -> Unit = {},
    onMergeSelected: (List<Document>) -> Unit,
    columnCount: Int = 3,
    showUnclassifiedOnly: Boolean = false,
    onSelectedCountChanged: (Int) -> Unit = {},
    onDocumentCountChanged: (Int) -> Unit = {},
    viewModel: AllDocumentsViewModel = hiltViewModel(),
    pendingMismatches   : List<ScannedMismatch> = emptyList(),
    onResolveMismatch   : (docId: String, label: String) -> Unit = { _, _ -> },
    onDismissMismatches : () -> Unit = {},
    isScanProcessing    : Boolean = false,
    scanFeedback        : ScanSaveFeedback? = null,
    onScanFeedbackShown : () -> Unit = {},
    onGroupTap: (String) -> Unit = {},
    ) {
    val allDocuments by viewModel.documents.collectAsState()
    val groupedByType by viewModel.groupedByDocType.collectAsState()
    val otherNames by viewModel.otherSectionNames.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val context = LocalContext.current
    val allFolders by viewModel.allFolders.collectAsState()
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var contextDoc by remember { mutableStateOf<Document?>(null) }
    var showMoveSheet by remember { mutableStateOf(false) }
    val aadhaarGroups   by viewModel.aadhaarGroups.collectAsState()
    val passportGroups  by viewModel.passportGroups.collectAsState()
    val docGroups       by viewModel.docGroups.collectAsState()
    var showGroupNameDialog by remember { mutableStateOf(false) }
    var pendingGroupDocIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var groupNameText by remember { mutableStateOf("") }
    var contextAadhaarGroupId    by remember { mutableStateOf<String?>(null) }
    var contextPassportGroupId   by remember { mutableStateOf<String?>(null) }
    var pendingPassportPairId    by remember { mutableStateOf<String?>(null) }
    var moveGroupTarget by remember { mutableStateOf<GroupMoveTarget?>(null) }


    val contextAadhaarGroup = remember(contextDoc, aadhaarGroups) {
        contextDoc?.let { doc ->
            aadhaarGroups.firstOrNull { g ->
                g.frontDoc?.id == doc.id || g.backDoc?.id == doc.id
            }
        }
    }

    val documents = remember(allDocuments, showUnclassifiedOnly) {
        if (showUnclassifiedOnly)
            allDocuments.filter { it.docClassLabel == null || it.docClassLabel == "Other" }
        else
            allDocuments
    }

    val filteredGrouped = remember(groupedByType, showUnclassifiedOnly, otherNames) {
        if (showUnclassifiedOnly)
            groupedByType.filterKeys { it in otherNames }
        else
            groupedByType.filterKeys { it !in otherNames }
    }

    var selectedOrder by remember { mutableStateOf(listOf<String>()) }
    val selectedIds = selectedOrder.toSet()
    var groupingScopeIds by remember { mutableStateOf<Set<String>?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var typeChangeDoc by remember { mutableStateOf<Document?>(null) }
    val sheetState = rememberModalBottomSheetState()
    var collapsedSections by remember { mutableStateOf(setOf<String>()) }
    var contextGroupId by remember { mutableStateOf<String?>(null) }
    var showGroupRenameDialog by remember { mutableStateOf(false) }
    var groupRenameText by remember { mutableStateOf("") }
    val docGroupNames by viewModel.docGroupNames.collectAsState()
    var pendingAadhaarPairId by remember { mutableStateOf<String?>(null) }

    val hasFolders = filteredGrouped.isNotEmpty()
    val feedbackBottomPadding = if (isSelectMode) 20.dp else 96.dp

    fun openGroupMoveSheet(
        title: String,
        subtitle: String,
        docs: List<Document>
    ) {
        val uniqueDocs = docs.distinctBy { it.id }
        if (uniqueDocs.isEmpty()) return
        moveGroupTarget = GroupMoveTarget(
            title = title,
            subtitle = subtitle,
            currentLabel = resolveSectionLabel(uniqueDocs),
            documents = uniqueDocs
        )
    }

    LaunchedEffect(isSelectMode) {
        if (!isSelectMode) {
            selectedOrder = emptyList()
            groupingScopeIds = null
        }
    }
    LaunchedEffect(documents) {
        selectedOrder = selectedOrder.filter { id -> documents.any { it.id == id } }
        groupingScopeIds = groupingScopeIds?.intersect(documents.map { it.id }.toSet())?.takeIf { it.isNotEmpty() }
    }
    LaunchedEffect(filteredGrouped) {
        val shouldCollapse = filteredGrouped
            .filter { (_, docs) -> docs.isEmpty() }
            .keys
            .toSet()
        collapsedSections = shouldCollapse
    }
    val selectedCount = selectedIds.size
    val documentCount = documents.size
    LaunchedEffect(selectedCount) { onSelectedCountChanged(selectedCount) }
    LaunchedEffect(documentCount) { onDocumentCountChanged(documentCount) }
    LaunchedEffect(scanFeedback?.id, isScanProcessing) {
        if (!isScanProcessing && scanFeedback != null) {
            delay(3200)
            onScanFeedbackShown()
        }
    }

    LaunchedEffect(selectAllTrigger) {
        if (selectAllTrigger > 0 && isSelectMode) {
            val all = documents.isNotEmpty() && documents.all { it.id in selectedIds }
            selectedOrder = if (all) emptyList() else documents.map { it.id }
        }
    }

    Box(Modifier
        .fillMaxSize()
        .background(BgBase)) {

        when {
            isLoading -> Unit
            !hasFolders && documents.isEmpty() -> EmptyState(showUnclassifiedOnly)

//            isSelectMode -> GallerySelectGrid(
//                documents = documents,
//                selectedIds = selectedIds,
//                columnCount = columnCount,
//                onSelect = { id ->
//                    selectedOrder =
//                        if (id in selectedIds) selectedOrder - id else selectedOrder + id
//                }
//            )

            else -> GallerySectionedGrid(
                grouped = filteredGrouped,
                allFolders = allFolders,
                collapsedSections = collapsedSections,
                onToggleSection = { label ->
                    collapsedSections = if (label in collapsedSections)
                        collapsedSections - label else collapsedSections + label
                },
                onSectionReorder = { label, from, to ->
                    val docs = groupedByType[label] ?: return@GallerySectionedGrid
                    viewModel.reorderSection(label, from, to, docs)
                },
                onScanToFolder = onScanToFolder,
                onScanClick = onScanClick,     // ← add this
                columnCount = columnCount,
                onDocumentClick = onDocumentClick,
                onBadgeTap = { typeChangeDoc = it },
                onMoreTap = { contextDoc = it },
                isSelectMode = isSelectMode,
                selectedIds = selectedIds,
                onSelect = { id ->
                    if (groupingScopeIds != null && id !in groupingScopeIds!!) return@GallerySectionedGrid
                    selectedOrder = if (id in selectedIds) selectedOrder - id else selectedOrder + id
                },
                onSelectSection = { sectionDocs ->
                    val sectionIds = sectionDocs.map { it.id }
                    val sectionIdSet = sectionIds.toSet()
                    if (groupingScopeIds != null && !sectionIds.all { it in groupingScopeIds!! }) {
                        return@GallerySectionedGrid
                    }
                    val allSelected = sectionIds.all { it in selectedIds }
                    selectedOrder = if (allSelected)
                        selectedOrder.filter { it !in sectionIdSet }
                    else
                        (selectedOrder + sectionIds).distinct()
                },
                onLongPressSection = { sectionDocs ->
                    val ids = sectionDocs.map { it.id }
                    if (!isSelectMode) onEnterSelectMode()   // ← was onSelectToggle()
                    groupingScopeIds = ids.toSet()
                    selectedOrder = ids                      // ← only this section, fresh
                },
                aadhaarGroups        = aadhaarGroups,
                passportGroups       = passportGroups,
                onManualGroup        = { id1, id2 -> viewModel.manuallyGroupAadhaar(id1, id2) },
                onManualPassportGroup = { id1, id2 -> viewModel.manuallyGroupPassport(id1, id2) },
                onAddToGroup = { docId, groupId, side ->
                    viewModel.addDocToExistingGroup(docId, groupId, side)
                },
                onGroupTap = onGroupTap,
                docGroups = docGroups,
                selectedOrder = selectedOrder,
                onGroupMoreTap = { groupId -> contextGroupId = groupId },
                docGroupNames = docGroupNames,
                onAadhaarGroupMoreTap  = { groupId -> contextAadhaarGroupId  = groupId },
                onPassportGroupMoreTap = { groupId -> contextPassportGroupId = groupId },
                pendingAadhaarPairId = pendingAadhaarPairId,
                onPendingAadhaarPairChange = { pendingAadhaarPairId = it },
                pendingPassportPairId = pendingPassportPairId,
                onPendingPassportPairChange = { pendingPassportPairId = it },
                groupingScopeIds = groupingScopeIds,
            )
        }

        AnimatedVisibility(
            visible = isScanProcessing || scanFeedback != null,
            enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = feedbackBottomPadding)
        ) {
            ScanFeedbackBanner(
                isProcessing = isScanProcessing,
                feedback = scanFeedback
            )
        }

        // ── Select action bar ─────────────────────────────────────────────────
        AnimatedVisibility(
            visible = isSelectMode && (selectedOrder.isNotEmpty() || groupingScopeIds != null),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit  = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val selectedDocs = remember(selectedOrder, documents) {
                documents.filter { it.id in selectedIds }
            }
            val isGroupingFlow = groupingScopeIds != null
            // All selected docs are the same generic-groupable type → can group
            val canGroup = remember(selectedDocs) {
                val normalizedType = selectedDocs
                    .map { normalizeSectionLabel(it.docClassLabel) }
                    .distinct()
                    .singleOrNull()

                selectedDocs.size >= 2 &&
                    normalizedType != null &&
                    supportsGenericGrouping(normalizedType)
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // ── Group button — shown throughout group-selection flow ──────
                if (isGroupingFlow || canGroup) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .shadow(16.dp, RoundedCornerShape(14.dp),
                                ambientColor = Color(0xFF059669).copy(if (canGroup) 0.25f else 0.10f))
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (canGroup) Color(0xFF059669) else BgSurface)
                            .border(
                                1.dp,
                                if (canGroup) Color.Transparent else StrokeLight,
                                RoundedCornerShape(14.dp)
                            )
                            .clickable(enabled = canGroup) {
                                pendingGroupDocIds = selectedOrder.toList()
                                groupNameText = ""
                                showGroupNameDialog = true
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CreateNewFolder, null,
                                tint = if (canGroup) Color.White else InkDim, modifier = Modifier.size(18.dp))
                            Text(
                                if (canGroup) {
                                    "Group ${selectedIds.size} documents"
                                } else {
                                    "Select at least 2 documents to group"
                                },
                                color = if (canGroup) Color.White else InkDim, fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // ── Delete button — always visible in normal multi-select flow ─
                if (!isGroupingFlow) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .shadow(16.dp, RoundedCornerShape(14.dp),
                                ambientColor = DangerRed.copy(0.25f))
                            .clip(RoundedCornerShape(14.dp))
                            .background(DangerRed)
                            .clickable { showDeleteDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.DeleteOutline, null,
                                tint = Color.White, modifier = Modifier.size(18.dp))
                            Text(
                                "Delete ${selectedIds.size} document${if (selectedIds.size != 1) "s" else ""}",
                                color = Color.White, fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // ── Scan FAB ──────────────────────────────────────────────────────────
        if (!isSelectMode) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
                    .navigationBarsPadding()
                    .shadow(20.dp, RoundedCornerShape(18.dp), ambientColor = Coral.copy(0.35f))
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        if (isScanProcessing) Brush.linearGradient(listOf(StrokeMid, StrokeMid))
                        else Brush.linearGradient(listOf(Coral, CoralDark))
                    )
                    .clickable(enabled = !isScanProcessing, onClick = onScanClick)
                    .padding(horizontal = 22.dp, vertical = 15.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CameraAlt,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        if (isScanProcessing) "Saving..." else "Scan",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }

    // ── Type change sheet ─────────────────────────────────────────────────────
    if (typeChangeDoc != null) {
        val doc = typeChangeDoc!!
        val currentLabel = doc.docClassLabel ?: "Other"
        ModalBottomSheet(
            onDismissRequest = { typeChangeDoc = null },
            sheetState = sheetState,
            containerColor = BgCard,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = {
                Box(
                    Modifier
                        .padding(top = 12.dp, bottom = 4.dp)
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(StrokeMid)
                )
            }
        ) {
            Column(Modifier
                .fillMaxWidth()
                .padding(bottom = 36.dp)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(BgSurface)
                            .border(1.dp, StrokeLight, RoundedCornerShape(10.dp))
                    ) {
                        if (doc.thumbnailPath != null)
                            AsyncImage(
                                model = doc.thumbnailPath, contentDescription = null,
                                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                            )
                        else Icon(
                            Icons.Default.Image, null, tint = InkDim,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(22.dp)
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            doc.name,
                            color = Ink,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(TypeColors[currentLabel] ?: InkMid)
                            )
                            Text("Currently: $currentLabel", color = InkMid, fontSize = 12.sp)
                        }
                    }
                }
                HorizontalDivider(color = StrokeLight, thickness = 0.5.dp)
                Text(
                    "CHANGE TYPE",
                    color = InkDim,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )
                ALL_TYPES.forEach { typeName ->
                    val typeColor = TypeColors[typeName] ?: InkMid
                    val isCurrent = typeName == currentLabel
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .then(if (isCurrent) Modifier.background(typeColor.copy(0.07f)) else Modifier)
                            .clickable {
                                if (!isCurrent) {
                                    viewModel.changeDocumentType(doc, typeName)
                                    Toast.makeText(
                                        context,
                                        "Changed to $typeName",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                typeChangeDoc = null
                            }
                            .padding(horizontal = 20.dp, vertical = 15.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(typeColor.copy(0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(typeColor))
                        }
                        Spacer(Modifier.width(14.dp))
                        Text(
                            typeName, color = if (isCurrent) typeColor else Ink, fontSize = 15.sp,
                            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal
                        )
                        Spacer(Modifier.weight(1f))
                        if (isCurrent)
                            Box(
                                Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(typeColor.copy(0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    null,
                                    tint = typeColor,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                    }
                }
            }
        }
    }

    // ── Delete dialog ─────────────────────────────────────────────────────────
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = BgCard,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    "Delete ${selectedIds.size} document${if (selectedIds.size != 1) "s" else ""}?",
                    color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp
                )
            },
            text = {
                Text(
                    "This action cannot be undone. The selected documents will be permanently removed.",
                    color = InkMid, fontSize = 14.sp, lineHeight = 20.sp
                )
            },
            confirmButton = {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(DangerRed)
                        .clickable {
                            documents.filter { it.id in selectedIds }
                                .forEach { viewModel.deleteDocument(it) }
                            selectedOrder = emptyList()
                            if (isSelectMode) onSelectToggle()
                            showDeleteDialog = false
                        }
                        .padding(horizontal = 20.dp, vertical = 11.dp)
                ) {
                    Text(
                        "Delete",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            },
            dismissButton = {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(BgSurface)
                        .border(1.dp, StrokeLight, RoundedCornerShape(12.dp))
                        .clickable { showDeleteDialog = false }
                        .padding(horizontal = 20.dp, vertical = 11.dp)) {
                    Text("Cancel", color = InkMid, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        )
    }

// ── Long-press context sheet ───────────────────────────────────────────────────
    if (contextDoc != null && !showMoveSheet && !showRenameDialog) {
        val doc          = contextDoc!!
        val currentLabel = doc.docClassLabel ?: "Other"
        val isAadhaar    = currentLabel.startsWith("Aadhaar")
        val isPassport   = currentLabel == "Passport"
        val isInGroup    = doc.docGroupId != null
        val isGroupedAadhaar = doc.aadhaarGroupId != null
        val isDetachedAadhaarDoc = isAadhaar && isGroupedAadhaar && contextAadhaarGroup == null

        ModalBottomSheet(
            onDismissRequest = { contextDoc = null },
            containerColor   = BgCard,
            shape            = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = {
                Box(
                    Modifier
                        .padding(top = 12.dp, bottom = 4.dp)
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(StrokeMid)
                )
            }
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 40.dp)
            ) {
                // Doc preview header
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(BgSurface)
                            .border(1.dp, StrokeLight, RoundedCornerShape(10.dp))
                    ) {
                        if (doc.thumbnailPath != null)
                            AsyncImage(
                                model                = doc.thumbnailPath,
                                contentDescription   = null,
                                contentScale         = ContentScale.Crop,
                                modifier             = Modifier.fillMaxSize()
                            )
                        else
                            Icon(
                                Icons.Default.Image, null, tint = InkDim,
                                modifier = Modifier.align(Alignment.Center).size(22.dp)
                            )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            doc.name, color = Ink, fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(3.dp))
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(TypeColors[currentLabel] ?: InkMid)
                            )
                            Text(currentLabel, color = InkMid, fontSize = 12.sp)
                        }
                    }
                }

                HorizontalDivider(color = StrokeLight, thickness = 0.5.dp)

                // Rename
                ContextAction(
                    icon  = Icons.Default.DriveFileRenameOutline,
                    label = "Rename",
                    color = Color(0xFF2563EB)
                ) {
                    renameText = doc.name
                    showRenameDialog = true
                }

                HorizontalDivider(color = StrokeLight, thickness = 0.5.dp,
                    modifier = Modifier.padding(horizontal = 20.dp))

                // Move to folder
                ContextAction(
                    icon  = Icons.Default.DriveFileMove,
                    label = "Move to folder",
                    color = Coral,
                    trailing = {
                        Icon(Icons.Default.ChevronRight, null,
                            tint = InkDim, modifier = Modifier.size(18.dp))
                    }
                ) { showMoveSheet = true }

                HorizontalDivider(color = StrokeLight, thickness = 0.5.dp,
                    modifier = Modifier.padding(horizontal = 20.dp))

                // Change type
                ContextAction(
                    icon  = Icons.Default.Label,
                    label = "Change type",
                    color = Color(0xFF7C3AED)
                ) {
                    typeChangeDoc = doc
                    contextDoc    = null
                }

                if (isAadhaar && !isGroupedAadhaar) {
                    HorizontalDivider(color = StrokeLight, thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 20.dp))

                    val isPendingSource = pendingAadhaarPairId == doc.id
                    val hasPendingOther = pendingAadhaarPairId != null && pendingAadhaarPairId != doc.id

                    ContextAction(
                        icon = if (isPendingSource) Icons.Default.Close else Icons.Default.Link,
                        label = when {
                            isPendingSource -> "Cancel pairing"
                            hasPendingOther -> "Pair with selected Aadhaar"
                            else -> "Pair with another Aadhaar"
                        },
                        color = if (isPendingSource) InkMid else Color(0xFF059669)
                    ) {
                        when {
                            isPendingSource -> {
                                pendingAadhaarPairId = null
                                Toast.makeText(context, "Aadhaar pairing cancelled", Toast.LENGTH_SHORT).show()
                            }
                            hasPendingOther -> {
                                viewModel.manuallyGroupAadhaar(pendingAadhaarPairId!!, doc.id)
                                pendingAadhaarPairId = null
                                Toast.makeText(context, "Aadhaar cards paired", Toast.LENGTH_SHORT).show()
                            }
                            else -> {
                                pendingAadhaarPairId = doc.id
                                Toast.makeText(context, "Now tap another Aadhaar to pair", Toast.LENGTH_SHORT).show()
                            }
                        }
                        contextDoc = null
                    }
                }

                if (isDetachedAadhaarDoc) {
                    HorizontalDivider(color = StrokeLight, thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 20.dp))

                    ContextAction(
                        icon  = Icons.Default.LinkOff,
                        label = "Remove from Aadhaar pair",
                        color = InkMid
                    ) {
                        viewModel.removeFromAadhaarGroup(doc)
                        Toast.makeText(context, "Removed from Aadhaar pair", Toast.LENGTH_SHORT).show()
                        contextDoc = null
                    }
                }

                // ── Passport pairing (unpaired passport docs only) ─────────────
                if (currentLabel == "Passport" && doc.passportGroupId == null) {
                    HorizontalDivider(color = StrokeLight, thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 20.dp))

                    val isPendingPassportSource = pendingPassportPairId == doc.id
                    val hasPendingPassportOther = pendingPassportPairId != null && pendingPassportPairId != doc.id

                    ContextAction(
                        icon  = if (isPendingPassportSource) Icons.Default.Close else Icons.Default.Link,
                        label = when {
                            isPendingPassportSource -> "Cancel pairing"
                            hasPendingPassportOther -> "Pair with selected passport"
                            else                    -> "Pair with another passport"
                        },
                        color = if (isPendingPassportSource) InkMid else Color(0xFF7C3AED)
                    ) {
                        when {
                            isPendingPassportSource -> {
                                pendingPassportPairId = null
                                Toast.makeText(context, "Passport pairing cancelled", Toast.LENGTH_SHORT).show()
                            }
                            hasPendingPassportOther -> {
                                viewModel.manuallyGroupPassport(pendingPassportPairId!!, doc.id)
                                pendingPassportPairId = null
                                Toast.makeText(context, "Passport pages paired", Toast.LENGTH_SHORT).show()
                            }
                            else -> {
                                pendingPassportPairId = doc.id
                                Toast.makeText(context, "Now tap another passport page to pair", Toast.LENGTH_SHORT).show()
                            }
                        }
                        contextDoc = null
                    }
                }
                // Ungroup for already-paired passports
                if (currentLabel == "Passport" && doc.passportGroupId != null) {
                    HorizontalDivider(color = StrokeLight, thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 20.dp))
                    val ppGroup = passportGroups.firstOrNull { it.groupId == doc.passportGroupId }
                    ContextAction(
                        icon  = Icons.Default.LinkOff,
                        label = "Ungroup passport pair",
                        color = InkMid
                    ) {
                        ppGroup?.let { viewModel.ungroupPassport(it) }
                        Toast.makeText(context, "Passport pair ungrouped", Toast.LENGTH_SHORT).show()
                        contextDoc = null
                    }
                }

                // ── Group actions (non-Aadhaar, non-Passport only) ────────────
                // Passport docs use passport pairing (above), not generic groups.
                if (!isAadhaar && !isPassport) {
                    HorizontalDivider(color = StrokeLight, thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 20.dp))

                    if (isInGroup) {
                        ContextAction(
                            icon  = Icons.Default.LinkOff,
                            label = "Remove from group",
                            color = InkMid
                        ) {
                            viewModel.removeFromGroup(doc.id)
                            Toast.makeText(context, "Removed from group", Toast.LENGTH_SHORT).show()
                            contextDoc = null
                        }

                        HorizontalDivider(color = StrokeLight, thickness = 0.5.dp,
                            modifier = Modifier.padding(horizontal = 20.dp))

                        ContextAction(
                            icon  = Icons.Default.FolderOff,
                            label = "Disband entire group",
                            color = DangerRed
                        ) {
                            doc.docGroupId?.let { viewModel.disbandGroup(it) }
                            Toast.makeText(context, "Group disbanded", Toast.LENGTH_SHORT).show()
                            contextDoc = null
                        }
                    } else {
                        // Only show grouping option if the section has more than 1 doc
                        val sectionDocCount = remember(doc, filteredGrouped) {
                            val sectionKey = filteredGrouped.entries
                                .firstOrNull { (_, docs) -> docs.any { it.id == doc.id } }?.key
                            filteredGrouped[sectionKey]?.size ?: 0
                        }

                        if (sectionDocCount > 1) {
                            ContextAction(
                                icon  = Icons.Default.Checklist,
                                label = "Select docs to group",
                                color = Color(0xFF059669)
                            ) {
                                contextDoc = null
                                groupingScopeIds = filteredGrouped.entries
                                    .firstOrNull { (_, docs) -> docs.any { it.id == doc.id } }
                                    ?.value
                                    ?.map { it.id }
                                    ?.toSet()
                                onEnterSelectMode()
                                selectedOrder = listOf(doc.id)
                            }
                        }
                    }
                }

                HorizontalDivider(color = StrokeLight, thickness = 0.5.dp,
                    modifier = Modifier.padding(horizontal = 20.dp))

                // Delete
                ContextAction(
                    icon  = Icons.Default.DeleteOutline,
                    label = "Delete",
                    color = DangerRed
                ) {
                    selectedOrder = listOf(doc.id)
                    showDeleteDialog = true
                    contextDoc = null
                }
            }
        }
    }

// ── Move To sheet ──────────────────────────────────────────────────────────────
    if (contextDoc != null && showMoveSheet) {
        val doc = contextDoc!!
        val currentLabel = doc.docClassLabel ?: "Other"

        ModalBottomSheet(
            onDismissRequest = { showMoveSheet = false; contextDoc = null },
            containerColor = BgCard,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = {
                Box(
                    Modifier
                        .padding(top = 12.dp, bottom = 4.dp)
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(StrokeMid)
                )
            }
        ) {
            Column(Modifier
                .fillMaxWidth()
                .padding(bottom = 40.dp)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.ArrowBack, null, tint = Ink,
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .clickable { showMoveSheet = false }
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Move to folder", color = Ink, fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                HorizontalDivider(color = StrokeLight, thickness = 0.5.dp)

                Text(
                    "FOLDERS", color = InkDim, fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )

                allFolders.forEach { folder ->
                    val isCurrent = folder.docType == currentLabel
                    val folderColor = TypeColors[folder.docType] ?: InkMid
                    val docCount = viewModel.groupedByDocType.value[folder.name]?.size ?: 0

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .then(if (isCurrent) Modifier.background(folderColor.copy(0.07f)) else Modifier)
                            .clickable {
                                if (!isCurrent) {
                                    viewModel.changeDocumentType(doc, folder.docType)
                                    Toast.makeText(
                                        context,
                                        "Moved to ${folder.name}", Toast.LENGTH_SHORT
                                    ).show()
                                }
                                showMoveSheet = false
                                contextDoc = null
                            }
                            .padding(horizontal = 20.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(folderColor.copy(0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(folder.icon, fontSize = 17.sp)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                folder.name,
                                color = if (isCurrent) folderColor else Ink,
                                fontSize = 15.sp,
                                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal
                            )
                            Text(
                                if (docCount == 0) "Empty"
                                else "$docCount doc${if (docCount != 1) "s" else ""}",
                                color = InkDim, fontSize = 11.sp
                            )
                        }
                        if (isCurrent)
                            Box(
                                Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(folderColor.copy(0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Check, null,
                                    tint = folderColor, modifier = Modifier.size(13.dp)
                                )
                            }
                    }
                }
            }
        }
    }

// ── Rename dialog ──────────────────────────────────────────────────────────────
    if (showRenameDialog && contextDoc != null) {
        val doc = contextDoc!!
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            containerColor = BgCard,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text("Rename", color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("Name") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (renameText.isBlank()) BgSurface else Coral)
                        .clickable(enabled = renameText.isNotBlank()) {
                            viewModel.renameDocument(doc, renameText.trim())
                            showRenameDialog = false
                            contextDoc = null
                        }
                        .padding(horizontal = 20.dp, vertical = 11.dp)
                ) {
                    Text(
                        "Rename",
                        color = if (renameText.isBlank()) InkDim else Color.White,
                        fontWeight = FontWeight.SemiBold, fontSize = 14.sp
                    )
                }
            },
            dismissButton = {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(BgSurface)
                        .border(1.dp, StrokeLight, RoundedCornerShape(12.dp))
                        .clickable { showRenameDialog = false }
                        .padding(horizontal = 20.dp, vertical = 11.dp)
                ) {
                    Text(
                        "Cancel", color = InkMid, fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        )
    }

// ── Move To sheet ──────────────────────────────────────────────────────────────
    if (contextDoc != null && showMoveSheet) {
        val doc = contextDoc!!
        val currentLabel = doc.docClassLabel ?: "Other"

        ModalBottomSheet(
            onDismissRequest = { showMoveSheet = false; contextDoc = null },
            containerColor = BgCard,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = {
                Box(
                    Modifier
                        .padding(top = 12.dp, bottom = 4.dp)
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(StrokeMid)
                )
            }
        ) {
            Column(Modifier
                .fillMaxWidth()
                .padding(bottom = 40.dp)) {

                // ── Back header ────────────────────────────────────────────────────
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.ArrowBack, null, tint = Ink,
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .clickable { showMoveSheet = false }
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Move to folder", color = Ink, fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                HorizontalDivider(color = StrokeLight, thickness = 0.5.dp)

                Text(
                    "FOLDERS", color = InkDim, fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )

                // ── All folders ────────────────────────────────────────────────────
                allFolders.forEach { folder ->
                    val isCurrent = folder.docType == currentLabel
                    val folderColor = TypeColors[folder.docType] ?: InkMid
                    val docCount = viewModel.groupedByDocType.value[folder.name]?.size
                        ?: 0   // 0 for empty folders

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .then(if (isCurrent) Modifier.background(folderColor.copy(0.07f)) else Modifier)
                            .clickable {
                                if (!isCurrent) {
                                    viewModel.changeDocumentType(doc, folder.docType)
                                    Toast.makeText(
                                        context,
                                        "Moved to ${folder.name}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                showMoveSheet = false
                                contextDoc = null
                            }
                            .padding(horizontal = 20.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Folder icon box
                        Box(
                            Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(folderColor.copy(0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(folder.icon, fontSize = 17.sp)
                        }

                        Column(Modifier.weight(1f)) {
                            Text(
                                folder.name,
                                color = if (isCurrent) folderColor else Ink,
                                fontSize = 15.sp,
                                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal
                            )
                            Text(
                                if (docCount == 0) "Empty" else "$docCount document${if (docCount != 1) "s" else ""}",
                                color = InkDim,
                                fontSize = 11.sp
                            )
                        }

                        if (isCurrent)
                            Box(
                                Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(folderColor.copy(0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Check, null, tint = folderColor,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                    }
                }
            }
        }
    }

// ── Rename dialog ──────────────────────────────────────────────────────────────
    if (showRenameDialog && contextDoc != null) {
        val doc = contextDoc!!
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            containerColor = BgCard,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    "Rename document", color = Ink,
                    fontWeight = FontWeight.Bold, fontSize = 17.sp
                )
            },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("Name") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (renameText.isBlank()) BgSurface else Coral)
                        .clickable(enabled = renameText.isNotBlank()) {
                            viewModel.renameDocument(doc, renameText.trim())
                            showRenameDialog = false
                            contextDoc = null
                        }
                        .padding(horizontal = 20.dp, vertical = 11.dp)
                ) {
                    Text(
                        "Rename",
                        color = if (renameText.isBlank()) InkDim else Color.White,
                        fontWeight = FontWeight.SemiBold, fontSize = 14.sp
                    )
                }
            },
            dismissButton = {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(BgSurface)
                        .border(1.dp, StrokeLight, RoundedCornerShape(12.dp))
                        .clickable { showRenameDialog = false }
                        .padding(horizontal = 20.dp, vertical = 11.dp)
                ) {
                    Text(
                        "Cancel", color = InkMid, fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        )
    }

// ── Mismatch resolution ───────────────────────────────────────────────────
    if (pendingMismatches.isNotEmpty()) {
        val choices = remember(pendingMismatches) {
            mutableStateMapOf<String, String>().apply {
                pendingMismatches.forEach { put(it.documentId, it.folderLabel) }
            }
        }
        val docs = viewModel.documents.collectAsState().value

        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(0.45f))
                .clickable(enabled = false) {},
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .shadow(24.dp, RoundedCornerShape(20.dp), ambientColor = Color.Black.copy(0.12f))
                    .clip(RoundedCornerShape(20.dp))
                    .background(BgCard)
            ) {
                // ── Header ────────────────────────────────────────────────────
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 14.dp, top = 16.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Warning icon box
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(CoralSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Warning, null,
                            tint = Coral,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Type mismatch",
                            color = Ink,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.2).sp
                        )
                        Text(
                            "${pendingMismatches.size} doc${if (pendingMismatches.size != 1) "s" else ""} · tap to choose",
                            color = InkDim,
                            fontSize = 12.sp
                        )
                    }
                    Box(
                        Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(BgSurface)
                            .clickable(onClick = onDismissMismatches),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Close, null,
                            tint = InkMid,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                HorizontalDivider(color = StrokeLight, thickness = 0.5.dp)

                // ── Cards list ────────────────────────────────────────────────
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    contentPadding = PaddingValues(10.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    items(
                        count = pendingMismatches.size,
                        key   = { pendingMismatches[it].documentId }
                    ) { i ->
                        val m      = pendingMismatches[i]
                        val chosen = choices[m.documentId] ?: m.folderLabel
                        val doc    = docs.firstOrNull { it.id == m.documentId }
                        val dColor = TypeColors[m.detectedLabel] ?: InkMid
                        val fColor = TypeColors[m.folderLabel]   ?: InkMid

                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(BgSurface)
                                .border(0.5.dp, StrokeLight, RoundedCornerShape(12.dp))
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // ── Thumbnail ─────────────────────────────────────
                            Box(
                                Modifier
                                    .size(62.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(StrokeMid.copy(0.3f))
                            ) {
                                if (doc?.thumbnailPath != null)
                                    AsyncImage(
                                        model = doc.thumbnailPath,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                else
                                    Icon(
                                        Icons.Default.Image, null,
                                        tint = InkDim,
                                        modifier = Modifier
                                            .size(20.dp)
                                            .align(Alignment.Center)
                                    )
                            }

                            // ── Right side ────────────────────────────────────
                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                // Doc name — small, muted
                                Text(
                                    m.documentName,
                                    color = InkMid,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    letterSpacing = 0.1.sp
                                )

                                // Pills
                                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    MismatchPill(
                                        modifier = Modifier.weight(1f),
                                        label    = m.detectedLabel,
                                        tag      = "AI",
                                        color    = dColor,
                                        selected = chosen == m.detectedLabel,
                                        onClick  = { choices[m.documentId] = m.detectedLabel }
                                    )
                                    MismatchPill(
                                        modifier = Modifier.weight(1f),
                                        label    = m.folderLabel,
                                        tag      = "Folder",
                                        color    = fColor,
                                        selected = chosen == m.folderLabel,
                                        onClick  = { choices[m.documentId] = m.folderLabel }
                                    )
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(2.dp)) }
                }

                HorizontalDivider(color = StrokeLight, thickness = 0.5.dp)

                // ── Buttons ───────────────────────────────────────────────────
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        Modifier
                            .weight(2f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(Brush.linearGradient(listOf(Coral, CoralDark)))
                            .clickable {
                                choices.forEach { (docId, label) ->
                                    val mm = pendingMismatches
                                        .firstOrNull { it.documentId == docId }
                                    if (mm != null && label != mm.folderLabel)
                                        onResolveMismatch(docId, label)
                                }
                                onDismissMismatches()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Done",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }

    // ── Group context sheet ───────────────────────────────────────────────────────
    if (contextGroupId != null && !showGroupRenameDialog) {
        val groupId   = contextGroupId!!
        val groupName = docGroupNames[groupId] ?: "Group"
        val groupDocs = viewModel.docGroups.collectAsState().value[groupId] ?: emptyList()

        ModalBottomSheet(
            onDismissRequest = { contextGroupId = null },
            containerColor   = BgCard,
            shape            = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = {
                Box(
                    Modifier
                        .padding(top = 12.dp, bottom = 4.dp)
                        .width(40.dp).height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(StrokeMid)
                )
            }
        ) {
            Column(Modifier.fillMaxWidth().padding(bottom = 40.dp)) {

                // Header
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(42.dp).clip(RoundedCornerShape(10.dp))
                            .background(Coral.copy(0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.CreateNewFolder, null,
                            tint = Coral, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(groupName, color = Ink, fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                        Text("${groupDocs.size} document${if (groupDocs.size != 1) "s" else ""}",
                            color = InkDim, fontSize = 12.sp)
                    }
                }

                HorizontalDivider(color = StrokeLight, thickness = 0.5.dp)

                // Rename
                ContextAction(
                    icon  = Icons.Default.DriveFileRenameOutline,
                    label = "Rename group",
                    color = Color(0xFF2563EB)
                ) {
                    groupRenameText = groupName
                    showGroupRenameDialog = true
                }

                HorizontalDivider(color = StrokeLight, thickness = 0.5.dp,
                    modifier = Modifier.padding(horizontal = 20.dp))

                ContextAction(
                    icon  = Icons.Default.DriveFileMove,
                    label = "Move to folder",
                    color = Coral,
                    trailing = {
                        Icon(
                            Icons.Default.ChevronRight,
                            null,
                            tint = InkDim,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                ) {
                    openGroupMoveSheet(
                        title = groupName,
                        subtitle = "${groupDocs.size} document${if (groupDocs.size != 1) "s" else ""}",
                        docs = groupDocs
                    )
                    contextGroupId = null
                }

                HorizontalDivider(color = StrokeLight, thickness = 0.5.dp,
                    modifier = Modifier.padding(horizontal = 20.dp))

                // Disband
                ContextAction(
                    icon  = Icons.Default.LinkOff,
                    label = "Disband group",
                    color = InkMid
                ) {
                    viewModel.disbandGroup(groupId)
                    Toast.makeText(context, "Group disbanded", Toast.LENGTH_SHORT).show()
                    contextGroupId = null
                }

                HorizontalDivider(color = StrokeLight, thickness = 0.5.dp,
                    modifier = Modifier.padding(horizontal = 20.dp))

                // Delete entire group
                ContextAction(
                    icon  = Icons.Default.DeleteOutline,
                    label = "Delete all ${groupDocs.size} documents",
                    color = DangerRed
                ) {
                    viewModel.deleteEntireGroup(groupId)
                    Toast.makeText(context, "Group deleted", Toast.LENGTH_SHORT).show()
                    contextGroupId = null
                }
            }
        }
    }

// ── Group rename dialog ───────────────────────────────────────────────────────
    if (showGroupRenameDialog && contextGroupId != null) {
        AlertDialog(
            onDismissRequest = { showGroupRenameDialog = false },
            containerColor   = BgCard,
            shape            = RoundedCornerShape(20.dp),
            title = {
                Text("Rename group", color = Ink,
                    fontWeight = FontWeight.Bold, fontSize = 17.sp)
            },
            text = {
                OutlinedTextField(
                    value         = groupRenameText,
                    onValueChange = { groupRenameText = it },
                    singleLine    = true,
                    label         = { Text("Group name") },
                    shape         = RoundedCornerShape(12.dp),
                    modifier      = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (groupRenameText.isBlank()) BgSurface else Coral)
                        .clickable(enabled = groupRenameText.isNotBlank()) {
                            viewModel.renameDocGroup(contextGroupId!!, groupRenameText.trim())
                            showGroupRenameDialog = false
                            contextGroupId = null
                        }
                        .padding(horizontal = 20.dp, vertical = 11.dp)
                ) {
                    Text("Rename",
                        color = if (groupRenameText.isBlank()) InkDim else Color.White,
                        fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            },
            dismissButton = {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(BgSurface)
                        .border(1.dp, StrokeLight, RoundedCornerShape(12.dp))
                        .clickable { showGroupRenameDialog = false }
                        .padding(horizontal = 20.dp, vertical = 11.dp)
                ) {
                    Text("Cancel", color = InkMid, fontSize = 14.sp,
                        fontWeight = FontWeight.Medium)
                }
            }
        )
    }

    // ── Group name dialog ─────────────────────────────────────────────────────────
    if (showGroupNameDialog) {
        AlertDialog(
            onDismissRequest = { showGroupNameDialog = false },
            containerColor   = BgCard,
            shape            = RoundedCornerShape(20.dp),
            title = {
                Text("Name this group", color = Ink,
                    fontWeight = FontWeight.Bold, fontSize = 17.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Grouping ${pendingGroupDocIds.size} documents",
                        color = InkDim, fontSize = 13.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value         = groupNameText,
                        onValueChange = { groupNameText = it },
                        singleLine    = true,
                        label         = { Text("Group name") },
                        placeholder   = { Text("e.g. Passport Set") },
                        shape         = RoundedCornerShape(12.dp),
                        modifier      = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (groupNameText.isBlank()) BgSurface else Coral)
                        .clickable(enabled = groupNameText.isNotBlank()) {
                            viewModel.createGroupFromDocs(
                                pendingGroupDocIds,
                                groupNameText.trim()        // ← pass name
                            )
                            showGroupNameDialog = false
                            groupNameText      = ""
                            pendingGroupDocIds = emptyList()
                            onSelectToggle()
                            Toast.makeText(
                                context,
                                "Group \"${groupNameText.trim()}\" created",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        .padding(horizontal = 20.dp, vertical = 11.dp)
                ) {
                    Text("Create",
                        color = if (groupNameText.isBlank()) InkDim else Color.White,
                        fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            },
            dismissButton = {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(BgSurface)
                        .border(1.dp, StrokeLight, RoundedCornerShape(12.dp))
                        .clickable { showGroupNameDialog = false }
                        .padding(horizontal = 20.dp, vertical = 11.dp)
                ) {
                    Text("Cancel", color = InkMid, fontSize = 14.sp,
                        fontWeight = FontWeight.Medium)
                }
            }
        )
    }

    // ── Aadhaar pair context sheet ────────────────────────────────────────────────
    if (contextAadhaarGroupId != null && !showGroupRenameDialog) {
        val groupId = contextAadhaarGroupId!!
        val group = aadhaarGroups.firstOrNull { it.groupId == groupId }
        val groupDocs = listOfNotNull(group?.frontDoc, group?.backDoc)
        val aadhaarBlue = Color(0xFF2563EB)

        var showAadhaarRenameDialog by remember { mutableStateOf(false) }
        var aadhaarRenameText by remember { mutableStateOf("") }

        if (!showAadhaarRenameDialog) {
            ModalBottomSheet(
                onDismissRequest = { contextAadhaarGroupId = null },
                containerColor = BgCard,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                dragHandle = {
                    Box(
                        Modifier
                            .padding(top = 12.dp, bottom = 4.dp)
                            .width(40.dp).height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(StrokeMid)
                    )
                }
            ) {
                Column(Modifier.fillMaxWidth().padding(bottom = 40.dp)) {

                    // Header
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(42.dp).clip(RoundedCornerShape(10.dp))
                                .background(aadhaarBlue.copy(0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CreditCard, null,
                                tint = aadhaarBlue, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                group?.displayName ?: "Aadhaar Pair",
                                color = Ink, fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                if (group?.isComplete == true) "Front + Back" else "Incomplete pair",
                                color = if (group?.isComplete == true) aadhaarBlue else Color(0xFFD97706),
                                fontSize = 12.sp
                            )
                        }
                    }

                    HorizontalDivider(color = StrokeLight, thickness = 0.5.dp)

                    // Rename pair
                    ContextAction(
                        icon  = Icons.Default.DriveFileRenameOutline,
                        label = "Rename pair",
                        color = Color(0xFF2563EB)
                    ) {
                        aadhaarRenameText = group?.frontDoc?.name
                            ?.substringBefore("_Aadhaar")
                            ?.replace("_", " ")
                            ?: group?.backDoc?.name
                                ?.substringBefore("_Aadhaar")
                                ?.replace("_", " ")
                                    ?: group?.displayName
                                    ?: ""
                        showAadhaarRenameDialog = true
                    }

                    HorizontalDivider(color = StrokeLight, thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 20.dp))

                    ContextAction(
                        icon  = Icons.Default.DriveFileMove,
                        label = "Move to folder",
                        color = Coral,
                        trailing = {
                            Icon(
                                Icons.Default.ChevronRight,
                                null,
                                tint = InkDim,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    ) {
                        openGroupMoveSheet(
                            title = group?.displayName ?: "Aadhaar Pair",
                            subtitle = if (group?.isComplete == true) "Front + Back" else "Incomplete pair",
                            docs = groupDocs
                        )
                        contextAadhaarGroupId = null
                    }

                    HorizontalDivider(color = StrokeLight, thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 20.dp))

                    // Swap front / back
                    ContextAction(
                        icon  = Icons.Default.SwapHoriz,
                        label = "Swap Front / Back",
                        color = Color(0xFF059669)
                    ) {
                        group?.let { viewModel.swapAadhaarSides(it) }
                        Toast.makeText(context, "Sides swapped", Toast.LENGTH_SHORT).show()
                        contextAadhaarGroupId = null
                    }

                    HorizontalDivider(color = StrokeLight, thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 20.dp))

                    // Ungroup pair
                    ContextAction(
                        icon  = Icons.Default.LinkOff,
                        label = "Ungroup pair",
                        color = InkMid
                    ) {
                        group?.let { viewModel.ungroupAadhaar(it) }
                        Toast.makeText(context, "Pair ungrouped", Toast.LENGTH_SHORT).show()
                        contextAadhaarGroupId = null
                    }
                }
            }
        }

        // Rename dialog for Aadhaar pair
        if (showAadhaarRenameDialog) {
            AlertDialog(
                onDismissRequest = { showAadhaarRenameDialog = false },
                containerColor = BgCard,
                shape = RoundedCornerShape(20.dp),
                title = {
                    Text("Rename pair", color = Ink,
                        fontWeight = FontWeight.Bold, fontSize = 17.sp)
                },
                text = {
                    OutlinedTextField(
                        value = aadhaarRenameText,
                        onValueChange = { aadhaarRenameText = it },
                        singleLine = true,
                        label = { Text("Pair name") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (aadhaarRenameText.isBlank()) BgSurface else Coral)
                            .clickable(enabled = aadhaarRenameText.isNotBlank()) {
                                val sanitized = aadhaarRenameText.trim().replace(" ", "_")
                                group?.frontDoc?.let {
                                    viewModel.renameDocument(it, "${sanitized}_Aadhaar_Front")
                                }
                                group?.backDoc?.let {
                                    viewModel.renameDocument(it, "${sanitized}_Aadhaar_Back")
                                }
                                showAadhaarRenameDialog = false
                                contextAadhaarGroupId = null
                            }
                            .padding(horizontal = 20.dp, vertical = 11.dp)
                    ) {
                        Text("Rename",
                            color = if (aadhaarRenameText.isBlank()) InkDim else Color.White,
                            fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                },
                dismissButton = {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(BgSurface)
                            .border(1.dp, StrokeLight, RoundedCornerShape(12.dp))
                            .clickable { showAadhaarRenameDialog = false }
                            .padding(horizontal = 20.dp, vertical = 11.dp)
                    ) {
                        Text("Cancel", color = InkMid, fontSize = 14.sp,
                            fontWeight = FontWeight.Medium)
                    }
                }
            )
        }
    }

    // ── Passport pair context sheet ───────────────────────────────────────────
    if (contextPassportGroupId != null) {
        val ppGroupId = contextPassportGroupId!!
        val ppGroup   = passportGroups.firstOrNull { it.groupId == ppGroupId }
        val ppGroupDocs = listOfNotNull(ppGroup?.frontDoc, ppGroup?.backDoc)
        val ppBlue    = Color(0xFF7C3AED)
        ModalBottomSheet(
            onDismissRequest = { contextPassportGroupId = null },
            containerColor   = BgCard,
            shape            = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = {
                Box(
                    Modifier
                        .padding(top = 12.dp, bottom = 4.dp)
                        .width(40.dp).height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(StrokeMid)
                )
            }
        ) {
            Column(Modifier.fillMaxWidth().padding(bottom = 40.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(42.dp).clip(RoundedCornerShape(10.dp))
                            .background(ppBlue.copy(0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("📔", fontSize = 20.sp)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            ppGroup?.displayName ?: "Passport Pair",
                            color = Ink, fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            if (ppGroup?.isComplete == true) "Data page + Back page"
                            else "Incomplete pair",
                            color = if (ppGroup?.isComplete == true) ppBlue else Color(0xFFD97706),
                            fontSize = 12.sp
                        )
                    }
                }
                HorizontalDivider(color = StrokeLight, thickness = 0.5.dp)
                ContextAction(
                    icon  = Icons.Default.DriveFileMove,
                    label = "Move to folder",
                    color = Coral,
                    trailing = {
                        Icon(
                            Icons.Default.ChevronRight,
                            null,
                            tint = InkDim,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                ) {
                    openGroupMoveSheet(
                        title = ppGroup?.displayName ?: "Passport Pair",
                        subtitle = if (ppGroup?.isComplete == true) "Data page + Back page" else "Incomplete pair",
                        docs = ppGroupDocs
                    )
                    contextPassportGroupId = null
                }
                HorizontalDivider(color = StrokeLight, thickness = 0.5.dp,
                    modifier = Modifier.padding(horizontal = 20.dp))
                ContextAction(
                    icon  = Icons.Default.SwapHoriz,
                    label = "Swap Data / Back page",
                    color = Color(0xFF059669)
                ) {
                    ppGroup?.let { viewModel.swapPassportSides(it) }
                    Toast.makeText(context, "Sides swapped", Toast.LENGTH_SHORT).show()
                    contextPassportGroupId = null
                }
                HorizontalDivider(color = StrokeLight, thickness = 0.5.dp,
                    modifier = Modifier.padding(horizontal = 20.dp))
                ContextAction(
                    icon  = Icons.Default.LinkOff,
                    label = "Ungroup pair",
                    color = InkMid
                ) {
                    ppGroup?.let { viewModel.ungroupPassport(it) }
                    Toast.makeText(context, "Passport pair ungrouped", Toast.LENGTH_SHORT).show()
                    contextPassportGroupId = null
                }
            }
        }
    }

    if (moveGroupTarget != null) {
        val target = moveGroupTarget!!
        MoveToFolderSheet(
            title = target.title,
            subtitle = target.subtitle,
            currentLabel = target.currentLabel,
            allFolders = allFolders,
            groupedByType = groupedByType,
            onDismiss = { moveGroupTarget = null },
            onSelect = { folder ->
                viewModel.changeDocumentSetType(target.documents, folder.docType)
                Toast.makeText(
                    context,
                    if (target.documents.size == 1) {
                        "Moved to ${folder.name}"
                    } else {
                        "Moved ${target.documents.size} documents to ${folder.name}"
                    },
                    Toast.LENGTH_SHORT
                ).show()
                moveGroupTarget = null
            }
        )
    }
}



// ═══════════════════════════════════════════════════════════════════════════════
// GALLERY SECTIONED GRID
// Sections are separated only by a gap + a slim label row — no card/box around them.
// Exactly like iOS Photos "months" layout.
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun GallerySectionedGrid(
    grouped: Map<String, List<Document>>,
    allFolders: List<Folder>,
    collapsedSections: Set<String>,
    onToggleSection: (String) -> Unit,
    onSectionReorder: (label: String, from: Int, to: Int) -> Unit,
    onScanToFolder: (Folder) -> Unit,
    onScanClick: () -> Unit,
    columnCount: Int,
    onDocumentClick: (Document) -> Unit,
    onBadgeTap: (Document) -> Unit,
    onMoreTap: (Document) -> Unit,
    isSelectMode: Boolean = false,
    selectedIds: Set<String> = emptySet(),
    onSelect: (String) -> Unit = {},
    onSelectSection: (List<Document>) -> Unit = {},
    onLongPressSection: (List<Document>) -> Unit = {},
    aadhaarGroups: List<com.example.docscanner.domain.model.AadhaarGroup> = emptyList(),
    passportGroups: List<com.example.docscanner.domain.model.PassportGroup> = emptyList(),
    onManualGroup: (String, String) -> Unit = { _, _ -> },
    onManualPassportGroup: (String, String) -> Unit = { _, _ -> },
    onAddToGroup: (docId: String, groupId: String, side: String) -> Unit = { _, _, _ -> },
    onGroupTap: (String) -> Unit = {},
    docGroups: Map<String, List<Document>> = emptyMap(),
    selectedOrder: List<String> = emptyList(),
    onGroupMoreTap: (groupId: String) -> Unit = {},
    docGroupNames: Map<String, String> = emptyMap(),
    onAadhaarGroupMoreTap: (groupId: String) -> Unit = {},
    onPassportGroupMoreTap: (groupId: String) -> Unit = {},
    pendingAadhaarPairId: String? = null,
    onPendingAadhaarPairChange: (String?) -> Unit = {},
    pendingPassportPairId: String? = null,
    onPendingPassportPairChange: (String?) -> Unit = {},
    groupingScopeIds: Set<String>? = null
) {
    val folderByName = remember(allFolders) { allFolders.associateBy { it.name } }

    LazyColumn(contentPadding = PaddingValues(bottom = 130.dp), modifier = Modifier.fillMaxSize()) {
        grouped.entries.forEachIndexed { index, (label, docs) ->
            val typeColor = TypeColors[label] ?: InkMid
            val folder = folderByName[label]
            val sectionSelectionEnabled = groupingScopeIds == null || docs.all { it.id in groupingScopeIds }
            val supportsSectionGrouping = supportsGenericGrouping(label)

            if (index > 0) item(key = "spacer_$label") { Spacer(Modifier.height(20.dp)) }

            item(key = "header_$label") {
                SectionHeaderRow(
                    label        = label,
                    count        = docs.size,
                    typeColor    = typeColor,
                    isCollapsed  = label in collapsedSections,
                    onToggle     = { onToggleSection(label) },
                    isSelectMode = isSelectMode,
                    sectionDocs  = docs,
                    selectedIds  = selectedIds,
                    onSelectSection = { onSelectSection(docs) },
                    onLongPress  = {
                        if (supportsSectionGrouping) onLongPressSection(docs)
                    },
                    isSelectionEnabled = sectionSelectionEnabled,
                )
            }

            if (label !in collapsedSections) {
                item(key = "grid_$label") {
                    val isAadhaarSection   = label.equals("Aadhaar", ignoreCase = true)
                    val isPassportSection  = label.equals("Passport", ignoreCase = true)

                    if (docs.isEmpty()) {
                        FolderEmptyState(
                            typeColor   = typeColor,
                            onScanClick = { folder?.let { onScanToFolder(it) } ?: onScanClick() }
                        )
                    } else if (isAadhaarSection) {
                        val sectionGroups = aadhaarGroups.filter { g ->
                            docs.any { d -> d.id == g.frontDoc?.id || d.id == g.backDoc?.id }
                        }
                        val orderedAadhaarDocs = buildList {
                            sectionGroups.forEach { group ->
                                group.frontDoc?.let(::add)
                                group.backDoc?.let(::add)
                            }
                            addAll(
                                docs.filter { d ->
                                    sectionGroups.none { g ->
                                        d.id == g.frontDoc?.id || d.id == g.backDoc?.id
                                    }
                                }
                            )
                        }
                        GalleryPhotoGrid(
                            docs            = orderedAadhaarDocs,
                            columnCount     = columnCount,
                            onReorder       = { from, to -> onSectionReorder(label, from, to) },
                            onDocumentClick = onDocumentClick,
                            onBadgeTap      = onBadgeTap,
                            onMoreTap       = onMoreTap,
                            onScanTap       = { folder?.let { onScanToFolder(it) } ?: onScanClick() },
                            isSelectMode    = isSelectMode,
                            selectedIds     = selectedIds,
                            onSelect        = onSelect,
                            selectedOrder   = selectedOrder,
                            aadhaarGroups   = sectionGroups,
                            docGroups       = docGroups,
                            onGroupMoreTap  = onGroupMoreTap,
                            docGroupNames   = docGroupNames,
                            onAadhaarGroupMoreTap = onAadhaarGroupMoreTap,
                            onManualGroup   = onManualGroup,
                            onAddToGroup    = onAddToGroup,
                            onGroupTap      = onGroupTap,
                            pendingAadhaarPairId = pendingAadhaarPairId,
                            onPendingAadhaarPairChange = onPendingAadhaarPairChange,
                            selectionEnabled = sectionSelectionEnabled,
                        )
                    } else if (isPassportSection) {
                        val ppSectionGroups = passportGroups.filter { g ->
                            docs.any { d -> d.id == g.frontDoc?.id || d.id == g.backDoc?.id }
                        }
                        val orderedPassportDocs = buildList {
                            ppSectionGroups.forEach { group ->
                                group.frontDoc?.let(::add)
                                group.backDoc?.let(::add)
                            }
                            addAll(
                                docs.filter { d ->
                                    ppSectionGroups.none { g ->
                                        d.id == g.frontDoc?.id || d.id == g.backDoc?.id
                                    }
                                }
                            )
                        }
                        GalleryPhotoGrid(
                            docs            = orderedPassportDocs,
                            columnCount     = columnCount,
                            onReorder       = { from, to -> onSectionReorder(label, from, to) },
                            onDocumentClick = onDocumentClick,
                            onBadgeTap      = onBadgeTap,
                            onMoreTap       = onMoreTap,
                            onScanTap       = { folder?.let { onScanToFolder(it) } ?: onScanClick() },
                            isSelectMode    = isSelectMode,
                            selectedIds     = selectedIds,
                            onSelect        = onSelect,
                            selectedOrder   = selectedOrder,
                            passportGroups  = ppSectionGroups,
                            docGroups       = docGroups,
                            onGroupMoreTap  = onGroupMoreTap,
                            docGroupNames   = docGroupNames,
                            onPassportGroupMoreTap = onPassportGroupMoreTap,
                            onManualPassportGroup = onManualPassportGroup,
                            onGroupTap      = onGroupTap,
                            pendingPassportPairId      = pendingPassportPairId,
                            onPendingPassportPairChange = onPendingPassportPairChange,
                            selectionEnabled = sectionSelectionEnabled,
                        )
                    } else {
                        GalleryPhotoGrid(
                            docs            = docs,
                            columnCount     = columnCount,
                            onReorder       = { from, to -> onSectionReorder(label, from, to) },
                            onDocumentClick = onDocumentClick,
                            onBadgeTap      = onBadgeTap,
                            onMoreTap       = onMoreTap,
                            onScanTap       = { folder?.let { onScanToFolder(it) } ?: onScanClick() },
                            isSelectMode    = isSelectMode,
                            selectedIds     = selectedIds,
                            onSelect        = onSelect,
                            onGroupTap      = onGroupTap,
                            docGroups       = docGroups,
                            selectedOrder   = selectedOrder,
                            onGroupMoreTap  = onGroupMoreTap,
                            docGroupNames   = docGroupNames,
                            onManualGroup   = onManualGroup,
                            onAddToGroup    = onAddToGroup,
                            onManualPassportGroup      = onManualPassportGroup,
                            pendingAadhaarPairId       = pendingAadhaarPairId,
                            onPendingAadhaarPairChange = onPendingAadhaarPairChange,
                            pendingPassportPairId       = pendingPassportPairId,
                            onPendingPassportPairChange = onPendingPassportPairChange,
                            selectionEnabled = sectionSelectionEnabled,
                        )
                    }
                }
            }
        }
    }
}

// ── Minimal header: dot · label · count · chevron ────────────────────────────

@Composable
private fun SectionHeaderRow(
    label: String,
    count: Int,
    typeColor: Color,
    isCollapsed: Boolean,
    onToggle: () -> Unit,
    isSelectMode: Boolean = false,
    sectionDocs: List<Document> = emptyList(),
    selectedIds: Set<String> = emptySet(),
    onSelectSection: () -> Unit = {},
    onLongPress: () -> Unit = {},        // ← new
    isSelectionEnabled: Boolean = true,
) {
    val sectionSelected = sectionDocs.count { it.id in selectedIds }
    val allSectionSelected = sectionDocs.isNotEmpty() && sectionSelected == sectionDocs.size
    val showSelectionUi = isSelectMode && sectionDocs.isNotEmpty() && isSelectionEnabled
    val chevron by animateFloatAsState(
        targetValue = if (isCollapsed) -90f else 0f,
        animationSpec = tween(200), label = "chev_$label"
    )

    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(
            onClick = onToggle,
            onLongClick = {
                if (sectionDocs.isNotEmpty()) onLongPress()
            }
        )
//            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(typeColor))
        Text(
            label, color = Ink, fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)
        )

        if (showSelectionUi) {
            if (sectionSelected > 0)
                Text(
                    "$sectionSelected/${sectionDocs.size}",
                    color = typeColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold
                )
            // ── Rounded-square checkbox (small) ──────────────────────────────
            Box(
                Modifier
                    .size(18.dp)                            // smaller than before
                    .clip(RoundedCornerShape(5.dp))         // rounded square
                    .background(if (allSectionSelected) typeColor else Color.Transparent)
                    .border(
                        1.5.dp,
                        if (allSectionSelected) typeColor else StrokeMid,
                        RoundedCornerShape(5.dp)
                    )
                    .clickable(enabled = isSelectionEnabled, onClick = onSelectSection),
                contentAlignment = Alignment.Center
            ) {
                if (allSectionSelected)
                    Icon(
                        Icons.Default.Check, null,
                        tint = Color.White,
                        modifier = Modifier.size(11.dp)
                    )
            }
        } else {
            Text("$count", color = InkDim, fontSize = 12.sp)
            Icon(
                Icons.Default.KeyboardArrowDown, null, tint = InkDim,
                modifier = Modifier.size(16.dp).rotate(chevron)
            )
        }
    }
}

@Composable
private fun FolderEmptyState(
    typeColor: Color,
    onScanClick: () -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(100.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(typeColor.copy(0.05f))
            .border(1.dp, typeColor.copy(0.15f), RoundedCornerShape(12.dp))
            .clickable(onClick = onScanClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.CameraAlt, null,
                tint = typeColor, modifier = Modifier.size(18.dp)
            )
            Text(
                "Tap to scan into this folder",
                color = typeColor, fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
// ═══════════════════════════════════════════════════════════════════════════════
// GALLERY PHOTO GRID — tight 2px gaps, square tiles, no card borders
// ═══════════════════════════════════════════════════════════════════════════════

data class DragState(val idx: Int = -1, val deltaX: Float = 0f, val deltaY: Float = 0f)
data class CardPos(val cx: Float, val cy: Float)

@Composable
private fun GalleryPhotoGrid(
    docs: List<Document>,
    columnCount: Int,
    onReorder: (from: Int, to: Int) -> Unit,
    onDocumentClick: (Document) -> Unit,
    onBadgeTap: (Document) -> Unit,
    onMoreTap: (Document) -> Unit,
    onScanTap: () -> Unit,
    isSelectMode: Boolean = false,
    selectedOrder: List<String> = emptyList(),
    selectedIds: Set<String> = emptySet(),
    onSelect: (String) -> Unit = {},
    onGroupTap: (String) -> Unit = {},
    docGroups: Map<String, List<Document>> = emptyMap(),
    onGroupMoreTap: (groupId: String) -> Unit = {},
    docGroupNames:Map<String, String> = emptyMap(),
    aadhaarGroups: List<com.example.docscanner.domain.model.AadhaarGroup> = emptyList(),
    passportGroups: List<com.example.docscanner.domain.model.PassportGroup> = emptyList(),
    onAadhaarGroupMoreTap: (groupId: String) -> Unit = {},
    onPassportGroupMoreTap: (groupId: String) -> Unit = {},
    onManualGroup: (String, String) -> Unit = { _, _ -> },
    onManualPassportGroup: (String, String) -> Unit = { _, _ -> },
    onAddToGroup: (docId: String, groupId: String, side: String) -> Unit = { _, _, _ -> },
    pendingAadhaarPairId: String? = null,
    onPendingAadhaarPairChange: (String?) -> Unit = {},
    pendingPassportPairId: String? = null,
    onPendingPassportPairChange: (String?) -> Unit = {},
    selectionEnabled: Boolean = true,
) {
    val context = LocalContext.current
    val cardPos = remember { HashMap<Int, CardPos>() }
    var dragState by remember { mutableStateOf(DragState()) }
    val aadhaarGroupsById  = remember(aadhaarGroups)  { aadhaarGroups.associateBy  { it.groupId } }
    val passportGroupsById = remember(passportGroups) { passportGroups.associateBy { it.groupId } }
    val aadhaarPairDocIds = remember(aadhaarGroups) {
        aadhaarGroups.flatMap { group ->
            listOfNotNull(group.frontDoc?.id, group.backDoc?.id)
        }.toSet()
    }
    val passportPairDocIds = remember(passportGroups) {
        passportGroups.flatMap { group ->
            listOfNotNull(group.frontDoc?.id, group.backDoc?.id)
        }.toSet()
    }
    val renderedDocs = remember(docs, docGroups, aadhaarGroups, passportGroups, isSelectMode, selectionEnabled) {
        val seenGroupIds         = mutableSetOf<String>()
        val seenAadhaarGroupIds  = mutableSetOf<String>()
        val seenPassportGroupIds = mutableSetOf<String>()
        docs.filter { doc ->
            val aadhaarGroupId = doc.aadhaarGroupId
            if (aadhaarGroupId != null &&
                doc.id in aadhaarPairDocIds &&
                aadhaarGroupsById.containsKey(aadhaarGroupId) &&
                (!isSelectMode || !selectionEnabled)
            ) {
                return@filter seenAadhaarGroupIds.add(aadhaarGroupId)
            }
            val ppGroupId = doc.passportGroupId
            if (ppGroupId != null &&
                doc.id in passportPairDocIds &&
                passportGroupsById.containsKey(ppGroupId) &&
                (!isSelectMode || !selectionEnabled)
            ) {
                return@filter seenPassportGroupIds.add(ppGroupId)
            }
            val gid = doc.docGroupId
            if (gid == null) true
            // Expand custom groups in select mode so every doc is individually
            // selectable and "Select All" / delete covers the full group.
            else if (isSelectMode && selectionEnabled) true
            else seenGroupIds.add(gid)
        }
    }

    val allItems: List<Document?> = listOf(null) + renderedDocs
    val rows = remember(allItems, columnCount) { allItems.chunked(columnCount) }
    val gap = 4.dp

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap)) {
        rows.forEachIndexed { rowIdx, rowItems ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(gap)
            ) {
                rowItems.forEachIndexed { colIdx, doc ->
                    if (doc == null) {
                        ScanTile(modifier = Modifier.weight(1f), onClick = onScanTap)
                    } else {
                        val flatIdx = rowIdx * columnCount + colIdx
                        val docIdx = flatIdx - 1

                        val isDragging by remember(docIdx) {
                            derivedStateOf { dragState.idx == docIdx }
                        }
                        val tx by remember(docIdx) {
                            derivedStateOf { if (dragState.idx == docIdx) dragState.deltaX else 0f }
                        }
                        val ty by remember(docIdx) {
                            derivedStateOf { if (dragState.idx == docIdx) dragState.deltaY else 0f }
                        }
                        val alpha by animateFloatAsState(
                            if (isDragging) 0.45f else 1f, tween(150), label = "a$docIdx"
                        )
                        val scale by animateFloatAsState(
                            if (isDragging) 1.05f else 1f, spring(stiffness = 400f),
                            label = "s$docIdx"
                        )
                        val groupDocs = remember(doc.docGroupId, docGroups) {
                            doc.docGroupId?.let { docGroups[it] } ?: emptyList()
                        }
                        val isGrouped = doc.docGroupId != null
                        val isUngroupedAadhaar = remember(doc.docClassLabel, doc.aadhaarGroupId) {
                            doc.docClassLabel?.startsWith("Aadhaar") == true && doc.aadhaarGroupId == null
                        }
                        val isUngroupedPassport = remember(doc.docClassLabel, doc.passportGroupId) {
                            doc.docClassLabel == "Passport" && doc.passportGroupId == null
                        }
                        val aadhaarGroup = remember(doc.id, doc.aadhaarGroupId, aadhaarGroupsById, aadhaarPairDocIds) {
                            if (doc.id in aadhaarPairDocIds) doc.aadhaarGroupId?.let { aadhaarGroupsById[it] }
                            else null
                        }
                        val passportGroup = remember(doc.id, doc.passportGroupId, passportGroupsById, passportPairDocIds) {
                            if (doc.id in passportPairDocIds) doc.passportGroupId?.let { passportGroupsById[it] }
                            else null
                        }

                        Box(
                            Modifier
                                .weight(1f)
                                .onGloballyPositioned { coords ->
                                    val b = coords.boundsInWindow()
                                    cardPos[docIdx] = CardPos(
                                        (b.left + b.right) / 2f,
                                        (b.top + b.bottom) / 2f
                                    )
                                }
                                .graphicsLayer {
                                    this.alpha = alpha
                                    this.scaleX = scale
                                    this.scaleY = scale
                                    this.translationX = tx
                                    this.translationY = ty
                                }
                                .then(
                                    if (isUngroupedAadhaar || isUngroupedPassport) Modifier else Modifier.pointerInput(doc.id) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { dragState = DragState(idx = docIdx) },
                                            onDrag = { change, amount ->
                                                change.consume()
                                                dragState = dragState.copy(
                                                    deltaX = dragState.deltaX + amount.x,
                                                    deltaY = dragState.deltaY + amount.y
                                                )
                                            },
                                            onDragEnd = {
                                                val cur = dragState
                                                if (cur.idx >= 0) {
                                                    val p = cardPos[cur.idx]
                                                    if (p != null) {
                                                        val dcx = p.cx + cur.deltaX
                                                        val dcy = p.cy + cur.deltaY
                                                        val target = cardPos.entries
                                                            .filter { it.key != cur.idx }
                                                            .minByOrNull { (_, q) ->
                                                                val dx = q.cx - dcx
                                                                val dy = q.cy - dcy
                                                                dx * dx + dy * dy
                                                            }?.key ?: cur.idx
                                                        if (target != cur.idx) onReorder(cur.idx, target)
                                                    }
                                                }
                                                dragState = DragState()
                                            },
                                            onDragCancel = { dragState = DragState() }
                                        )
                                    }
                                )
                        ) {
                            if (isSelectMode && selectionEnabled) {
                                val orderNumber = if (doc.id in selectedIds)
                                    selectedOrder.indexOf(doc.id) + 1  // ← now correct
                                else null
                                SelectTile(
                                    doc         = doc,
                                    isSelected  = doc.id in selectedIds,
                                    orderNumber = orderNumber,
                                    isEnabled   = selectionEnabled,
                                    onTap       = { if (selectionEnabled) onSelect(doc.id) }
                                )
                            } else if (aadhaarGroup != null) {
                                AadhaarGroupedTile(
                                    group = aadhaarGroup,
                                    isPendingFill = selectionEnabled && pendingAadhaarPairId != null && !aadhaarGroup.isComplete,
                                    onTap = {
                                        if (isSelectMode && !selectionEnabled) return@AadhaarGroupedTile
                                        val pendingId = pendingAadhaarPairId
                                        if (pendingId != null && !aadhaarGroup.isComplete) {
                                            val missingSide = if (aadhaarGroup.frontDoc == null) "FRONT" else "BACK"
                                            onAddToGroup(pendingId, aadhaarGroup.groupId, missingSide)
                                            Toast.makeText(context, "Added to Aadhaar pair", Toast.LENGTH_SHORT).show()
                                            onPendingAadhaarPairChange(null)
                                        } else {
                                            onGroupTap(aadhaarGroup.groupId)
                                        }
                                    },
                                    onMoreTap = {
                                        if (isSelectMode && !selectionEnabled) return@AadhaarGroupedTile
                                        onAadhaarGroupMoreTap(aadhaarGroup.groupId)
                                    }
                                )
                            } else if (passportGroup != null) {
                                PassportGroupedTile(
                                    group = passportGroup,
                                    isPendingFill = selectionEnabled &&
                                        pendingPassportPairId != null &&
                                        !passportGroup.isComplete,
                                    onTap = {
                                        if (isSelectMode && !selectionEnabled) return@PassportGroupedTile
                                        val pendingId = pendingPassportPairId
                                        val existingDoc = passportGroup.frontDoc ?: passportGroup.backDoc
                                        if (pendingId != null && !passportGroup.isComplete && existingDoc != null) {
                                            onManualPassportGroup(pendingId, existingDoc.id)
                                            Toast.makeText(context, "Passport pages paired", Toast.LENGTH_SHORT).show()
                                            onPendingPassportPairChange(null)
                                        } else {
                                            onGroupTap(passportGroup.groupId)
                                        }
                                    },
                                    onLongPress = {
                                        if (isSelectMode && !selectionEnabled) return@PassportGroupedTile
                                        if (!passportGroup.isComplete) {
                                            val existingDoc = passportGroup.frontDoc ?: passportGroup.backDoc
                                            if (existingDoc != null) {
                                                onPendingPassportPairChange(existingDoc.id)
                                                Toast.makeText(context, "Now tap another passport page to pair", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    onMoreTap = {
                                        if (isSelectMode && !selectionEnabled) return@PassportGroupedTile
                                        onPassportGroupMoreTap(passportGroup.groupId)
                                    }
                                )
                            } else if (isGrouped) {
                                GroupedTile(
                                    docs      = groupDocs.sortedByDescending { it.createdAt },
                                    groupName = docGroupNames[doc.docGroupId] ?: "Group",
                                    onTap     = {
                                        if (isSelectMode && !selectionEnabled) return@GroupedTile
                                        onGroupTap(doc.docGroupId!!)
                                    },
                                    onMoreTap = {
                                        if (isSelectMode && !selectionEnabled) return@GroupedTile
                                        onGroupMoreTap(doc.docGroupId!!)
                                    }
                                )
                            } else {
                                GalleryTile(
                                    doc        = doc,
                                    isPendingAadhaarPair   = selectionEnabled && doc.id == pendingAadhaarPairId,
                                    isPendingPassportPair  = selectionEnabled && doc.id == pendingPassportPairId,
                                    onTap      = {
                                        if (isSelectMode && !selectionEnabled) return@GalleryTile
                                        when {
                                            isUngroupedAadhaar -> when {
                                                pendingAadhaarPairId == doc.id -> onPendingAadhaarPairChange(null)
                                                pendingAadhaarPairId != null -> {
                                                    onManualGroup(pendingAadhaarPairId!!, doc.id)
                                                    Toast.makeText(context, "Aadhaar cards paired", Toast.LENGTH_SHORT).show()
                                                    onPendingAadhaarPairChange(null)
                                                }
                                                else -> onDocumentClick(doc)
                                            }
                                            isUngroupedPassport -> when {
                                                pendingPassportPairId == doc.id -> onPendingPassportPairChange(null)
                                                pendingPassportPairId != null -> {
                                                    onManualPassportGroup(pendingPassportPairId!!, doc.id)
                                                    Toast.makeText(context, "Passport pages paired", Toast.LENGTH_SHORT).show()
                                                    onPendingPassportPairChange(null)
                                                }
                                                else -> onDocumentClick(doc)
                                            }
                                            else -> onDocumentClick(doc)
                                        }
                                    },
                                    onLongPress = {
                                        if (isSelectMode && !selectionEnabled) return@GalleryTile
                                        when {
                                            isUngroupedAadhaar -> {
                                                onPendingAadhaarPairChange(doc.id)
                                                Toast.makeText(context, "Now tap another Aadhaar to pair", Toast.LENGTH_SHORT).show()
                                            }
                                            isUngroupedPassport -> {
                                                onPendingPassportPairChange(doc.id)
                                                Toast.makeText(context, "Now tap another passport page to pair", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    onBadgeTap = {
                                        if (isSelectMode && !selectionEnabled) return@GalleryTile
                                        onBadgeTap(doc)
                                    },
                                    onMoreTap  = {
                                        if (isSelectMode && !selectionEnabled) return@GalleryTile
                                        onMoreTap(doc)
                                    },
                                )
                            }
                        }
                    }
                }
                repeat(columnCount - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun AadhaarGroupedTile(
    group: com.example.docscanner.domain.model.AadhaarGroup,
    isPendingFill: Boolean = false,
    onTap: () -> Unit,
    onMoreTap: () -> Unit,
) {
    val leftDoc = group.frontDoc
    val rightDoc = group.backDoc

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .background(BgSurface)
            .clickable(onClick = onTap)
    ) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            AadhaarHalfTile(
                doc = leftDoc,
                label = "Front",
                modifier = Modifier.weight(1f),
                isPendingFill = isPendingFill && leftDoc == null
            )
            AadhaarHalfTile(
                doc = rightDoc,
                label = "Back",
                modifier = Modifier.weight(1f),
                isPendingFill = isPendingFill && rightDoc == null
            )
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(42.dp)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
        )

        Text(
            group.displayName,
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 6.dp, bottom = 6.dp, end = 28.dp)
        )

        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(2.dp)
                .size(24.dp)
                .clip(CircleShape)
                .clickable(onClick = onMoreTap),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.MoreVert,
                null,
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun AadhaarHalfTile(
    doc: Document?,
    label: String,
    modifier: Modifier = Modifier,
    isPendingFill: Boolean = false,
) {
    Box(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(4.dp))
            .background(BgSurface)
            .then(
                if (isPendingFill) Modifier.border(2.dp, Coral, RoundedCornerShape(4.dp))
                else Modifier
            )
    ) {
        if (doc?.thumbnailPath != null) {
            AsyncImage(
                model = doc.thumbnailPath,
                contentDescription = label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(if (isPendingFill) Coral.copy(0.12f) else Color.Black.copy(0.04f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (isPendingFill) "Tap to add $label" else label,
                    color = if (isPendingFill) Coral else InkDim,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(4.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.Black.copy(0.55f))
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(
                label,
                color = Color.White,
                fontSize = 7.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ── Passport pair tile ────────────────────────────────────────────────────────

@Composable
private fun PassportGroupedTile(
    group: com.example.docscanner.domain.model.PassportGroup,
    isPendingFill: Boolean = false,
    onTap: () -> Unit,
    onLongPress: () -> Unit = {},
    onMoreTap: () -> Unit,
) {
    val ppViolet = Color(0xFF7C3AED)

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .background(BgSurface)
            .combinedClickable(onClick = onTap, onLongClick = onLongPress)
    ) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            PassportHalfTile(doc = group.frontDoc, label = "Data Page",  modifier = Modifier.weight(1f))
            PassportHalfTile(doc = group.backDoc,  label = "Back Page",  modifier = Modifier.weight(1f))
        }

        // Pending-pair glow (shown when this is the source tile or the target for completion)
        if (isPendingFill) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(ppViolet.copy(alpha = 0.18f))
                    .border(2.dp, ppViolet, RoundedCornerShape(4.dp))
            )
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(42.dp)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
        )

        Text(
            group.displayName,
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 6.dp, bottom = 6.dp, end = 28.dp)
        )

        // Passport badge (bottom-right corner)
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(ppViolet.copy(0.85f))
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text("📔", fontSize = 7.sp)
        }

        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(2.dp)
                .size(24.dp)
                .clip(CircleShape)
                .clickable(onClick = onMoreTap),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.MoreVert,
                null,
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun PassportHalfTile(
    doc: Document?,
    label: String,
    modifier: Modifier = Modifier,
) {
    val ppViolet = Color(0xFF7C3AED)
    Box(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(4.dp))
            .background(BgSurface)
    ) {
        if (doc?.thumbnailPath != null) {
            AsyncImage(
                model = doc.thumbnailPath,
                contentDescription = label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Missing-page placeholder — clearly prompts the user to pair
            Column(
                Modifier
                    .fillMaxSize()
                    .background(ppViolet.copy(alpha = 0.06f))
                    .border(1.dp, ppViolet.copy(alpha = 0.25f), RoundedCornerShape(4.dp)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Add, null,
                    tint = ppViolet.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    label,
                    color = ppViolet.copy(alpha = 0.7f),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Text(
                    "Long press to pair",
                    color = ppViolet.copy(alpha = 0.45f),
                    fontSize = 7.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }

        // Side label badge (top-left)
        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(4.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(if (doc != null) Color.Black.copy(0.55f) else ppViolet.copy(0.7f))
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(label, color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// TILE COMPOSABLES
// ═══════════════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GalleryTile(
    doc: Document,
    isPendingAadhaarPair: Boolean = false,
    isPendingPassportPair: Boolean = false,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
    onLongPress: () -> Unit = {},
    onBadgeTap: () -> Unit,
    onMoreTap: () -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(BgSurface)
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongPress
            )
    ) {
        // ── Thumbnail ─────────────────────────────────────────────────────────
        if (doc.thumbnailPath != null)
            AsyncImage(
                model = doc.thumbnailPath,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        else
            Icon(
                Icons.Default.Image, null,
                Modifier
                    .size(28.dp)
                    .align(Alignment.Center),
                tint = InkDim
            )

        if (isPendingAadhaarPair) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Coral.copy(alpha = 0.16f))
                    .border(2.dp, Coral, RoundedCornerShape(4.dp))
            )
        }
        if (isPendingPassportPair) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xFF7C3AED).copy(alpha = 0.16f))
                    .border(2.dp, Color(0xFF7C3AED), RoundedCornerShape(4.dp))
            )
        }

        // ── Bottom scrim ──────────────────────────────────────────────────────
        Box(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)))
                )
        )

        // ── Doc name (bottom-left) ────────────────────────────────────────────
        Text(
            doc.name,
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 6.dp, bottom = 6.dp, end = 28.dp) // leave room for 3-dot
        )

        // ── Type badge (top-left) ─────────────────────────────────────────────
        val label = doc.docClassLabel ?: "Other"
        val c = TypeColors[label] ?: InkMid
        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(4.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(c.copy(0.85f))
                .clickable(onClick = onBadgeTap)
                .padding(horizontal = 5.dp, vertical = 2.dp)
        ) {
            Text(label, color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.SemiBold)
        }

        // ── PDF pill (top-right) ──────────────────────────────────────────────
        if (doc.pdfPath != null)
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFFDC2626).copy(0.85f))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text("PDF", color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Bold)
            }

        // ── 3-dot menu button (bottom-right over scrim) ───────────────────────
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(2.dp)
                .size(24.dp)
                .clip(CircleShape)
                .clickable(onClick = onMoreTap),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = "More",
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
@Composable
private fun SelectTile(
    doc        : Document,
    isSelected : Boolean,
    orderNumber: Int? = null,          // ← new: 1-based tap order
    modifier   : Modifier = Modifier,
    isEnabled  : Boolean = true,
    onTap      : () -> Unit
) {
    val scaleAnim by animateFloatAsState(
        targetValue    = if (isSelected) 0.93f else 1f,
        animationSpec  = spring(stiffness = 600f), label = "sc_${doc.id}"
    )

    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .graphicsLayer { scaleX = scaleAnim; scaleY = scaleAnim }
            .background(BgSurface)
            .clickable(enabled = isEnabled, onClick = onTap)
    ) {
        if (doc.thumbnailPath != null)
            AsyncImage(
                model = doc.thumbnailPath, contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
            )
        else
            Icon(Icons.Default.Image, null,
                Modifier.size(24.dp).align(Alignment.Center), tint = InkDim)

        if (isSelected)
            Box(Modifier.fillMaxSize().background(Coral.copy(alpha = 0.22f)))
        else if (!isEnabled)
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.24f)))

        Box(
            Modifier
                .fillMaxWidth().height(52.dp).align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xBB000000))))
        )
        Text(
            doc.name, color = Color.White, fontSize = 9.sp,
            fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 6.dp, vertical = 5.dp)
        )

        val label = doc.docClassLabel ?: "Other"
        val c = TypeColors[label] ?: InkMid
        Box(
            Modifier
                .align(Alignment.TopStart).padding(4.dp)
                .clip(RoundedCornerShape(3.dp)).background(c.copy(0.85f))
                .padding(horizontal = 5.dp, vertical = 2.dp)
        ) {
            Text(label, color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.SemiBold)
        }

        // ── Order number badge (top-right) ────────────────────────────────
        Box(
            Modifier
                .align(Alignment.TopEnd).padding(5.dp)
                .size(21.dp).clip(CircleShape)
                .background(if (isSelected) Coral else Color.Black.copy(0.28f))
                .border(1.5.dp,
                    if (isSelected) Color.Transparent else Color.White.copy(0.55f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected && orderNumber != null)
                Text(
                    "$orderNumber",
                    color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold
                )
            else if (isSelected)
                Icon(Icons.Default.Check, null,
                    tint = Color.White, modifier = Modifier.size(12.dp))
        }
    }
}


@Composable
fun ContextAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(color.copy(0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        }
        Text(
            label, color = if (label == "Delete") color else Ink,
            fontSize = 15.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        trailing?.invoke()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoveToFolderSheet(
    title: String,
    subtitle: String,
    currentLabel: String?,
    allFolders: List<Folder>,
    groupedByType: Map<String, List<Document>>,
    onDismiss: () -> Unit,
    onSelect: (Folder) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BgCard,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(StrokeMid)
            )
        }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 40.dp)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    null,
                    tint = Ink,
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onDismiss)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Move to folder",
                        color = Ink,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (subtitle.isBlank()) title else "$title · $subtitle",
                        color = InkDim,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider(color = StrokeLight, thickness = 0.5.dp)

            Text(
                "FOLDERS",
                color = InkDim,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
            )

            allFolders.forEach { folder ->
                val isCurrent = folder.docType == currentLabel
                val folderColor = TypeColors[folder.docType] ?: InkMid
                val docCount = groupedByType[folder.name]?.size ?: 0

                Row(
                    Modifier
                        .fillMaxWidth()
                        .then(
                            if (isCurrent) Modifier.background(folderColor.copy(0.07f))
                            else Modifier
                        )
                        .clickable {
                            if (isCurrent) onDismiss() else onSelect(folder)
                        }
                        .padding(horizontal = 20.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(folderColor.copy(0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(folder.icon, fontSize = 17.sp)
                    }

                    Column(Modifier.weight(1f)) {
                        Text(
                            folder.name,
                            color = if (isCurrent) folderColor else Ink,
                            fontSize = 15.sp,
                            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal
                        )
                        Text(
                            if (docCount == 0) "Empty"
                            else "$docCount document${if (docCount != 1) "s" else ""}",
                            color = InkDim,
                            fontSize = 11.sp
                        )
                    }

                    if (isCurrent) {
                        Box(
                            Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(folderColor.copy(0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Check,
                                null,
                                tint = folderColor,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanTile(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(BgSurface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Coral.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.CameraAlt,
                contentDescription = "Scan into folder",
                tint = Coral,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
// ─── Shared composables kept for external references ─────────────────────────

@Composable
internal fun ClassificationBadge(document: Document, modifier: Modifier = Modifier) {
    val label = document.docClassLabel ?: "Other"
    val c = TypeColors[label] ?: InkMid
    Box(
        modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(c.copy(0.88f))
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Text(label, color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun DocumentNameRow(document: Document, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 7.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (document.pdfPath != null)
            Icon(
                Icons.Default.PictureAsPdf,
                null,
                tint = PdfBadgeBg,
                modifier = Modifier.size(10.dp)
            )
        Spacer(Modifier.width(3.dp))
        Text(
            document.name, color = Ink, fontSize = 10.sp, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EmptyState(unclassifiedOnly: Boolean = false) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(BgSurface)
                    .border(1.dp, StrokeLight, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (unclassifiedOnly) Icons.Default.HelpOutline else Icons.Default.Description,
                    null, tint = InkDim, modifier = Modifier.size(34.dp)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                if (unclassifiedOnly) "All classified" else "No documents yet",
                color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold
            )
            Text(
                if (unclassifiedOnly) "Every document has been assigned a type"
                else "Tap Scan below to add your first document",
                color = InkMid, fontSize = 13.sp, lineHeight = 19.sp
            )
        }
    }
}


@Composable
private fun MismatchPill(
    modifier : Modifier,
    label    : String,
    tag      : String,
    color    : Color,
    selected : Boolean,
    onClick  : () -> Unit
) {
    Row(
        modifier
            .clip(RoundedCornerShape(7.dp))
            .background(if (selected) color.copy(0.09f) else BgCard)
            .border(
                width = if (selected) 1.5.dp else 0.5.dp,
                color = if (selected) color else StrokeLight,
                shape = RoundedCornerShape(7.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Column(Modifier.weight(1f)) {
            Text(
                label,
                color      = if (selected) color else Ink,
                fontSize   = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis
            )
        }
        if (selected)
            Icon(
                Icons.Default.Check, null,
                tint     = color,
                modifier = Modifier.size(11.dp)
            )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// AADHAAR PAIR GRID
// Shows grouped front+back pairs as wide cards, unpaired docs as normal tiles
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun AadhaarPairGrid(
    groups          : List<com.example.docscanner.domain.model.AadhaarGroup>,
    unpairedDocs    : List<Document>,
    onDocumentClick : (Document) -> Unit,
    onMoreTap       : (Document) -> Unit,
    onManualGroup   : (String, String) -> Unit,
    onAddToGroup    : (docId: String, groupId: String, side: String) -> Unit,  // ← new
    onScanTap       : () -> Unit,
    onGroupMoreTap  : (groupId: String) -> Unit = {},  // ← ADD THIS
) {
    var pendingPairId by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ScanTile(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .height(64.dp)
                .clip(RoundedCornerShape(10.dp)),
            onClick = onScanTap
        )

        groups.forEach { group ->
            AadhaarPairCard(
                group           = group,
                pendingPairId   = pendingPairId,          // ← pass down
                onDocumentClick = onDocumentClick,
                onMoreTap       = onMoreTap,
                onFillSlot      = { side ->               // ← new
                    val pid = pendingPairId ?: return@AadhaarPairCard
                    onAddToGroup(pid, group.groupId, side)
                    Toast.makeText(context, "Added to pair", Toast.LENGTH_SHORT).show()
                    pendingPairId = null
                },
                onGroupMoreTap  = { onGroupMoreTap(group.groupId) }
            )
        }

        if (unpairedDocs.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.Info, null, tint = InkDim, modifier = Modifier.size(12.dp))
                Text(
                    "Long-press a card, then tap another to pair",
                    color = InkDim, fontSize = 10.sp
                )
            }
            val rows = unpairedDocs.chunked(2)
            rows.forEach { rowDocs ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    rowDocs.forEach { doc ->
                        Box(Modifier.weight(1f)) {
                            UnpairedAadhaarTile(
                                doc         = doc,
                                isPending   = doc.id == pendingPairId,
                                onTap       = {
                                    when {
                                        pendingPairId != null && pendingPairId != doc.id -> {
                                            onManualGroup(pendingPairId!!, doc.id)
                                            Toast.makeText(context, "Aadhaar cards paired", Toast.LENGTH_SHORT).show()
                                            pendingPairId = null
                                        }
                                        doc.id == pendingPairId -> pendingPairId = null
                                        else -> onDocumentClick(doc)
                                    }
                                },
                                onLongPress = {
                                    pendingPairId = doc.id
                                    Toast.makeText(context, "Now tap another card to pair", Toast.LENGTH_SHORT).show()
                                },
                                onMoreTap   = { onMoreTap(doc) }
                            )
                        }
                    }
                    if (rowDocs.size < 2) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

// ── A paired front+back card ──────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AadhaarPairCard(
    group           : com.example.docscanner.domain.model.AadhaarGroup,
    pendingPairId   : String?,
    onDocumentClick : (Document) -> Unit,
    onMoreTap       : (Document) -> Unit,
    onFillSlot      : (side: String) -> Unit,
    onGroupMoreTap  : () -> Unit = {},
) {
    val aadhaarBlue = Color(0xFF2563EB)

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(BgCard)
            .border(1.dp, aadhaarBlue.copy(0.18f), RoundedCornerShape(10.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // ── Header row ────────────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(aadhaarBlue)
            )
            Text(
                group.displayName,
                color      = Ink,
                fontSize   = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
                modifier   = Modifier.weight(1f)
            )
            if (group.isManuallyGrouped) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(InkDim.copy(0.12f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text("Manual", color = InkMid, fontSize = 9.sp)
                }
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (group.isComplete) aadhaarBlue.copy(0.1f)
                        else Color(0xFFD97706).copy(0.1f)
                    )
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(
                    if (group.isComplete) "Front + Back" else "Incomplete",
                    color      = if (group.isComplete) aadhaarBlue else Color(0xFFD97706),
                    fontSize   = 9.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            // ── 3-dot menu button ─────────────────────────────────────────────
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(BgSurface)
                    .clickable(onClick = onGroupMoreTap),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.MoreVert, null,
                    tint     = InkMid,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        // ── Two thumbnails ────────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AadhaarThumbSlot(
                doc           = group.frontDoc,
                sideLabel     = "Front",
                modifier      = Modifier.weight(1f),
                isPendingFill = pendingPairId != null && group.frontDoc == null,
                onClick       = { group.frontDoc?.let(onDocumentClick) },
                onFillSlot    = { onFillSlot("FRONT") },
                onMore        = { group.frontDoc?.let(onMoreTap) }
            )
            AadhaarThumbSlot(
                doc           = group.backDoc,
                sideLabel     = "Back",
                modifier      = Modifier.weight(1f),
                isPendingFill = pendingPairId != null && group.backDoc == null,
                onClick       = { group.backDoc?.let(onDocumentClick) },
                onFillSlot    = { onFillSlot("BACK") },
                onMore        = { group.backDoc?.let(onMoreTap) }
            )
        }
    }
}

// ── One half of the pair card ─────────────────────────────────────────────────

@Composable
private fun AadhaarThumbSlot(
    doc           : Document?,
    sideLabel     : String,
    modifier      : Modifier = Modifier,
    isPendingFill : Boolean = false,                      // ← new
    onClick       : () -> Unit,
    onFillSlot    : () -> Unit,                           // ← new
    onMore        : () -> Unit,
) {
    val borderColor by animateColorAsState(
        targetValue   = if (isPendingFill) Coral else Color.Transparent,
        animationSpec = tween(200),
        label         = "slot_border_$sideLabel"
    )

    Box(
        modifier
            .aspectRatio(1.586f)
            .clip(RoundedCornerShape(7.dp))
            .background(BgSurface)
            .border(2.dp, borderColor, RoundedCornerShape(7.dp))   // ← pulsing border hint
            .then(when {
                doc != null      -> Modifier.clickable(onClick = onClick)
                isPendingFill    -> Modifier.clickable(onClick = onFillSlot)  // ← empty but tappable
                else             -> Modifier
            })
    ) {
        if (doc?.thumbnailPath != null) {
            AsyncImage(
                model              = doc.thumbnailPath,
                contentDescription = sideLabel,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize()
            )
        } else {
            // Empty slot
            Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    if (isPendingFill) Icons.Default.AddCircleOutline   // ← visual hint
                    else Icons.Default.CreditCard,
                    null,
                    tint     = if (isPendingFill) Coral else InkDim,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    if (isPendingFill) "Tap to fill $sideLabel"
                    else "No $sideLabel",
                    color    = if (isPendingFill) Coral else InkDim,
                    fontSize = 9.sp
                )
            }
        }

        // Bottom scrim + label (unchanged)
        Box(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color(0xBB000000)))
                )
        )
        Text(
            sideLabel,
            color      = Color.White,
            fontSize   = 9.sp,
            fontWeight = FontWeight.SemiBold,
            modifier   = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 5.dp, bottom = 4.dp)
        )

        if (doc != null) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(2.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onMore),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.MoreVert, null,
                    tint     = Color.White,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

// ── Unpaired single tile (with pending-pair highlight) ────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UnpairedAadhaarTile(
    doc         : Document,
    isPending   : Boolean,
    onTap       : () -> Unit,
    onLongPress : () -> Unit,
    onMoreTap   : () -> Unit,
) {
    val borderColor by animateColorAsState(
        targetValue    = if (isPending) Coral else Color.Transparent,
        animationSpec  = tween(200),
        label          = "border_${doc.id}"
    )

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(BgSurface)
            .border(2.dp, borderColor, RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick     = onTap,
                onLongClick = onLongPress
            )
    ) {
        if (doc.thumbnailPath != null)
            AsyncImage(
                model              = doc.thumbnailPath,
                contentDescription = null,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize()
            )
        else
            Icon(
                Icons.Default.Image, null,
                tint     = InkDim,
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.Center)
            )

        // Pending highlight overlay
        if (isPending)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Coral.copy(0.15f))
            )

        // Bottom scrim + name
        Box(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color(0xBB000000)))
                )
        )
        Text(
            doc.name,
            color    = Color.White,
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 6.dp, bottom = 5.dp, end = 24.dp)
        )

        // Side label badge (FRONT / BACK / unknown)
        val sideText = doc.aadhaarSideLabel()
        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(4.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFF2563EB).copy(0.85f))
                .padding(horizontal = 5.dp, vertical = 2.dp)
        ) {
            Text(sideText, color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.SemiBold)
        }

        // 3-dot
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(2.dp)
                .size(22.dp)
                .clip(CircleShape)
                .clickable(onClick = onMoreTap),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.MoreVert, null,
                tint     = Color.White,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

@Composable
fun ScanFeedbackBanner(
    isProcessing: Boolean,
    feedback: ScanSaveFeedback?,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")
    val delays = listOf(0, 150, 300)

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = BgCard,
        border = BorderStroke(0.5.dp, StrokeLight),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isProcessing) {
                // Animated dots
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    delays.forEach { delay ->
                        val alpha by infiniteTransition.animateFloat(
                            initialValue = 0.2f, targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(600, delayMillis = delay),
                                repeatMode = RepeatMode.Reverse
                            ), label = "dot_$delay"
                        )
                        Box(
                            Modifier
                                .size(5.dp)
                                .graphicsLayer { this.alpha = alpha }
                                .clip(CircleShape)
                                .background(Coral)
                        )
                    }
                }
            } else {
                // Check circle
                Box(
                    Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(GreenAccent.copy(0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check, null,
                        tint = GreenAccent,
                        modifier = Modifier.size(9.dp)
                    )
                }
            }

            // Divider
            Box(Modifier.width(0.5.dp).height(14.dp).background(StrokeLight))

            // Label
            val label = when {
                isProcessing -> "Understanding your scan"
                feedback != null -> "${feedback.savedCount} page${if (feedback.savedCount == 1) "" else "s"} saved"
                else -> ""
            }
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Ink)

            // Subtitle
            val sub = when {
                isProcessing -> null
                feedback?.mismatchCount != null && feedback.mismatchCount > 0 ->
                    "· ${feedback.destinationLabel}, ${feedback.mismatchCount} suggestions"
                feedback != null -> "· ${feedback.destinationLabel}"
                else -> null
            }
            sub?.let {
                Text(it, fontSize = 12.sp, color = InkDim)
            }
        }
    }
}

@Composable
private fun GroupedTile(
    docs     : List<Document>,
    groupName : String,
    onTap    : () -> Unit,
    onMoreTap: () -> Unit,
) {
    val primary   = docs.firstOrNull()
    val secondary = docs.getOrNull(1)
    val tertiary  = docs.getOrNull(2)
    val count     = docs.size

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(onClick = onTap)
    ) {
        if (tertiary != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(start = 12.dp, top = 12.dp)
                    .graphicsLayer { rotationZ = 6f }
                    .clip(RoundedCornerShape(4.dp))
                    .background(BgSurface)
            ) {
                if (tertiary.thumbnailPath != null)
                    AsyncImage(
                        model = tertiary.thumbnailPath, contentDescription = null,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                    )
            }
        }

        if (secondary != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(start = 6.dp, top = 6.dp)
                    .graphicsLayer { rotationZ = 3f }
                    .clip(RoundedCornerShape(4.dp))
                    .background(BgSurface)
            ) {
                if (secondary.thumbnailPath != null)
                    AsyncImage(
                        model = secondary.thumbnailPath, contentDescription = null,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                    )
            }
        }

        // Primary (always shown)
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(4.dp))
                .background(BgSurface)
                // Single-doc group: show a dashed coral border as "waiting for more" hint
                .then(
                    if (count == 1)
                        Modifier.border(1.5.dp, Coral.copy(0.5f), RoundedCornerShape(4.dp))
                    else
                        Modifier
                )
        ) {
            if (primary?.thumbnailPath != null)
                AsyncImage(
                    model = primary.thumbnailPath, contentDescription = null,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                )
            else
                Icon(
                    Icons.Default.Image, null,
                    Modifier.size(28.dp).align(Alignment.Center),
                    tint = InkDim
                )

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)))
                    )
            )

            Text(
                groupName,
                color = Color.White, fontSize = 9.sp,
                fontWeight = FontWeight.Medium, maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 6.dp, bottom = 6.dp, end = 28.dp)
            )

            // Count badge — shows "1" with a + hint when solo, normal count otherwise
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (count == 1) Coral.copy(0.85f) else Color(0xCC000000))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(
                    if (count == 1) "+" else "$count",
                    color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold
                )
            }

            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(2.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onMoreTap),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.MoreVert, null,
                    tint = Color.White, modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
