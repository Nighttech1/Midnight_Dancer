package com.midnightdancer.core.data.db

import androidx.room.withTransaction as androidRoomTransaction

internal actual suspend fun <T> CatalogDatabase.withTransaction(block: suspend () -> T): T =
    androidRoomTransaction(block)
