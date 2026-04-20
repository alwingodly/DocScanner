package com.example.docscanner.presentation.shared

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.docscanner.data.classifier.DocumentClassifier
import com.example.docscanner.data.export.DocumentExporter
import com.example.docscanner.data.masking.AadhaarMasker
import com.example.docscanner.data.masking.PanMasker
import com.example.docscanner.data.ocr.ExtractionUtils
import com.example.docscanner.data.ocr.fusion.ExtractionFusion
import com.example.docscanner.data.processor.DocumentProcessor
import com.example.docscanner.data.security.AadhaarSecureHelper
import com.example.docscanner.domain.model.DocClassType
import com.example.docscanner.domain.model.Document
import com.example.docscanner.domain.model.DocumentDetail
import com.example.docscanner.domain.model.DocumentCorners
import com.example.docscanner.domain.model.ExtractedFields
import com.example.docscanner.domain.model.ExportFormat
import com.example.docscanner.domain.model.FolderExportType
import com.example.docscanner.domain.model.ScannedPage
import com.example.docscanner.domain.model.toDocumentDetailsJson
import com.example.docscanner.domain.repository.DocumentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

data class ScannedMismatch(
    val documentId    : String,
    val documentName  : String,
    val detectedLabel : String,
    val folderLabel   : String
)

data class ScanSaveFeedback(
    val id: Long = System.currentTimeMillis(),
    val savedCount: Int,
    val destinationLabel: String,
    val mismatchCount: Int = 0
)

