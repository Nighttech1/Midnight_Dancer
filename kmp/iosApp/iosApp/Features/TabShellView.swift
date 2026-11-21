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
                onCloseDetail: actions.elementsCloseDetail,
                onSave: actions.elementsSave
            )
        case .music(let snap):
            MotionDeckView(
                snapshot: snap,
                onSelect: actions.musicSelect,
                onSearch: actions.musicSearch,
                onUpload: actions.musicBeginUpload,
                onEdit: actions.musicEdit,
                onDelete: actions.musicDelete,
                onImport: actions.musicImport,
                onSave: actions.musicSave,
                onCloseEdit: actions.musicCloseEdit,
                onStop: actions.musicStop,
                onSeek: actions.musicSeek
            )
        case .choreography(let snap):
            if let editor = snap.editor {
                ProEditorShellView(
                    snapshot: editor,
                    onBack: actions.vaultEditorBack,
                    onTogglePlay: actions.vaultEditorTogglePlay,
                    onSetTitle: actions.vaultEditorSetTitle,
                    onSetStyle: actions.vaultEditorSetStyle,
                    onSave: actions.vaultEditorSave,
                    elementChoices: actions.vaultEditorElementChoices,
                    onAddElement: actions.vaultEditorAddElement,
                    timeline: editor.timeline.withActions(actions.vaultTimelineActions)
                )
            } else {
                KineticVaultView(
                    snapshot: snap,
                    onSearch: actions.vaultSearch,
                    onCreate: actions.vaultCreate,
                    onOpen: actions.vaultOpen,
                    onDelete: actions.vaultDelete
                )
            }
        case .trainer(let snap):
            if let session = snap.session {
                TrainerSessionView(snapshot: session, onTogglePause: actions.trainerSessionTogglePause,
                    onPause: actions.trainerSessionPause, onSeek: actions.trainerSessionSeek,
                    onVolume: actions.trainerSessionVolume, onFinish: actions.trainerSessionFinish)
                    .id(session.id)
            } else {
                TrainerHubView(snapshot: snap,
                    onSelectVoice: actions.trainerSelectVoice, onSpeak: actions.trainerSpeak,
                    onStartDance: actions.trainerStartDance, onSetStep: actions.trainerSetStep,
                    onSetStyle: actions.trainerSetStyle, onSetTrack: actions.trainerSetTrack,
                    onSetTag: actions.trainerSetTag, onSetInterval: actions.trainerSetInterval,
                    onSetElementTiming: actions.trainerSetElementTiming)
            }
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
    var elementsSave: (ElementEditorSnapshot) -> Void = { _ in }

    var musicSelect: (String) -> Void = { _ in }
    var musicSearch: (String) -> Void = { _ in }
    var musicBeginUpload: () -> Void = {}
    var musicEdit: (String) -> Void = { _ in }
    var musicDelete: (String) -> Void = { _ in }
    var musicImport: (OwnedMusicAudio) async -> Bool = { _ in false }
    var musicSave: (MusicEditDraft) -> Void = { _ in }
    var musicCloseEdit: () -> Void = {}
    var musicStop: (String) -> Void = { _ in }
    var musicSeek: (String, Float) -> Void = { _, _ in }

    var vaultSearch: (String) -> Void = { _ in }
    var vaultCreate: () -> Void = {}
    var vaultOpen: (String) -> Void = { _ in }
    var vaultDelete: (String) -> Void = { _ in }
    var vaultEditorBack: () -> Void = {}
    var vaultEditorTogglePlay: () -> Void = {}
    var vaultEditorSetTitle: (String) -> Void = { _ in }
    var vaultEditorSetStyle: (String) -> Void = { _ in }
    var vaultEditorSave: () -> Void = {}
    var vaultEditorElementChoices: () -> [SequenceElementChoice] = { [] }
    var vaultEditorAddElement: (String) -> Void = { _ in }
    var vaultTimelineActions: ProEditorTimelineActions? = nil

    var trainerSelectVoice: (Int) -> Void = { _ in }
    var trainerSpeak: () -> Void = {}
    var trainerStartDance: () -> Void = {}
    var trainerSetStep: (Int) -> Void = { _ in }
    var trainerSetStyle: (String) -> Void = { _ in }
    var trainerSetTrack: (String) -> Void = { _ in }
    var trainerSetTag: (String) -> Void = { _ in }
    var trainerSetInterval: (Float) -> Void = { _ in }
    var trainerSetElementTiming: (Bool) -> Void = { _ in }
    var trainerSessionTogglePause: () -> Void = {}
    var trainerSessionPause: () -> Void = {}
    var trainerSessionSeek: (Float, Bool) -> Void = { _, _ in }
    var trainerSessionVolume: (Float) -> Void = { _ in }
    var trainerSessionFinish: () -> Void = {}

    var exchangeOpenExport: () -> Void = {}
    var exchangeDismissExport: () -> Void = {}
    var exchangeSelectStyle: (String) -> Void = { _ in }
    var exchangeConfirmExport: () -> Void = {}
    var exchangeOpenImport: () -> Void = {}
    var exchangeDismissImport: () -> Void = {}
    var exchangeSetImportStyle: (String) -> Void = { _ in }
    var exchangeConfirmImport: () -> Void = {}
}
