package com.midnightdancer.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.extensions.compose.pages.ChildPages
import com.arkivanov.decompose.extensions.compose.pages.PagesScrollAnimation
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.midnightdancer.app.ui.components.CyberAcousticDock
import com.midnightdancer.app.ui.components.DiskPlaqueHost
import com.midnightdancer.app.ui.components.LocalShowLowDiskBanner
import com.midnightdancer.app.ui.components.ProvideLowDiskBanner
import com.midnightdancer.app.ui.motion.MdMotion
import com.midnightdancer.app.ui.motion.rememberMdHaptics
import com.midnightdancer.app.ui.settings.SettingsScreen
import com.midnightdancer.app.ui.tabs.ChoreographyTabScreen
import com.midnightdancer.app.ui.tabs.ElementsTabScreen
import com.midnightdancer.app.ui.tabs.ExchangeTabScreen
import com.midnightdancer.app.ui.tabs.MusicTabScreen
import com.midnightdancer.app.ui.tabs.TrainerTabScreen
import com.midnightdancer.app.ui.theme.toComposeColor
import com.midnightdancer.core.designsystem.ColorTokens
import com.midnightdancer.core.designsystem.ShellChromeTokens
import com.midnightdancer.core.navigation.MainComponent
import com.midnightdancer.core.navigation.NavTab

@Composable
private fun rememberElementsEditorOpen(
    pages: com.arkivanov.decompose.router.pages.ChildPages<*, MainComponent.TabChild>,
): Boolean {
    val elements =
        pages.items.getOrNull(NavTab.ELEMENTS.ordinal)?.instance as? MainComponent.TabChild.Elements
    val emptyFlow = androidx.compose.runtime.remember {
        kotlinx.coroutines.flow.MutableStateFlow(
            com.midnightdancer.feature.elements.logic.ElementsUiState(),
        )
    }
    val state by (elements?.component?.uiState ?: emptyFlow).collectAsStateWithLifecycle()
    return elements != null && state.editor != null
}

