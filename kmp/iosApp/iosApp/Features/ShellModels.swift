import Foundation

/// Pure Swift navigation snapshot — feature views never import Shared.
enum AppShellRoute: Equatable {
    case splash(SplashShellSnapshot)
    case main(MainChromeSnapshot)
}

struct SplashShellSnapshot: Equatable {
    var title: String
    var bootReady: Bool
    var prepareFailed: Bool
}

struct MainChromeSnapshot: Equatable {
    var selectedTabIndex: Int
    var tabs: [TabShellSnapshot]
    var settings: SettingsShellSnapshot?
}

struct TabShellSnapshot: Equatable, Identifiable {
    var id: Int
    var title: String
    var systemImage: String
    var feature: TabFeatureSnapshot
}

enum TabFeatureSnapshot: Equatable {
    case elements(ElementsFeatureSnapshot)
    case music(MusicFeatureSnapshot)
    case choreography(ChoreographyFeatureSnapshot)
    case trainer(TrainerFeatureSnapshot)
    case exchange(ExchangeFeatureSnapshot)
}

struct ElementCardSnapshot: Equatable, Identifiable {
    var id: String
    var title: String
    var tag: String
    var durationLabel: String
    var masteryPercent: Int
    var coverUrl: String?
    var description: String
    var kindLabel: String
}

struct ElementEditorSnapshot: Equatable {
    var cardId: String?
    var title: String
    var styleId: String
    var styleName: String
    var tag: String?
    var videoUri: String?
    var description: String
    var masteryPercent: Int
    var durationMs: Int64
}

struct ElementsFeatureSnapshot: Equatable {
    var title: String
    var styleName: String
    var styleIndex: Int
    var styleCount: Int
    var searchQuery: String
    var filterCategory: String?
    var categories: [String]
    var cards: [ElementCardSnapshot]
    var playingCardId: String?
    var detail: ElementCardSnapshot?
    var editor: ElementEditorSnapshot? = nil
    var saving = false
    var saveError: String? = nil
}

struct MusicTrackSnapshot: Equatable, Identifiable {
    var id: String
    var title: String
    var style: String
    var tag: String
    var bpm: Int
    var durationLabel: String
    var weightLabel: String
    var isPlaying: Bool
    var progress: Float
    var positionLabel: String
    var basePlaybackSpeed: Double
    var mediaUri: String? = nil
    var tags: [String] = []
}

struct MusicFeatureSnapshot: Equatable {
    var title: String
    var searchQuery: String
    var tracks: [MusicTrackSnapshot]
    var playingTrackId: String?
    var editingTrackId: String?
    var editingTrack: MusicTrackSnapshot? = nil
    var selectedTrack: MusicTrackSnapshot? = nil
    var styles: [String] = []
    var tags: [String] = []
    var saving: Bool = false
    var error: String? = nil
}

struct MusicEditDraft {
    var id: String
    var title: String
    var style: String
    var tags: [String]
    var speed: Double
}

struct VaultItemSnapshot: Equatable, Identifiable {
    var id: String
    var title: String
    var style: String
    var tag: String
    var bpm: Int
    var durationLabel: String
    var trackName: String
    var statusLabel: String
}

struct EditorClipSnapshot: Equatable, Identifiable {
    var label: String
    var durationMs: Int64
    var mediaUri: String?
    var speed: Double
    var sourceStartMs: Int64

    var id: String {
        "\(mediaUri ?? label)|\(sourceStartMs)|\(durationMs)|\(speed)"
    }
}

struct SequenceElementChoice: Equatable, Identifiable {
    var id: String
    var title: String
}

/// Pro Editor state mapped from [SequenceEditorUiState] — pure Swift for Features/.
struct SequenceEditorSnapshot: Equatable {
    var title: String
    var isPlaying: Bool
    var previewElementLabel: String?
    var elementClips: [EditorClipSnapshot]
    var elementsDurationMs: Int64
    var playheadInElementsMs: Int64
    /// C-231: playhead past last element → empty preview stage.
    var previewIsEmpty: Bool
    var styleName: String
    var availableStyles: [String]
    var saveCount: Int
    var saveError: String?
    var timeline: SequenceTimelineSnapshot
}

struct SequenceTimelineSnapshot: Equatable {
    var elementClips: [EditorClipSnapshot]
    var musicClips: [EditorClipSnapshot]
    var musicSourceClips: [EditorClipSnapshot]
    var durationMs: Int64
    var playheadFraction: Double
    var timecode: String
    var durationTimecode: String
    var selectedElementIndex: Int
    var selectedMusicIndex: Int
    var canUndo: Bool
    var canRedo: Bool
    var songPlaybackSpeed: Double
    var musicVolume: Float

