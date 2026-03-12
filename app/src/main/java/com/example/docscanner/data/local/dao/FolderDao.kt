package com.example.docscanner.data.local.dao

import androidx.room.*
import com.example.docscanner.data.local.entity.FolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {

    /**
     * Live folder list with document count calculated directly from the
     * documents table via subquery — always accurate after any insert,
     * delete, or move without needing manual increment/decrement.
     */
    @Query("""
        SELECT f.*,
               (SELECT COUNT(*) FROM documents d WHERE d.folderId = f.id) AS documentCount
        FROM folders f
        ORDER BY f.sortOrder ASC, f.createdAt ASC
    """)
    fun getAllFolders(): Flow<List<FolderEntity>>

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