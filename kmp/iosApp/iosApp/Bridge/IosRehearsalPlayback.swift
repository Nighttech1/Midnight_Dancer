import AVFoundation
import Foundation
@preconcurrency import Shared

/// Main-owned driver. The Kotlin port owns timeouts, cancellation and the A–B state machine.
@MainActor
final class IosRehearsalPlayback: NSObject, IosRehearsalAudioDriver {
    private var player: AVPlayer?
    private var preparation: Task<Void, Never>?
    private var status: NSKeyValueObservation?
    private var generation = UUID()
    private var seekGeneration = UUID()
    private var prepared = false
    private var seeking = false
    private var confirmedDuration: Int64 = 0
    private var failure: String?
    private var interrupted = false
    private var observers: [NSObjectProtocol] = []

    func beginPrepare(plan: RehearsalPlan) {
        stop()
        let token = generation
        preparation = Task { @MainActor [weak self] in
            do {
                let composition = AVMutableComposition()
                guard let output = composition.addMutableTrack(withMediaType: .audio,
                    preferredTrackID: kCMPersistentTrackID_Invalid) else { throw PlaybackError.invalid }
                var end: Int64 = 0
                guard !plan.musicClips.isEmpty, plan.musicVolume.isFinite else { throw PlaybackError.invalid }
                for clip in plan.musicClips {
                    try Task.checkCancellation()
                    guard clip.timelineStartMs == end, clip.sourceStartMs >= 0, clip.durationMs > 0,
                          clip.durationMs <= Int64.max - clip.sourceStartMs,
                          clip.durationMs <= Int64.max - end,
                          let url = OwnedMusicAudio.resolve(clip.uri) else { throw PlaybackError.missing }
                    let asset = AVURLAsset(url: url)
                    guard try await asset.load(.isPlayable),
                          let audio = try await asset.loadTracks(withMediaType: .audio).first else { throw PlaybackError.invalid }
                    let available = try await audio.load(.timeRange)
                    let start = Double(clip.sourceStartMs) / 1000
                    let finish = Double(clip.sourceStartMs + clip.durationMs) / 1000
                    guard available.start.seconds.isFinite, available.end.seconds.isFinite,
                          start >= available.start.seconds - 0.0005,
                          finish <= available.end.seconds + 0.0005 else { throw PlaybackError.changedDuration }
                    try output.insertTimeRange(CMTimeRange(start: CMTime(value: clip.sourceStartMs, timescale: 1000),
                        duration: CMTime(value: clip.durationMs, timescale: 1000)), of: audio,
                        at: CMTime(value: end, timescale: 1000))
                    end += clip.durationMs
                }
                try Task.checkCancellation()
                guard end == plan.durationMs, composition.duration.seconds.isFinite,
                      abs(composition.duration.seconds * 1000 - Double(plan.durationMs)) <= 0.5 else { throw PlaybackError.changedDuration }
                guard let self, self.generation == token else { return }
                try AVAudioSession.sharedInstance().setCategory(.playback, mode: .default, options: [.mixWithOthers])
                try AVAudioSession.sharedInstance().setActive(true)
                let item = AVPlayerItem(asset: composition)
                item.audioTimePitchAlgorithm = .spectral
                let player = AVPlayer(playerItem: item)
                player.volume = min(1, max(0, plan.musicVolume))
                self.player = player
                self.confirmedDuration = end
                self.status = item.observe(\.status, options: [.initial, .new]) { [weak self] item, _ in
                    Task { @MainActor [weak self] in
                        guard let self, self.generation == token, self.player?.currentItem === item else { return }
                        if item.status == .failed {
                            self.failure = "Не удалось подготовить музыку отработки."
                            self.player?.pause()
                        }
                    }
                }
                self.observers.append(NotificationCenter.default.addObserver(
                    forName: AVAudioSession.interruptionNotification, object: nil, queue: .main) { [weak self] notification in
                    let raw = notification.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt
                    if raw == AVAudioSession.InterruptionType.began.rawValue {
                        Task { @MainActor [weak self] in
                            guard let self, self.generation == token else { return }
                            self.player?.pause()
                            self.interrupted = true
                        }
                    }
                })
                self.observers.append(NotificationCenter.default.addObserver(
                    forName: AVAudioSession.routeChangeNotification, object: nil, queue: .main) { [weak self] notification in
                    let raw = notification.userInfo?[AVAudioSessionRouteChangeReasonKey] as? UInt
                    if raw == AVAudioSession.RouteChangeReason.oldDeviceUnavailable.rawValue {
                        Task { @MainActor [weak self] in
                            guard let self, self.generation == token else { return }
                            self.player?.pause()
                            self.interrupted = true
                        }
                    }
                })
                self.observers.append(NotificationCenter.default.addObserver(
                    forName: .AVPlayerItemFailedToPlayToEndTime, object: item, queue: .main) { [weak self] _ in
                    Task { @MainActor [weak self] in
                        guard let self, self.generation == token else { return }
                        self.failure = "Воспроизведение музыки отработки прервано."
                        self.player?.pause()
                    }
                })
                self.prepared = true
            } catch is CancellationError {
                // Stop/selection replacement already invalidated this generation.
            } catch {
                guard let self, self.generation == token else { return }
                self.failure = (error as? PlaybackError)?.localizedDescription ?? "Не удалось подготовить музыку отработки."
                self.player?.pause()
            }
        }
    }

