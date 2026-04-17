package com.example.docscanner.domain.repository

import com.example.docscanner.data.local.dao.DocGroupDao
import com.example.docscanner.data.local.dao.DocumentDao
import com.example.docscanner.data.local.dao.FolderDao
import com.example.docscanner.data.local.entity.DocGroupEntity
import com.example.docscanner.data.local.entity.DocumentEntity
import com.example.docscanner.domain.model.Document
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentRepository @Inject constructor(
    private val documentDao : DocumentDao,
    private val folderDao   : FolderDao,
    private val docGroupDao : DocGroupDao       // ← new
) {
    // ── Existing ──────────────────────────────────────────────────────────────

    fun getDocuments(folderId: String): Flow<List<Document>> =
        documentDao.getDocumentsByFolder(folderId).map { it.map { e -> e.toDomain() } }

    fun getAllDocuments(): Flow<List<Document>> =
        documentDao.getAllDocuments().map { it.map { e -> e.toDomain() } }

    suspend fun getDocumentsByIds(documentIds: List<String>): List<Document> =
        documentDao.getDocumentsByIds(documentIds).map { it.toDomain() }

    suspend fun saveDocument(document: Document) {
        documentDao.insertDocument(document.toEntity())
    }

    suspend fun deleteDocument(document: Document) {
        documentDao.deleteDocument(document.toEntity())
    }

    suspend fun moveDocumentToFolder(documentId: String, targetFolderId: String) {
        val doc = documentDao.getDocumentById(documentId) ?: return
        if (doc.folderId == targetFolderId) return
        documentDao.updateDocumentFolder(documentId, targetFolderId)
    }

    suspend fun renameDocument(documentId: String, newName: String) {
        documentDao.renameDocument(documentId, newName)
    }

    suspend fun updateDocClassLabel(documentId: String, label: String) {
        documentDao.updateDocClassLabel(documentId, label)
    }

    suspend fun updateClassification(documentId: String, label: String) {
        documentDao.updateClassification(documentId, label)
    }

    suspend fun markAsMergedSources(documentIds: List<String>) {
        documentDao.markAsMergedSources(documentIds)
    }

    suspend fun restoreMergedSources(documentIds: List<String>) {
        documentDao.restoreMergedSources(documentIds)
    }

    fun getDocumentsForSession(sessionId: String): Flow<List<Document>> =
        documentDao.getDocumentsForSession(sessionId).map { it.map { e -> e.toDomain() } }

    fun getDocumentsByFolderAndSession(sessionId: String, folderId: String): Flow<List<Document>> =
        documentDao.getDocumentsByFolderAndSession(sessionId, folderId)
            .map { it.map { e -> e.toDomain() } }

    suspend fun deleteAllDocumentsForSession(sessionId: String) {
        documentDao.deleteAllDocumentsForSession(sessionId)
    }

    suspend fun updateAadhaarGroup(docId: String, side: String?, groupId: String?) {
        documentDao.updateAadhaarGroup(docId, side, groupId)
    }

    suspend fun getExistingAadhaarDocs(sessionId: String): List<Document> =
        documentDao.getExistingAadhaarDocs(sessionId).map { it.toDomain() }

    suspend fun updateAadhaarGroupIdOnly(docId: String, groupId: String) {
        documentDao.updateAadhaarGroupIdOnly(docId, groupId)
    }

    suspend fun updateDocGroupId(docId: String, groupId: String?) {
        documentDao.updateDocGroupId(docId, groupId)
    }

    fun getDocsByGroup(groupId: String): Flow<List<Document>> =
        documentDao.getDocsByGroup(groupId).map { it.map { e -> e.toDomain() } }

    suspend fun getGroupIdsForType(docType: String, sessionId: String): List<String> =
        documentDao.getGroupIdsForType(docType, sessionId)

    // ── Doc group name CRUD ───────────────────────────────────────────────────

    fun getAllDocGroups(): Flow<List<DocGroupEntity>> =
        docGroupDao.getAllGroups()

    suspend fun createDocGroup(groupId: String, name: String) {
        docGroupDao.insertGroup(
            DocGroupEntity(id = groupId, name = name, createdAt = System.currentTimeMillis())
        )
    }

    suspend fun renameDocGroup(groupId: String, name: String) {
        docGroupDao.renameGroup(groupId, name)
    }

    suspend fun deleteDocGroup(groupId: String) {
        docGroupDao.deleteGroup(groupId)
    }

    suspend fun pruneOrphanedGroups(activeGroupIds: List<String>) {
        if (activeGroupIds.isEmpty()) return
        docGroupDao.deleteOrphanedGroups(activeGroupIds)
    }

    suspend fun getGlobalAadhaarDocs(): List<Document> =
        documentDao.getGlobalAadhaarDocs().map { it.toDomain() }

    // ── Passport pairing ──────────────────────────────────────────────────────

    suspend fun updatePassportGroup(docId: String, side: String?, groupId: String?, holderName: String?) {
        documentDao.updatePassportGroup(docId, side, groupId, holderName)
    }

    suspend fun updatePassportGroupIdOnly(docId: String, groupId: String) {
        documentDao.updatePassportGroupIdOnly(docId, groupId)
    }

    suspend fun getExistingPassportDocs(sessionId: String): List<Document> =
        documentDao.getExistingPassportDocs(sessionId).map { it.toDomain() }

    suspend fun getGlobalPassportDocs(): List<Document> =
        documentDao.getGlobalPassportDocs().map { it.toDomain() }

    suspend fun getPassportDocsByHash(hash: String): List<Document> =
        documentDao.getPassportDocsByHash(hash).map { it.toDomain() }

    suspend fun getUnpairedPassportDocs(sessionId: String?): List<Document> =
        if (sessionId != null)
            documentDao.getUnpairedPassportDocs(sessionId).map { it.toDomain() }
        else
            documentDao.getUnpairedGlobalPassportDocs().map { it.toDomain() }


    /** All FRONT-side passports (has group, no back yet) in session/global scope. */
    suspend fun getUnmatchedFrontPassports(sessionId: String?): List<Document> {
        val all = if (sessionId != null)
            documentDao.getExistingPassportDocs(sessionId).map { it.toDomain() }
        else
            documentDao.getGlobalPassportDocs().map { it.toDomain() }

        val frontGroupIds = all.filter { it.passportSide == "FRONT" && it.passportGroupId != null }
            .map { it.passportGroupId!! }.toSet()
        val backGroupIds  = all.filter { it.passportSide == "BACK"  && it.passportGroupId != null }
            .map { it.passportGroupId!! }.toSet()
        val unmatchedGroupIds = frontGroupIds - backGroupIds

        return all.filter { it.passportGroupId in unmatchedGroupIds && it.passportSide == "FRONT" }
    }
}

