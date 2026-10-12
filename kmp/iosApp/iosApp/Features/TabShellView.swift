import SwiftUI

/// Routes tab feature snapshots to concrete SwiftUI screens.
struct TabShellView: View {
    let tab: TabShellSnapshot
    let actions: TabFeatureActions

    var body: some View {
        switch tab.feature {
        case .elements(let snap):
            ElementsAtelierView(
                snapshot: snap,
                onStylePrevious: actions.elementsStylePrevious,
                onStyleNext: actions.elementsStyleNext,
                onAdd: actions.elementsAdd,
                onSearch: actions.elementsSearch,
                onFilter: actions.elementsFilter,
                onPlay: actions.elementsPlay,
                onOpenDetail: actions.elementsOpenDetail,
                onCloseDetail: actions.elementsCloseDetail
            )
        case .music(let snap):
            MotionDeckView(
                snapshot: snap,
                onSelect: actions.musicSelect,
                onSearch: actions.musicSearch,
                onUpload: actions.musicBeginUpload,
                onEdit: actions.musicEdit,
                onDelete: actions.musicDelete
            )
        case .choreography(let snap):
            KineticVaultView(
                snapshot: snap,
                onSearch: actions.vaultSearch,
                onCreate: actions.vaultCreate,
                onOpen: actions.vaultOpen,
                onDelete: actions.vaultDelete
            )
        case .trainer(let snap):
            TrainerHubView(
                snapshot: snap,
                onSelectVoice: actions.trainerSelectVoice,
                onSpeak: actions.trainerSpeak,
                onStartDance: actions.trainerStartDance,
                onSetStep: actions.trainerSetStep,
                onSetStyle: actions.trainerSetStyle,
                onSetTrack: actions.trainerSetTrack,
                onSetTag: actions.trainerSetTag,
                onSetInterval: actions.trainerSetInterval
            )
        case .exchange(let snap):
            SyncDeckView(
                snapshot: snap,
                onOpenExport: actions.exchangeOpenExport,
                onDismissExport: actions.exchangeDismissExport,
                onSelectExportStyle: actions.exchangeSelectStyle,
                onConfirmExport: actions.exchangeConfirmExport,
                onOpenImport: actions.exchangeOpenImport,
                onDismissImport: actions.exchangeDismissImport,
                onSetImportStyle: actions.exchangeSetImportStyle,
                onConfirmImport: actions.exchangeConfirmImport
            )
        }
    }
}

/// Closures from RootBridge — pure Swift, no Shared.
struct TabFeatureActions {
    var elementsStylePrevious: () -> Void = {}
    var elementsStyleNext: () -> Void = {}
    var elementsAdd: () -> Void = {}
    var elementsSearch: (String) -> Void = { _ in }
    var elementsFilter: (String?) -> Void = { _ in }
    var elementsPlay: (String) -> Void = { _ in }
    var elementsOpenDetail: (String) -> Void = { _ in }
    var elementsCloseDetail: () -> Void = {}

    var musicSelect: (String) -> Void = { _ in }
    var musicSearch: (String) -> Void = { _ in }
    var musicBeginUpload: () -> Void = {}
    var musicEdit: (String) -> Void = { _ in }
    var musicDelete: (String) -> Void = { _ in }

    var vaultSearch: (String) -> Void = { _ in }
    var vaultCreate: () -> Void = {}
    var vaultOpen: (String) -> Void = { _ in }
    var vaultDelete: (String) -> Void = { _ in }

    var trainerSelectVoice: (Int) -> Void = { _ in }
    var trainerSpeak: () -> Void = {}
    var trainerStartDance: () -> Void = {}
    var trainerSetStep: (Int) -> Void = { _ in }
    var trainerSetStyle: (String) -> Void = { _ in }
    var trainerSetTrack: (String) -> Void = { _ in }
    var trainerSetTag: (String) -> Void = { _ in }
    var trainerSetInterval: (Float) -> Void = { _ in }

    var exchangeOpenExport: () -> Void = {}
    var exchangeDismissExport: () -> Void = {}
    var exchangeSelectStyle: (String) -> Void = { _ in }
    var exchangeConfirmExport: () -> Void = {}
    var exchangeOpenImport: () -> Void = {}
    var exchangeDismissImport: () -> Void = {}
    var exchangeSetImportStyle: (String) -> Void = { _ in }
    var exchangeConfirmImport: () -> Void = {}
}
