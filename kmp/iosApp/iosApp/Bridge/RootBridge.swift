import Foundation
import SwiftUI
@preconcurrency import Shared

/// Bridge: owns Essenty lifecycle + Decompose Root/Main; maps to pure Swift snapshots.
/// Feature views subscribe only to published Swift state + Intent closures.
@MainActor
final class RootBridge: ObservableObject {
    @Published private(set) var route: AppShellRoute = .splash(
        SplashShellSnapshot(title: "Midnight Dancer", bootReady: false, prepareFailed: false)
    )

    private var root: RootComponent?
    private var lifecycle: LifecycleRegistry?
    private var stackCancellation: Cancellation?
    private var pagesCancellation: Cancellation?
    private var overlayCancellation: Cancellation?
    private var tabCollectTasks: [Task<Void, Never>] = []
    private var settingsCollectTask: Task<Void, Never>?
    private var splashCollectTask: Task<Void, Never>?

    private var tabSnapshots: [TabShellSnapshot] = ShellTabKind.allCases.map { kind in
        TabShellSnapshot(
            id: kind.rawValue,
            title: kind.defaultTitle,
            systemImage: kind.systemImage,
            feature: RootBridge.emptyFeature(for: kind)
        )
    }

    private var selectedTabIndex: Int = 0
    private var settingsSnapshot: SettingsShellSnapshot?
    private weak var mainRef: MainComponent?
    private weak var splashRef: SplashComponent?
    private weak var elementsRef: ElementsComponent?
    private weak var musicRef: MusicComponent?
    private weak var choreographyRef: ChoreographyComponent?
    private weak var sequenceEditorRef: SequenceEditorStubComponent?
    private var choreographyStackCancellation: Cancellation?
    private var editorCollectTask: Task<Void, Never>?
    private var choreographyTabIndex: Int = ShellTabKind.choreography.rawValue
    private var choreographyHubTitle: String = ShellTabKind.choreography.defaultTitle
    private var choreographyHubSearch: String = ""
    private var choreographyHubItems: [VaultItemSnapshot] = []
    private var choreographyEditorSnapshot: SequenceEditorSnapshot?
    private weak var trainerRef: TrainerComponent?
    private weak var trainerSessionRef: TrainerSessionStubComponent?
    private var trainerStackCancellation: Cancellation?
    private var trainerSessionTask: Task<Void, Never>?
    private var trainerSessionSnapshot: TrainerSessionSnapshot?
    private var trainerSessionID = UUID().uuidString
    private var trainerTabIndex = ShellTabKind.trainer.rawValue
    private weak var exchangeRef: ExchangeComponent?
    private weak var settingsRef: SettingsComponent?

    @Published private(set) var startupError: String?
    private var starting = false

    func start() {
        guard root == nil, !starting else { return }
        starting = true
        startupError = nil
        Task { [weak self] in
            guard let self else { return }
            defer { self.starting = false }
            do {
                let result = try await IosLibraryBootstrap.shared.prepare()
                guard result.ready else {
                    self.startupError = result.error ?? "Не удалось открыть библиотеку."
                    return
                }
                self.startRoot()
            } catch {
                self.startupError = "Открытие библиотеки прервано. Повторите попытку."
            }
        }
    }

    private func startRoot() {

        MdImageCacheConfig.installSharedURLCacheIfNeeded()

        let lifecycle = LifecycleRegistry()
        lifecycle.onCreate()
        lifecycle.onStart()
        lifecycle.onResume()
        self.lifecycle = lifecycle

        let root = IosLibraryBootstrap.shared.createRoot(
            componentContext: DefaultComponentContext(lifecycle: lifecycle),
            startAtMain: false
        )
        self.root = root

        stackCancellation = root.stack.subscribe { [weak self] stack in
            Task { @MainActor in
                self?.onRootStackChanged(stack)
            }
        }
    }

    // MARK: - Chrome actions

    func selectTab(index: Int) {
        guard mainRef != nil, ShellTabKind(rawValue: index) != nil else { return }
        mainRef?.onTabSelected(index: Int32(index))
    }

    func openSettings() { mainRef?.openSettings() }

    func closeSettingsOrBack() {
        if mainRef?.onBack() == true { return }
        _ = root?.onBack()
    }

    func splashContinue() { splashRef?.onFinished() }

