package com.example.groupaac.data.repository

import androidx.room.withTransaction
import com.example.groupaac.data.AppDatabase

interface TransactionRunner {
    suspend fun <T> inTransaction(block: suspend () -> T): T
}

class RoomTransactionRunner(
    private val database: AppDatabase
) : TransactionRunner {
    override suspend fun <T> inTransaction(block: suspend () -> T): T =
        database.withTransaction(block)
}

object ImmediateTransactionRunner : TransactionRunner {
    override suspend fun <T> inTransaction(block: suspend () -> T): T = block()
}
