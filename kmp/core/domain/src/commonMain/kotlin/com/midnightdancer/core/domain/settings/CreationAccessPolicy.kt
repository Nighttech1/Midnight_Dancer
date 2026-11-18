package com.midnightdancer.core.domain.settings

/** Evaluated at creation, never on saving/editing an existing choreography or restoring data. */
fun interface CreationAccessPolicy {
    fun canCreate(existingOwnChoreographies: Int): Boolean
}
