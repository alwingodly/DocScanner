package com.example.docscanner.domain.repository

import com.example.docscanner.data.local.dao.ApplicationDocumentDao
import com.example.docscanner.data.local.dao.ApplicationSessionDao
import com.example.docscanner.data.local.entity.ApplicationDocumentEntity
import com.example.docscanner.data.local.entity.ApplicationSessionEntity
import com.example.docscanner.domain.model.ApplicationDocument
import com.example.docscanner.domain.model.ApplicationSession
import com.example.docscanner.domain.model.ApplicationStatus
import com.example.docscanner.domain.model.ApplicationType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApplicationRepository @Inject constructor(
    private val sessionDao: ApplicationSessionDao,
    private val documentDao: ApplicationDocumentDao
) {
    fun getAllSessions(): Flow<List<ApplicationSession>> =
        sessionDao.getAllSessions().map { it.map { e -> e.toDomain() } }

    suspend fun getSessionById(id: String): ApplicationSession? =
        sessionDao.getSessionById(id)?.toDomain()

    suspend fun createSession(session: ApplicationSession) {
        sessionDao.insertSession(session.toEntity())
    }

    suspend fun updateStatus(id: String, status: ApplicationStatus) {
        sessionDao.updateStatus(id, status.name)
    }

    suspend fun deleteSession(session: ApplicationSession) {
        documentDao.deleteAllForSession(session.id)
        sessionDao.deleteSession(session.toEntity())
    }

    fun getDocumentsForSession(sessionId: String): Flow<List<ApplicationDocument>> =
        documentDao.getDocumentsForSession(sessionId).map { it.map { e -> e.toDomain() } }

    suspend fun attachDocument(doc: ApplicationDocument) {
        documentDao.insertApplicationDocument(doc.toEntity())
    }

    suspend fun detachDocument(doc: ApplicationDocument) {
        documentDao.deleteApplicationDocument(doc.toEntity())
    }
}

// ── Mappers ───────────────────────────────────────────────────────────────────

fun ApplicationSessionEntity.toDomain() = ApplicationSession(
    id = id, name = name,
    applicationType = runCatching { ApplicationType.valueOf(applicationType) }
        .getOrDefault(ApplicationType.PERSONAL_LOAN),
    referenceNumber = referenceNumber,
    status = runCatching { ApplicationStatus.valueOf(status) }
        .getOrDefault(ApplicationStatus.PENDING),
    createdAt = createdAt, updatedAt = updatedAt
)

fun ApplicationSession.toEntity() = ApplicationSessionEntity(
    id = id, name = name,
    applicationType = applicationType.name,
    referenceNumber = referenceNumber,
    status = status.name,
    createdAt = createdAt, updatedAt = updatedAt
)

fun ApplicationDocumentEntity.toDomain() = ApplicationDocument(
    id = id, sessionId = sessionId,
    documentId = documentId, docTypeRequired = docTypeRequired,
    uploadedAt = uploadedAt
)

fun ApplicationDocument.toEntity() = ApplicationDocumentEntity(
    id = id, sessionId = sessionId,
    documentId = documentId, docTypeRequired = docTypeRequired,
    uploadedAt = uploadedAt
)