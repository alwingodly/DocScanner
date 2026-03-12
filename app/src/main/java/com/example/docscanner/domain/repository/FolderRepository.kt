package com.example.docscanner.domain.repository

import com.example.docscanner.data.local.dao.FolderDao
import com.example.docscanner.data.local.entity.FolderEntity
import com.example.docscanner.data.remote.ApiFolderDto
import com.example.docscanner.data.remote.FolderApiService
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
fun ApiFolderDto.toEntity() = FolderEntity(
    id         = id,
    name       = name,
    icon       = icon,
    exportType = "PDF",
    createdAt  = System.currentTimeMillis(),
    sortOrder  = 0
)
@Singleton
class FolderRepository @Inject constructor(
    private val folderDao     : FolderDao,
    private val folderApiService: FolderApiService
) {
    /** Live folder list ordered by user-chosen sortOrder. */
    val folders: Flow<List<Folder>> = folderDao.getAllFolders().map { entities ->
        entities.map { it.toDomain() }
    }

    suspend fun syncFolders(): FolderResult {
        return try {
            val response = folderApiService.getFolders()        // ApiFolderResponse
            folderDao.insertFolders(response.folders.map { it.toEntity() })  // .folders = List<ApiFolderDto>
            FolderResult.Success
        } catch (e: Exception) {
            FolderResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Reorder folders by swapping [fromIndex] and [toIndex] in the current
     * ordered list, then persisting the new sortOrder values to Room.
     *
     * @param currentFolders  The list as currently displayed (already ordered).
     * @param fromIndex       Index being dragged.
     * @param toIndex         Drop target index.
     */
    suspend fun reorderFolders(
        currentFolders: List<Folder>,
        fromIndex: Int,
        toIndex: Int
    ) {
        if (fromIndex == toIndex) return
        if (fromIndex !in currentFolders.indices || toIndex !in currentFolders.indices) return

        // Build new order by moving the dragged item to the target position
        val reordered = currentFolders.toMutableList()
        val moved = reordered.removeAt(fromIndex)
        reordered.add(toIndex, moved)

        // Persist new sortOrder values
        reordered.forEachIndexed { order, folder ->
            folderDao.updateSortOrder(folder.id, order)
        }
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