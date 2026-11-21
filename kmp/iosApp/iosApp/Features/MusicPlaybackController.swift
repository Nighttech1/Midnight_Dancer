import AVFoundation
import Combine
import Foundation

@MainActor
final class MusicPlaybackController: ObservableObject {
    @Published var error: String?
    @Published private(set) var progress: Float = 0
    private var player: AVPlayer?
    private var trackID: String?
    private var reference: String?
    private var ticker: Any?
    private var status: NSKeyValueObservation?
    private var notifications: [NSObjectProtocol] = []
    private var reportPosition: ((String, Float) -> Void)?
    private var reportStop: ((String) -> Void)?

    func update(_ track: MusicTrackSnapshot?, position: @escaping (String, Float) -> Void,
                stop: @escaping (String) -> Void) {
        reportPosition = position
        reportStop = stop
        guard let track else { close(); return }
        if trackID != track.id || reference != track.mediaUri || player == nil {
            close()
            error = nil
            trackID = track.id
            reference = track.mediaUri
            guard let raw = track.mediaUri, let url = OwnedMusicAudio.resolve(raw) else {
                fail("Файл музыки недоступен. Выберите файл снова.")
                return
            }
            do {
                try AVAudioSession.sharedInstance().setCategory(.playback, mode: .default)
                try AVAudioSession.sharedInstance().setActive(true)
            } catch { fail("Не удалось включить звук."); return }
            let item = AVPlayerItem(url: url)
            let newPlayer = AVPlayer(playerItem: item)
            player = newPlayer
            if track.progress > 0 {
                // Seek after readiness, when the real duration is known.
                progress = track.progress
            }
            status = item.observe(\.status, options: [.new, .initial]) { [weak self] item, _ in
                Task { @MainActor [weak self] in
                    guard let self, self.player?.currentItem === item else { return }
                    if item.status == .failed { self.fail("Не удалось воспроизвести музыку.") }
                    else if item.status == .readyToPlay, track.progress > 0 { self.seek(track.progress) }
                }
            }
            ticker = newPlayer.addPeriodicTimeObserver(forInterval: CMTime(seconds: 0.25, preferredTimescale: 600),
                queue: .main) { [weak self] time in
                Task { @MainActor [weak self] in
                    guard let self, self.player === newPlayer, let id = self.trackID else { return }
                    let duration = newPlayer.currentItem?.duration.seconds ?? 0
                    guard duration.isFinite, duration > 0 else { return }
                    self.progress = Float(min(1, max(0, time.seconds / duration)))
                    self.reportPosition?(id, self.progress)
                }
            }
            notifications.append(NotificationCenter.default.addObserver(forName: .AVPlayerItemDidPlayToEndTime,
                object: item, queue: .main) { [weak self] _ in
                Task { @MainActor [weak self] in
                    guard let self, self.player?.currentItem === item, let id = self.trackID else { return }
                    self.player?.pause()
                    self.player?.seek(to: .zero)
                    self.progress = 0
                    self.reportPosition?(id, 0)
                    self.reportStop?(id)
                }
            })
            notifications.append(NotificationCenter.default.addObserver(forName: .AVPlayerItemFailedToPlayToEndTime,
                object: item, queue: .main) { [weak self] _ in
                Task { @MainActor [weak self] in
                    guard let self, self.player?.currentItem === item else { return }
                    self.fail("Не удалось завершить воспроизведение музыки.")
                }
            })
            notifications.append(NotificationCenter.default.addObserver(forName: AVAudioSession.interruptionNotification,
                object: nil, queue: .main) { [weak self] _ in
                Task { @MainActor [weak self] in self?.pause() }
            })
        }
        if track.isPlaying { player?.playImmediately(atRate: Float(track.basePlaybackSpeed)) }
        else { player?.pause() }
    }

    func seek(_ value: Float) {
        guard let player, let duration = player.currentItem?.duration.seconds,
              duration.isFinite, duration > 0 else { return }
        progress = min(1, max(0, value))
        player.seek(to: CMTime(seconds: duration * Double(progress), preferredTimescale: 600),
            toleranceBefore: .zero, toleranceAfter: .zero)
        if let id = trackID { reportPosition?(id, progress) }
    }

    func pause() {
        player?.pause()
        if let id = trackID { reportStop?(id) }
    }

    func close() {
        player?.pause()
        if let ticker { player?.removeTimeObserver(ticker) }
        ticker = nil
        status = nil
        notifications.forEach { NotificationCenter.default.removeObserver($0) }
        notifications.removeAll()
        player = nil
        trackID = nil
        reference = nil
        progress = 0
    }

    private func fail(_ message: String) {
        error = message
        pause()
    }
}