/** C-197: Pro Editor open → disable ChildPages swipe so timeline pans, not tabs. */
@Composable
private fun rememberChoreoEditorOpen(
    pages: com.arkivanov.decompose.router.pages.ChildPages<*, MainComponent.TabChild>,
): Boolean {
    val choreo =
        pages.items.getOrNull(NavTab.CHOREOGRAPHY.ordinal)?.instance
            as? MainComponent.TabChild.Choreography
            ?: return false
    val stack by choreo.component.stack.subscribeAsState()
    return stack.active.instance is
        com.midnightdancer.feature.choreography.logic.ChoreographyComponent.Child.SequenceEditor
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalDecomposeApi::class)
@Composable
fun MainScaffold(
    component: MainComponent,
    modifier: Modifier = Modifier,
) {
    val overlayStack by component.overlayStack.subscribeAsState()
    val pages by component.pages.subscribeAsState()
    val selectedTab = NavTab.fromIndex(pages.selectedIndex)
    val settingsVisible = overlayStack.active.instance is MainComponent.OverlayChild.Settings

    ProvideLowDiskBanner {
        Box(modifier = modifier.fillMaxSize()) {
            Children(
                stack = component.overlayStack,
                modifier = Modifier.fillMaxSize(),
            ) { child ->
                when (val instance = child.instance) {
                    is MainComponent.OverlayChild.None -> {
                        MainTabsChrome(
                            component = component,
                            selectedTab = selectedTab,
                            settingsVisible = settingsVisible,
                            pages = pages,
                        )
                    }
                    is MainComponent.OverlayChild.Settings -> {
                        SettingsScreen(
                            component = instance.component,
                            onBack = { component.onBack() },
                        )
                    }
                }
            }
            DiskPlaqueHost(
                selectedTab = if (settingsVisible) null else selectedTab,
                settingsOverlay = settingsVisible,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalDecomposeApi::class)
@Composable
private fun MainTabsChrome(
    component: MainComponent,
    selectedTab: NavTab,
    settingsVisible: Boolean,
    pages: com.arkivanov.decompose.router.pages.ChildPages<*, MainComponent.TabChild>,
) {
    val haptics = rememberMdHaptics()
    @Suppress("UNUSED_VARIABLE")
    val unusedHaptics = haptics
    val navFadeSpec = MdMotion.navigationCriticalSpec<Float>()
    val lockPagerForElementEditor = rememberElementsEditorOpen(pages)
    val lockPagerForChoreoEditor = rememberChoreoEditorOpen(pages)
    val lockPagerForEditor = lockPagerForElementEditor || lockPagerForChoreoEditor
    // C-179: editor open → stay on Elements (no accidental swipe flash to Music).
    LaunchedEffect(lockPagerForElementEditor) {
        if (lockPagerForElementEditor && selectedTab != NavTab.ELEMENTS) {
            component.onTabSelected(NavTab.ELEMENTS)
        }
    }
    LaunchedEffect(lockPagerForChoreoEditor) {
        if (lockPagerForChoreoEditor && selectedTab != NavTab.CHOREOGRAPHY) {
            component.onTabSelected(NavTab.CHOREOGRAPHY)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = ColorTokens.OledTrueBlack.toComposeColor(),
        topBar = {
            val hideChromeTopBar =
                selectedTab == NavTab.ELEMENTS ||
                    selectedTab == NavTab.MUSIC ||
                    selectedTab == NavTab.CHOREOGRAPHY ||
                    selectedTab == NavTab.TRAINER ||
                    selectedTab == NavTab.EXCHANGE
            if (!hideChromeTopBar) {
                TopAppBar(
                    title = {
                        AnimatedContent(
                            targetState = selectedTab,
                            transitionSpec = {
                                fadeIn(animationSpec = navFadeSpec) togetherWith
                                    fadeOut(animationSpec = navFadeSpec)
                            },
                            label = "tabTitle",
                        ) { tab ->
                            Text(
                                text = tab.title,
                                style = MaterialTheme.typography.titleLarge,
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = component::openSettings,
                            enabled = !settingsVisible,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = "Settings",
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = ColorTokens.OledTrueBlack.toComposeColor(),
                        titleContentColor = ColorTokens.TextPrimary.toComposeColor(),
                        actionIconContentColor = ColorTokens.NeonOrange.toComposeColor(),
                    ),
                )
            }
        },
        bottomBar = {
            CyberAcousticDock(
                selected = selectedTab,
                onSelect = { tab ->
                    // C-141: dock taps silent — no Selection buzz on tab change.
                    component.onTabSelected(tab)
                },
            )
        },
    ) { innerPadding ->
        // C-259/C-262: only attach leftover-X consumer while editor open (swipe restored otherwise).
        val blockPagerWhenEditor = remember {
            object : NestedScrollConnection {
                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset = Offset(available.x, 0f)
            }
        }
        ChildPages(
            pages = component.pages,
            onPageSelected = { index ->
                component.onPagerPageSelected(index)
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .then(
                    if (lockPagerForEditor) {
                        Modifier.nestedScroll(blockPagerWhenEditor)
                    } else {
                        Modifier
                    },
                ),
            // C-259: soft swipe between tabs; Disabled only while editors need exclusive X.
            scrollAnimation = if (lockPagerForEditor) {
                PagesScrollAnimation.Disabled
            } else {
                PagesScrollAnimation.Default
            },
        ) { _, page ->
            when (page) {
                is MainComponent.TabChild.Elements -> ElementsTabScreen(
                    component = page.component,
                    onOpenSettings = component::openSettings,
                )
                is MainComponent.TabChild.Music -> MusicTabScreen(
                    component = page.component,
                    onOpenSettings = component::openSettings,
                )
                is MainComponent.TabChild.Choreography -> ChoreographyTabScreen(
                    component = page.component,
                    onOpenSettings = component::openSettings,
                )
                is MainComponent.TabChild.Trainer -> {
                    CompositionLocalProvider(LocalShowLowDiskBanner provides false) {
                        TrainerTabScreen(
                            component = page.component,
                            onOpenSettings = component::openSettings,
                            isTabSelected = selectedTab == NavTab.TRAINER,
                        )
                    }
                }
                is MainComponent.TabChild.Exchange -> ExchangeTabScreen(
                    component = page.component,
                    onOpenSettings = component::openSettings,
                )
            }
        }
    }
}

@Suppress("unused")
private val BottomBarHeightHint = ShellChromeTokens.BottomTabBarHeightDp
