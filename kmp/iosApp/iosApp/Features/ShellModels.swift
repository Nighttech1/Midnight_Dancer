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
}

struct MusicFeatureSnapshot: Equatable {
    var title: String
    var searchQuery: String
    var tracks: [MusicTrackSnapshot]
    var playingTrackId: String?
    var editingTrackId: String?
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

struct ChoreographyFeatureSnapshot: Equatable {
    var title: String
    var searchQuery: String
    var items: [VaultItemSnapshot]
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

    var systemImage: String {
        switch self {
        case .elements: return "figure.dance"
        case .music: return "music.note.list"
        case .choreography: return "list.bullet.rectangle"
        case .trainer: return "person.badge.clock"
        case .exchange: return "arrow.left.arrow.right.circle"
        }
    }
}
