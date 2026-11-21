package com.midnightdancer.shared

import com.midnightdancer.feature.trainer.logic.TrainerSessionStubComponent
import com.midnightdancer.feature.trainer.logic.TrainerSessionIntent

object IosTrainerActions {
    fun togglePause(component: TrainerSessionStubComponent) = component.onIntent(TrainerSessionIntent.TogglePause)
    fun seek(component: TrainerSessionStubComponent, fraction: Float, fromUser: Boolean) =
        component.onIntent(TrainerSessionIntent.Seek(fraction, fromUser))
    fun trackVolume(component: TrainerSessionStubComponent, volume: Float) =
        component.onIntent(TrainerSessionIntent.SetTrackVol(volume))
}
