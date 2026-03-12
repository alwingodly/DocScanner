package com.example.docscanner.data.local.dao

import androidx.room.*
import com.example.docscanner.data.local.entity.DocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {

    /**
     * "All Documents" tab — only shows documents that have NOT been
     * assigned to a folder yet (folderId is blank / empty string).
     * Once a document is dragged to a folder, folderId is updated
     * and it disappears from this list automatically.
     */
    @Query("SELECT * FROM documents WHERE folderId = '' ORDER BY createdAt DESC")
    fun getAllDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE folderId = :folderId ORDER BY createdAt DESC")
    fun getDocumentsByFolder(folderId: String): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :documentId LIMIT 1")
    suspend fun getDocumentById(documentId: String): DocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity)

    @Delete
    suspend fun deleteDocument(document: DocumentEntity)

    @Query("UPDATE documents SET folderId = :newFolderId WHERE id = :documentId")
    suspend fun updateDocumentFolder(documentId: String, newFolderId: String)
}