    // MARK: - Elements

    func elementsStylePrevious() { elementsRef?.stylePrevious() }
    func elementsStyleNext() { elementsRef?.styleNext() }
    func elementsAdd() { elementsRef?.addNewElement() }
    func elementsSearch(_ q: String) { elementsRef?.setSearchQuery(query: q) }
    func elementsFilter(_ cat: String?) { elementsRef?.setFilterCategory(category: cat) }
    func elementsPlay(_ id: String) { elementsRef?.playCard(cardId: id) }
    func elementsOpenDetail(_ id: String) { elementsRef?.openDetail(cardId: id) }
    func elementsCloseDetail() { elementsRef?.closeEditor() }

    func elementsSave(_ draft: ElementEditorSnapshot) {
        guard let component = elementsRef else { return }
        component.updateEditorDraft(
            cardId: draft.cardId,
            title: draft.title,
            styleId: draft.styleId,
            tag: draft.tag,
            videoUri: draft.videoUri,
            description: draft.description,
            masteryPercent: Int32(draft.masteryPercent),
            durationMs: draft.durationMs
        )
        component.saveElement()
    }

    // MARK: - Music

    func musicSelect(_ id: String) { musicRef?.selectTrack(id: id) }
    func musicStop(_ id: String) { musicRef?.stopTrack(id: id) }
    func musicSearch(_ q: String) { musicRef?.setSearchQuery(query: q) }
    func musicBeginUpload() { musicRef?.beginUpload() }
    func musicEdit(_ id: String) { musicRef?.editTrack(id: id) }
    func musicDelete(_ id: String) { musicRef?.deleteTrack(id: id) }
    func musicCloseEdit() { musicRef?.closeEdit() }

    func musicImport(_ audio: OwnedMusicAudio) async -> Bool {
        guard let component = musicRef else { return false }
        // A previous attempt may have committed before its observer was replaced.
        for await current in component.uiState {
            if current.tracks.contains(where: { $0.mediaUri == audio.reference }) { return true }
            if current.saving { return false }
            break
        }
        IosMusicActions.shared.addPicked(component: component, title: audio.title, reference: audio.reference, durationMs: audio.durationMs)
        for await state in component.uiState {
            if state.saving { continue }
            return state.tracks.contains(where: { $0.mediaUri == audio.reference })
        }
        return false
    }

    func musicSave(_ draft: MusicEditDraft) {
        guard let component = musicRef else { return }
        IosMusicActions.shared.save(component: component, id: draft.id, title: draft.title,
            style: draft.style, tags: draft.tags, speed: draft.speed)
    }

    func musicSeek(_ id: String, _ progress: Float) {
        guard let component = musicRef else { return }
        IosMusicActions.shared.seek(component: component, id: id, progress: progress)
    }


    // MARK: - Vault

    func vaultSearch(_ q: String) { choreographyRef?.setSearchQuery(query: q) }
    func vaultCreate() { choreographyRef?.createChoreography() }
    func vaultOpen(_ id: String) { choreographyRef?.openEditor(id: id) }
    func vaultDelete(_ id: String) { choreographyRef?.deleteItem(id: id) }
    func vaultEditorBack() { _ = choreographyRef?.onBack() }
    func vaultEditorTogglePlay() { sequenceEditorRef?.togglePlay() }
    func vaultEditorSetTitle(_ title: String) { sequenceEditorRef?.setSequenceTitle(title: title) }
    func vaultEditorSetStyle(_ style: String) { sequenceEditorRef?.setSequenceStyle(style: style) }
    func vaultEditorSave() { sequenceEditorRef?.saveSequence() }

    func vaultEditorElementChoices() -> [SequenceElementChoice] {
        guard let editor = sequenceEditorRef else { return [] }
        return editor.elementChoices().map {
            SequenceElementChoice(id: $0.id, title: $0.title)
        }
    }

    func vaultEditorAddElement(_ id: String) {
        sequenceEditorRef?.addVideoElement(id: id)
    }

