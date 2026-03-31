package com.example.docscanner.presentation.shared

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.docscanner.data.classifier.DocumentClassifier
import com.example.docscanner.data.export.DocumentExporter
import com.example.docscanner.data.masking.AadhaarMasker
import com.example.docscanner.data.masking.PanMasker
import com.example.docscanner.data.ocr.MlKitOcrHelper
import com.example.docscanner.data.processor.DocumentProcessor
import com.example.docscanner.domain.model.DocClassType
import com.example.docscanner.domain.model.Document
import com.example.docscanner.domain.model.DocumentCorners
import com.example.docscanner.domain.model.ExportFormat
import com.example.docscanner.domain.model.FolderExportType
import com.example.docscanner.domain.model.ScannedPage
import com.example.docscanner.domain.repository.DocumentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class ScannedMismatch(
    val documentId    : String,
    val documentName  : String,
    val detectedLabel : String,
    val folderLabel   : String
)

@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val documentProcessor  : DocumentProcessor,
    val          exporter          : DocumentExporter,
    private val documentRepository : DocumentRepository,
    private val documentClassifier : DocumentClassifier,
    private val ocrHelper          : MlKitOcrHelper,
    private val aadhaarMasker      : AadhaarMasker,
    private val panMasker          : PanMasker
) : ViewModel() {

    private val _state = MutableStateFlow(ScannerState())
    val state: StateFlow<ScannerState> = _state.asStateFlow()

    private var lastAadhaarGroupId : String? = null
    private var anchoredGroupId    : String? = null
    private var lastTargetFolderId : String  = ""

    private val pendingCropJobs = mutableListOf<Job>()
    private val cropJobLock     = Any()

    override fun onCleared() {
        super.onCleared()
        documentClassifier.close()
    }

    fun setSessionId(sessionId: String?) {
        _state.value = _state.value.copy(sessionId = sessionId)
    }

    fun setTargetFolder(
        folderId   : String,
        folderName : String,
        exportType : FolderExportType,
        docType    : String? = null
    ) {
        if (folderId != lastTargetFolderId) {
            lastAadhaarGroupId = null
            anchoredGroupId    = null
            lastTargetFolderId = folderId
        }
        _state.value = _state.value.copy(
            targetFolderId   = folderId,
            targetFolderName = folderName,
            targetExportType = exportType,
            targetDocType    = docType
        )
    }

    fun clearAadhaarGroup() {
        lastAadhaarGroupId = null
        anchoredGroupId    = null
        lastTargetFolderId = ""
    }

    fun onPhotoCaptured(bitmap: Bitmap, detectedCorners: DocumentCorners? = null) {
        val pageId = UUID.randomUUID().toString()

        _state.value = _state.value.copy(
            pages = _state.value.pages + ScannedPage(
                id             = pageId,
                originalBitmap = bitmap
            )
        )

        val job = viewModelScope.launch {
            // ML Kit already returns perspective-corrected bitmaps.
            // Only run perspectiveTransform when caller explicitly provides corners.
            val cropped = if (detectedCorners != null) {
                documentProcessor.perspectiveTransform(bitmap, detectedCorners)
            } else {
                bitmap
            }

            val docType = if (_state.value.targetDocType == null) {
                try { documentClassifier.classify(cropped) } catch (_: Exception) { null }
            } else null

            updatePage(pageId) {
                it.copy(
                    corners        = detectedCorners,
                    croppedBitmap  = cropped,
                    enhancedBitmap = cropped,
                    docClassType   = docType
                )
            }
        }

        synchronized(cropJobLock) { pendingCropJobs.add(job) }
    }

    fun onAutoSavePages() {
        val currentState = _state.value
        if (currentState.pages.isEmpty()) return
        _state.value = currentState.copy(isSaving = true)

        val isFolderScan = currentState.targetDocType != null

        Log.d("AadhaarDebug", "═══ onAutoSavePages START ═══")
        Log.d("AadhaarDebug", "isFolderScan: $isFolderScan")
        Log.d("AadhaarDebug", "targetDocType: ${currentState.targetDocType}")
        Log.d("AadhaarDebug", "targetFolderId: ${currentState.targetFolderId}")
        Log.d("AadhaarDebug", "pageCount: ${currentState.pages.size}")
        Log.d("AadhaarDebug", "anchoredGroupId (before): $anchoredGroupId")
        Log.d("AadhaarDebug", "lastAadhaarGroupId (before): $lastAadhaarGroupId")

        viewModelScope.launch {
            try {
                // Wait for every crop coroutine to finish before reading bitmaps
                val jobs = synchronized(cropJobLock) { pendingCropJobs.toList() }
                Log.d("AadhaarDebug", "Waiting for ${jobs.size} crop job(s)…")
                jobs.forEach { it.join() }
                synchronized(cropJobLock) { pendingCropJobs.clear() }
                Log.d("AadhaarDebug", "All crop jobs done — proceeding with save")

                // Re-read state AFTER all crops are done
                val freshState = _state.value

                val savedDocInfos    = mutableListOf<SavedDocInfo>()
                // This list grows as each page is saved. It is passed into
                // resolveGroupId so the carry-forward step can see what groups
                // the current batch already has, preventing N backs from all
                // being blindly assigned to the last front when OCR is weak.
                val savedAadhaarDocs = mutableListOf<SavedAadhaarDocInfo>()

                var lastAadhaarSide     : String? = null
                var lastAadhaarGroupId2 : String? = null

                // ── Sort pages: FRONT before BACK ─────────────────────────────
                val sortedPages = if (freshState.pages.all { it.docClassType != null }) {
                    freshState.pages.sortedWith(compareBy { page ->
                        when (page.docClassType) {
                            DocClassType.AADHAAR_FRONT -> 0
                            DocClassType.AADHAAR_BACK  -> 1
                            else                       -> 2
                        }
                    })
                } else {
                    val pageWithType = freshState.pages.map { page ->
                        val bmp  = page.enhancedBitmap ?: page.croppedBitmap ?: page.originalBitmap
                        val type = try { documentClassifier.classify(bmp) }
                        catch (_: Exception) { DocClassType.OTHER }
                        page to type
                    }
                    Log.d("AadhaarDebug", "Pre-sort types: ${pageWithType.map { it.second.name }}")
                    pageWithType
                        .sortedWith(compareBy { (_, type) ->
                            when (type) {
                                DocClassType.AADHAAR_FRONT -> 0
                                DocClassType.AADHAAR_BACK  -> 1
                                else                       -> 2
                            }
                        })
                        .map { (page, _) -> page }
                }

                Log.d("AadhaarDebug", "Processing ${sortedPages.size} pages after sort")

                sortedPages.forEachIndexed { index, page ->
                    Log.d("AadhaarDebug", "─── Page $index ───")

                    val originalBitmap =
                        page.enhancedBitmap ?: page.croppedBitmap ?: page.originalBitmap

                    val docClassLabel    : String
                    val smartName        : String?
                    val saveBitmap       : Bitmap
                    var detectedLabel    : String? = null
                    var aadhaarSide      : String? = null
                    var aadhaarGroupId   : String? = null
                    var aadhaarOcrDigits : String? = null

                    if (isFolderScan) {
                        val folderLabel = freshState.targetDocType!!

                        val detected = try {
                            documentClassifier.classify(originalBitmap)
                        } catch (e: Exception) {
                            Log.e("AadhaarDebug", "Classification failed", e)
                            DocClassType.OTHER
                        }

                        Log.d("AadhaarDebug", "detected: ${detected.name} | displayName: ${detected.displayName}")
                        Log.d("AadhaarDebug", "detected.isAadhaar: ${detected.isAadhaar}")
                        Log.d("AadhaarDebug", "folderLabel: $folderLabel")

                        detectedLabel = detected.displayName

                        docClassLabel = if (detected.isAadhaar) {
                            detected.displayName
                        } else {
                            folderLabel
                        }

                        Log.d("AadhaarDebug", "docClassLabel (folder scan): $docClassLabel")

                        if (detected.isAadhaar) {
                            aadhaarSide = aadhaarSideOf(detected)

                            val ocrFields = try {
                                ocrHelper.extractFields(originalBitmap, detected)
                            } catch (e: Exception) {
                                Log.e("AadhaarDebug", "Folder OCR failed", e)
                                null
                            }

                            aadhaarOcrDigits = ocrFields?.idNumber?.filter { it.isDigit() }
                            aadhaarGroupId   = resolveGroupId(
                                bitmap      = originalBitmap,
                                detected    = detected,
                                ocrFields   = ocrFields,
                                inBatchDocs = savedAadhaarDocs      // ← pass running batch
                            )

                            Log.d("AadhaarDebug",
                                "✓ isAadhaar=true → side: $aadhaarSide | groupId: $aadhaarGroupId | ocrDigits: $aadhaarOcrDigits")
                        } else {
                            Log.d("AadhaarDebug", "✗ isAadhaar=false → aadhaarSide=null, aadhaarGroupId=null")
                        }

                        saveBitmap = maskIfNeeded(detected, originalBitmap)
                        smartName  = null

                    } else {
                        val classifiedType = try {
                            documentClassifier.classify(originalBitmap)
                        } catch (e: Exception) {
                            Log.e("AadhaarDebug", "Classification failed", e)
                            page.docClassType ?: DocClassType.OTHER
                        }

                        docClassLabel = classifiedType.displayName

                        Log.d("AadhaarDebug", "Global scan → classifiedType: ${classifiedType.name}")
                        Log.d("AadhaarDebug", "docClassLabel: $docClassLabel")

                        val ocrFields = try {
                            ocrHelper.extractFields(originalBitmap, classifiedType)
                        } catch (e: Exception) {
                            Log.e("AadhaarDebug", "OCR failed", e)
                            null
                        }

                        Log.d("AadhaarDebug", "ocrFields.name: ${ocrFields?.name}")
                        Log.d("AadhaarDebug", "ocrFields.idNumber: ${ocrFields?.idNumber}")

                        smartName = try {
                            buildSmartName(classifiedType, ocrFields?.name, ocrFields?.idNumber)
                        } catch (e: Exception) {
                            Log.e("AadhaarDebug", "OCR naming failed", e)
                            null
                        }

                        if (classifiedType.isAadhaar) {
                            aadhaarSide      = aadhaarSideOf(classifiedType)
                            aadhaarOcrDigits = ocrFields?.idNumber?.filter { it.isDigit() }
                            aadhaarGroupId   = resolveGroupId(
                                bitmap      = originalBitmap,
                                detected    = classifiedType,
                                ocrFields   = ocrFields,
                                inBatchDocs = savedAadhaarDocs      // ← pass running batch
                            )
                            Log.d("AadhaarDebug",
                                "✓ isAadhaar=true → side: $aadhaarSide | groupId: $aadhaarGroupId | ocrDigits: $aadhaarOcrDigits")
                        } else {
                            Log.d("AadhaarDebug", "✗ isAadhaar=false → resetting group state")
                            lastAadhaarGroupId = null
                            anchoredGroupId    = null
                        }

                        saveBitmap = maskIfNeeded(classifiedType, originalBitmap)
                    }

                    lastAadhaarSide     = aadhaarSide
                    lastAadhaarGroupId2 = aadhaarGroupId

                    val timestamp = System.currentTimeMillis()
                    val name = smartName
                        ?: if (freshState.pages.size == 1) "Scan_$timestamp"
                        else "Scan_${timestamp}_p${index + 1}"

                    val imageUri = exporter.exportAsImage(
                        page.copy(enhancedBitmap = saveBitmap),
                        name,
                        ExportFormat.JPEG
                    )

                    val docId = UUID.randomUUID().toString()

                    Log.d("AadhaarDebug", "Saving doc → id: $docId | name: $name")
                    Log.d("AadhaarDebug", "  docClassLabel: $docClassLabel")
                    Log.d("AadhaarDebug", "  aadhaarSide: $aadhaarSide")
                    Log.d("AadhaarDebug", "  aadhaarGroupId: $aadhaarGroupId")

                    documentRepository.saveDocument(
                        Document(
                            id             = docId,
                            folderId       = freshState.targetFolderId,
                            name           = name,
                            pageCount      = 1,
                            thumbnailPath  = imageUri?.toString(),
                            pdfPath        = null,
                            docClassLabel  = docClassLabel,
                            createdAt      = System.currentTimeMillis(),
                            sessionId      = freshState.sessionId,
                            aadhaarSide    = aadhaarSide,
                            aadhaarGroupId = aadhaarGroupId
                        )
                    )

                    if (aadhaarGroupId != null && aadhaarSide != null) {
                        savedAadhaarDocs.add(
                            SavedAadhaarDocInfo(
                                documentId = docId,
                                groupId    = aadhaarGroupId,
                                side       = aadhaarSide,
                                ocrDigits  = aadhaarOcrDigits
                            )
                        )
                    }

                    if (isFolderScan) {
                        savedDocInfos.add(
                            SavedDocInfo(
                                documentId    = docId,
                                documentName  = name,
                                detectedLabel = detectedLabel,
                                folderLabel   = freshState.targetDocType
                            )
                        )
                    }
                }

                // ── Number-based reconciliation ───────────────────────────────
                if (savedAadhaarDocs.size > 1) {

                    val fullNumberToGroupId = mutableMapOf<String, String>()
                    savedAadhaarDocs.forEach { info ->
                        val digits = info.ocrDigits ?: return@forEach
                        if (digits.length == 12) {
                            val existing = fullNumberToGroupId[digits]
                            val isConfident = !info.groupId.startsWith("aadhaar_num_") &&
                                    !info.groupId.startsWith("aadhaar_grp_")
                            val existingIsConfident = existing != null &&
                                    !existing.startsWith("aadhaar_num_") &&
                                    !existing.startsWith("aadhaar_grp_")
                            if (existing == null || (isConfident && !existingIsConfident)) {
                                fullNumberToGroupId[digits] = info.groupId
                            }
                        }
                    }

                    Log.d("AadhaarDebug", "fullNumberToGroupId: $fullNumberToGroupId")

                    savedAadhaarDocs.forEach { info ->
                        val digits = info.ocrDigits ?: return@forEach

                        val correctGroupId = when {
                            digits.length == 12 -> fullNumberToGroupId[digits]
                            digits.length >= 4  -> {
                                val matches = fullNumberToGroupId.entries
                                    .filter { (fullNum, _) -> fullNum.endsWith(digits) }
                                if (matches.size == 1) matches.first().value else null
                            }
                            else -> null
                        }

                        if (correctGroupId != null && correctGroupId != info.groupId) {
                            Log.d("AadhaarDebug",
                                "Number-reconcile ${info.documentId}: ${info.groupId} → $correctGroupId (digits: $digits)")
                            documentRepository.updateAadhaarGroupIdOnly(info.documentId, correctGroupId)
                        }
                    }
                }

                // ── Mismatch detection ────────────────────────────────────────
                val mismatches = savedDocInfos
                    .filter { info ->
                        info.detectedLabel != null &&
                                info.detectedLabel != DocClassType.OTHER.displayName &&
                                isMismatch(info.detectedLabel!!, info.folderLabel ?: "")
                    }
                    .map { info ->
                        ScannedMismatch(
                            documentId    = info.documentId,
                            documentName  = info.documentName,
                            detectedLabel = info.detectedLabel!!,
                            folderLabel   = info.folderLabel!!
                        )
                    }

                Log.d("AadhaarDebug", "═══ FINAL STATE ═══")
                Log.d("AadhaarDebug", "lastAadhaarSide: $lastAadhaarSide")
                Log.d("AadhaarDebug", "lastAadhaarGroupId2: $lastAadhaarGroupId2")
                Log.d("AadhaarDebug", "anchoredGroupId (after): $anchoredGroupId")
                Log.d("AadhaarDebug", "lastAadhaarGroupId (after): $lastAadhaarGroupId")
                Log.d("AadhaarDebug", "mismatches: ${mismatches.size}")
                Log.d("AadhaarDebug", "═══════════════════")

                _state.value = _state.value.copy(
                    isSaving          = false,
                    saveSuccess       = true,
                    pendingMismatches = mismatches
                )

            } catch (e: Exception) {
                Log.e("AadhaarDebug", "onAutoSavePages CRASHED", e)
                _state.value = _state.value.copy(isSaving = false)
            }
        }
    }

    // ── Group ID resolution ───────────────────────────────────────────────────

    private suspend fun resolveGroupId(
        bitmap      : Bitmap,
        detected    : DocClassType,
        ocrFields   : com.example.docscanner.data.ocr.ExtractedFields? = null,
        inBatchDocs : List<SavedAadhaarDocInfo> = emptyList()
    ): String {

        val fields = ocrFields ?: try {
            ocrHelper.extractFields(bitmap, detected)
        } catch (e: Exception) {
            Log.e("AadhaarDebug", "OCR groupId failed", e)
            null
        }

        Log.d("AadhaarDebug", "resolveGroupId → detected: ${detected.name}")
        Log.d("AadhaarDebug", "resolveGroupId → fields.name: ${fields?.name}")
        Log.d("AadhaarDebug", "resolveGroupId → fields.idNumber: ${fields?.idNumber}")
        Log.d("AadhaarDebug", "resolveGroupId → anchoredGroupId: $anchoredGroupId")
        Log.d("AadhaarDebug", "resolveGroupId → lastAadhaarGroupId: $lastAadhaarGroupId")
        Log.d("AadhaarDebug", "resolveGroupId → inBatchDocs.size: ${inBatchDocs.size}")

        val ocrDigits = fields?.idNumber?.filter { it.isDigit() }
        val sessionId = _state.value.sessionId
        val thisSide  = aadhaarSideOf(detected)  // "FRONT" or "BACK"

        // ── 1. Confident OCR (name + number) ─────────────────────────────────
        val confidentKey = if (fields?.name != null && fields.idNumber != null)
            ocrHelper.buildAadhaarGroupId(fields.name, fields.idNumber)
        else null

        if (confidentKey != null) {
            if (sessionId != null) {
                val existingDocs = try {
                    documentRepository.getExistingAadhaarDocs(sessionId)
                } catch (e: Exception) { emptyList() }

                val digits12 = ocrDigits?.takeIf { it.length == 12 }
                if (digits12 != null) {
                    val crossMatch = existingDocs
                        .mapNotNull { it.aadhaarGroupId }
                        .distinct()
                        .firstOrNull { gId -> gId != confidentKey && gId.contains(digits12) }
                    if (crossMatch != null) {
                        val docsInCross  = existingDocs.filter { it.aadhaarGroupId == crossMatch }
                        val pairComplete = docsInCross.any { it.aadhaarSide == "FRONT" } &&
                                docsInCross.any { it.aadhaarSide == "BACK" }
                        if (!pairComplete) {
                            anchoredGroupId    = crossMatch
                            lastAadhaarGroupId = crossMatch
                            Log.d("AadhaarDebug",
                                "resolveGroupId → cross-format match: $confidentKey → $crossMatch")
                            return crossMatch
                        }
                    }
                }

                val docsInGroup     = existingDocs.filter { it.aadhaarGroupId == confidentKey }
                val hasCompletePair = docsInGroup.any { it.aadhaarSide == "FRONT" } &&
                        docsInGroup.any { it.aadhaarSide == "BACK" }

                if (hasCompletePair) {
                    val newKey = "${confidentKey}_${System.currentTimeMillis()}"
                    if (anchoredGroupId != null && anchoredGroupId != newKey) {
                        lastAadhaarGroupId = null
                    }
                    anchoredGroupId    = newKey
                    lastAadhaarGroupId = newKey
                    Log.d("AadhaarDebug", "resolveGroupId → complete pair exists, new group: $newKey")
                    return newKey
                }
            }

            if (anchoredGroupId != null && anchoredGroupId != confidentKey) {
                lastAadhaarGroupId = null
            }
            anchoredGroupId    = confidentKey
            lastAadhaarGroupId = confidentKey
            Log.d("AadhaarDebug", "resolveGroupId → confident key: $confidentKey")
            return confidentKey
        }

        // ── 2. Full 12-digit DB cross-match ──────────────────────────────────
        if (sessionId != null && ocrDigits != null && ocrDigits.length == 12) {
            val existingDocs = try {
                documentRepository.getExistingAadhaarDocs(sessionId)
            } catch (e: Exception) {
                Log.e("AadhaarDebug", "DB cross-match failed", e)
                emptyList()
            }

            val matchedGroupId = existingDocs
                .mapNotNull { it.aadhaarGroupId }
                .distinct()
                .firstOrNull { groupId -> groupId.contains(ocrDigits) }

            if (matchedGroupId != null) {
                lastAadhaarGroupId = matchedGroupId
                Log.d("AadhaarDebug", "resolveGroupId → full-number DB match: $matchedGroupId")
                return matchedGroupId
            }
        }

        // ── 3. Carry-forward — only when unambiguous in the current batch ─────
        //
        // Problem: pages are sorted [F1..FN, B1..BN]. After all fronts are
        // processed, lastAadhaarGroupId = FN's groupId. Without this guard,
        // B1..B(N-1) all carry-forward to FN's group when OCR is weak.
        //
        // Fix: only carry-forward when the candidate group is the ONLY one in
        // the current batch that is still missing thisSide. If there are
        // multiple incomplete groups, fall through to the in-batch classifier
        // fill (step 4.5) which picks the correct one from the DB.
        if (lastAadhaarGroupId != null && thisSide != null) {
            val batchGroupsMissingThisSide = inBatchDocs
                .groupBy { it.groupId }
                .filter { (_, docs) -> docs.none { it.side == thisSide } }
                .keys

            val unambiguous = when {
                // No in-batch Aadhaar docs yet → single-card scan, safe to carry-forward
                batchGroupsMissingThisSide.isEmpty() -> true
                // Exactly one incomplete group and it IS the carry-forward candidate
                batchGroupsMissingThisSide.size == 1 &&
                        batchGroupsMissingThisSide.contains(lastAadhaarGroupId) -> true
                // Multiple incomplete groups → ambiguous, must not carry-forward blindly
                else -> false
            }

            if (unambiguous) {
                Log.d("AadhaarDebug",
                    "resolveGroupId → carry-forward (unambiguous): $lastAadhaarGroupId")
                return lastAadhaarGroupId!!
            } else {
                Log.d("AadhaarDebug",
                    "resolveGroupId → carry-forward SKIPPED " +
                            "(${batchGroupsMissingThisSide.size} incomplete groups in batch)")
            }
        }

        // ── 4. Partial 4-digit DB cross-match ────────────────────────────────
        if (sessionId != null && ocrDigits != null && ocrDigits.length >= 4) {
            val existingDocs = try {
                documentRepository.getExistingAadhaarDocs(sessionId)
            } catch (e: Exception) { emptyList() }

            val allGroupIds = existingDocs.mapNotNull { it.aadhaarGroupId }.distinct()
            val last4 = ocrDigits.takeLast(4)
            val matches = allGroupIds.filter { it.endsWith(last4) }

            if (matches.size == 1) {
                lastAadhaarGroupId = matches.first()
                Log.d("AadhaarDebug", "resolveGroupId → partial DB match: ${matches.first()}")
                return matches.first()
            }
        }

        // ── 4.5 Classifier-guided incomplete group fill ───────────────────────
        // First try the in-memory batch (avoids DB round-trip and is always
        // fresh, since docs are saved sequentially before this point).
        // Then fall back to DB for docs from previous scans in this session.
        if (thisSide != null) {
            // In-batch check
            val batchIncomplete = inBatchDocs
                .groupBy { it.groupId }
                .filter { (_, docs) -> docs.none { it.side == thisSide } }
                .keys.toList()

            if (batchIncomplete.size == 1) {
                val matchId = batchIncomplete.first()
                lastAadhaarGroupId = matchId
                Log.d("AadhaarDebug",
                    "resolveGroupId → in-batch classifier fill ($thisSide): $matchId")
                return matchId
            }

            // DB-based check for docs from earlier scans in this session
            if (sessionId != null) {
                val existingDocs = try {
                    documentRepository.getExistingAadhaarDocs(sessionId)
                } catch (e: Exception) { emptyList() }

                val groupsByMissingSide = existingDocs
                    .filter { it.aadhaarGroupId != null }
                    .groupBy { it.aadhaarGroupId!! }
                    .mapValues { (_, docs) ->
                        val hasFront = docs.any { it.aadhaarSide == "FRONT" }
                        val hasBack  = docs.any { it.aadhaarSide == "BACK"  }
                        when {
                            hasFront && !hasBack -> "BACK"
                            hasBack && !hasFront -> "FRONT"
                            else                 -> null
                        }
                    }
                    .filter { (_, missingSide) -> missingSide != null }

                val matchingGroups = groupsByMissingSide
                    .filter { (_, missingSide) -> missingSide == thisSide }
                    .keys.toList()

                if (matchingGroups.size == 1) {
                    val matchId = matchingGroups.first()
                    lastAadhaarGroupId = matchId
                    Log.d("AadhaarDebug",
                        "resolveGroupId → DB classifier fill ($thisSide): $matchId")
                    return matchId
                }
            }
        }

        // ── 5. Partial OCR key (name only OR number only) ─────────────────────
        val partialKey = if (fields != null)
            ocrHelper.buildAadhaarGroupId(fields.name, fields.idNumber)
        else null

        // ── 6. Last resort — unique timestamp ────────────────────────────────
        val result = partialKey ?: "aadhaar_grp_${System.currentTimeMillis()}"
        lastAadhaarGroupId = result
        Log.d("AadhaarDebug", "resolveGroupId → fallback key: $result")
        return result
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun normalizeForMismatch(label: String): String = when (label) {
        "Aadhaar Front", "Aadhaar Back" -> "Aadhaar"
        else -> label
    }

    private fun aadhaarSideOf(type: DocClassType): String? = when (type) {
        DocClassType.AADHAAR_FRONT -> "FRONT"
        DocClassType.AADHAAR_BACK  -> "BACK"
        else                       -> null
    }

    private fun isMismatch(detectedLabel: String, folderLabel: String): Boolean {
        if (detectedLabel == folderLabel) return false
        return normalizeForMismatch(detectedLabel) != normalizeForMismatch(folderLabel)
    }

    private suspend fun maskIfNeeded(type: DocClassType, bitmap: Bitmap): Bitmap = when {
        type.isAadhaar -> try { aadhaarMasker.mask(bitmap) }
        catch (e: Exception) { Log.e("ScannerVM", "Aadhaar mask failed", e); bitmap }
        type == DocClassType.PAN -> try { panMasker.mask(bitmap) }
        catch (e: Exception) { Log.e("ScannerVM", "PAN mask failed", e); bitmap }
        else -> bitmap
    }

    private fun buildSmartName(
        type       : DocClassType,
        personName : String?,
        idNumber   : String?
    ): String? {
        if (personName == null && idNumber == null) return null
        val parts = mutableListOf<String>()
        personName?.let { parts.add(it) }
        val label = type.displayName.replace(" ", "_")
        if (type != DocClassType.OTHER) parts.add(label)
        idNumber?.let { parts.add(it.replace("\\s".toRegex(), "").takeLast(6)) }
        return if (parts.isEmpty()) null else parts.joinToString("_")
    }

    fun dismissMismatches() {
        _state.value = _state.value.copy(pendingMismatches = emptyList())
    }

    fun onSaveNavigated() {
        _state.value = _state.value.copy(saveSuccess = false)
    }

    fun onReset() {
        synchronized(cropJobLock) { pendingCropJobs.clear() }
        _state.value = ScannerState(
            sessionId        = _state.value.sessionId,
            targetFolderId   = _state.value.targetFolderId,
            targetFolderName = _state.value.targetFolderName,
            targetExportType = _state.value.targetExportType,
            targetDocType    = _state.value.targetDocType
        )
    }

    private fun updatePage(pageId: String, transform: (ScannedPage) -> ScannedPage) {
        _state.value = _state.value.copy(
            pages = _state.value.pages.map {
                if (it.id == pageId) transform(it) else it
            }
        )
    }

    private data class SavedDocInfo(
        val documentId    : String,
        val documentName  : String,
        val detectedLabel : String?,
        val folderLabel   : String?
    )

    private data class SavedAadhaarDocInfo(
        val documentId : String,
        val groupId    : String,
        val side       : String,
        val ocrDigits  : String?
    )
}

data class ScannerState(
    val pages             : List<ScannedPage>     = emptyList(),
    val isSaving          : Boolean               = false,
    val saveSuccess       : Boolean               = false,
    val targetFolderId    : String                = "",
    val targetFolderName  : String                = "",
    val targetExportType  : FolderExportType      = FolderExportType.PDF,
    val targetDocType     : String?               = null,
    val sessionId         : String?               = null,
    val pendingMismatches : List<ScannedMismatch> = emptyList()
)