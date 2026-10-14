package com.quantumff.segue.core.data.repository

import com.quantumff.segue.core.database.dao.ServerDao
import com.quantumff.segue.core.database.model.asExternalModel
import com.quantumff.segue.core.model.Server
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface ServerRepository {
    val servers: Flow<List<Server>>
}

/** Reads from Room only. Network refreshes write to Room; they never feed the UI directly. */
internal class OfflineFirstServerRepository @Inject constructor(private val serverDao: ServerDao) : ServerRepository {
    override val servers: Flow<List<Server>> =
        serverDao.observeAll().map { entities -> entities.map { it.asExternalModel() } }
}
