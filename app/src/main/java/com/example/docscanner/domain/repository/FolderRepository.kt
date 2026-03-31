package com.example.docscanner.domain.repository

import com.example.docscanner.data.local.dao.FolderDao
import com.example.docscanner.data.local.entity.FolderEntity
import com.example.docscanner.data.remote.ApiFolderDto
import com.example.docscanner.data.remote.FolderApiService
import com.example.docscanner.domain.model.ApplicationType
import com.example.docscanner.domain.model.Folder
import com.example.docscanner.domain.model.FolderExportType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

sealed class FolderResult {
    data object Loading : FolderResult()
    data object Success : FolderResult()
    data class Error(val message: String) : FolderResult()
}

fun ApiFolderDto.toEntity(sessionId: String? = null) = FolderEntity(
    id        = if (sessionId != null) "${sessionId}_${id}" else id,
    name      = name,
    icon      = icon,
    exportType = "PDF",
    createdAt = System.currentTimeMillis(),
    sortOrder = 0,
    sessionId = sessionId,
    docType   = docType                     // ← pass through
)

@Singleton
class FolderRepository @Inject constructor(
    private val folderDao       : FolderDao,
    private val folderApiService: FolderApiService
) {

    val folders: Flow<List<Folder>> = folderDao.getAllFolders().map { entities ->
        entities.map { it.toDomain() }
    }

    suspend fun syncFolders(): FolderResult {
        return try {
            val response = folderApiService.getFolders(null)
            folderDao.insertFolders(response.folders.map { it.toEntity(sessionId = null) })
            FolderResult.Success
        } catch (e: Exception) {
            FolderResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun reorderFolders(
        currentFolders: List<Folder>,
        fromIndex: Int,
        toIndex: Int
    ) {
        if (fromIndex == toIndex) return
        if (fromIndex !in currentFolders.indices || toIndex !in currentFolders.indices) return

        val reordered = currentFolders.toMutableList()
        val moved     = reordered.removeAt(fromIndex)
        reordered.add(toIndex, moved)

        reordered.forEachIndexed { order, folder ->
            folderDao.updateSortOrder(folder.id, order)
        }
    }

    fun getFoldersForSession(sessionId: String): Flow<List<Folder>> =
        folderDao.getFoldersForSession(sessionId).map { entities ->
            entities.map { it.toDomain() }
        }

    suspend fun syncFoldersForSession(
        sessionId: String,
        applicationType: ApplicationType
    ): FolderResult {
        return try {
            val existing = folderDao.countFoldersForSession(sessionId)
            if (existing > 0) return FolderResult.Success

            val response = folderApiService.getFolders(applicationType)
            val entities = response.folders.mapIndexed { index, dto ->
                dto.toEntity(sessionId = sessionId).copy(sortOrder = index)
            }
            folderDao.insertFolders(entities)
            FolderResult.Success
        } catch (e: Exception) {
            FolderResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun deleteFoldersForSession(sessionId: String) {
        folderDao.deleteFoldersForSession(sessionId)
    }
}

// ── Mappers ───────────────────────────────────────────────────────────────────

fun FolderEntity.toDomain() = Folder(
    id            = id,
    name          = name,
    icon          = icon,
    exportType    = runCatching { FolderExportType.valueOf(exportType) }
        .getOrDefault(FolderExportType.PDF),
    createdAt     = createdAt,
    documentCount = documentCount,
    docType       = docType                 // ← map through
)