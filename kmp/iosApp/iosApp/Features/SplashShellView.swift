import AVFoundation
import SwiftUI
import UIKit

struct SplashShellView: View {
    let snapshot: SplashShellSnapshot
    var onContinue: () -> Void

    @StateObject private var playback = SplashPlayback()
    @State private var advanced = false

    private let aspectRatio: CGFloat = 9.0 / 16.0

    var body: some View {
        GeometryReader { geometry in
            let width = min(geometry.size.width / 1.5, geometry.size.height * aspectRatio)
            ZStack {
                Color.black
                SplashVideoLayer(player: playback.player)
                    .frame(width: width, height: width / aspectRatio)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .ignoresSafeArea()
        .onAppear { playback.start() }
        .onDisappear { playback.stop() }
        .onChange(of: playback.finished) { _ in advanceIfReady() }
        .onChange(of: snapshot.bootReady) { _ in advanceIfReady() }
    }

    private func advanceIfReady() {
        // The view can finish playing before the library bootstrap attaches the root.
        guard playback.finished, snapshot.bootReady, !advanced else { return }
        advanced = true
        onContinue()
    }
}

@MainActor
private final class SplashPlayback: ObservableObject {
    let player = AVPlayer()
    @Published private(set) var finished = false

    private var started = false
    private var statusObservation: NSKeyValueObservation?
    private var notifications: [NSObjectProtocol] = []
    private var timeout: Task<Void, Never>?

    func start() {
        guard !started else { return }
        started = true

        guard let url = Bundle.main.url(forResource: "splash_boot", withExtension: "mp4")
                ?? Bundle.main.url(forResource: "splash_boot", withExtension: "mp4", subdirectory: "video")
                ?? Bundle.main.url(forResource: "splash_boot", withExtension: "mp4", subdirectory: "Resources/video")
        else {
            finish()
            return
        }

        player.isMuted = true
        player.actionAtItemEnd = .pause
        let item = AVPlayerItem(url: url)
        statusObservation = item.observe(\.status, options: [.initial, .new]) { [weak self] item, _ in
            guard item.status == .failed else { return }
            Task { @MainActor [weak self] in self?.finish() }
        }
        notifications = [
            AVPlayerItem.didPlayToEndTimeNotification,
            AVPlayerItem.failedToPlayToEndTimeNotification,
        ].map { name in
            NotificationCenter.default.addObserver(forName: name, object: item, queue: .main) { [weak self] _ in
                Task { @MainActor [weak self] in self?.finish() }
            }
        }
        timeout = Task { [weak self] in
            // Prevent a stalled decoder from trapping the user at launch.
            do { try await Task.sleep(nanoseconds: 8_000_000_000) }
            catch { return }
            self?.finish()
        }
        player.replaceCurrentItem(with: item)
        player.play()
    }

    func stop() {
        timeout?.cancel()
        timeout = nil
        statusObservation?.invalidate()
        statusObservation = nil
        for observation in notifications {
            NotificationCenter.default.removeObserver(observation)
        }
        notifications.removeAll()
        player.pause()
        player.replaceCurrentItem(with: nil)
    }

    private func finish() {
        guard !finished else { return }
        stop()
        finished = true
    }
}

private struct SplashVideoLayer: UIViewRepresentable {
    let player: AVPlayer

    func makeUIView(context: Context) -> Surface {
        let surface = Surface()
        surface.bind(player)
        return surface
    }

    func updateUIView(_ surface: Surface, context: Context) {
        surface.bind(player)
    }

    static func dismantleUIView(_ surface: Surface, coordinator: ()) {
        surface.unbind()
    }

    final class Surface: UIView {
        override class var layerClass: AnyClass { AVPlayerLayer.self }

        private var videoLayer: AVPlayerLayer { layer as! AVPlayerLayer }
        private var frameObservation: NSKeyValueObservation?

        func bind(_ player: AVPlayer) {
            guard videoLayer.player !== player else { return }
            backgroundColor = .black
            videoLayer.videoGravity = .resizeAspect
            videoLayer.player = player
            isAccessibilityElement = true
            accessibilityIdentifier = "splash.video"
            accessibilityLabel = "Заставка Midnight Dancer"
            accessibilityValue = "loading"
            frameObservation = videoLayer.observe(\.isReadyForDisplay, options: [.initial, .new]) { [weak self] layer, _ in
                let ready = layer.isReadyForDisplay
                Task { @MainActor [weak self] in
                    self?.accessibilityValue = ready ? "ready" : "loading"
                }
            }
        }

        func unbind() {
            frameObservation?.invalidate()
            frameObservation = nil
            videoLayer.player = nil
        }
    }
}