    func vaultTimelineActions() -> ProEditorTimelineActions {
        let actions = IosChoreographyActions.shared
        return ProEditorTimelineActions(
            seek: { [weak self] fraction in
                self?.sequenceEditorRef?.seekToFraction(fraction: Float(fraction))
            },
            select: { [weak self] lane, index in
                guard let editor = self?.sequenceEditorRef else { return }
                switch lane {
                case .elements: actions.selectElement(editor: editor, index: Int32(index))
                case .music: actions.selectMusic(editor: editor, index: Int32(index))
                }
            },
            split: { [weak self] in
                if let editor = self?.sequenceEditorRef { actions.split(editor: editor) }
            },
            delete: { [weak self] in
                if let editor = self?.sequenceEditorRef { actions.delete(editor: editor) }
            },
            reorder: { [weak self] lane, from, to in
                guard let editor = self?.sequenceEditorRef else { return }
                switch lane {
                case .elements: actions.reorderElements(editor: editor, from: Int32(from), to: Int32(to))
                case .music: actions.reorderMusic(editor: editor, from: Int32(from), to: Int32(to))
                }
            },
            undo: { [weak self] in
                if let editor = self?.sequenceEditorRef { actions.undo(editor: editor) }
            },
            redo: { [weak self] in
                if let editor = self?.sequenceEditorRef { actions.redo(editor: editor) }
            },
            musicChoices: { [weak self] in
                guard let editor = self?.sequenceEditorRef else { return [] }
                return actions.musicChoices(editor: editor).map {
                    SequenceElementChoice(id: $0.id, title: $0.title)
                }
            },
            addMusic: { [weak self] id in
                if let editor = self?.sequenceEditorRef { actions.addMusic(editor: editor, id: id) }
            }
        )
    }

    // MARK: - Trainer

    func trainerSelectVoice(index: Int) { trainerRef?.selectVoiceByIndex(index: Int32(index)) }
    func trainerSpeak() { trainerRef?.requestSpeak() }
    func trainerStartDance() { trainerRef?.startDance() }
    func trainerSetStep(_ step: Int) { trainerRef?.setExpandedStep(step: Int32(step)) }
    func trainerSetStyle(_ s: String) { trainerRef?.setStyle(style: s) }
    func trainerSetTrack(_ t: String) { trainerRef?.setTrack(track: t) }
    func trainerSetTag(_ t: String) { trainerRef?.setElementTag(tag: t) }
    func trainerSetInterval(_ sec: Float) { trainerRef?.setInterval(seconds: sec) }
    func trainerSetElementTiming(_ enabled: Bool) { trainerRef?.setUseElementTiming(enabled: enabled) }
    func trainerSessionTogglePause() {
        guard let session = trainerSessionRef else { return }
        IosTrainerActions.shared.togglePause(component: session)
    }
    func trainerSessionPause() {
        if trainerSessionSnapshot?.paused == false { trainerSessionTogglePause() }
    }
    func trainerSessionSeek(_ fraction: Float, _ fromUser: Bool) {
        guard let session = trainerSessionRef else { return }
        IosTrainerActions.shared.seek(component: session, fraction: fraction, fromUser: fromUser)
    }
    func trainerSessionVolume(_ volume: Float) {
        guard let session = trainerSessionRef else { return }
        IosTrainerActions.shared.trackVolume(component: session, volume: volume)
    }
    func trainerSessionFinish() { _ = trainerRef?.onBack() }

    private func bindTrainerStack(_ component: TrainerComponent) {
        trainerStackCancellation?.cancel()
        onTrainerStackChanged(component.stack.value)
        trainerStackCancellation = component.stack.subscribe { [weak self, weak component] stack in
            Task { @MainActor [weak component] in
                guard let self, let component, self.trainerRef === component else { return }
                self.onTrainerStackChanged(stack)
            }
        }
    }

    private func onTrainerStackChanged(_ stack: ChildStack<AnyObject, TrainerComponentChild>) {
        trainerSessionTask?.cancel()
        trainerSessionTask = nil
        switch onEnum(of: stack.active.instance) {
        case .hub:
            trainerSessionID = UUID().uuidString
            trainerSessionRef = nil
            trainerSessionSnapshot = nil
            publishTrainerSession()
        case .session(let wrapped):
            let component = wrapped.component
            trainerSessionRef = component
            trainerSessionID = UUID().uuidString
            let sessionID = trainerSessionID
            trainerSessionTask = Task { @MainActor [weak self] in
                for await state in component.uiState {
                    guard let self, !Task.isCancelled, self.trainerSessionID == sessionID else { return }
                    self.trainerSessionSnapshot = TrainerSessionSnapshot(id: sessionID,
                        trackTitle: state.trackLabel, mediaUri: state.trackMediaUri,
                        durationMs: state.trackDurationMs, paused: state.isPaused,
                        progress: state.playheadFraction, moveTitle: state.moveTitle,
                        cueVideo: state.currentElementMediaUri, phaseIndex: Int(state.phaseIndex),
                        phaseTotal: Int(state.phaseTotal), trackVolume: state.trackVol,
                        playlistCount: state.musicPlaylist.count)
                    self.publishTrainerSession()
                }
            }
        }
    }

