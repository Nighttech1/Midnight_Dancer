package com.midnightdancer.core.navigation

/**
 * Shared UI state for Stage 1 hello button (consumed by Compose / SwiftUI).
 * SKIE-friendly: immutable data class + StateFlow on RootComponent.
 */
data class HelloUiState(
    val clickCount: Int = 0,
    val label: String = "Hello Midnight Dancer",
) {
    val displayLabel: String
        get() = if (clickCount == 0) label else "$label ($clickCount)"
}
