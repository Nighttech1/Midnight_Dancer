import AVFoundation
import Combine
import Foundation

/// Free-mode clock follows actual audio; no wall-clock fallback or invented progress.
@MainActor
final class TrainerSessionPlayback: ObservableObject {
    @Published var error: String?
    @Published private(set) var progress: Float = 0
    private var player: AVPlayer?
    private var ticker: Any?
    private var status: NSKeyValueObservation?
    private var observers: [NSObjectProtocol] = []
    private var report: ((Float, Bool) -> Void)?
    private var pauseSession: (() -> Void)?

    func prepare(_ snapshot: TrainerSessionSnapshot, onPosition: @escaping (Float, Bool) -> Void,
                 onPause: @escaping () -> Void) {
        close()
        error = nil
        report = onPosition
        pauseSession = onPause
        guard snapshot.playlistCount <= 1, let reference = snapshot.mediaUri,
              let url = OwnedMusicAudio.resolve(reference) else {
            fail("Для этой тренировки недоступна музыка. Вернитесь и выберите доступный трек.")
            return
        }
        do {
            try AVAudioSession.sharedInstance().setCategory(.playback, mode: .default)
            try AVAudioSession.sharedInstance().setActive(true)
        } catch { fail("Не удалось включить звук."); return }
        let item = AVPlayerItem(url: url)
        let audio = AVPlayer(playerItem: item)
        player = audio
        progress = snapshot.progress
        status = item.observe(\.status, options: [.initial, .new]) { [weak self] item, _ in
            Task { @MainActor [weak self] in
                guard let self, self.player?.currentItem === item else { return }
                if item.status == .failed { self.fail("Не удалось воспроизвести музыку тренировки.") }
                else if item.status == .readyToPlay, snapshot.progress > 0 { self.seek(snapshot.progress, fromUser: false) }
            }
        }
        ticker = audio.addPeriodicTimeObserver(forInterval: CMTime(seconds: 0.05, preferredTimescale: 600), queue: .main) { [weak self] time in
            Task { @MainActor [weak self] in
                guard let self, self.player === audio,
                      let duration = audio.currentItem?.duration.seconds,
                      duration.isFinite, duration > 0, time.seconds.isFinite else { return }
                self.progress = Float(min(1, max(0, time.seconds / duration)))
                self.report?(self.progress, false)
            }
        }
        observers.append(NotificationCenter.default.addObserver(forName: .AVPlayerItemDidPlayToEndTime, object: item, queue: .main) { [weak self] _ in
            Task { @MainActor [weak self] in
                guard let self, self.player?.currentItem === item else { return }
                self.pause()
                self.progress = 1
                self.report?(1, false)
            }
        })
        observers.append(NotificationCenter.default.addObserver(forName: .AVPlayerItemFailedToPlayToEndTime, object: item, queue: .main) { [weak self] _ in
            Task { @MainActor [weak self] in
                guard let self, self.player?.currentItem === item else { return }
                self.fail("Воспроизведение музыки прервано.")
            }
        })
        observers.append(NotificationCenter.default.addObserver(forName: AVAudioSession.interruptionNotification, object: nil, queue: .main) { [weak self] _ in
            Task { @MainActor [weak self] in self?.pause() }
        })
        update(paused: snapshot.paused, volume: snapshot.trackVolume)
    }

    func update(paused: Bool, volume: Float) {
        player?.volume = min(1, max(0, volume))
        if paused || error != nil { player?.pause() } else { player?.play() }
    }

    func seek(_ fraction: Float, fromUser: Bool = true) {
        guard let player, let duration = player.currentItem?.duration.seconds,
              duration.isFinite, duration > 0 else { return }
        progress = min(1, max(0, fraction))
        player.seek(to: CMTime(seconds: Double(progress) * duration, preferredTimescale: 600), toleranceBefore: .zero, toleranceAfter: .zero)
        report?(progress, fromUser)
    }

    func pause() { player?.pause(); pauseSession?() }
    func close() {
        player?.pause()
        if let ticker { player?.removeTimeObserver(ticker) }
        ticker = nil
        status = nil
        observers.forEach { NotificationCenter.default.removeObserver($0) }
        observers.removeAll()
        player = nil
    }
    private func fail(_ message: String) { error = message; pause() }
}