    private func publishTrainerSession() {
        guard case .trainer(var snapshot) = tabSnapshots[trainerTabIndex].feature else { return }
        snapshot.session = trainerSessionSnapshot
        replaceTab(index: trainerTabIndex, title: snapshot.title, feature: .trainer(snapshot))
    }


    // MARK: - Exchange

    func exchangeOpenExport() { exchangeRef?.openExport() }
    func exchangeDismissExport() { exchangeRef?.dismissExport() }
    func exchangeSelectStyle(_ id: String) { exchangeRef?.selectExportStyle(styleId: id) }
    func exchangeConfirmExport() { exchangeRef?.confirmExport() }
    func exchangeOpenImport() { exchangeRef?.openImport() }
    func exchangeDismissImport() { exchangeRef?.dismissImport() }
    func exchangeSetImportStyle(_ id: String) { exchangeRef?.setImportTargetStyle(localStyleId: id) }
    func exchangeConfirmImport() { exchangeRef?.confirmImport() }

    // MARK: - Settings

    func settingsSetLanguage(_ code: String) { settingsRef?.setLanguage(code: code) }
    func settingsSetReminder(_ on: Bool) { settingsRef?.setReminderEnabled(enabled: on) }
    func settingsSetStyleDraft(_ t: String) { settingsRef?.setStyleDraft(text: t) }
    func settingsAddStyle() { settingsRef?.addStyle() }
    func settingsRemoveStyle(_ name: String) { settingsRef?.removeStyle(name: name) }
    func settingsSetCategoryDraft(_ t: String) { settingsRef?.setCategoryDraft(text: t) }
    func settingsAddCategory() { settingsRef?.addCategory() }
    func settingsRemoveCategory(_ name: String) { settingsRef?.removeCategory(name: name) }
    func settingsSetLowDiskGib(_ gib: Int) { settingsRef?.setLowDiskWarnGib(gib: Int32(gib)) }
    func settingsSetLowDiskEnabled(_ on: Bool) { settingsRef?.setLowDiskWarnEnabled(enabled: on) }

    // MARK: - Root

    private func onRootStackChanged(_ stack: ChildStack<AnyObject, RootComponentChild>) {
        let instance = stack.active.instance
        switch onEnum(of: instance) {
        case .splash(let wrapped):
            clearMainSubscriptions()
            mainRef = nil
            bindSplash(wrapped.component)
        case .main(let wrapped):
            splashCollectTask?.cancel()
            splashRef = nil
            bindMain(wrapped.component)
        }
    }

    private func bindSplash(_ splash: SplashComponent) {
        splashRef = splash
        splashCollectTask?.cancel()
        splashCollectTask = Task { @MainActor [weak self] in
            for await state in splash.uiState {
                self?.route = .splash(
                    SplashShellSnapshot(
                        title: "Midnight Dancer",
                        bootReady: state.bootReady,
                        prepareFailed: state.prepareFailed
                    )
                )
            }
        }
    }

    private func bindMain(_ main: MainComponent) {
        if mainRef === main {
            publishMain()
            return
        }
        clearMainSubscriptions()
        mainRef = main

        let pages = main.pages.value
        selectedTabIndex = Int(pages.selectedIndex)
        let items = pages.items
        for index in 0 ..< items.count {
            let child = items[index]
            if let instance = child.instance {
                attachTabCollector(index: index, child: instance)
            }
        }
        applyOverlay(main.overlayStack.value)

        pagesCancellation = main.pages.subscribe { [weak self] pages in
            Task { @MainActor in
                self?.selectedTabIndex = Int(pages.selectedIndex)
                self?.publishMain()
            }
        }
        overlayCancellation = main.overlayStack.subscribe { [weak self] overlay in
            Task { @MainActor in
                self?.applyOverlay(overlay)
            }
        }
        publishMain()
    }

