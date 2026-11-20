package com.midnightdancer.core.data.db

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection

internal actual suspend fun <T> CatalogDatabase.withTransaction(block: suspend () -> T): T =
    useWriterConnection { connection ->
        connection.immediateTransaction { block() }
    }
