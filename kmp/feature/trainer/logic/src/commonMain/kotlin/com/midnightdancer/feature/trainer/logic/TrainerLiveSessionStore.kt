package com.midnightdancer.feature.trainer.logic

/**
 * C-268: process-scoped Live session snapshot so a ChildPages remount cannot wipe
 * an active training run down to a single «Фристайл» cue. Cleared only on Finish.
 */
object TrainerLiveSessionStore {
    @Volatile
    var active: Boolean = false
        private set

    @Volatile
    var snapshot: TrainerSessionUiState? = null
        private set

    fun begin(state: TrainerSessionUiState) {
        snapshot = state
        active = true
    }

    fun update(state: TrainerSessionUiState) {
        if (!active) return
        snapshot = state
    }

    fun end() {
        active = false
        snapshot = null
    }
}