    private func applyOverlay(_ overlay: ChildStack<AnyObject, MainComponentOverlayChild>) {
        let active = onEnum(of: overlay.active.instance)
        if case .settings(let wrapped) = active {
            attachSettingsCollector(wrapped.component)
            return
        }
        settingsCollectTask?.cancel()
        settingsCollectTask = nil
        settingsRef = nil
        settingsSnapshot = nil
        publishMain()
    }

    private func attachTabCollector(index: Int, child: MainComponentTabChild) {
        switch onEnum(of: child) {
        case .elements(let c):
            elementsRef = c.component
            tabCollectTasks.append(Task { @MainActor [weak self] in
                for await state in c.component.uiState {
                    self?.replaceTab(
                        index: index,
                        title: state.title,
                        feature: .elements(Self.mapElements(state))
                    )
                }
            })
        case .music(let c):
            musicRef = c.component
            tabCollectTasks.append(Task { @MainActor [weak self] in
                for await state in c.component.uiState {
                    self?.replaceTab(
                        index: index,
                        title: state.title,
                        feature: .music(Self.mapMusic(state))
                    )
                }
            })
        case .choreography(let c):
            choreographyRef = c.component
            choreographyTabIndex = index
            bindChoreographyStack(c.component)
            tabCollectTasks.append(Task { @MainActor [weak self] in
                for await state in c.component.uiState {
                    self?.choreographyHubTitle = state.title
                    self?.choreographyHubSearch = state.searchQuery
                    self?.choreographyHubItems = Self.mapVaultItems(state)
                    self?.publishChoreographyTab()
                }
            })
        case .trainer(let c):
            trainerRef = c.component
            trainerTabIndex = index
            bindTrainerStack(c.component)
            tabCollectTasks.append(Task { @MainActor [weak self] in
                for await state in c.component.uiState {
                    guard let self else { return }
                    var snapshot = Self.mapTrainer(state)
                    snapshot.session = self.trainerSessionSnapshot
                    self.replaceTab(index: index, title: state.title, feature: .trainer(snapshot))
                }
            })
        case .exchange(let c):
            exchangeRef = c.component
            tabCollectTasks.append(Task { @MainActor [weak self] in
                for await state in c.component.uiState {
                    self?.replaceTab(
                        index: index,
                        title: state.title,
                        feature: .exchange(Self.mapExchange(state))
                    )
                }
            })
        }
    }

    private func attachSettingsCollector(_ component: SettingsComponent) {
        settingsRef = component
        settingsCollectTask?.cancel()
        settingsCollectTask = Task { @MainActor [weak self] in
            for await state in component.uiState {
                self?.settingsSnapshot = Self.mapSettings(state)
                self?.publishMain()
            }
        }
    }

    private func replaceTab(index: Int, title: String, feature: TabFeatureSnapshot) {
        guard tabSnapshots.indices.contains(index) else { return }
        let kind = ShellTabKind(rawValue: index) ?? .elements
        tabSnapshots[index] = TabShellSnapshot(
            id: index,
            title: title,
            systemImage: kind.systemImage,
            feature: feature
        )
        publishMain()
    }

    private func publishMain() {
        guard mainRef != nil else { return }
        route = .main(
            MainChromeSnapshot(
                selectedTabIndex: selectedTabIndex,
                tabs: tabSnapshots,
                settings: settingsSnapshot
            )
        )
    }

    private func clearMainSubscriptions() {
        pagesCancellation?.cancel()
        pagesCancellation = nil
        overlayCancellation?.cancel()
        overlayCancellation = nil
        tabCollectTasks.forEach { $0.cancel() }
        tabCollectTasks.removeAll()
        settingsCollectTask?.cancel()
        settingsCollectTask = nil
        settingsSnapshot = nil
        elementsRef = nil
        musicRef = nil
        choreographyRef = nil
        sequenceEditorRef = nil
        choreographyStackCancellation?.cancel()
        choreographyStackCancellation = nil
        editorCollectTask?.cancel()
        editorCollectTask = nil
        choreographyEditorSnapshot = nil
        trainerRef = nil
        trainerSessionRef = nil
        trainerStackCancellation?.cancel()
        trainerStackCancellation = nil
        trainerSessionTask?.cancel()
        trainerSessionTask = nil
        trainerSessionSnapshot = nil
        exchangeRef = nil
        settingsRef = nil
    }

