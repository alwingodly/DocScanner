package com.example.docscanner.data.local.dao

import androidx.room.*
import com.example.docscanner.data.local.entity.DocGroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DocGroupDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: DocGroupEntity)

    @Query("SELECT * FROM doc_groups WHERE id = :groupId LIMIT 1")
    suspend fun getGroupById(groupId: String): DocGroupEntity?

    @Query("SELECT * FROM doc_groups")
    fun getAllGroups(): Flow<List<DocGroupEntity>>

    @Query("UPDATE doc_groups SET name = :name WHERE id = :groupId")
    suspend fun renameGroup(groupId: String, name: String)

    @Query("DELETE FROM doc_groups WHERE id = :groupId")
    suspend fun deleteGroup(groupId: String)

    @Query("DELETE FROM doc_groups WHERE id NOT IN (:activeGroupIds)")
    suspend fun deleteOrphanedGroups(activeGroupIds: List<String>)
}