@HiltViewModel
class ScannerViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val documentProcessor  : DocumentProcessor,
    val          exporter          : DocumentExporter,
    private val documentRepository : DocumentRepository,
    private val documentClassifier : DocumentClassifier,
    private val extractionFusion   : ExtractionFusion,
    private val secureHelper       : AadhaarSecureHelper,   // ← NEW
    private val aadhaarMasker      : AadhaarMasker,
    private val panMasker          : PanMasker
) : ViewModel() {

    private val _state = MutableStateFlow(ScannerState())
    val state: StateFlow<ScannerState> = _state.asStateFlow()

    private var lastAadhaarGroupId  : String? = null
    private var anchoredGroupId     : String? = null
    private var lastTargetFolderId  : String  = ""
    private var lastPassportGroupId : String? = null

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
        lastAadhaarGroupId  = null
        anchoredGroupId     = null
        lastTargetFolderId  = ""
        lastPassportGroupId = null
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

    fun onImportedPages(uris: List<Uri>) {
        if (uris.isEmpty()) return

        val currentState = _state.value
        _state.value = currentState.copy(
            isSaving = true,
            saveSuccess = false,
            saveFeedback = null
        )

        viewModelScope.launch {
            try {
                var importedCount = 0
                uris.forEach { uri ->
                    uriToBitmap(uri)?.let { bitmap ->
                        importedCount++
                        onPhotoCaptured(bitmap, null)
                    }
                }

                if (importedCount == 0) {
                    _state.value = _state.value.copy(isSaving = false)
                    return@launch
                }

                onAutoSavePages()
            } catch (e: Exception) {
                Log.e("ScannerVM", "Failed to import pages", e)
                _state.value = _state.value.copy(isSaving = false)
            }
        }
    }

    private val isFolderScan: Boolean
        get() = _state.value.targetDocType != null
    fun onAutoSavePages() {
        val currentState = _state.value
        if (currentState.pages.isEmpty()) return
        _state.value = currentState.copy(
            isSaving = true,
            saveSuccess = false,
            saveFeedback = null
        )

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

                var lastAadhaarSide      : String? = null
                var lastAadhaarGroupId2 : String? = null
                val savedPassportDocs   = mutableListOf<SavedPassportDocInfo>()

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
                    var extractedDetails = emptyList<DocumentDetail>()

                    // Hashes — never store raw digits beyond this function scope
                    var aadhaarNumHash  : String? = null   // hash of full 12 digits
                    var aadhaarLast4Hash: String? = null   // hash of last 4 digits
                    var aadhaarDigitLen : Int     = 0      // 12, 4, or 0 — for reconciliation logic

                    // Passport pairing (hashed passport number, side, group)
                    var passportSide      : String? = null
                    var passportGroupId   : String? = null
                    var passportHolderName: String? = null
                    var passportNumHashVal: String? = null   // cached hash for batch reconciliation

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

                        val rawFolderOcrFields = try {
                            extractNormalizedFields(originalBitmap, detected)
                        } catch (e: Exception) {
                            Log.e("AadhaarDebug", "Folder OCR failed", e)
                            null
                        }

                        // Passport OCR-override for folder scans (same logic as global scan)
                        val (effectiveDetected, ocrFields) =
                            if (detected == DocClassType.OTHER &&
                                rawFolderOcrFields?.rawText?.let {
                                    ExtractionUtils.PASSPORT_NO.containsMatchIn(it)
                                } == true
                            ) {
                                Log.d("AadhaarDebug", "Folder: Passport OCR-override triggered")
                                val pf = try {
                                    extractNormalizedFields(originalBitmap, DocClassType.PASSPORT)
                                } catch (e: Exception) { rawFolderOcrFields }
                                DocClassType.PASSPORT to pf
                            } else {
                                detected to rawFolderOcrFields
                            }

                        extractedDetails = ocrFields?.details.orEmpty().cleanDetails()

                        if (effectiveDetected.isAadhaar) {
                            aadhaarSide = aadhaarSideOf(effectiveDetected)

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
                                detected     = effectiveDetected,
                                ocrFields    = ocrFields,
                                inBatchDocs  = savedAadhaarDocs
                            )

                            Log.d("AadhaarDebug",
                                "✓ isAadhaar=true → side=$aadhaarSide | groupId=$aadhaarGroupId | digitLen=$aadhaarDigitLen")
                        } else {
                            Log.d("AadhaarDebug", "✗ isAadhaar=false → aadhaarSide=null, aadhaarGroupId=null")
                        }

                        // ── Passport pairing ──────────────────────────────
                        if (effectiveDetected == DocClassType.PASSPORT) {
                            passportSide       = if (ocrFields?.hasMrz == true) "FRONT" else "BACK"
                            passportHolderName = ocrFields?.name
                            passportNumHashVal = ocrFields?.idNumber?.let { hashPassportNum(it) }
                            passportGroupId    = resolvePassportGroupId(
                                passportNumHash = passportNumHashVal,
                                passportSide    = passportSide!!,
                                inBatchDocs     = savedPassportDocs
                            )
                        }

                        saveBitmap = maskIfNeeded(effectiveDetected, originalBitmap)
                        smartName  = null

                    } else {
                        val rawClassifiedType = try {
                            documentClassifier.classify(originalBitmap)
                        } catch (e: Exception) {
                            Log.e("AadhaarDebug", "Classification failed", e)
                            page.docClassType ?: DocClassType.OTHER
                        }

                        Log.d("AadhaarDebug", "Global scan → classifiedType: ${rawClassifiedType.name}")

                        val rawOcrFields = try {
                            extractNormalizedFields(originalBitmap, rawClassifiedType)
                        } catch (e: Exception) {
                            Log.e("AadhaarDebug", "OCR failed", e)
                            null
                        }

                        // ── Passport OCR-override ─────────────────────────
                        // If the ML classifier said OTHER but the raw OCR text contains
                        // a valid passport number ([A-Z]\d{7}), treat this page as a
                        // passport and re-run extraction with the PassportExtractor.
                        // This makes pairing work even when the model misclassifies.
                        val (classifiedType, ocrFields) =
                            if (rawClassifiedType == DocClassType.OTHER &&
                                rawOcrFields?.rawText?.let {
                                    ExtractionUtils.PASSPORT_NO.containsMatchIn(it)
                                } == true
                            ) {
                                Log.d("AadhaarDebug", "Passport OCR-override triggered")
                                val passportFields = try {
                                    extractNormalizedFields(originalBitmap, DocClassType.PASSPORT)
                                } catch (e: Exception) { rawOcrFields }
                                DocClassType.PASSPORT to passportFields
                            } else {
                                rawClassifiedType to rawOcrFields
                            }

                        docClassLabel = classifiedType.displayName

                        extractedDetails = ocrFields?.details.orEmpty().cleanDetails()
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

                        // ── Passport pairing ──────────────────────────────
                        if (classifiedType == DocClassType.PASSPORT) {
                            passportSide       = if (ocrFields?.hasMrz == true) "FRONT" else "BACK"
                            passportHolderName = ocrFields?.name
                            passportNumHashVal = ocrFields?.idNumber?.let { hashPassportNum(it) }
                            passportGroupId    = resolvePassportGroupId(
                                passportNumHash = passportNumHashVal,
                                passportSide    = passportSide!!,
                                inBatchDocs     = savedPassportDocs
                            )
                        } else {
                            lastPassportGroupId = null
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
                            id                   = docId,
                            folderId             = freshState.targetFolderId,
                            name                 = name,
                            pageCount            = 1,
                            thumbnailPath        = imageUri?.toString(),
                            pdfPath              = null,
                            docClassLabel        = docClassLabel,
                            createdAt            = System.currentTimeMillis(),
                            sessionId            = freshState.sessionId,
                            aadhaarSide          = aadhaarSide,
                            aadhaarGroupId       = aadhaarGroupId,
                            aadhaarName          = aadhaarName,
                            aadhaarDob           = aadhaarDob,
                            aadhaarGender        = aadhaarGender,
                            aadhaarMaskedNumber  = aadhaarMaskedNumber,
                            aadhaarAddress       = aadhaarAddress,
                            extractedDetailsJson = extractedDetails.toDocumentDetailsJson(),
                            ocrRawText           = null,
                            passportGroupId      = passportGroupId,
                            passportSide         = passportSide,
                            passportHolderName   = passportHolderName,
                            passportNumHash      = passportNumHashVal,
                        )
                    )

                    if (passportGroupId != null && passportSide != null) {
                        savedPassportDocs.add(
                            SavedPassportDocInfo(
                                documentId = docId,
                                groupId    = passportGroupId!!,
                                side       = passportSide!!,
                                numHash    = passportNumHashVal
                            )
                        )
                    }

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

                // ── Passport group reconciliation ─────────────────────────
                // If two passports with the same number hash were scanned in
                // the same batch but got different groupIds, unify them.
                if (savedPassportDocs.size > 1) {
                    val hashToGroup = mutableMapOf<String, String>()
                    savedPassportDocs.forEach { info ->
                        val h = info.numHash ?: return@forEach
                        if (!hashToGroup.containsKey(h)) hashToGroup[h] = info.groupId
                    }
                    savedPassportDocs.forEach { info ->
                        val h = info.numHash ?: return@forEach
                        val correct = hashToGroup[h] ?: return@forEach
                        if (correct != info.groupId) {
                            documentRepository.updatePassportGroupIdOnly(info.documentId, correct)
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

                val destinationLabel = when {
                    freshState.targetFolderName.isNotBlank() -> freshState.targetFolderName
                    freshState.targetDocType != null -> freshState.targetDocType
                    else -> "All Documents"
                }

                _state.value = _state.value.copy(
                    isSaving          = false,
                    saveSuccess       = true,
                    pendingMismatches = mismatches,
                    saveFeedback = ScanSaveFeedback(
                        savedCount = sortedPages.size,
                        destinationLabel = destinationLabel,
                        mismatchCount = mismatches.size
                    )
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
        ocrFields   : NormalizedOcrFields? = null,
        inBatchDocs : List<SavedAadhaarDocInfo> = emptyList()
    ): String {

        val fields    = ocrFields ?: try { extractNormalizedFields(bitmap, detected) }
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

        fun canAttachToGroup(groupDocs: List<Document>, side: String?): Boolean {
            if (groupDocs.isEmpty()) return true
            if (side == null) return false

            val hasFront = groupDocs.any { it.aadhaarSide == "FRONT" }
            val hasBack  = groupDocs.any { it.aadhaarSide == "BACK" }
            if (hasFront && hasBack) return false

            return groupDocs.none { it.aadhaarSide == side }
        }

        fun freshGroupId(base: String? = null): String =
            base?.let { "${it}_${System.currentTimeMillis()}" }
                ?: "aadhaar_grp_${System.currentTimeMillis()}"

        // ── Strategy 1: confident key (name + full number, both hashed) ───────
        val confidentKey = if (fields?.name != null && numHash12 != null)
            extractionFusion.buildAadhaarGroupId(fields.name, rawDigits)
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
                    val canAttach = canAttachToGroup(docsInCross, thisSide)
                    Log.d("AadhaarDebug", "S1 crossMatch canAttach=$canAttach size=${docsInCross.size}")
                    if (canAttach) {
                        Log.d("AadhaarDebug", "S1 → RETURN crossMatch")
                        anchoredGroupId = crossMatch; lastAadhaarGroupId = crossMatch
                        return crossMatch
                    }
                }
            }

            val docsInGroup     = existingDocs.filter { it.aadhaarGroupId == confidentKey }
            val canAttach = canAttachToGroup(docsInGroup, thisSide)
            Log.d("AadhaarDebug", "S1 docsInGroup=${docsInGroup.size} canAttach=$canAttach")

            if (docsInGroup.isNotEmpty() && !canAttach) {
                val newKey = freshGroupId(confidentKey)
                Log.d("AadhaarDebug", "S1 → RETURN newKey (group full or same side exists)")
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
            val matchedGroupIds = existingDocs.mapNotNull { it.aadhaarGroupId }
                .distinct()
                .filter { groupId -> groupId.contains(numHash12) }
            val reusableMatch = matchedGroupIds.firstOrNull { groupId ->
                canAttachToGroup(
                    existingDocs.filter { it.aadhaarGroupId == groupId },
                    thisSide
                )
            }
            Log.d("AadhaarDebug", "S2 full-hash matches=${matchedGroupIds.size} reusable=${reusableMatch != null}")
            if (reusableMatch != null) {
                lastAadhaarGroupId = reusableMatch
                Log.d("AadhaarDebug", "S2 → RETURN matched group")
                return reusableMatch
            }
            if (matchedGroupIds.isNotEmpty()) {
                val newKey = freshGroupId(matchedGroupIds.first())
                anchoredGroupId = newKey
                lastAadhaarGroupId = newKey
                Log.d("AadhaarDebug", "S2 → RETURN newKey (matched group already has this side)")
                return newKey
            }
        } else {
            Log.d("AadhaarDebug", "S2 SKIPPED (no full 12-digit hash)")
        }

        // ── Strategy 3+: exact-number-only fallback ───────────────────────────
        if (numHash12 == null) {
            Log.d("AadhaarDebug", "S3+ exact-number fallback disabled (no full 12-digit match)")
        }

        val result = numHash12?.let { "ag_n_$it" }
            ?: "aadhaar_grp_${System.currentTimeMillis()}"
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

    // ── Passport pairing helpers ──────────────────────────────────────────────

    /**
     * Hashes a passport number (e.g. "A1234567") using SHA-256.
     * The raw number is never stored — only the 20-char hex prefix is kept.
     */
    private fun hashPassportNum(rawNum: String): String {
        val normalized = rawNum.filter { it.isLetterOrDigit() }.uppercase()
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(20)
    }

    /**
     * Resolves a passport group ID for the current scan.
     *
     * Strategy 1: passport number hash matches a doc already in the session → reuse.
     * Strategy 2: carry-forward the last passport group if it is missing this side.
     * Strategy 3: new group (timestamp fallback).
     */
    /**
     * Four-strategy passport group resolution — works across sessions.
     *
     * S1a – same-batch hash match: front+back scanned together, OCR reads number on both.
     * S1b – cross-session hash match: hash stored in DB → pairs even days apart.
     * S2  – carry-forward: consecutive scan in same session (e.g. flip passport).
     * S2b – unmatched-FRONT finder: back scanned a different day/session when OCR
     *        can't read the barcode. If there is exactly ONE FRONT page without a
     *        paired BACK page → it must be this passport.
     * S3  – new group (fallback).
     */
    private suspend fun resolvePassportGroupId(
        passportNumHash: String?,
        passportSide: String,
        inBatchDocs: List<SavedPassportDocInfo>,
    ): String {

        // ── S1a: same-batch hash match ────────────────────────────────────────
        if (passportNumHash != null) {
            val batchMatch = inBatchDocs
                .firstOrNull { it.numHash == passportNumHash && it.side != passportSide }
                ?.groupId
            if (batchMatch != null) {
                lastPassportGroupId = batchMatch
                return batchMatch
            }
        }

        // ── S1b: cross-session hash match (hash persisted in DB) ─────────────
        if (passportNumHash != null) {
            val dbDocs = try {
                documentRepository.getPassportDocsByHash(passportNumHash)
            } catch (e: Exception) { emptyList() }

            val crossMatch = dbDocs
                .firstOrNull { it.passportSide != passportSide && it.passportGroupId != null }
                ?.passportGroupId
            if (crossMatch != null) {
                lastPassportGroupId = crossMatch
                return crossMatch
            }
        }

        // ── S2: carry-forward within same scan session ────────────────────────
        lastPassportGroupId?.let { lastGid ->
            val groupsMissingThisSide = inBatchDocs
                .groupBy { it.groupId }
                .filter { (_, docs) -> docs.none { it.side == passportSide } }
                .keys
            if (groupsMissingThisSide.contains(lastGid)) {
                return lastGid
            }
        }

        // ── S2b: unmatched opposite-side finder (cross-session) ─────────────
        // If there is exactly ONE page of the opposite side that has no match yet,
        // this page must belong to it.  Works for both FRONT-then-BACK and BACK-then-FRONT.
        // Searches globally across all sessions so "front today / back tomorrow" is handled.
        val oppositeSide = if (passportSide == "FRONT") "BACK" else "FRONT"
        val unmatchedOpposite = try {
            documentRepository.getUnmatchedPassportsBySide(oppositeSide)
        } catch (e: Exception) { emptyList() }

        if (unmatchedOpposite.size == 1) {
            val matchedGroupId = unmatchedOpposite.first().passportGroupId!!
            lastPassportGroupId = matchedGroupId
            return matchedGroupId
        }

        // ── S3: new group ─────────────────────────────────────────────────────
        val newGroup = "pp_grp_${System.currentTimeMillis()}"
        lastPassportGroupId = newGroup
        return newGroup
    }

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

    fun clearSaveFeedback() {
        _state.value = _state.value.copy(saveFeedback = null)
    }

    fun onReset(keepTarget: Boolean = false, keepFeedback: Boolean = false) {
        synchronized(cropJobLock) { pendingCropJobs.clear() }
        val savedFeedback = if (keepFeedback) _state.value.saveFeedback else null
        _state.value = if (keepTarget) {
            ScannerState(
                sessionId        = _state.value.sessionId,
                targetFolderId   = _state.value.targetFolderId,
                targetFolderName = _state.value.targetFolderName,
                targetExportType = _state.value.targetExportType,
                targetDocType    = _state.value.targetDocType,
                saveFeedback     = savedFeedback
            )
        } else {
            ScannerState(
                sessionId = _state.value.sessionId,
                saveFeedback = savedFeedback
            )
        }
        lastAadhaarGroupId  = null
        anchoredGroupId     = null
        lastPassportGroupId = null
    }

    private suspend fun uriToBitmap(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            appContext.contentResolver.openInputStream(uri)?.use { stream ->
                val bitmap = BitmapFactory.decodeStream(stream) ?: return@withContext null
                val exif = androidx.exifinterface.media.ExifInterface(
                    appContext.contentResolver.openInputStream(uri) ?: return@withContext bitmap
                )
                val rotation = when (
                    exif.getAttributeInt(
                        androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                        androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
                    )
                ) {
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90  -> 90f
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }

                if (rotation != 0f) {
                    Bitmap.createBitmap(
                        bitmap,
                        0,
                        0,
                        bitmap.width,
                        bitmap.height,
                        Matrix().apply { postRotate(rotation) },
                        true
                    )
                } else {
                    bitmap
                }
            }
        } catch (e: Exception) {
            Log.e("ScannerVM", "Failed to decode imported page: $uri", e)
            null
        }
    }

    private fun updatePage(pageId: String, transform: (ScannedPage) -> ScannedPage) {
        _state.value = _state.value.copy(
            pages = _state.value.pages.map {
                if (it.id == pageId) transform(it) else it
            }
        )
    }

    private fun List<DocumentDetail>.cleanDetails(): List<DocumentDetail> {
        val usedLabels = mutableSetOf<String>()
        return mapNotNull { detail ->
            val label = detail.label.trim()
            val value = detail.value.trim()
            if (label.isEmpty() || value.isEmpty()) return@mapNotNull null
            if (!usedLabels.add(label.lowercase())) return@mapNotNull null
            detail.copy(label = label, value = value)
        }
    }

    private suspend fun extractNormalizedFields(
        bitmap: Bitmap,
        docType: DocClassType
    ): NormalizedOcrFields {
        val extracted = extractionFusion.extract(bitmap, docType)
        return extracted.toNormalizedFields()
    }

    private fun ExtractedFields.toNormalizedFields(): NormalizedOcrFields = when (this) {
        is ExtractedFields.AadhaarFront -> NormalizedOcrFields(
            name = name,
            idNumber = idNumber,
            dob = dob,
            gender = gender,
            address = null,
            rawText = rawText,
            details = details
        )
        is ExtractedFields.AadhaarBack -> NormalizedOcrFields(
            name = null,
            idNumber = idNumber,
            dob = null,
            gender = null,
            address = address,
            rawText = rawText,
            details = details
        )
        is ExtractedFields.Pan -> NormalizedOcrFields(
            name = name,
            idNumber = idNumber,
            dob = dob,
            gender = null,
            address = null,
            rawText = rawText,
            details = details
        )
        is ExtractedFields.Passport -> NormalizedOcrFields(
            name = name,
            idNumber = idNumber,
            dob = dob,
            gender = gender,
            address = null,
            rawText = rawText,
            details = details,
            // "<<" is the MRZ filler — present only on the data (front) page.
            // Fall back to it when full structural MRZ parsing fails due to OCR noise.
            hasMrz = mrzLines.isNotEmpty() || rawText.contains("<<")
        )
        is ExtractedFields.VoterId -> NormalizedOcrFields(
            name = name,
            idNumber = idNumber,
            dob = dob ?: age,
            gender = gender,
            address = address,
            rawText = rawText,
            details = details
        )
        is ExtractedFields.DrivingLicence -> NormalizedOcrFields(
            name = name,
            idNumber = idNumber,
            dob = dob,
            gender = gender,
            address = address,
            rawText = rawText,
            details = details
        )
        is ExtractedFields.Unknown -> NormalizedOcrFields(
            name = null,
            idNumber = null,
            dob = null,
            gender = null,
            address = null,
            rawText = rawText,
            details = details
        )
    }

    // ── Private data classes ──────────────────────────────────────────────────

    private data class SavedDocInfo(
        val documentId    : String,
        val documentName  : String,
        val detectedLabel : String?,
        val folderLabel   : String?
    )

    private data class NormalizedOcrFields(
        val name: String?,
        val idNumber: String?,
        val dob: String?,
        val gender: String?,
        val address: String?,
        val rawText: String,
        val details: List<DocumentDetail>,
        val hasMrz: Boolean = false,   // true = passport data page (FRONT)
    )

    private data class SavedPassportDocInfo(
        val documentId : String,
        val groupId    : String,
        val side       : String,   // "FRONT" | "BACK"
        val numHash    : String?,  // SHA-256 hash of passport number (null if OCR missed it)
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
    val pendingMismatches : List<ScannedMismatch> = emptyList(),
    val saveFeedback      : ScanSaveFeedback?     = null
)
