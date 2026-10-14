package com.quantumff.segue.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.quantumff.segue.core.database.model.ServerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerDao {
    @Query("SELECT * FROM servers ORDER BY name")
    fun observeAll(): Flow<List<ServerEntity>>

    @Upsert
    suspend fun upsert(server: ServerEntity): Long
}