    func isPrepared() -> Bool { prepared }
    func preparedDurationMs() -> Int64 { confirmedDuration }
    func errorMessage() -> String? { failure }
    func takeInterruption() -> Bool {
        let pending = interrupted
        interrupted = false
        return pending
    }
    func isReady() -> Bool { prepared && !seeking && player?.currentItem?.status == .readyToPlay }
    func seek(positionMs: Int64) {
        guard prepared, positionMs >= 0, positionMs <= confirmedDuration, let player else {
            failure = "Не удалось перейти к началу участка."
            return
        }
        let token = generation
        let seekToken = UUID()
        seekGeneration = seekToken
        seeking = true
        player.seek(to: CMTime(value: positionMs, timescale: 1000), toleranceBefore: .zero, toleranceAfter: .zero) { [weak self] finished in
            Task { @MainActor [weak self] in
                guard let self, self.generation == token, self.seekGeneration == seekToken else { return }
                self.seeking = false
                if !finished { self.failure = "Не удалось перейти к началу участка." }
            }
        }
    }
    func play() {
        guard isReady(), failure == nil else { return }
        player?.playImmediately(atRate: 1)
    }
    func isPlaying() -> Bool { player?.timeControlStatus == .playing && (player?.rate ?? 0) > 0 }
    func positionMs() -> Int64 {
        let value = (player?.currentTime().seconds ?? 0) * 1000
        guard value.isFinite, value >= 0, value < Double(Int64.max) else { return 0 }
        return Int64(value.rounded(.down))
    }
    func pause() { player?.pause() }
    func stop() {
        generation = UUID()
        seekGeneration = UUID()
        preparation?.cancel()
        preparation = nil
        player?.pause()
        observers.forEach { NotificationCenter.default.removeObserver($0) }
        observers.removeAll()
        interrupted = false
        status = nil
        player = nil
        prepared = false
        seeking = false
        confirmedDuration = 0
        failure = nil
    }

    private enum PlaybackError: LocalizedError {
        case missing, invalid, changedDuration
        var errorDescription: String? {
            switch self {
            case .missing: return "Музыкальный файл постановки недоступен."
            case .invalid: return "Не удалось открыть музыку постановки."
            case .changedDuration: return "Длительность музыки изменилась. Проверьте музыкальные участки в редакторе."
            }
        }
    }
}
