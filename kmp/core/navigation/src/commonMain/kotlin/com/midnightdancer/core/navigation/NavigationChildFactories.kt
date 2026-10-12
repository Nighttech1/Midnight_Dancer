package com.midnightdancer.core.navigation

import com.arkivanov.decompose.ComponentContext
import com.midnightdancer.feature.choreography.logic.ChoreographyComponent
import com.midnightdancer.feature.choreography.logic.DefaultChoreographyComponent
import com.midnightdancer.feature.elements.logic.DefaultElementsComponent
import com.midnightdancer.feature.elements.logic.ElementsComponent
import com.midnightdancer.feature.exchange.logic.DefaultExchangeComponent
import com.midnightdancer.feature.exchange.logic.ExchangeComponent
import com.midnightdancer.feature.music.logic.DefaultMusicComponent
import com.midnightdancer.feature.music.logic.MusicComponent
import com.midnightdancer.feature.settings.logic.DefaultSettingsComponent
import com.midnightdancer.feature.settings.logic.SettingsComponent
import com.midnightdancer.feature.splash.logic.DefaultSplashComponent
import com.midnightdancer.feature.splash.logic.SplashComponent
import com.midnightdancer.feature.trainer.logic.DefaultTrainerComponent
import com.midnightdancer.feature.trainer.logic.TrainerComponent

/**
 * Child factories for Root/Main — wire from Koin in `:shared` (C-21).
 * Defaults construct feature `:logic` shells; nav tree stays in this module.
 */
data class NavigationChildFactories(
    val splash: (
        componentContext: ComponentContext,
        finishSplash: () -> Unit,
        autoFinish: Boolean,
    ) -> SplashComponent = { ctx, finish, auto ->
        DefaultSplashComponent(ctx, finish, auto)
    },
    val elements: (ComponentContext) -> ElementsComponent = ::DefaultElementsComponent,
    val music: (ComponentContext) -> MusicComponent = ::DefaultMusicComponent,
    val choreography: (ComponentContext) -> ChoreographyComponent = ::DefaultChoreographyComponent,
    val trainer: (ComponentContext) -> TrainerComponent = ::DefaultTrainerComponent,
    val exchange: (ComponentContext) -> ExchangeComponent = { ctx ->
        DefaultExchangeComponent(componentContext = ctx)
    },
    val settings: (ComponentContext) -> SettingsComponent = { ctx ->
        DefaultSettingsComponent(ctx)
    },
)
