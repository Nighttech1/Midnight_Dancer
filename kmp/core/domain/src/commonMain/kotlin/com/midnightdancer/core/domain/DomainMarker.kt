package com.midnightdancer.core.domain

/** Pure-Kotlin marker for `:core:domain` (full product only — no lite flavors). */
object DomainMarker {
    const val NAME: String = "core-domain"

    /** Locked product model: KMP ships full package only (`com.midnightdancer.app`). */
    const val PRODUCT: String = "full"
}
