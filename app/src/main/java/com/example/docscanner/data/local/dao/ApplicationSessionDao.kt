package com.example.docscanner.data.local.dao

import androidx.room.*
import com.example.docscanner.data.local.entity.ApplicationSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ApplicationSessionDao {

    @Query("SELECT * FROM application_sessions ORDER BY updatedAt DESC")
    fun getAllSessions(): Flow<List<ApplicationSessionEntity>>

    @Query("SELECT * FROM application_sessions WHERE id = :id LIMIT 1")
    suspend fun getSessionById(id: String): ApplicationSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ApplicationSessionEntity)

    @Update
    suspend fun updateSession(session: ApplicationSessionEntity)

    @Delete
    suspend fun deleteSession(session: ApplicationSessionEntity)

    @Query("UPDATE application_sessions SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, updatedAt: Long = System.currentTimeMillis())
}