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
            feature: Self.emptyFeature(for: kind)
        )
    }

    private var selectedTabIndex: Int = 0
    private var settingsSnapshot: SettingsShellSnapshot?
    private weak var mainRef: MainComponent?
    private weak var splashRef: SplashComponent?
    private weak var elementsRef: ElementsComponent?
    private weak var musicRef: MusicComponent?
    private weak var choreographyRef: ChoreographyComponent?
    private weak var trainerRef: TrainerComponent?
    private weak var exchangeRef: ExchangeComponent?
    private weak var settingsRef: SettingsComponent?

    func start() {
        guard root == nil else { return }

        MdImageCacheConfig.installSharedURLCacheIfNeeded()

        let lifecycle = LifecycleRegistry()
        lifecycle.onCreate()
        lifecycle.onStart()
        lifecycle.onResume()
        self.lifecycle = lifecycle

        let root = DefaultRootComponent(
            componentContext: DefaultComponentContext(lifecycle: lifecycle)
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
    func elementsCloseDetail() { elementsRef?.closeDetail() }

    // MARK: - Music

    func musicSelect(_ id: String) { musicRef?.selectTrack(id: id) }
    func musicStop(_ id: String) { musicRef?.stopTrack(id: id) }
    func musicSearch(_ q: String) { musicRef?.setSearchQuery(query: q) }
    func musicBeginUpload() { musicRef?.beginUpload() }
    func musicEdit(_ id: String) { musicRef?.editTrack(id: id) }
    func musicDelete(_ id: String) { musicRef?.deleteTrack(id: id) }
    func musicCloseEdit() { musicRef?.closeEdit() }

    // MARK: - Vault

    func vaultSearch(_ q: String) { choreographyRef?.setSearchQuery(query: q) }
    func vaultCreate() { choreographyRef?.createChoreography() }
    func vaultOpen(_ id: String) { choreographyRef?.openEditor(id: id) }
    func vaultDelete(_ id: String) { choreographyRef?.deleteItem(id: id) }

    // MARK: - Trainer

    func trainerSelectVoice(index: Int) { trainerRef?.selectVoiceByIndex(index: Int32(index)) }
    func trainerSpeak() { trainerRef?.requestSpeak() }
    func trainerStartDance() { trainerRef?.startDance() }
    func trainerSetStep(_ step: Int) { trainerRef?.setExpandedStep(step: Int32(step)) }
    func trainerSetStyle(_ s: String) { trainerRef?.setStyle(style: s) }
    func trainerSetTrack(_ t: String) { trainerRef?.setTrack(track: t) }
    func trainerSetTag(_ t: String) { trainerRef?.setElementTag(tag: t) }
    func trainerSetInterval(_ sec: Float) { trainerRef?.setInterval(seconds: sec) }

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
            tabCollectTasks.append(Task { @MainActor [weak self] in
                for await state in c.component.uiState {
                    self?.replaceTab(
                        index: index,
                        title: state.title,
                        feature: .choreography(Self.mapVault(state))
                    )
                }
            })
        case .trainer(let c):
            trainerRef = c.component
            tabCollectTasks.append(Task { @MainActor [weak self] in
                for await state in c.component.uiState {
                    self?.replaceTab(
                        index: index,
                        title: state.title,
                        feature: .trainer(Self.mapTrainer(state))
                    )
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
        trainerRef = nil
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
                ChoreographyFeatureSnapshot(title: kind.defaultTitle, searchQuery: "", items: [])
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
            detail: detail
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
        let tracks = state.filteredTracks.map { t in
            MusicTrackSnapshot(
                id: t.id,
                title: t.title,
                style: t.style,
                tag: t.tags.first ?? t.level,
                bpm: Int(t.bpm),
                durationLabel: t.durationLabel,
                weightLabel: t.weightLabel,
                isPlaying: t.isPlaying,
                progress: t.progress,
                positionLabel: t.positionLabel,
                basePlaybackSpeed: t.basePlaybackSpeed
            )
        }
        return MusicFeatureSnapshot(
            title: state.title,
            searchQuery: state.searchQuery,
            tracks: tracks,
            playingTrackId: state.playingTrack?.id,
            editingTrackId: state.editingTrackId
        )
    }

    private static func mapVault(_ state: ChoreographyUiState) -> ChoreographyFeatureSnapshot {
        let items = state.filteredItems.map { item in
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
        return ChoreographyFeatureSnapshot(
            title: state.title,
            searchQuery: state.searchQuery,
            items: items
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
            storylineTitles: state.storylineSteps.map { $0.title }
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
