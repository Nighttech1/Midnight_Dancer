import AVFoundation
import SwiftUI

/// Native composition transport. Periodic updates are reports, not new seek commands.
@MainActor
final class ChoreoCompositionPlaybackController: ObservableObject {
    @Published private(set) var player: AVPlayer?
    @Published private(set) var isBuilding = false
    @Published private(set) var playbackFailed = false
    @Published private(set) var reportedPositionMs: Int64?
    @Published private(set) var reachedEnd = false

    private var bindingID: UUID?
    private var hasAudio = false
    private var durationMs: Int64 = 0
    private var targetMs: Int64 = 0
    private var requestedPlaying = false
    private var seeking = false
    private var seekGeneration = 0
    private var volume: Float = 1
    private var periodicObserver: Any?
    private var endObserver: NSObjectProtocol?
    private var itemObservation: NSKeyValueObservation?

    func rebind(
        elementClips: [EditorClipSnapshot],
        musicClips: [EditorClipSnapshot],
        songSpeed: Double
    ) async {
        detachPlayer()
        let id = UUID()
        bindingID = id
        playbackFailed = false
        reachedEnd = false
        reportedPositionMs = nil

        let hasMedia = (elementClips + musicClips).contains {
            ChoreoCompositionBuilder.mediaReference($0) != nil
        }
        hasAudio = musicClips.contains { ChoreoCompositionBuilder.mediaReference($0) != nil }
        isBuilding = hasMedia
        guard hasMedia else { return }

        do {
            let result = try await ChoreoCompositionBuilder.makeCompositionItem(
                elementClips: elementClips, musicClips: musicClips, songSpeed: songSpeed
            )
            try Task.checkCancellation()
            guard bindingID == id else { return }
            let avPlayer = AVPlayer(playerItem: result.item)
            avPlayer.actionAtItemEnd = .pause
            avPlayer.volume = volume
            durationMs = result.durationMs
            player = avPlayer
            installObservers(on: avPlayer, item: result.item)
            isBuilding = false
            seek(to: targetMs)
        } catch {
            guard !Task.isCancelled, bindingID == id else { return }
            isBuilding = false
            playbackFailed = true
        }
    }

    func setVolume(_ newVolume: Float) {
        volume = newVolume.isFinite ? min(max(newVolume, 0), 1) : 1
        player?.volume = volume
    }

    func setPlaying(_ playing: Bool, at positionMs: Int64) {
        requestedPlaying = playing
        if !playing {
            player?.pause()
            // The player is already at the visible position when pausing.
            targetMs = positionMs
            return
        }
        seek(to: positionMs)
    }

    /// All explicit transport gestures take this path, including sub-500ms jumps.
    func seek(to positionMs: Int64) {
        targetMs = max(positionMs, 0)
        reachedEnd = false
        guard let player else { return }
        let target = min(targetMs, durationMs)
        seekGeneration += 1
        let generation = seekGeneration
        seeking = true
        player.pause()
        player.currentItem?.cancelPendingSeeks()
        player.seek(
            to: CMTime(value: target, timescale: 1_000),
            toleranceBefore: .zero,
            toleranceAfter: .zero
        ) { [weak self, weak player] finished in
            Task { @MainActor in
                guard let self, let player, self.player === player,
                      self.seekGeneration == generation else { return }
                self.seeking = false
                if finished && self.requestedPlaying { self.play(player) }
            }
        }
    }

    private func play(_ player: AVPlayer) {
        if hasAudio {
            do {
                let session = AVAudioSession.sharedInstance()
                try session.setCategory(.playback, mode: .default)
                try session.setActive(true)
            } catch {
                requestedPlaying = false
                playbackFailed = true
                detachPlayer()
                return
            }
        }
        player.play()
    }

    func teardown() {
        bindingID = nil
        requestedPlaying = false
        targetMs = 0
        isBuilding = false
        detachPlayer()
    }

    private func installObservers(on player: AVPlayer, item: AVPlayerItem) {
        periodicObserver = player.addPeriodicTimeObserver(
            forInterval: CMTime(value: 1, timescale: 10), queue: .main
        ) { [weak self, weak player] time in
            Task { @MainActor in
                guard let self, let player, self.player === player,
                      self.requestedPlaying, !self.seeking, player.rate > 0 else { return }
                let ms = time.seconds * 1_000
                guard ms.isFinite, ms >= 0, ms < Double(Int64.max) else { return }
                let position = Int64(ms)
                if position < self.durationMs { self.reportedPositionMs = position }
            }
        }
        endObserver = NotificationCenter.default.addObserver(
            forName: AVPlayerItem.didPlayToEndTimeNotification, object: item, queue: .main
        ) { [weak self, weak player] _ in
            Task { @MainActor in
                guard let self, let player, self.player === player,
                      self.requestedPlaying, !self.seeking else { return }
                self.requestedPlaying = false
                self.reachedEnd = true
            }
        }
        itemObservation = item.observe(\.status, options: [.initial, .new]) { [weak self, weak player] item, _ in
            guard item.status == .failed else { return }
            Task { @MainActor in
                guard let self, let player, self.player === player else { return }
                self.playbackFailed = true
                self.requestedPlaying = false
                self.detachPlayer()
            }
        }
    }

    private func detachPlayer() {
        seekGeneration += 1
        seeking = false
        if let periodicObserver { player?.removeTimeObserver(periodicObserver) }
        periodicObserver = nil
        if let endObserver { NotificationCenter.default.removeObserver(endObserver) }
        endObserver = nil
        itemObservation?.invalidate()
        itemObservation = nil
        player?.pause()
        player?.currentItem?.cancelPendingSeeks()
        player?.replaceCurrentItem(with: nil)
        player = nil
        durationMs = 0
    }
}
