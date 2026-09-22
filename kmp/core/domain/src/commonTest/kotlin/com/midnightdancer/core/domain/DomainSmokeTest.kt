package com.midnightdancer.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class DomainSmokeTest {
    @Test
    fun domainMarker_isPresent() {
        assertEquals("core-domain", DomainMarker.NAME)
    }
}