    deinit {
        stackCancellation?.cancel()
        pagesCancellation?.cancel()
        overlayCancellation?.cancel()
        tabCollectTasks.forEach { $0.cancel() }
        settingsCollectTask?.cancel()
        splashCollectTask?.cancel()
        trainerStackCancellation?.cancel()
        trainerSessionTask?.cancel()
    }

    // MARK: - Mappers

    private static func emptyFeature(for kind: ShellTabKind) -> TabFeatureSnapshot {
        switch kind {
        case .elements:
            return .elements(
                ElementsFeatureSnapshot(
                    title: kind.defaultTitle, styleName: "—", styleIndex: 0, styleCount: 0,
                    searchQuery: "", filterCategory: nil, categories: [], cards: [],
                    playingCardId: nil, detail: nil
                )
            )
        case .music:
            return .music(
                MusicFeatureSnapshot(
                    title: kind.defaultTitle, searchQuery: "", tracks: [],
                    playingTrackId: nil, editingTrackId: nil
                )
            )
        case .choreography:
            return .choreography(
                ChoreographyFeatureSnapshot(
                    title: kind.defaultTitle,
                    searchQuery: "",
                    items: [],
                    editor: nil
                )
            )
        case .trainer:
            return .trainer(
                TrainerFeatureSnapshot(
                    title: kind.defaultTitle, modeLabel: "Freestyle", expandedStep: 1,
                    styleName: "", trackTitle: "", elementTag: "", intervalSec: 0,
                    readyPercent: 0, speakLabel: "Speak", selectedVoiceIndex: 0,
                    storylineTitles: []
                )
            )
        case .exchange:
            return .exchange(
                ExchangeFeatureSnapshot(
                    title: kind.defaultTitle, lastActionLabel: nil, busy: false,
                    importEnabled: false, freeLabel: "—", requiredLabel: "—",
                    showExportDialog: false, showImportParams: false,
                    exportStyleId: "", exportStyleName: "",
                    exportLimitsText:
                        "Один стиль. Не больше пяти элементов, двух треков и одной хореографии. "
                        + "Жёсткий лимит размера пакета — пятьсот мегабайт.",
                    exportStyles: [], exportSelectedMoveCount: 0,
                    exportSelectedSongCount: 0, exportSelectedChoreoCount: 0,
                    importLocalStyles: [], importTargetStyleId: "",
                    lastImportName: ""
                )
            )
        }
    }

    private static func mapElements(_ state: ElementsUiState) -> ElementsFeatureSnapshot {
        let cards = state.cards.map { mapElementCard($0) }
        let detail = state.detailCard.map { mapElementCard($0) }
        return ElementsFeatureSnapshot(
            title: state.title,
            styleName: state.selectedStyle.name,
            styleIndex: Int(state.selectedStyleIndex),
            styleCount: state.styles.count,
            searchQuery: state.searchQuery,
            filterCategory: state.filterCategory,
            categories: Array(state.categories),
            cards: cards,
            playingCardId: state.playingCardId,
            detail: detail,
            editor: state.editor.map { draft in
                ElementEditorSnapshot(
                    cardId: draft.cardId,
                    title: draft.title,
                    styleId: draft.styleId,
                    styleName: state.styles.first(where: { $0.id == draft.styleId })?.name ?? state.selectedStyle.name,
                    tag: draft.tag,
                    videoUri: draft.videoUri,
                    description: draft.description,
                    masteryPercent: Int(draft.masteryPercent),
                    durationMs: draft.durationMs
                )
            },
            saving: state.saving,
            saveError: state.saveError
        )
    }

    private static func mapElementCard(_ card: ElementCard) -> ElementCardSnapshot {
        let kindLabel: String
        switch onEnum(of: card.kind) {
        case .videoCover: kindLabel = "video"
        case .waveform: kindLabel = "wave"
        case .cueOverlay: kindLabel = "cue"
        }
        return ElementCardSnapshot(
            id: card.id,
            title: card.title,
            tag: card.tags.first ?? card.levelLabel,
            durationLabel: card.durationLabel,
            masteryPercent: Int(card.masteryPercent),
            coverUrl: card.coverUrl,
            description: card.description,
            kindLabel: kindLabel
        )
    }

