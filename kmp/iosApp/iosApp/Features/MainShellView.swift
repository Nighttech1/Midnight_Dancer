import SwiftUI

/// Main chrome: 5 tabs + CyberAcoustic dock + Settings overlay.
struct MainShellView: View {
    let chrome: MainChromeSnapshot
    let tabActions: TabFeatureActions
    let settingsActions: SettingsFeatureActions
    let onSelectTab: (Int) -> Void
    let onOpenSettings: () -> Void
    let onBack: () -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private var navAnimation: Animation? {
        reduceMotion ? nil : MdSpring.navigationCritical
    }

    var body: some View {
        ZStack {
            tabScaffold
                .opacity(chrome.settings == nil ? 1 : 0.35)
                .allowsHitTesting(chrome.settings == nil)

            if let settings = chrome.settings {
                SettingsScreenView(
                    snapshot: settings,
                    onBack: onBack,
                    onSetLanguage: settingsActions.setLanguage,
                    onSetReminder: settingsActions.setReminder,
                    onSetStyleDraft: settingsActions.setStyleDraft,
                    onAddStyle: settingsActions.addStyle,
                    onRemoveStyle: settingsActions.removeStyle,
                    onSetCategoryDraft: settingsActions.setCategoryDraft,
                    onAddCategory: settingsActions.addCategory,
                    onRemoveCategory: settingsActions.removeCategory,
                    onSetLowDiskGib: settingsActions.setLowDiskGib,
                    onSetLowDiskEnabled: settingsActions.setLowDiskEnabled
                )
                .transition(.move(edge: .trailing).combined(with: .opacity))
                .zIndex(1)
            }
        }
        .animation(navAnimation, value: chrome.settings != nil)
        .onAppear { MdHaptics.prepare() }
    }

    private var tabScaffold: some View {
        VStack(spacing: 0) {
            topBar
            ZStack {
                if let tab = chrome.tabs.first(where: { $0.id == chrome.selectedTabIndex }) {
                    TabShellView(tab: tab, actions: tabActions)
                        .id(tab.id)
                        .transition(.opacity)
                } else {
                    Color.clear
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .animation(navAnimation, value: chrome.selectedTabIndex)
        }
        .safeAreaInset(edge: .bottom, spacing: 0) {
            CyberAcousticDockView(
                tabs: chrome.tabs,
                selectedIndex: chrome.selectedTabIndex,
                onSelect: onSelectTab
            )
        }
        .background(MdColor.oledTrueBlack)
    }

    private var topBar: some View {
        HStack {
            Spacer(minLength: 0)
            Button(action: onOpenSettings) {
                Image(systemName: "gearshape.fill")
                    .font(.system(size: MdChrome.tabIconSize))
                    .foregroundStyle(MdColor.neonOrange)
                    .frame(width: MdChrome.minTouchTarget, height: MdChrome.minTouchTarget)
                    .background(Circle().fill(MdColor.neonOrange.opacity(0.12)))
            }
            .accessibilityLabel("Настройки")
        }
        .padding(.horizontal, MdChrome.contentHorizontalPadding)
        .frame(height: 44)
        .background(MdColor.oledTrueBlack.opacity(0.94))
    }
}

struct SettingsFeatureActions {
    var setLanguage: (String) -> Void = { _ in }
    var setReminder: (Bool) -> Void = { _ in }
    var setStyleDraft: (String) -> Void = { _ in }
    var addStyle: () -> Void = {}
    var removeStyle: (String) -> Void = { _ in }
    var setCategoryDraft: (String) -> Void = { _ in }
    var addCategory: () -> Void = {}
    var removeCategory: (String) -> Void = { _ in }
    var setLowDiskGib: (Int) -> Void = { _ in }
    var setLowDiskEnabled: (Bool) -> Void = { _ in }
}
