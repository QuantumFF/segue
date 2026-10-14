package com.quantumff.segue.core.database.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.quantumff.segue.core.model.Server

@Entity(tableName = "servers")
data class ServerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val baseUrl: String,
    val username: String,
)

fun ServerEntity.asExternalModel() = Server(id = id.toString(), name = name, baseUrl = baseUrl, username = username)
