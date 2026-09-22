package com.midnightdancer.core.data

/**
 * Stage 1 stub — no Ktor/Room yet (later stages).
 */
interface DataRepositoryStub {
    fun ping(): String
}

class InMemoryDataRepositoryStub : DataRepositoryStub {
    override fun ping(): String = "ok"
}
