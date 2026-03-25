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
    id         = if (sessionId != null) "${sessionId}_${id}" else id, // ← unique per session
    name       = name,
    icon       = icon,
    exportType = "PDF",
    createdAt  = System.currentTimeMillis(),
    sortOrder  = 0,
    sessionId  = sessionId
)

@Singleton
class FolderRepository @Inject constructor(
    private val folderDao      : FolderDao,
    private val folderApiService: FolderApiService
) {

    // ── Existing (untouched) ──────────────────────────────────────────────────

    /** Global folders for the plain doc scanner (sessionId IS NULL) */
    val folders: Flow<List<Folder>> = folderDao.getAllFolders().map { entities ->
        entities.map { it.toDomain() }
    }

    suspend fun syncFolders(): FolderResult {
        return try {
            val response = folderApiService.getFolders(null) // global folders
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
        val moved = reordered.removeAt(fromIndex)
        reordered.add(toIndex, moved)

        reordered.forEachIndexed { order, folder ->
            folderDao.updateSortOrder(folder.id, order)
        }
    }

    // ── New session-aware methods ─────────────────────────────────────────────

    /** Live folder list scoped to a specific session */
    fun getFoldersForSession(sessionId: String): Flow<List<Folder>> =
        folderDao.getFoldersForSession(sessionId).map { entities ->
            entities.map { it.toDomain() }
        }

    /**
     * Fetch folders from API for the given application type
     * and save them locally tied to this session.
     * Safe to call multiple times — skips if folders already exist.
     */
    suspend fun syncFoldersForSession(
        sessionId: String,
        applicationType: ApplicationType
    ): FolderResult {
        return try {
            // Skip if already synced for this session
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

    /** Call when deleting a session — cleans up its folders */
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
    documentCount = documentCount
)