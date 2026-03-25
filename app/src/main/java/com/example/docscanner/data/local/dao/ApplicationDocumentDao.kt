package com.example.docscanner.data.local.dao

import androidx.room.*
import com.example.docscanner.data.local.entity.ApplicationDocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ApplicationDocumentDao {

    @Query("SELECT * FROM application_documents WHERE sessionId = :sessionId")
    fun getDocumentsForSession(sessionId: String): Flow<List<ApplicationDocumentEntity>>

    @Query("SELECT * FROM application_documents WHERE sessionId = :sessionId")
    suspend fun getDocumentsForSessionOnce(sessionId: String): List<ApplicationDocumentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApplicationDocument(doc: ApplicationDocumentEntity)

    @Delete
    suspend fun deleteApplicationDocument(doc: ApplicationDocumentEntity)

    @Query("DELETE FROM application_documents WHERE sessionId = :sessionId")
    suspend fun deleteAllForSession(sessionId: String)
}