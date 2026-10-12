package com.midnightdancer.core.domain.usecase

import com.midnightdancer.core.domain.entity.AppData
import kotlinx.coroutines.flow.Flow

/** Load / observe the library catalog. Implementation lives in `:core:data`. */
interface LoadLibrary {
    suspend fun load(): AppData

    fun observe(): Flow<AppData>
}
