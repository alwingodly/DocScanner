package com.example.docscanner.data.local.dao

import androidx.room.*
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.docscanner.data.local.entity.FolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {

    /**
     * Live folder list with document count.
     *
     * Uses @RawQuery to ensure Room's invalidation tracker monitors BOTH
     * the `folders` and `documents` tables. With a plain @Query containing
     * a subquery on `documents`, Room sometimes misses invalidation when
     * documents are moved/deleted/merged via raw UPDATE statements.
     *
     * observedEntities forces Room to re-emit when either table changes.
     */
    @RawQuery(observedEntities = [FolderEntity::class, com.example.docscanner.data.local.entity.DocumentEntity::class])
    fun getAllFoldersRaw(query: SupportSQLiteQuery): Flow<List<FolderEntity>>

    fun getAllFolders(): Flow<List<FolderEntity>> = getAllFoldersRaw(
        SimpleSQLiteQuery("""
            SELECT f.*,
                   (SELECT COUNT(*) FROM documents d WHERE d.folderId = f.id AND d.isMergedSource = 0) AS documentCount
            FROM folders f
            ORDER BY f.sortOrder ASC, f.createdAt ASC
        """)
    )

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFolder(folder: FolderEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFolders(folders: List<FolderEntity>)

    @Update
    suspend fun updateFolder(folder: FolderEntity)

    @Query("UPDATE folders SET sortOrder = :order WHERE id = :folderId")
    suspend fun updateSortOrder(folderId: String, order: Int)

    @Delete
    suspend fun deleteFolder(folder: FolderEntity)
}