import Foundation

struct TrainerChoreographyState: Equatable {
    var enabled = false
    var choices: [SequenceElementChoice] = []
    var selectedID = ""
    var selectedTitle = ""
    var partExpanded = false
    var start = "0:00.0"
    var end = "0:00.0"
    var passes = "5"
    var rest = "5"
    var durationMs: Int64 = 0
    var accessGranted = false
    var accessLabel = "Проверка подписки недоступна"
    var loading = false
    var active = false
    var paused = false
    var phase = "Готово"
    var pass = 0
    var totalPasses = 0
    var currentLabel = ""
    var remainingMs: Int64 = 0
    var revisionNeedsConfirmation = false
    var message: String?
    var ordinary: TrainerChoreographySession?
}

struct TrainerChoreographySession: Equatable, Identifiable {
    var id = UUID().uuidString
    var title: String
    var elements: [EditorClipSnapshot]
    var music: [EditorClipSnapshot]
    var songSpeed: Double
    var volume: Float
    var durationMs: Int64
    var positionMs: Int64 = 0
    var playing = true
    var error: String?
    var seek: ChoreoPlaybackSeek?
    var currentIndex: Int? {
        var time: Int64 = 0
        for (index, clip) in elements.enumerated() {
            time += Int64(Double(clip.durationMs) / clip.speed)
            if positionMs < time { return index }
        }
        return nil
    }
    var currentLabel: String { currentIndex.map { elements[$0].label } ?? "" }
}

struct TrainerChoreographyActions {
    var setMode: (Bool) -> Void = { _ in }
    var select: (String) -> Void = { _ in }
    var part: (String) -> Void = { _ in }
    var field: (String, String) -> Void = { _, _ in }
    var ordinary: (String, Int64) -> Void = { _, _ in }
}