    func withActions(_ actions: ProEditorTimelineActions?) -> ProEditorTimeline? {
        guard let actions else { return nil }
        return ProEditorTimeline(
            elementClips: elementClips, musicClips: musicClips,
            durationMs: durationMs, playheadFraction: playheadFraction,
            timecode: timecode, durationTimecode: durationTimecode,
            selectedElementIndex: selectedElementIndex, selectedMusicIndex: selectedMusicIndex,
            canUndo: canUndo, canRedo: canRedo, actions: actions,
            musicSourceClips: musicSourceClips, songPlaybackSpeed: songPlaybackSpeed, musicVolume: musicVolume
        )
    }
}

struct ChoreographyFeatureSnapshot: Equatable {
    var title: String
    var searchQuery: String
    var items: [VaultItemSnapshot]
    /// Non-nil when nested stack shows Pro Editor (mirrors Android TabScreens).
    var editor: SequenceEditorSnapshot?
}

struct TrainerFeatureSnapshot: Equatable {
    var title: String
    var modeLabel: String
    var expandedStep: Int
    var styleName: String
    var trackTitle: String
    var elementTag: String
    var intervalSec: Float
    var readyPercent: Int
    var speakLabel: String
    var selectedVoiceIndex: Int
    var storylineTitles: [String]
    var styleOptions: [String] = []
    var trackOptions: [String] = []
    var tagOptions: [String] = []
    var useElementTiming: Bool = true
    var session: TrainerSessionSnapshot? = nil
}

struct TrainerSessionSnapshot: Equatable, Identifiable {
    var id: String
    var trackTitle: String
    var mediaUri: String?
    var durationMs: Int64
    var paused: Bool
    var progress: Float
    var moveTitle: String
    var cueVideo: String?
    var phaseIndex: Int
    var phaseTotal: Int
    var trackVolume: Float
    var playlistCount: Int
}

struct StyleRefSnapshot: Equatable, Identifiable {
    var id: String
    var name: String
}

struct ExchangeFeatureSnapshot: Equatable {
    var title: String
    var lastActionLabel: String?
    var busy: Bool
    var importEnabled: Bool
    var freeLabel: String
    var requiredLabel: String
    var showExportDialog: Bool
    var showImportParams: Bool
    var exportStyleId: String
    var exportStyleName: String
    var exportLimitsText: String
    var exportStyles: [StyleRefSnapshot]
    var exportSelectedMoveCount: Int
    var exportSelectedSongCount: Int
    var exportSelectedChoreoCount: Int
    var importLocalStyles: [StyleRefSnapshot]
    var importTargetStyleId: String
    var lastImportName: String
}

struct SettingsShellSnapshot: Equatable {
    var title: String
    var languageCode: String
    var reminderEnabled: Bool
    var reminderHour: Int
    var reminderMinute: Int
    var prefsLoaded: Bool
    var danceStyles: [String]
    var newStyleDraft: String
    var elementCategories: [String]
    var newCategoryDraft: String
    var lowDiskWarnGib: Int
    var lowDiskWarnEnabled: Bool
    var lowDiskPresetsGib: [Int]
}

enum ShellTabKind: Int, CaseIterable, Identifiable {
    case elements = 0
    case music = 1
    case choreography = 2
    case trainer = 3
    case exchange = 4

    var id: Int { rawValue }

    var defaultTitle: String {
        switch self {
        case .elements: return "Элементы"
        case .music: return "Музыка"
        case .choreography: return "Хореография"
        case .trainer: return "Тренировка"
        case .exchange: return "Обмен"
        }
    }

    var automationName: String {
        switch self {
        case .elements: return "elements"
        case .music: return "music"
        case .choreography: return "choreography"
        case .trainer: return "trainer"
        case .exchange: return "exchange"
        }
    }

    var dockLabel: String {
        switch self {
        case .elements: return "Каталог"
        case .music: return "Музыка"
        case .choreography: return "Связки"
        case .trainer: return "Тренинг"
        case .exchange: return "Сеты"
        }
    }

    var systemImage: String {
        switch self {
        case .elements: return "list.bullet"
        case .music: return "music.note"
        case .choreography: return "person.3.fill"
        case .trainer: return "bolt.fill"
        case .exchange: return "arrow.left.arrow.right"
        }
    }
}