// ── Mappers ───────────────────────────────────────────────────────────────────

fun DocumentEntity.toDomain() = Document(
    id                    = id,
    folderId              = folderId,
    name                  = name,
    pageCount             = pageCount,
    thumbnailPath         = thumbnailPath,
    pdfPath               = pdfPath,
    docClassLabel         = docClassLabel,
    createdAt             = createdAt,
    mergedFromDocumentIds = mergedFromDocumentIds,
    isMergedSource        = isMergedSource,
    sessionId             = sessionId,
    aadhaarSide           = aadhaarSide,
    aadhaarGroupId        = aadhaarGroupId,
    docGroupId            = docGroupId,
    aadhaarName           = aadhaarName,
    aadhaarDob            = aadhaarDob,
    aadhaarGender         = aadhaarGender,
    aadhaarMaskedNumber   = aadhaarMaskedNumber,
    aadhaarAddress        = aadhaarAddress,
    extractedDetailsJson  = extractedDetailsJson,
    ocrRawText            = ocrRawText,
    passportGroupId       = passportGroupId,
    passportSide          = passportSide,
    passportHolderName    = passportHolderName,
    passportNumHash       = passportNumHash,
)

fun Document.toEntity() = DocumentEntity(
    id                    = id,
    folderId              = folderId,
    name                  = name,
    pageCount             = pageCount,
    thumbnailPath         = thumbnailPath,
    pdfPath               = pdfPath,
    docClassLabel         = docClassLabel,
    createdAt             = createdAt,
    mergedFromDocumentIds = mergedFromDocumentIds,
    isMergedSource        = isMergedSource,
    sessionId             = sessionId,
    aadhaarSide           = aadhaarSide,
    aadhaarGroupId        = aadhaarGroupId,
    docGroupId            = docGroupId,
    aadhaarName           = aadhaarName,
    aadhaarDob            = aadhaarDob,
    aadhaarGender         = aadhaarGender,
    aadhaarMaskedNumber   = aadhaarMaskedNumber,
    aadhaarAddress        = aadhaarAddress,
    extractedDetailsJson  = extractedDetailsJson,
    ocrRawText            = ocrRawText,
    passportGroupId       = passportGroupId,
    passportSide          = passportSide,
    passportHolderName    = passportHolderName,
    passportNumHash       = passportNumHash,
)
