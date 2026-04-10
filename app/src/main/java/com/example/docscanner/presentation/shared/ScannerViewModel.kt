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
import com.example.docscanner.data.security.AadhaarSecureHelper
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
    private val secureHelper       : AadhaarSecureHelper,   // ← NEW
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
            pages = _state.value.pages + ScannedPage(id = pageId, originalBitmap = bitmap)
        )
        val job = viewModelScope.launch {
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
        synchronized(pendingCropJobs) { pendingCropJobs.add(job) }
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
                val jobs = synchronized(cropJobLock) { pendingCropJobs.toList() }
                Log.d("AadhaarDebug", "Waiting for ${jobs.size} crop job(s)…")
                jobs.forEach { it.join() }
                synchronized(cropJobLock) { pendingCropJobs.clear() }
                Log.d("AadhaarDebug", "All crop jobs done — proceeding with save")

                val freshState = _state.value

                val savedDocInfos    = mutableListOf<SavedDocInfo>()
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

                    val docClassLabel  : String
                    val smartName      : String?
                    val saveBitmap     : Bitmap
                    var detectedLabel  : String? = null
                    var aadhaarSide    : String? = null
                    var aadhaarGroupId : String? = null
                    var aadhaarName    : String? = null
                    var aadhaarDob     : String? = null
                    var aadhaarGender  : String? = null
                    var aadhaarMaskedNumber: String? = null
                    var aadhaarAddress : String? = null

                    // Hashes — never store raw digits beyond this function scope
                    var aadhaarNumHash  : String? = null   // hash of full 12 digits
                    var aadhaarLast4Hash: String? = null   // hash of last 4 digits
                    var aadhaarDigitLen : Int     = 0      // 12, 4, or 0 — for reconciliation logic

                    if (isFolderScan) {
                        val folderLabel = freshState.targetDocType!!

                        val detected = try {
                            documentClassifier.classify(originalBitmap)
                        } catch (e: Exception) {
                            Log.e("AadhaarDebug", "Classification failed", e)
                            DocClassType.OTHER
                        }

                        Log.d("AadhaarDebug", "detected: ${detected.name} | isAadhaar: ${detected.isAadhaar}")
                        Log.d("AadhaarDebug", "folderLabel: $folderLabel")

                        detectedLabel = detected.displayName

                        // Keep folder scans in the chosen section until the user
                        // resolves any mismatch from the modal.
                        docClassLabel = folderLabel

                        if (detected.isAadhaar) {
                            aadhaarSide = aadhaarSideOf(detected)

                            val ocrFields = try {
                                ocrHelper.extractFields(originalBitmap, detected)
                            } catch (e: Exception) {
                                Log.e("AadhaarDebug", "Folder OCR failed", e)
                                null
                            }

                            aadhaarName = ocrFields?.name?.replace("_", " ")
                            aadhaarDob = ocrFields?.dob
                            aadhaarGender = ocrFields?.gender
                            aadhaarMaskedNumber = maskAadhaarNumber(ocrFields?.idNumber)
                            aadhaarAddress = ocrFields?.address

                            // Compute hashes immediately — raw digits not kept
                            val rawDigits = ocrFields?.idNumber?.filter { it.isDigit() }
                            if (rawDigits != null && rawDigits.length == 12) {
                                aadhaarNumHash   = secureHelper.hashAadhaarNumber(rawDigits)
                                aadhaarLast4Hash = secureHelper.hashLast4(rawDigits)
                                aadhaarDigitLen  = 12
                            } else if (rawDigits != null && rawDigits.length >= 4) {
                                aadhaarLast4Hash = secureHelper.hashLast4(rawDigits)
                                aadhaarDigitLen  = rawDigits.length
                            }

                            aadhaarGroupId = resolveGroupId(
                                bitmap       = originalBitmap,
                                detected     = detected,
                                ocrFields    = ocrFields,
                                inBatchDocs  = savedAadhaarDocs
                            )

                            Log.d("AadhaarDebug",
                                "✓ isAadhaar=true → side=$aadhaarSide | groupId=$aadhaarGroupId | digitLen=$aadhaarDigitLen")
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

                        val ocrFields = try {
                            ocrHelper.extractFields(originalBitmap, classifiedType)
                        } catch (e: Exception) {
                            Log.e("AadhaarDebug", "OCR failed", e)
                            null
                        }

                        aadhaarName = ocrFields?.name?.replace("_", " ")
                        aadhaarDob = ocrFields?.dob
                        aadhaarGender = ocrFields?.gender
                        aadhaarMaskedNumber = maskAadhaarNumber(ocrFields?.idNumber)
                        aadhaarAddress = ocrFields?.address

                        // buildSmartName uses only last-4 of idNumber — acceptable per UIDAI masking standard
                        smartName = try {
                            buildSmartName(classifiedType, ocrFields?.name, ocrFields?.idNumber)
                        } catch (e: Exception) {
                            Log.e("AadhaarDebug", "OCR naming failed", e)
                            null
                        }

                        if (classifiedType.isAadhaar) {
                            aadhaarSide = aadhaarSideOf(classifiedType)

                            val rawDigits = ocrFields?.idNumber?.filter { it.isDigit() }
                            if (rawDigits != null && rawDigits.length == 12) {
                                aadhaarNumHash   = secureHelper.hashAadhaarNumber(rawDigits)
                                aadhaarLast4Hash = secureHelper.hashLast4(rawDigits)
                                aadhaarDigitLen  = 12
                            } else if (rawDigits != null && rawDigits.length >= 4) {
                                aadhaarLast4Hash = secureHelper.hashLast4(rawDigits)
                                aadhaarDigitLen  = rawDigits.length
                            }

                            aadhaarGroupId = resolveGroupId(
                                bitmap      = originalBitmap,
                                detected    = classifiedType,
                                ocrFields   = ocrFields,
                                inBatchDocs = savedAadhaarDocs
                            )
                            Log.d("AadhaarDebug",
                                "✓ isAadhaar=true → side=$aadhaarSide | groupId=$aadhaarGroupId | digitLen=$aadhaarDigitLen")
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

                    Log.d("AadhaarDebug", "Saving doc → id=$docId | name=$name")
                    Log.d("AadhaarDebug", "  docClassLabel=$docClassLabel | aadhaarSide=$aadhaarSide | aadhaarGroupId=$aadhaarGroupId")

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
                            aadhaarGroupId = aadhaarGroupId,
                            aadhaarName = aadhaarName,
                            aadhaarDob = aadhaarDob,
                            aadhaarGender = aadhaarGender,
                            aadhaarMaskedNumber = aadhaarMaskedNumber,
                            aadhaarAddress = aadhaarAddress
                        )
                    )

                    if (aadhaarGroupId != null && aadhaarSide != null) {
                        savedAadhaarDocs.add(
                            SavedAadhaarDocInfo(
                                documentId   = docId,
                                groupId      = aadhaarGroupId,
                                side         = aadhaarSide,
                                numHash      = aadhaarNumHash,    // hash of full 12, or null
                                last4Hash    = aadhaarLast4Hash,  // hash of last 4, or null
                                digitLen     = aadhaarDigitLen
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

                // ── Hash-based reconciliation ─────────────────────────────────
                // Corrects group IDs when OCR was more reliable on one side than the other.
                // Uses SHA-256 hashes — no raw digits involved.
                if (savedAadhaarDocs.size > 1) {
                    // Build: numHash → most-confident groupId (for full-12 docs only)
                    val hashToGroupId = mutableMapOf<String, String>()
                    savedAadhaarDocs.forEach { info ->
                        val nh = info.numHash ?: return@forEach   // skip partial-OCR docs
                        val existing           = hashToGroupId[nh]
                        val isConfident        = isConfidentGroupId(info.groupId)
                        val existingConfident  = existing != null && isConfidentGroupId(existing)
                        if (existing == null || (isConfident && !existingConfident)) {
                            hashToGroupId[nh] = info.groupId
                        }
                    }

                    Log.d("AadhaarDebug", "reconciliation: ${hashToGroupId.size} distinct num-hash(es)")

                    savedAadhaarDocs.forEach { info ->
                        val nh = info.numHash ?: return@forEach
                        val correctGroupId = hashToGroupId[nh] ?: return@forEach
                        if (correctGroupId != info.groupId) {
                            Log.d("AadhaarDebug",
                                "Number-reconcile ${info.documentId}: ${info.groupId} → $correctGroupId")
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

        val fields    = ocrFields ?: try { ocrHelper.extractFields(bitmap, detected) }
        catch (e: Exception) { Log.e("AadhaarDebug", "OCR groupId failed", e); null }

        // Raw digits — in-memory only, used to build hashes for lookups, never logged or stored
        val rawDigits  = fields?.idNumber?.filter { it.isDigit() }
        val numHash12  = rawDigits?.takeIf { it.length == 12 }
            ?.let { secureHelper.hashAadhaarNumber(it) }
        val last4Hash  = rawDigits?.takeIf { it.length >= 4 }
            ?.let { secureHelper.hashLast4(it) }

        val sessionId = _state.value.sessionId
        val thisSide  = aadhaarSideOf(detected)

        Log.d("AadhaarDebug",
            "resolveGroupId ENTER → side=$thisSide | hasName=${fields?.name != null} | digitLen=${rawDigits?.length} | inBatch=${inBatchDocs.size}")

        suspend fun fetchAadhaarDocs(): List<Document> =
            try {
                if (sessionId != null) documentRepository.getExistingAadhaarDocs(sessionId)
                else documentRepository.getGlobalAadhaarDocs()
            } catch (e: Exception) {
                Log.e("AadhaarDebug", "fetchAadhaarDocs failed", e); emptyList()
            }

        // ── Strategy 1: confident key (name + full number, both hashed) ───────
        val confidentKey = if (fields?.name != null && fields.idNumber != null)
            ocrHelper.buildAadhaarGroupId(fields.name, fields.idNumber)   // returns hashed ID
        else null

        Log.d("AadhaarDebug", "S1 confidentKey=${confidentKey}")

        if (confidentKey != null) {
            val existingDocs = fetchAadhaarDocs()
            Log.d("AadhaarDebug", "S1 existingDocs count=${existingDocs.size}")

            // Cross-match: another group already holds the same number hash but different name format
            if (numHash12 != null) { //work when take picture separately
                val crossMatch = existingDocs
                    .mapNotNull { it.aadhaarGroupId }.distinct()
                    .firstOrNull { gId -> gId != confidentKey && gId.contains(numHash12) }
                Log.d("AadhaarDebug", "S1 crossMatch=${crossMatch}")
                if (crossMatch != null) {
                    val docsInCross  = existingDocs.filter { it.aadhaarGroupId == crossMatch }
                    val pairComplete = docsInCross.any { it.aadhaarSide == "FRONT" } &&
                            docsInCross.any { it.aadhaarSide == "BACK" }
                    Log.d("AadhaarDebug", "S1 crossMatch pairComplete=$pairComplete $docsInCross")
                    if (!pairComplete) {
                        Log.d("AadhaarDebug", "S1 → RETURN crossMatch")
                        anchoredGroupId = crossMatch; lastAadhaarGroupId = crossMatch
                        return crossMatch
                    }
                }
            }

            val docsInGroup     = existingDocs.filter { it.aadhaarGroupId == confidentKey }
            val hasCompletePair = docsInGroup.any { it.aadhaarSide == "FRONT" } &&
                    docsInGroup.any { it.aadhaarSide == "BACK" }
            Log.d("AadhaarDebug", "S1 docsInGroup=${docsInGroup.size} hasCompletePair=$hasCompletePair")

            if (hasCompletePair) {
                val newKey = "${confidentKey}_${System.currentTimeMillis()}"
                Log.d("AadhaarDebug", "S1 → RETURN newKey (pair full)")
                anchoredGroupId = newKey; lastAadhaarGroupId = newKey
                return newKey
            }

            Log.d("AadhaarDebug", "S1 → RETURN confidentKey")
            anchoredGroupId = confidentKey; lastAadhaarGroupId = confidentKey
            return confidentKey
        }

        Log.d("AadhaarDebug", "S1 SKIPPED (no name or no idNumber)")

        // ── Strategy 2: full number hash matches an existing group ─────────────
        if (numHash12 != null) {
            val existingDocs   = fetchAadhaarDocs()
            val matchedGroupId = existingDocs.mapNotNull { it.aadhaarGroupId }.distinct()
                .firstOrNull { groupId -> groupId.contains(numHash12) }
            Log.d("AadhaarDebug", "S2 full-hash match=${matchedGroupId != null}")
            if (matchedGroupId != null) {
                lastAadhaarGroupId = matchedGroupId
                Log.d("AadhaarDebug", "S2 → RETURN matched group")
                return matchedGroupId
            }
        } else {
            Log.d("AadhaarDebug", "S2 SKIPPED (no full 12-digit hash)")
        }

        // ── Strategy 3: carry-forward last group if unambiguous ────────────────
        Log.d("AadhaarDebug", "S3 hasLastGroup=${lastAadhaarGroupId != null} thisSide=$thisSide")
        if (lastAadhaarGroupId != null && thisSide != null) {
            val batchGroupsMissingThisSide = inBatchDocs
                .groupBy { it.groupId }
                .filter { (_, docs) -> docs.none { it.side == thisSide } }
                .keys
            val unambiguous = when {
                batchGroupsMissingThisSide.isEmpty() -> true
                batchGroupsMissingThisSide.size == 1 &&
                        batchGroupsMissingThisSide.contains(lastAadhaarGroupId) -> true
                else -> false
            }
            Log.d("AadhaarDebug",
                "S3 batchGroupsMissingThisSide.size=${batchGroupsMissingThisSide.size} unambiguous=$unambiguous")
            if (unambiguous) {
                Log.d("AadhaarDebug", "S3 → RETURN carry-forward")
                return lastAadhaarGroupId!!
            }
        } else {
            Log.d("AadhaarDebug", "S3 SKIPPED (no lastGroupId or no thisSide)")
        }

        // ── Strategy 4: last-4 hash matches exactly one existing group ─────────
        if (last4Hash != null) {
            val existingDocs = fetchAadhaarDocs()
            val allGroupIds  = existingDocs.mapNotNull { it.aadhaarGroupId }.distinct()
            val matches      = allGroupIds.filter { it.contains(last4Hash) }
            Log.d("AadhaarDebug", "S4 last4-hash matches=${matches.size}")
            if (matches.size == 1) {
                lastAadhaarGroupId = matches.first()
                Log.d("AadhaarDebug", "S4 → RETURN last4 match")
                return matches.first()
            } else {
                Log.d("AadhaarDebug", "S4 SKIPPED matches.size=${matches.size}")
            }
        } else {
            Log.d("AadhaarDebug", "S4 SKIPPED (no last4 hash)")
        }

        // ── Strategy 4.5: fill an incomplete pair from batch or DB ────────────
        if (thisSide != null) {
            val batchIncomplete = inBatchDocs.groupBy { it.groupId }
                .filter { (_, docs) -> docs.none { it.side == thisSide } }.keys.toList()
            Log.d("AadhaarDebug", "S4.5 batchIncomplete=${batchIncomplete.size}")

            if (batchIncomplete.size == 1) {
                lastAadhaarGroupId = batchIncomplete.first()
                Log.d("AadhaarDebug", "S4.5 → RETURN batch fill")
                return batchIncomplete.first()
            }

            val existingDocs        = fetchAadhaarDocs()
            val groupsByMissingSide = existingDocs
                .filter { it.aadhaarGroupId != null }
                .groupBy { it.aadhaarGroupId!! }
                .mapValues { (_, docs) ->
                    val hasFront = docs.any { it.aadhaarSide == "FRONT" }
                    val hasBack  = docs.any { it.aadhaarSide == "BACK" }
                    when { hasFront && !hasBack -> "BACK"; hasBack && !hasFront -> "FRONT"; else -> null }
                }.filter { (_, missingSide) -> missingSide != null }

            val matchingGroups = groupsByMissingSide
                .filter { (_, missingSide) -> missingSide == thisSide }.keys.toList()
            Log.d("AadhaarDebug", "S4.5 matchingGroups=${matchingGroups.size}")

            when {
                matchingGroups.size == 1 -> {
                    lastAadhaarGroupId = matchingGroups.first()
                    Log.d("AadhaarDebug", "S4.5 → RETURN DB fill")
                    return matchingGroups.first()
                }
                matchingGroups.size > 1 -> {
                    val mostRecent = existingDocs
                        .filter { it.aadhaarGroupId in matchingGroups }
                        .maxByOrNull { it.createdAt }?.aadhaarGroupId
                    Log.d("AadhaarDebug", "S4.5 multiple incomplete, mostRecent=${mostRecent != null}")
                    if (mostRecent != null) { lastAadhaarGroupId = mostRecent; return mostRecent }
                }
            }
            Log.d("AadhaarDebug", "S4.5 FAILED — fell through")
        }

        // ── Strategy 5 / 6: partial key or timestamp fallback ────────────────
        val partialKey = if (fields != null)
            ocrHelper.buildAadhaarGroupId(fields.name, fields.idNumber) else null
        val result = partialKey ?: "aadhaar_grp_${System.currentTimeMillis()}"
        Log.d("AadhaarDebug", "S5/6 LAST RESORT → ${result.take(20)}")
        lastAadhaarGroupId = result
        return result
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * A "confident" group ID is one derived from both name + number (Strategy 1).
     * Fallback IDs from Strategy 5/6 start with "aadhaar_grp_" or have the
     * "ag_n_" prefix (number-only, no name anchor).
     */
    private fun isConfidentGroupId(groupId: String): Boolean =
        !groupId.startsWith("ag_n_") && !groupId.startsWith("aadhaar_grp_")

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

    /**
     * File names show only the last 4 digits of the ID number — consistent with
     * the UIDAI masked-Aadhaar format (XXXX XXXX 1234). The full number is
     * never embedded in file names or logs.
     */
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
        idNumber
            ?.filter { it.isDigit() }
            ?.takeLast(4)                // ← last 4 only, never full number
            ?.let { if (it.length == 4) parts.add(it) }
        return if (parts.isEmpty()) null else parts.joinToString("_")
    }

    private fun maskAadhaarNumber(idNumber: String?): String? {
        val last4 = idNumber?.filter { it.isDigit() }?.takeLast(4)
        return if (last4 != null && last4.length == 4) "xxxx xxxx $last4" else null
    }

    fun dismissMismatches() {
        _state.value = _state.value.copy(pendingMismatches = emptyList())
    }

    fun onSaveNavigated() {
        _state.value = _state.value.copy(saveSuccess = false)
    }

    fun onReset(keepTarget: Boolean = false) {
        synchronized(cropJobLock) { pendingCropJobs.clear() }
        _state.value = if (keepTarget) {
            ScannerState(
                sessionId        = _state.value.sessionId,
                targetFolderId   = _state.value.targetFolderId,
                targetFolderName = _state.value.targetFolderName,
                targetExportType = _state.value.targetExportType,
                targetDocType    = _state.value.targetDocType
            )
        } else {
            ScannerState(sessionId = _state.value.sessionId)
        }
        lastAadhaarGroupId = null
        anchoredGroupId    = null
    }

    private fun updatePage(pageId: String, transform: (ScannedPage) -> ScannedPage) {
        _state.value = _state.value.copy(
            pages = _state.value.pages.map {
                if (it.id == pageId) transform(it) else it
            }
        )
    }

    // ── Private data classes ──────────────────────────────────────────────────

    private data class SavedDocInfo(
        val documentId    : String,
        val documentName  : String,
        val detectedLabel : String?,
        val folderLabel   : String?
    )

    /**
     * Holds per-page Aadhaar metadata for the in-flight batch.
     * Raw digits are NEVER stored here — only their SHA-256 hashes.
     */
    private data class SavedAadhaarDocInfo(
        val documentId : String,
        val groupId    : String,
        val side       : String,
        val numHash    : String?,   // SHA-256(salt + full 12 digits), null if OCR incomplete
        val last4Hash  : String?,   // SHA-256(salt + last 4 digits), null if OCR gave < 4
        val digitLen   : Int        // actual digit count OCR extracted (0, 4–12)
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
