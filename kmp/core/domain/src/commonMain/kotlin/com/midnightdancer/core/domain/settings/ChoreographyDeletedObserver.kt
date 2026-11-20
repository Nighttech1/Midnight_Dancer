package com.midnightdancer.core.domain.settings

fun interface ChoreographyDeletedObserver {
    fun onDeleted(choreographyId: String)
}
