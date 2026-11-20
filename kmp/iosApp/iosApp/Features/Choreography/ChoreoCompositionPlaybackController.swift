import AVFoundation
import Foundation

/// Single AVPlayer timeline — seek policy mirrors Android C-293 (no per-tick seek while playing).
@MainActor
final class ChoreoCompositionPlaybackController: ObservableObject {
    @Published private(set) var player: AVPlayer?
    private var boundKey: String?
    private var lastPlayheadTargetMs: Int64 = 0
    private var compositionDurationMs: Int64 = 0

    func rebind(clips: [EditorClipSnapshot], compositionKey: String) {
        guard compositionKey != boundKey else { return }
        player?.pause()
        player?.replaceCurrentItem(with: nil)

        guard !compositionKey.isEmpty else {
            boundKey = nil
            compositionDurationMs = 0
            player = nil
            return
        }

        do {
            let built = try ChoreoCompositionBuilder.makeCompositionItem(from: clips)
            let avPlayer = AVPlayer(playerItem: built.item)
            avPlayer.isMuted = true
            avPlayer.actionAtItemEnd = .pause
            player = avPlayer
            boundKey = compositionKey
            compositionDurationMs = built.durationMs
            lastPlayheadTargetMs = 0
        } catch {
            boundKey = nil
            compositionDurationMs = 0
            player = nil
        }
    }

    func applyScrubbing() {
        player?.pause()
    }

    /// Mirrors Android play-edge LaunchedEffect — not on every playhead tick.
    func applyPlayEdge(targetMs: Int64) {
        guard boundKey != nil, let player else { return }
        let clamped = clampTarget(targetMs)
        let positionMs = playerPositionMs(player)
        if ChoreoCompositionTimelineMath.playEdgeNeedsSeek(
            playerPositionMs: positionMs,
            targetMs: clamped
        ) {
            seekPlayer(toMs: clamped, player: player)
        }
        lastPlayheadTargetMs = clamped
        player.play()
    }

    func applyPauseSync(targetMs: Int64) {
        guard boundKey != nil, let player else { return }
        let clamped = clampTarget(targetMs)
        player.pause()
        let positionMs = playerPositionMs(player)
        if ChoreoCompositionTimelineMath.shouldSeekWhilePaused(
            playerPositionMs: positionMs,
            targetMs: clamped
        ) {
            seekPlayer(toMs: clamped, player: player)
        }
        lastPlayheadTargetMs = clamped
    }

    /// While playing: seek only on sudden playhead jumps (Seek start / scrub latch).
    func applyPlayheadWhilePlaying(targetMs: Int64) {
        guard boundKey != nil, let player else { return }
        let clamped = clampTarget(targetMs)
        let prevTarget = lastPlayheadTargetMs
        lastPlayheadTargetMs = clamped
        let jump = abs(clamped - prevTarget)
        guard jump > 500 else { return }
        seekPlayer(toMs: clamped, player: player)
        player.play()
    }

    func scrubEnded(isPlaying: Bool, targetMs: Int64) {
        guard let player, boundKey != nil else { return }
        let clamped = max(0, min(targetMs, max(compositionDurationMs, 1)))
        seekPlayer(toMs: clamped, player: player)
        lastPlayheadTargetMs = clamped
        if isPlaying {
            player.play()
        }
    }

    func teardown() {
        player?.pause()
        player?.replaceCurrentItem(with: nil)
        player = nil
        boundKey = nil
    }

    private func seekPlayer(toMs ms: Int64, player: AVPlayer) {
        let time = CMTime(value: ms, timescale: 1_000)
        player.seek(to: time, toleranceBefore: .zero, toleranceAfter: .zero)
    }

    private func clampTarget(_ targetMs: Int64) -> Int64 {
        max(0, min(targetMs, max(compositionDurationMs, 1)))
    }

    private func playerPositionMs(_ player: AVPlayer) -> Int64 {
        let positionMs = CMTimeGetSeconds(player.currentTime()) * 1_000
        guard positionMs.isFinite, positionMs >= 0, positionMs < Double(Int64.max) else {
            return 0
        }
        return Int64(positionMs)
    }
}
