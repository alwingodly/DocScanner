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
    fun getDocuments(folderId: String): Flow<List<Document>> =
        documentDao.getDocumentsByFolder(folderId).map { entities ->
            entities.map { it.toDomain() }
        }

    fun getAllDocuments(): Flow<List<Document>> =
        documentDao.getAllDocuments().map { entities ->
            entities.map { it.toDomain() }
        }

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
}

// ── Mappers ───────────────────────────────────────────────────────────────────

fun DocumentEntity.toDomain() = Document(
    id            = id,
    folderId      = folderId,
    name          = name,
    pageCount     = pageCount,
    thumbnailPath = thumbnailPath,
    pdfPath       = pdfPath,
    docClassLabel = docClassLabel,
    createdAt     = createdAt
)

fun Document.toEntity() = DocumentEntity(
    id            = id,
    folderId      = folderId,
    name          = name,
    pageCount     = pageCount,
    thumbnailPath = thumbnailPath,
    pdfPath       = pdfPath,
    docClassLabel = docClassLabel,
    createdAt     = createdAt
)