    private static func mapMusic(_ state: MusicUiState) -> MusicFeatureSnapshot {
        func track(_ t: MusicTrack) -> MusicTrackSnapshot {
            MusicTrackSnapshot(id: t.id, title: t.title,
                style: state.displayStyle(value: t.style), tag: t.tags.first ?? t.level,
                bpm: Int(t.bpm), durationLabel: t.durationLabel, weightLabel: t.weightLabel,
                isPlaying: t.isPlaying, progress: t.progress, positionLabel: t.positionLabel,
                basePlaybackSpeed: t.basePlaybackSpeed, mediaUri: t.mediaUri, tags: t.tags)
        }
        return MusicFeatureSnapshot(title: state.title, searchQuery: state.searchQuery,
            tracks: state.filteredTracks.map(track), playingTrackId: state.playingTrack?.id,
            editingTrackId: state.editingTrackId,
            editingTrack: state.editingTrack.map(track), selectedTrack: state.selectedTrack.map(track),
            styles: state.styleCatalog, tags: state.tagCatalog,
            saving: state.saving, error: state.operationError)
    }

    private func bindChoreographyStack(_ component: ChoreographyComponent) {
        choreographyStackCancellation?.cancel()
        editorCollectTask?.cancel()
        editorCollectTask = nil
        sequenceEditorRef = nil
        choreographyEditorSnapshot = nil
        onChoreographyStackChanged(component.stack.value)
        choreographyStackCancellation = component.stack.subscribe { [weak self] stack in
            Task { @MainActor in
                self?.onChoreographyStackChanged(stack)
            }
        }
    }

    private func onChoreographyStackChanged(_ stack: ChildStack<AnyObject, ChoreographyComponentChild>) {
        let instance = stack.active.instance
        switch onEnum(of: instance) {
        case .hub:
            editorCollectTask?.cancel()
            editorCollectTask = nil
            sequenceEditorRef = nil
            choreographyEditorSnapshot = nil
            publishChoreographyTab()
        case .sequenceEditor(let wrapped):
            let editor = wrapped.component
            sequenceEditorRef = editor
            editorCollectTask?.cancel()
            editorCollectTask = Task { @MainActor [weak self] in
                for await state in editor.uiState {
                    self?.choreographyEditorSnapshot = Self.mapSequenceEditor(state)
                    self?.publishChoreographyTab()
                }
            }
        }
    }

    private func publishChoreographyTab() {
        replaceTab(
            index: choreographyTabIndex,
            title: choreographyHubTitle,
            feature: .choreography(
                ChoreographyFeatureSnapshot(
                    title: choreographyHubTitle,
                    searchQuery: choreographyHubSearch,
                    items: choreographyHubItems,
                    editor: choreographyEditorSnapshot
                )
            )
        )
    }

    private static func mapVaultItems(_ state: ChoreographyUiState) -> [VaultItemSnapshot] {
        state.filteredItems.map { item in
            let status: String = {
                switch item.status {
                case .draft: return "Draft"
                case .ready: return "Ready"
                default: return ""
                }
            }()
            return VaultItemSnapshot(
                id: item.id,
                title: item.title,
                style: item.style,
                tag: item.tags.first ?? "",
                bpm: Int(item.bpm),
                durationLabel: item.durationLabel,
                trackName: item.trackName,
                statusLabel: status
            )
        }
    }

    private static func mapEditorClip(_ clip: EditorClip) -> EditorClipSnapshot {
        EditorClipSnapshot(
            label: clip.label,
            durationMs: clip.durationMs,
            mediaUri: clip.mediaUri,
            speed: clip.speed,
            sourceStartMs: clip.sourceStartMs
        )
    }

