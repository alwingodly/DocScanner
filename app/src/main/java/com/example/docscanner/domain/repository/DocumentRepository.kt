package com.example.docscanner.domain.repository

import com.example.docscanner.data.local.dao.DocumentDao
import com.example.docscanner.data.local.dao.FolderDao
import com.example.docscanner.data.local.entity.DocumentEntity
import com.example.docscanner.domain.model.Document
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentRepository @Inject constructor(
    private val documentDao: DocumentDao,
    private val folderDao  : FolderDao
) {
    // ── Existing (untouched) ──────────────────────────────────────────────────

    fun getDocuments(folderId: String): Flow<List<Document>> =
        documentDao.getDocumentsByFolder(folderId).map { entities ->
            entities.map { it.toDomain() }
        }

    fun getAllDocuments(): Flow<List<Document>> =
        documentDao.getAllDocuments().map { entities ->
            entities.map { it.toDomain() }
        }

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

    // ── New session-aware methods ─────────────────────────────────────────────

    /** Unfoldered docs scoped to a session — for AllDocumentsScreen */
    fun getDocumentsForSession(sessionId: String): Flow<List<Document>> =
        documentDao.getDocumentsForSession(sessionId).map { entities ->
            entities.map { it.toDomain() }
        }

    /** Folder docs scoped to a session — for FolderDetailScreen */
    fun getDocumentsByFolderAndSession(
        sessionId: String,
        folderId: String
    ): Flow<List<Document>> =
        documentDao.getDocumentsByFolderAndSession(sessionId, folderId).map { entities ->
            entities.map { it.toDomain() }
        }

    suspend fun deleteAllDocumentsForSession(sessionId: String) {
        documentDao.deleteAllDocumentsForSession(sessionId)
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
    sessionId             = sessionId
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
    sessionId             = sessionId
)