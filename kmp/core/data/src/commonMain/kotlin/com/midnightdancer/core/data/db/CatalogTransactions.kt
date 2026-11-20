package com.midnightdancer.core.data.db

internal expect suspend fun <T> CatalogDatabase.withTransaction(block: suspend () -> T): T
