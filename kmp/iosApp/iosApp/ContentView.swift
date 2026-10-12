import SwiftUI

/// Root SwiftUI entry — feature screens on Decompose/SKIE SoT.
struct ContentView: View {
    @StateObject private var bridge = RootBridge()

    var body: some View {
        ZStack {
            MdColor.oledTrueBlack.ignoresSafeArea()
            switch bridge.route {
            case .splash(let snap):
                SplashShellView(snapshot: snap, onContinue: { bridge.splashContinue() })
            case .main(let chrome):
                MainShellView(
                    chrome: chrome,
                    tabActions: tabActions,
                    settingsActions: settingsActions,
                    onSelectTab: { bridge.selectTab(index: $0) },
                    onOpenSettings: { bridge.openSettings() },
                    onBack: { bridge.closeSettingsOrBack() }
                )
            }
        }
        .preferredColorScheme(.dark)
        .onAppear { bridge.start() }
    }

    private var tabActions: TabFeatureActions {
        TabFeatureActions(
            elementsStylePrevious: { bridge.elementsStylePrevious() },
            elementsStyleNext: { bridge.elementsStyleNext() },
            elementsAdd: { bridge.elementsAdd() },
            elementsSearch: { bridge.elementsSearch($0) },
            elementsFilter: { bridge.elementsFilter($0) },
            elementsPlay: { bridge.elementsPlay($0) },
            elementsOpenDetail: { bridge.elementsOpenDetail($0) },
            elementsCloseDetail: { bridge.elementsCloseDetail() },
            musicSelect: { bridge.musicSelect($0) },
            musicSearch: { bridge.musicSearch($0) },
            musicBeginUpload: { bridge.musicBeginUpload() },
            musicEdit: { bridge.musicEdit($0) },
            musicDelete: { bridge.musicDelete($0) },
            vaultSearch: { bridge.vaultSearch($0) },
            vaultCreate: { bridge.vaultCreate() },
            vaultOpen: { bridge.vaultOpen($0) },
            vaultDelete: { bridge.vaultDelete($0) },
            trainerSelectVoice: { bridge.trainerSelectVoice(index: $0) },
            trainerSpeak: { bridge.trainerSpeak() },
            trainerStartDance: { bridge.trainerStartDance() },
            trainerSetStep: { bridge.trainerSetStep($0) },
            trainerSetStyle: { bridge.trainerSetStyle($0) },
            trainerSetTrack: { bridge.trainerSetTrack($0) },
            trainerSetTag: { bridge.trainerSetTag($0) },
            trainerSetInterval: { bridge.trainerSetInterval($0) },
            exchangeOpenExport: { bridge.exchangeOpenExport() },
            exchangeDismissExport: { bridge.exchangeDismissExport() },
            exchangeSelectStyle: { bridge.exchangeSelectStyle($0) },
            exchangeConfirmExport: { bridge.exchangeConfirmExport() },
            exchangeOpenImport: { bridge.exchangeOpenImport() },
            exchangeDismissImport: { bridge.exchangeDismissImport() },
            exchangeSetImportStyle: { bridge.exchangeSetImportStyle($0) },
            exchangeConfirmImport: { bridge.exchangeConfirmImport() }
        )
    }

    private var settingsActions: SettingsFeatureActions {
        SettingsFeatureActions(
            setLanguage: { bridge.settingsSetLanguage($0) },
            setReminder: { bridge.settingsSetReminder($0) },
            setStyleDraft: { bridge.settingsSetStyleDraft($0) },
            addStyle: { bridge.settingsAddStyle() },
            removeStyle: { bridge.settingsRemoveStyle($0) },
            setCategoryDraft: { bridge.settingsSetCategoryDraft($0) },
            addCategory: { bridge.settingsAddCategory() },
            removeCategory: { bridge.settingsRemoveCategory($0) },
            setLowDiskGib: { bridge.settingsSetLowDiskGib($0) },
            setLowDiskEnabled: { bridge.settingsSetLowDiskEnabled($0) }
        )
    }
}

#Preview {
    ContentView()
}
