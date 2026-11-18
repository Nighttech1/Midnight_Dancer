import Foundation

/// Mirrors shared [EditorTrackDurations] for iOS composition preview (C-300).
enum ChoreoCompositionTimelineMath {
    private static let seekEpsilonMs: Int64 = 120
    private static let playheadJumpSeekMs: Int64 = 500

    static func effectiveMs(durationMs: Int64, speed: Double) -> Int64 {
        let safe = speed > 0 ? speed : 1.0
        return Int64(Double(durationMs) / safe)
    }

    static func elementsDurationMs(clips: [EditorClipSnapshot]) -> Int64 {
        clips.reduce(into: Int64(0)) { partial, clip in
            partial += effectiveMs(durationMs: clip.durationMs, speed: clip.speed)
        }
    }

    static func timelineClips(_ clips: [EditorClipSnapshot]) -> [EditorClipSnapshot] {
        clips.map { clip in
            var copy = clip
            copy.durationMs = effectiveMs(durationMs: clip.durationMs, speed: clip.speed)
            return copy
        }
    }

    static func clipIndexAt(clips: [EditorClipSnapshot], positionMs: Int64) -> Int {
        guard !clips.isEmpty, positionMs >= 0 else { return -1 }
        var start: Int64 = 0
        for (index, clip) in clips.enumerated() {
            let end = start + max(clip.durationMs, 0)
            if positionMs < end { return index }
            start = end
        }
        return -1
    }

    static func compositionKey(for clips: [EditorClipSnapshot]) -> String {
        clips
            .filter { !($0.mediaUri ?? "").trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
            .map { "\($0.mediaUri ?? "")|\($0.sourceStartMs)|\($0.durationMs)|\($0.speed)" }
            .joined(separator: ";")
    }

    static func shouldSeekWhilePlaying(
        playerPositionMs: Int64,
        targetMs: Int64,
        previousTargetMs: Int64
    ) -> Bool {
        let jump = abs(targetMs - previousTargetMs)
        if jump > playheadJumpSeekMs { return true }
        if abs(playerPositionMs - targetMs) > seekEpsilonMs { return false }
        return false
    }

    static func shouldSeekWhilePaused(playerPositionMs: Int64, targetMs: Int64) -> Bool {
        abs(playerPositionMs - targetMs) > seekEpsilonMs
    }

    static func playEdgeNeedsSeek(playerPositionMs: Int64, targetMs: Int64) -> Bool {
        abs(playerPositionMs - targetMs) > seekEpsilonMs
    }
}
