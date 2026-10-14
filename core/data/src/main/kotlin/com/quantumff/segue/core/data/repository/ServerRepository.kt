package com.quantumff.segue.core.data.repository

import com.quantumff.segue.core.datastore.AccountsDataSource
import com.quantumff.segue.core.model.Server
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

interface ServerRepository {
    val servers: Flow<List<Server>>
}

internal class DataStoreServerRepository @Inject constructor(private val accounts: AccountsDataSource) :
    ServerRepository {
    override val servers: Flow<List<Server>> = accounts.accounts
}
