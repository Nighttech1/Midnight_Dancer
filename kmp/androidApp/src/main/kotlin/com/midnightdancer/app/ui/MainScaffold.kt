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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import com.midnightdancer.core.designsystem.HapticEvent
import com.midnightdancer.core.designsystem.ShellChromeTokens
import com.midnightdancer.core.navigation.MainComponent
import com.midnightdancer.core.navigation.NavTab

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
) {
    val haptics = rememberMdHaptics()
    val navFadeSpec = MdMotion.navigationCriticalSpec<Float>()

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
                    if (tab != selectedTab) {
                        haptics.perform(HapticEvent.Selection)
                    }
                    component.onTabSelected(tab)
                },
            )
        },
    ) { innerPadding ->
        ChildPages(
            pages = component.pages,
            onPageSelected = component::onTabSelected,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            scrollAnimation = PagesScrollAnimation.Custom(MdMotion.navigationCritical),
        ) { _, page ->
            when (page) {
                is MainComponent.TabChild.Elements -> ElementsTabScreen(
                    component = page.component,
                    onOpenSettings = component::openSettings,
                )
                is MainComponent.TabChild.Music -> MusicTabScreen(page.component)
                is MainComponent.TabChild.Choreography -> ChoreographyTabScreen(page.component)
                is MainComponent.TabChild.Trainer -> {
                    CompositionLocalProvider(LocalShowLowDiskBanner provides false) {
                        TrainerTabScreen(page.component)
                    }
                }
                is MainComponent.TabChild.Exchange -> ExchangeTabScreen(page.component)
            }
        }
    }
}

@Suppress("unused")
private val BottomBarHeightHint = ShellChromeTokens.BottomTabBarHeightDp
