package com.midnightdancer.app.ui.tabs

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.midnightdancer.app.ui.choreography.KineticVaultScreen
import com.midnightdancer.app.ui.choreography.ProEditorScreen
import com.midnightdancer.app.ui.elements.KineticAtelierScreen
import com.midnightdancer.app.ui.exchange.KineticSyncDeckScreen
import com.midnightdancer.app.ui.music.MotionDeckScreen
import com.midnightdancer.app.ui.trainer.LiveFlowScreen
import com.midnightdancer.app.ui.trainer.StorylineScreen
import com.midnightdancer.feature.choreography.logic.ChoreographyComponent
import com.midnightdancer.feature.elements.logic.ElementsComponent
import com.midnightdancer.feature.exchange.logic.ExchangeComponent
import com.midnightdancer.feature.music.logic.MusicComponent
import com.midnightdancer.feature.trainer.logic.TrainerComponent

@Composable
fun ElementsTabScreen(
    component: ElementsComponent,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by component.uiState.collectAsStateWithLifecycle()
    KineticAtelierScreen(
        state = state,
        onIntent = component::onIntent,
        onOpenSettings = onOpenSettings,
        modifier = modifier.fillMaxSize(),
    )
}

@Composable
fun MusicTabScreen(
    component: MusicComponent,
    modifier: Modifier = Modifier,
) {
    val state by component.uiState.collectAsStateWithLifecycle()
    MotionDeckScreen(
        state = state,
        onIntent = component::onIntent,
        modifier = modifier.fillMaxSize(),
    )
}

@Composable
fun ChoreographyTabScreen(
    component: ChoreographyComponent,
    modifier: Modifier = Modifier,
) {
    val stack by component.stack.subscribeAsState()

    when (val child = stack.active.instance) {
        is ChoreographyComponent.Child.Hub -> {
            val hubState by child.component.uiState.collectAsStateWithLifecycle()
            KineticVaultScreen(
                state = hubState,
                onIntent = child.component::onIntent,
                modifier = modifier.fillMaxSize(),
            )
        }
        is ChoreographyComponent.Child.SequenceEditor -> {
            val editorState by child.component.uiState.collectAsStateWithLifecycle()
            ProEditorScreen(
                state = editorState,
                onIntent = child.component::onIntent,
                onBack = { component.onBack() },
                modifier = modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun TrainerTabScreen(
    component: TrainerComponent,
    modifier: Modifier = Modifier,
) {
    val stack by component.stack.subscribeAsState()

    when (val child = stack.active.instance) {
        is TrainerComponent.Child.Hub -> {
            val hubState by child.component.uiState.collectAsStateWithLifecycle()
            StorylineScreen(
                state = hubState,
                onIntent = child.component::onIntent,
                modifier = modifier.fillMaxSize(),
            )
        }
        is TrainerComponent.Child.Session -> {
            val sessionState by child.component.uiState.collectAsStateWithLifecycle()
            LiveFlowScreen(
                state = sessionState,
                onIntent = child.component::onIntent,
                onBack = { component.onBack() },
                modifier = modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun ExchangeTabScreen(
    component: ExchangeComponent,
    modifier: Modifier = Modifier,
) {
    val state by component.uiState.collectAsStateWithLifecycle()
    KineticSyncDeckScreen(
        state = state,
        onIntent = component::onIntent,
        modifier = modifier.fillMaxSize(),
    )
}
