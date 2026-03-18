package com.example.docscanner.data.local.dao

import androidx.room.*
import com.example.docscanner.data.local.entity.DocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {

    @Query("SELECT * FROM documents WHERE folderId = '' AND isMergedSource = 0 ORDER BY createdAt DESC")
    fun getAllDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE folderId = :folderId AND isMergedSource = 0 ORDER BY createdAt DESC")
    fun getDocumentsByFolder(folderId: String): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :documentId LIMIT 1")
    suspend fun getDocumentById(documentId: String): DocumentEntity?

    @Query("SELECT * FROM documents WHERE id IN (:documentIds)")
    suspend fun getDocumentsByIds(documentIds: List<String>): List<DocumentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity)

    @Delete
    suspend fun deleteDocument(document: DocumentEntity)

    @Query("UPDATE documents SET folderId = :newFolderId WHERE id = :documentId")
    suspend fun updateDocumentFolder(documentId: String, newFolderId: String)

    @Query("UPDATE documents SET name = :newName WHERE id = :documentId")
    suspend fun renameDocument(documentId: String, newName: String)

    @Query("UPDATE documents SET docClassLabel = :label WHERE id = :documentId")
    suspend fun updateDocClassLabel(documentId: String, label: String)

    @Query("UPDATE documents SET docClassLabel = :label WHERE id = :documentId")
    suspend fun updateClassification(documentId: String, label: String)

    @Query("UPDATE documents SET isMergedSource = 1 WHERE id IN (:documentIds)")
    suspend fun markAsMergedSources(documentIds: List<String>)

    @Query("UPDATE documents SET isMergedSource = 0 WHERE id IN (:documentIds)")
    suspend fun restoreMergedSources(documentIds: List<String>)
}