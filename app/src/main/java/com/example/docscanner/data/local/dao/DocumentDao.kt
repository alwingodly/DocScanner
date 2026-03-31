package com.example.docscanner.data.local.dao

import androidx.room.*
import com.example.docscanner.data.local.entity.DocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {

    // ── Existing (untouched) ──────────────────────────────────────────────────

    /** Global doc scanner — no session */
    @Query("SELECT * FROM documents WHERE folderId = '' AND sessionId IS NULL AND isMergedSource = 0 ORDER BY createdAt DESC")
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

    // ── Session-aware queries ─────────────────────────────────────────────────

    /**
     * ALL docs for a session — both foldered and unfoldered.
     * folderId = '' filter removed so folder-scanned docs are visible
     * in AllDocumentsScreen alongside global-scan docs.
     */
    @Query("""
        SELECT * FROM documents 
        WHERE sessionId = :sessionId 
        AND isMergedSource = 0 
        ORDER BY createdAt DESC
    """)
    fun getDocumentsForSession(sessionId: String): Flow<List<DocumentEntity>>

    /** Docs inside a specific folder for a specific session */
    @Query("""
        SELECT * FROM documents 
        WHERE sessionId = :sessionId 
        AND folderId = :folderId 
        AND isMergedSource = 0 
        ORDER BY createdAt DESC
    """)
    fun getDocumentsByFolderAndSession(
        sessionId: String,
        folderId: String
    ): Flow<List<DocumentEntity>>

    @Query("DELETE FROM documents WHERE sessionId = :sessionId")
    suspend fun deleteAllDocumentsForSession(sessionId: String)

    @Query("UPDATE documents SET aadhaarSide = :side, aadhaarGroupId = :groupId WHERE id = :docId")
    suspend fun updateAadhaarGroup(docId: String, side: String?, groupId: String?)

    @Query("SELECT * FROM documents WHERE aadhaarGroupId = :groupId AND isMergedSource = 0")
    fun getAadhaarGroup(groupId: String): Flow<List<DocumentEntity>>

    @Query("""
    SELECT * FROM documents 
    WHERE sessionId = :sessionId 
    AND (
        docClassLabel = 'Aadhaar'
        OR docClassLabel = 'Aadhaar Front' 
        OR docClassLabel = 'Aadhaar Back'
    )
    AND aadhaarGroupId IS NOT NULL
    AND isMergedSource = 0
""")
    suspend fun getExistingAadhaarDocs(sessionId: String): List<DocumentEntity>

    @Query("UPDATE documents SET aadhaarGroupId = :groupId WHERE id = :docId")
    suspend fun updateAadhaarGroupIdOnly(docId: String, groupId: String)

    @Query("UPDATE documents SET docGroupId = :groupId WHERE id = :docId")
    suspend fun updateDocGroupId(docId: String, groupId: String?)

    @Query("SELECT * FROM documents WHERE docGroupId = :groupId AND isMergedSource = 0 ORDER BY createdAt DESC")
    fun getDocsByGroup(groupId: String): Flow<List<DocumentEntity>>

    @Query("SELECT DISTINCT docGroupId FROM documents WHERE docClassLabel = :docType AND docGroupId IS NOT NULL AND sessionId = :sessionId")
    suspend fun getGroupIdsForType(docType: String, sessionId: String): List<String>

    @Query("""
    SELECT * FROM documents 
    WHERE sessionId IS NULL
    AND (
        docClassLabel = 'Aadhaar'
        OR docClassLabel = 'Aadhaar Front' 
        OR docClassLabel = 'Aadhaar Back'
    )
    AND aadhaarGroupId IS NOT NULL
    AND isMergedSource = 0
""")
    suspend fun getGlobalAadhaarDocs(): List<DocumentEntity>
}