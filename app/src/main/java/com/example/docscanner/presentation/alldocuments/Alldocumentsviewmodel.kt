package com.example.docscanner.presentation.alldocuments

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.docscanner.data.masking.AadhaarMasker
import com.example.docscanner.data.masking.PanMasker
import com.example.docscanner.domain.model.AadhaarGroup
import com.example.docscanner.domain.model.Document
import com.example.docscanner.domain.model.Folder
import com.example.docscanner.domain.repository.DocumentRepository
import com.example.docscanner.domain.repository.FolderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class AllDocumentsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val documentRepository : DocumentRepository,
    private val folderRepository   : FolderRepository,
    private val aadhaarMasker      : AadhaarMasker,
    private val panMasker          : PanMasker
) : ViewModel() {

    // ── SharedPreferences ─────────────────────────────────────────────────────

    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("doc_section_order", Context.MODE_PRIVATE)

    private fun loadOrderFromPrefs(): Map<String, List<String>> =
        prefs.all.entries.associate { (key, value) ->
            key to ((value as? String)?.split(",")?.filter { it.isNotEmpty() } ?: emptyList())
        }

    private fun saveOrderToPrefs(orderMap: Map<String, List<String>>) {
        prefs.edit().apply {
            clear()
            orderMap.forEach { (label, ids) -> putString(label, ids.joinToString(",")) }
        }.apply()
    }

    // ── Session ───────────────────────────────────────────────────────────────

    private val _activeSessionId = MutableStateFlow<String?>(null)
    private val _isLoading       = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun setSessionId(sessionId: String?) {
        if (_activeSessionId.value == sessionId) return
        _isLoading.value = true
        _activeSessionId.value = sessionId
    }

    // ── Documents ─────────────────────────────────────────────────────────────

    @OptIn(ExperimentalCoroutinesApi::class)
    val documents: StateFlow<List<Document>> = _activeSessionId
        .flatMapLatest { sessionId ->
            if (sessionId != null) documentRepository.getDocumentsForSession(sessionId)
            else documentRepository.getAllDocuments()
        }
        .onEach { _isLoading.value = false }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ── Group names ───────────────────────────────────────────────────────────

    /** groupId → display name */
    val docGroupNames: StateFlow<Map<String, String>> =
        documentRepository.getAllDocGroups()
            .map { list -> list.associate { it.id to it.name } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    // ── Aadhaar grouping ──────────────────────────────────────────────────────

    val aadhaarGroups: StateFlow<List<AadhaarGroup>> = documents
        .map { docs ->
            docs
                .filter { it.docClassLabel?.startsWith("Aadhaar") == true }
                .filter { it.aadhaarGroupId != null }
                .groupBy { it.aadhaarGroupId!! }
                .map { (groupId, groupDocs) ->
                    val front = groupDocs.filter { it.aadhaarSide == "FRONT" }
                        .maxByOrNull { it.createdAt }
                    val back  = groupDocs.filter { it.aadhaarSide == "BACK" }
                        .maxByOrNull { it.createdAt }

                    // ── Always prefer front doc name, fall back to back ──────────
                    val holderName = front?.aadhaarName
                        ?: back?.aadhaarName
                        ?: (front ?: back)
                            ?.name
                            ?.substringBefore("_Aadhaar")
                            ?.replace("_", " ")
                            ?.ifBlank { null }

                    AadhaarGroup(
                        groupId           = groupId,
                        holderName        = holderName,
                        frontDoc          = front,
                        backDoc           = back,
                        isManuallyGrouped = groupDocs.any {
                            it.aadhaarGroupId?.startsWith("manual_") == true
                        }
                    )
                }
                .sortedByDescending {
                    maxOf(
                        it.frontDoc?.createdAt ?: 0L,
                        it.backDoc?.createdAt ?: 0L
                    )
                }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    fun getGroupDocs(groupId: String): Flow<List<Document>> =
        documentRepository.getDocsByGroup(groupId)

    // ── Generic doc groups ────────────────────────────────────────────────────

    val docGroups: StateFlow<Map<String, List<Document>>> = documents
        .map { docs ->
            docs.filter { it.docGroupId != null }.groupBy { it.docGroupId!! }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    // ── Aadhaar actions ───────────────────────────────────────────────────────

    fun manuallyGroupAadhaar(docId1: String, docId2: String) {
        viewModelScope.launch {
            val doc1 = documents.value.firstOrNull { it.id == docId1 } ?: return@launch
            val doc2 = documents.value.firstOrNull { it.id == docId2 } ?: return@launch
            val groupId = "manual_${System.currentTimeMillis()}"
            val (side1, side2) = when {
                doc1.aadhaarSide != null && doc2.aadhaarSide != null ->
                    doc1.aadhaarSide to doc2.aadhaarSide
                doc1.aadhaarSide != null ->
                    doc1.aadhaarSide to if (doc1.aadhaarSide == "FRONT") "BACK" else "FRONT"
                doc2.aadhaarSide != null ->
                    (if (doc2.aadhaarSide == "FRONT") "BACK" else "FRONT") to doc2.aadhaarSide
                else -> "FRONT" to "BACK"
            }
            documentRepository.updateAadhaarGroup(docId1, side1, groupId)
            documentRepository.updateAadhaarGroup(docId2, side2, groupId)
        }
    }

    fun addDocToExistingGroup(docId: String, existingGroupId: String, side: String) {
        viewModelScope.launch {
            documentRepository.updateAadhaarGroup(docId, side, existingGroupId)
        }
    }

    fun ungroupAadhaar(group: AadhaarGroup) {
        viewModelScope.launch {
            listOfNotNull(group.frontDoc, group.backDoc).forEach { doc ->
                documentRepository.updateAadhaarGroup(doc.id, doc.aadhaarSide, null)
            }
        }
    }

    fun swapAadhaarSides(group: AadhaarGroup) {
        viewModelScope.launch {
            group.frontDoc?.let { documentRepository.updateAadhaarGroup(it.id, "BACK", group.groupId) }
            group.backDoc?.let  { documentRepository.updateAadhaarGroup(it.id, "FRONT", group.groupId) }
        }
    }

    // ── Generic doc group actions ─────────────────────────────────────────────

    fun createGroupFromDocs(docIds: List<String>, name: String) {
        if (docIds.size < 2) return
        viewModelScope.launch {
            val groupId = "grp_${System.currentTimeMillis()}"
            documentRepository.createDocGroup(groupId, name)
            docIds.forEach { documentRepository.updateDocGroupId(it, groupId) }
        }
    }

    fun renameDocGroup(groupId: String, newName: String) {
        viewModelScope.launch {
            documentRepository.renameDocGroup(groupId, newName)
        }
    }

    fun disbandGroup(groupId: String) {
        viewModelScope.launch {
            val docs = documents.value.filter { it.docGroupId == groupId }
            docs.forEach { documentRepository.updateDocGroupId(it.id, null) }
            documentRepository.deleteDocGroup(groupId)
        }
    }

    fun deleteEntireGroup(groupId: String) {
        viewModelScope.launch {
            val docs = documents.value.filter { it.docGroupId == groupId }
            docs.forEach { documentRepository.deleteDocument(it) }
            documentRepository.deleteDocGroup(groupId)
        }
    }

    fun removeFromGroup(docId: String) {
        viewModelScope.launch {
            documentRepository.updateDocGroupId(docId, null)
        }
    }

    fun addToGroup(docId: String, groupId: String) {
        viewModelScope.launch { documentRepository.updateDocGroupId(docId, groupId) }
    }

    fun createGroup(docId: String) {
        viewModelScope.launch {
            val groupId = "grp_${System.currentTimeMillis()}"
            documentRepository.createDocGroup(groupId, "Group ${docGroups.value.size + 1}")
            documentRepository.updateDocGroupId(docId, groupId)
        }
    }

    fun reorderGroupDocs(groupId: String, fromIdx: Int, toIdx: Int, docs: List<Document>) {
        if (fromIdx == toIdx || fromIdx !in docs.indices || toIdx !in docs.indices) return
        val ids   = docs.map { it.id }.toMutableList()
        val moved = ids.removeAt(fromIdx)
        ids.add(toIdx, moved)
        val updated = _sectionDocOrder.value + ("group_$groupId" to ids)
        _sectionDocOrder.value = updated
        saveOrderToPrefs(updated)
    }

    suspend fun getGroupsForType(docType: String): List<DocGroupInfo> {
        val sessionId = _activeSessionId.value ?: return emptyList()
        val groupIds  = documentRepository.getGroupIdsForType(docType, sessionId)
        return groupIds.map { groupId ->
            val docs = documents.value.filter { it.docGroupId == groupId }
            DocGroupInfo(
                groupId    = groupId,
                docType    = docType,
                previewDoc = docs.maxByOrNull { it.createdAt },
                count      = docs.size
            )
        }
    }

    data class DocGroupInfo(
        val groupId    : String,
        val docType    : String,
        val previewDoc : Document?,
        val count      : Int
    )

    // ── Folders ───────────────────────────────────────────────────────────────

    @OptIn(ExperimentalCoroutinesApi::class)
    val allFolders: StateFlow<List<Folder>> = _activeSessionId
        .flatMapLatest { sessionId ->
            if (sessionId != null) folderRepository.getFoldersForSession(sessionId)
            else folderRepository.folders
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val otherSectionNames: StateFlow<Set<String>> = allFolders
        .map { folderList ->
            folderList.filter { it.docType == "Other" }.map { it.name }.toSet()
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    // ── Section ordering ──────────────────────────────────────────────────────

    private val _sectionDocOrder = MutableStateFlow<Map<String, List<String>>>(
        loadOrderFromPrefs()
    )
    val sectionDocOrder: StateFlow<Map<String, List<String>>> = _sectionDocOrder.asStateFlow()

    fun reorderSection(label: String, fromIdx: Int, toIdx: Int, docs: List<Document>) {
        if (fromIdx == toIdx || fromIdx !in docs.indices || toIdx !in docs.indices) return
        val ids   = docs.map { it.id }.toMutableList()
        val moved = ids.removeAt(fromIdx)
        ids.add(toIdx, moved)
        val updated = _sectionDocOrder.value + (label to ids)
        _sectionDocOrder.value = updated
        saveOrderToPrefs(updated)
    }

    // ── Grouped by folder docType ─────────────────────────────────────────────

    val groupedByDocType: StateFlow<Map<String, List<Document>>> =
        combine(allFolders, documents, _sectionDocOrder) { folderList, docList, orderMap ->
            if (folderList.isEmpty()) return@combine emptyMap()

            val labelToFolderName = folderList.associate { it.docType to it.name }
            val otherFolderName   = folderList.lastOrNull { it.docType == "Other" }?.name
                ?: folderList.last().name

            val bucket = mutableMapOf<String, MutableList<Document>>()
            docList.forEach { doc ->
                val label = when (val raw = doc.docClassLabel ?: "Other") {
                    "Aadhaar Front", "Aadhaar Back" -> "Aadhaar"
                    else -> raw
                }
                val folderName = labelToFolderName[label] ?: otherFolderName
                bucket.getOrPut(folderName) { mutableListOf() }.add(doc)
            }

            val ordered = bucket.mapValues { (sectionLabel, sectionDocs) ->
                val userOrder = orderMap[sectionLabel]
                if (userOrder.isNullOrEmpty()) return@mapValues sectionDocs
                val byId         = sectionDocs.associateBy { it.id }
                val validUserIds = userOrder.filter { it in byId }
                val newDocs      = sectionDocs.filter { it.id !in validUserIds.toSet() }
                validUserIds.mapNotNull { byId[it] } + newDocs
            }

            folderList.associate { folder ->
                folder.name to (ordered[folder.name] ?: emptyList())
            }
        }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    // ── Document actions ──────────────────────────────────────────────────────

    fun moveDocumentToFolder(documentId: String, targetFolderId: String) {
        viewModelScope.launch { documentRepository.moveDocumentToFolder(documentId, targetFolderId) }
    }

    fun deleteDocument(document: Document) {
        viewModelScope.launch { documentRepository.deleteDocument(document) }
    }

    fun renameDocument(document: Document, newName: String) {
        viewModelScope.launch { documentRepository.renameDocument(document.id, newName) }
    }

    fun changeDocumentType(document: Document, newLabel: String) {
        viewModelScope.launch {
            val groupId = document.docGroupId
            if (groupId != null) {
                val groupDocs = documents.value.filter { it.docGroupId == groupId }
                groupDocs.forEach { doc ->
                    documentRepository.updateDocClassLabel(doc.id, newLabel)
                    syncAadhaarMetadata(doc, newLabel)
                    if (newLabel.startsWith("Aadhaar") || newLabel == "PAN Card")
                        applyMaskingIfNeeded(doc, newLabel)
                }
            } else {
                documentRepository.updateDocClassLabel(document.id, newLabel)
                syncAadhaarMetadata(document, newLabel)
                if (newLabel.startsWith("Aadhaar") || newLabel == "PAN Card")
                    applyMaskingIfNeeded(document, newLabel)
            }
        }
    }

    private suspend fun syncAadhaarMetadata(document: Document, newLabel: String) {
        val resolvedSide = when (newLabel) {
            "Aadhaar Front" -> "FRONT"
            "Aadhaar Back" -> "BACK"
            "Aadhaar" -> document.aadhaarSide
            else -> null
        }

        if (newLabel.startsWith("Aadhaar")) {
            documentRepository.updateAadhaarGroup(
                docId = document.id,
                side = resolvedSide,
                groupId = document.aadhaarGroupId
            )
        } else if (document.aadhaarSide != null || document.aadhaarGroupId != null) {
            documentRepository.updateAadhaarGroup(
                docId = document.id,
                side = null,
                groupId = null
            )
        }
    }

    private suspend fun applyMaskingIfNeeded(document: Document, label: String) {
        val imagePath = document.thumbnailPath ?: return
        try {
            val uri    = android.net.Uri.parse(imagePath)
            val bitmap: Bitmap? = when (uri.scheme) {
                "file"    -> BitmapFactory.decodeFile(uri.path)
                "content" -> appContext.contentResolver.openInputStream(uri)
                    ?.use { BitmapFactory.decodeStream(it) }
                else -> BitmapFactory.decodeFile(imagePath)
            }
            if (bitmap == null) return
            val maskedBitmap = when {
                label.startsWith("Aadhaar") -> aadhaarMasker.mask(bitmap)
                label == "PAN Card"         -> panMasker.mask(bitmap)
                else                        -> return
            }
            if (maskedBitmap !== bitmap) {
                when (uri.scheme) {
                    "file"    -> File(uri.path!!).outputStream().use {
                        maskedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)
                    }
                    "content" -> appContext.contentResolver.openOutputStream(uri, "wt")?.use {
                        maskedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)
                    }
                    else -> File(imagePath).outputStream().use {
                        maskedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AllDocumentsVM", "Masking on type change failed", e)
        }
    }

    fun updateClassification(document: Document, label: String) {
        viewModelScope.launch { documentRepository.updateClassification(document.id, label) }
    }

    // ── Organize ──────────────────────────────────────────────────────────────

    private val _isOrganizing     = MutableStateFlow(false)
    val isOrganizing: StateFlow<Boolean> = _isOrganizing.asStateFlow()

    private val _organizeProgress = MutableStateFlow(0f)
    val organizeProgress: StateFlow<Float> = _organizeProgress.asStateFlow()

    fun runAiOrganize() {
        if (_isOrganizing.value) return
        val docs = documents.value
        if (docs.isEmpty()) return
        _isOrganizing.value     = true
        _organizeProgress.value = 0f
        viewModelScope.launch {
            docs.forEachIndexed { index, _ ->
                _organizeProgress.value = (index + 1).toFloat() / docs.size
            }
            _organizeProgress.value = 1f
            _isOrganizing.value     = false
        }
    }

    fun clearOrganize() { /* no state to clear */ }
}
