import SwiftUI

/// Root SwiftUI entry — feature screens on Decompose/SKIE SoT.
struct ContentView: View {
    @StateObject private var bridge = RootBridge()

    var body: some View {
        ZStack {
            MdColor.oledTrueBlack.ignoresSafeArea()
            switch bridge.route {
            case .splash(let snap):
                if let error = bridge.startupError {
                    VStack(spacing: 16) {
                        Text("Не удалось открыть библиотеку").font(.headline)
                        Text(error).multilineTextAlignment(.center)
                        Button("Повторить", action: bridge.start)
                            .buttonStyle(.borderedProminent)
                    }
                    .padding(24)
                } else {
                    SplashShellView(snapshot: snap, onContinue: { bridge.splashContinue() })
                }
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
            elementsSave: { bridge.elementsSave($0) },
            musicSelect: { bridge.musicSelect($0) },
            musicSearch: { bridge.musicSearch($0) },
            musicBeginUpload: { bridge.musicBeginUpload() },
            musicEdit: { bridge.musicEdit($0) },
            musicDelete: { bridge.musicDelete($0) },
            musicImport: { await bridge.musicImport($0) },
            musicSave: { bridge.musicSave($0) },
            musicCloseEdit: { bridge.musicCloseEdit() },
            musicStop: { bridge.musicStop($0) },
            musicSeek: { bridge.musicSeek($0, $1) },
            vaultSearch: { bridge.vaultSearch($0) },
            vaultCreate: { bridge.vaultCreate() },
            vaultDismissCreationMessage: { bridge.vaultDismissCreationMessage() },
            vaultOpen: { bridge.vaultOpen($0) },
            vaultDelete: { bridge.vaultDelete($0) },
            vaultEditorBack: { bridge.vaultEditorBack() },
            vaultEditorTogglePlay: { bridge.vaultEditorTogglePlay() },
            vaultEditorSetTitle: { bridge.vaultEditorSetTitle($0) },
            vaultEditorSetStyle: { bridge.vaultEditorSetStyle($0) },
            vaultEditorSave: { bridge.vaultEditorSave() },
            vaultEditorElementChoices: { bridge.vaultEditorElementChoices() },
            vaultEditorAddElement: { bridge.vaultEditorAddElement($0) },
            vaultTimelineActions: bridge.vaultTimelineActions(),
            trainerChoreography: bridge.trainerChoreographyActions,
            trainerSelectVoice: { bridge.trainerSelectVoice(index: $0) },
            trainerSpeak: { bridge.trainerSpeak() },
            trainerStartDance: { bridge.trainerStartDance() },
            trainerSetStep: { bridge.trainerSetStep($0) },
            trainerSetStyle: { bridge.trainerSetStyle($0) },
            trainerSetTrack: { bridge.trainerSetTrack($0) },
            trainerSetTag: { bridge.trainerSetTag($0) },
            trainerSetInterval: { bridge.trainerSetInterval($0) },
            trainerSetElementTiming: { bridge.trainerSetElementTiming($0) },
            trainerSessionTogglePause: { bridge.trainerSessionTogglePause() },
            trainerSessionPause: { bridge.trainerSessionPause() },
            trainerSessionSeek: { bridge.trainerSessionSeek($0, $1) },
            trainerSessionVolume: { bridge.trainerSessionVolume($0) },
            trainerSessionFinish: { bridge.trainerSessionFinish() },
            sets: bridge.setsActions()
        )
    }

    private var settingsActions: SettingsFeatureActions {
        SettingsFeatureActions(
            setLanguage: { bridge.settingsSetLanguage($0) },
            setReminder: { bridge.settingsSetReminder($0) },
            setStyleDraft: { bridge.settingsSetStyleDraft($0) },
            addStyle: { bridge.settingsAddStyle() },
            renameStyle: { bridge.settingsRenameStyle($0, $1) },
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
