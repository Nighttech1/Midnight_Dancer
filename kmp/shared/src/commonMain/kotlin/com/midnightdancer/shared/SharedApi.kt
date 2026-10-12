package com.midnightdancer.shared

/**
 * Re-export public navigation + feature logic API from shared for UI consumers.
 * Prefer importing `com.midnightdancer.core.navigation.*` /
 * `com.midnightdancer.feature.*.logic.*` or this facade.
 *
 * Stage 3 (C-19/C-21) surface for Compose / SwiftUI:
 * - [com.midnightdancer.core.navigation.RootComponent]
 * - [com.midnightdancer.core.navigation.MainComponent]
 * - [com.midnightdancer.core.navigation.NavTab]
 * - [com.midnightdancer.core.navigation.NavigationChildFactories]
 * - feature Intent / UiState / Component types under `feature.*.logic`
 *
 * C-22 binds Compose shells to feature StateFlow + Intent.
 * C-23 binds SwiftUI shells the same way (SKIE).
 * C-33 Trainer speak → `:core:tts` ([com.midnightdancer.core.tts.TrainerTtsController]).
 */
object SharedApi {
    const val FRAMEWORK_NAME: String = "Shared"
}