    private static func mapSequenceEditor(_ state: SequenceEditorUiState) -> SequenceEditorSnapshot {
        let clips = state.elementClips.map(mapEditorClip)
        let elementsDuration = state.elementsDurationMs
        let playheadMs = state.playheadMs
        let pastElements = !clips.isEmpty && playheadMs >= elementsDuration
        let playheadInElements: Int64 = pastElements
            ? elementsDuration
            : min(playheadMs, elementsDuration)
        return SequenceEditorSnapshot(
            title: state.title,
            isPlaying: state.isPlaying,
            previewElementLabel: state.previewElementLabel,
            elementClips: clips,
            elementsDurationMs: elementsDuration,
            playheadInElementsMs: playheadInElements,
            previewIsEmpty: clips.isEmpty || pastElements,
            styleName: state.styleNamesById[state.style] ?? state.style,
            availableStyles: Array(state.availableStyles),
            saveCount: Int(state.saveCount),
            saveError: state.saveError,
            timeline: SequenceTimelineSnapshot(
                elementClips: state.elementTimelineClips.map(mapEditorClip),
                musicClips: state.musicTimelineClips.map(mapEditorClip),
                musicSourceClips: state.musicClips.map(mapEditorClip),
                durationMs: state.durationMs,
                playheadFraction: Double(state.playheadFraction),
                timecode: state.timecode,
                durationTimecode: state.durationTimecode,
                selectedElementIndex: Int(state.selectedElementIndex),
                selectedMusicIndex: Int(state.selectedMusicIndex),
                canUndo: state.canUndo,
                canRedo: state.canRedo,
                songPlaybackSpeed: state.songPlaybackSpeed,
                musicVolume: state.musicVolume
            )
        )
    }

    private static func mapTrainer(_ state: TrainerUiState) -> TrainerFeatureSnapshot {
        let modeLabel: String = {
            switch state.mode {
            case .freestyle: return "Свободная"
            case .choreography: return "Хореография"
            default: return "Свободная"
            }
        }()
        return TrainerFeatureSnapshot(
            title: state.title,
            modeLabel: modeLabel,
            expandedStep: Int(state.expandedStep),
            styleName: state.styleName,
            trackTitle: state.trackTitle,
            elementTag: state.elementTagLabel.isEmpty ? state.levelLabel : state.elementTagLabel,
            intervalSec: state.intervalSec,
            readyPercent: Int(state.readyPercent),
            speakLabel: state.speakLabel,
            selectedVoiceIndex: Int(state.selectedVoiceIndex),
            storylineTitles: state.storylineSteps.map { $0.title },
            styleOptions: state.styleOptions, trackOptions: state.trackOptions, tagOptions: state.tagOptions,
            useElementTiming: state.useElementTiming
        )
    }

    private static func mapExchange(_ state: ExchangeUiState) -> ExchangeFeatureSnapshot {
        ExchangeFeatureSnapshot(
            title: state.title,
            lastActionLabel: state.lastActionLabel,
            busy: state.busy,
            importEnabled: state.importEnabled,
            freeLabel: state.freeLabel,
            requiredLabel: state.requiredLabel,
            showExportDialog: state.showExportDialog,
            showImportParams: state.showImportParams,
            exportStyleId: state.exportStyleId,
            exportStyleName: state.exportStyleName,
            exportLimitsText: state.exportLimitsText,
            exportStyles: state.exportCatalog.styles.map {
                StyleRefSnapshot(id: $0.id, name: $0.name)
            },
            exportSelectedMoveCount: Array(state.exportSelectedMoveIds).count,
            exportSelectedSongCount: Array(state.exportSelectedSongIds).count,
            exportSelectedChoreoCount: Array(state.exportSelectedChoreoIds).count,
            importLocalStyles: state.importLocalStyles.map {
                StyleRefSnapshot(id: $0.id, name: $0.name)
            },
            importTargetStyleId: state.importTargetStyleId,
            lastImportName: state.lastImportName
        )
    }

    private static func mapSettings(_ state: SettingsUiState) -> SettingsShellSnapshot {
        SettingsShellSnapshot(
            title: state.title,
            languageCode: state.languageCode,
            reminderEnabled: state.reminderEnabled,
            reminderHour: Int(state.reminderHour),
            reminderMinute: Int(state.reminderMinute),
            prefsLoaded: state.prefsLoaded,
            danceStyles: Array(state.danceStyles),
            newStyleDraft: state.newStyleDraft,
            elementCategories: Array(state.elementCategories),
            newCategoryDraft: state.newCategoryDraft,
            lowDiskWarnGib: Int(state.lowDiskWarnGib),
            lowDiskWarnEnabled: state.lowDiskWarnEnabled,
            lowDiskPresetsGib: [1, 2, 5, 10]
        )
    }
}
