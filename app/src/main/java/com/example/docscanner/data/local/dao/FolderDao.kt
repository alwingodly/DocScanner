package com.example.docscanner.data.local.dao

import androidx.room.*
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.docscanner.data.local.entity.FolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {

    // ── Existing (untouched) ──────────────────────────────────────────────────

    @RawQuery(observedEntities = [FolderEntity::class, com.example.docscanner.data.local.entity.DocumentEntity::class])
    fun getAllFoldersRaw(query: SupportSQLiteQuery): Flow<List<FolderEntity>>

    fun getAllFolders(): Flow<List<FolderEntity>> = getAllFoldersRaw(
        SimpleSQLiteQuery("""
            SELECT f.*,
                   (SELECT COUNT(*) FROM documents d WHERE d.folderId = f.id AND d.isMergedSource = 0) AS documentCount
            FROM folders f
            WHERE f.sessionId IS NULL
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

    // ── New session-aware queries ─────────────────────────────────────────────

    @RawQuery(observedEntities = [FolderEntity::class, com.example.docscanner.data.local.entity.DocumentEntity::class])
    fun getFoldersForSessionRaw(query: SupportSQLiteQuery): Flow<List<FolderEntity>>

    fun getFoldersForSession(sessionId: String): Flow<List<FolderEntity>> =
        getFoldersForSessionRaw(
            SimpleSQLiteQuery("""
                SELECT f.*,
                       (SELECT COUNT(*) FROM documents d WHERE d.folderId = f.id AND d.isMergedSource = 0) AS documentCount
                FROM folders f
                WHERE f.sessionId = ?
                ORDER BY f.sortOrder ASC, f.createdAt ASC
            """, arrayOf(sessionId))
        )

    @Query("SELECT COUNT(*) FROM folders WHERE sessionId = :sessionId")
    suspend fun countFoldersForSession(sessionId: String): Int

    @Query("DELETE FROM folders WHERE sessionId = :sessionId")
    suspend fun deleteFoldersForSession(sessionId: String)
}