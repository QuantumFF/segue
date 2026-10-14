package com.quantumff.segue.core.database

import androidx.room.withTransaction
import javax.inject.Inject

/** Runs several DAO writes in one transaction, so readers see all of them or none. */
interface TransactionRunner {
    suspend fun <T> run(block: suspend () -> T): T
}

internal class RoomTransactionRunner @Inject constructor(private val database: SegueDatabase) : TransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T = database.withTransaction { block() }
}
