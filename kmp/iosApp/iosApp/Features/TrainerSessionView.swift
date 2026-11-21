import AVKit
import SwiftUI

struct TrainerSessionView: View {
    let snapshot: TrainerSessionSnapshot
    var onTogglePause: () -> Void
    var onPause: () -> Void
    var onSeek: (Float, Bool) -> Void
    var onVolume: (Float) -> Void
    var onFinish: () -> Void
    @StateObject private var playback = TrainerSessionPlayback()
    @State private var preview: AVPlayer?
    @State private var confirmingFinish = false
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        ZStack {
            MdNeonAtmosphere(accent: MdColor.neonOrange)
            ScrollView {
                VStack(spacing: 18) {
                    Text("Тренировка").font(MdType.titleLarge)
                    Text(snapshot.trackTitle).font(MdType.bodyLarge).foregroundStyle(MdColor.textSecondary)
                    Text(snapshot.moveTitle).font(.title2.bold()).multilineTextAlignment(.center)
                        .accessibilityIdentifier("trainer.session.move")
                    if let preview {
                        VideoPlayer(player: preview).frame(height: 240)
                            .allowsHitTesting(false)
                    } else {
                        Text("Видео элемента недоступно").font(.footnote).foregroundStyle(MdColor.textSecondary)
                    }
                    Text("Элемент \(snapshot.phaseIndex + 1) / \(snapshot.phaseTotal)")
                    Slider(value: Binding(get: { Double(playback.progress) }, set: { playback.seek(Float($0)) }), in: 0...1)
                        .accessibilityIdentifier("trainer.session.seek")
                    Text(timecode).monospacedDigit()
                    if let error = playback.error {
                        Text(error).foregroundStyle(.red).accessibilityIdentifier("trainer.session.error")
                    }
                    Button(snapshot.paused ? "Продолжить" : "Пауза", action: onTogglePause)
                        .buttonStyle(.borderedProminent)
                        .disabled(playback.error != nil)
                        .accessibilityIdentifier("trainer.session.pause")
                    HStack {
                        Image(systemName: "speaker.wave.2")
                        Slider(value: Binding(get: { Double(snapshot.trackVolume) }, set: { onVolume(Float($0)) }), in: 0...1)
                    }
                    Button("Завершить тренировку", role: .destructive) { confirmingFinish = true }
                        .accessibilityIdentifier("trainer.session.finish")
                }
                .padding(20).padding(.bottom, 100)
            }
        }
        .foregroundStyle(MdColor.textPrimary)
        .tint(MdColor.neonOrange)
        .onAppear {
            playback.prepare(snapshot, onPosition: onSeek, onPause: onPause)
            updatePreview()
        }
        .onChange(of: snapshot.paused) { paused in
            playback.update(paused: paused, volume: snapshot.trackVolume)
            if paused { preview?.pause() } else { preview?.play() }
        }
        .onChange(of: snapshot.trackVolume) { _ in playback.update(paused: snapshot.paused, volume: snapshot.trackVolume) }
        .onChange(of: snapshot.phaseIndex) { _ in updatePreview() }
        .onChange(of: scenePhase) { if $0 != .active { playback.pause(); preview?.pause() } }
        .onDisappear { playback.pause(); playback.close(); preview?.pause(); preview = nil }
        .confirmationDialog("Завершить тренировку?", isPresented: $confirmingFinish, titleVisibility: .visible) {
            Button("Завершить", role: .destructive) { playback.close(); onFinish() }
            Button("Отмена", role: .cancel) {}
        }
    }

    private var timecode: String {
        let seconds = Int(Double(snapshot.durationMs) / 1000 * Double(playback.progress))
        return String(format: "%02d:%02d", seconds / 60, seconds % 60)
    }
    private func updatePreview() {
        preview?.pause()
        guard let raw = snapshot.cueVideo, let url = OwnedElementVideo.resolve(raw) else { preview = nil; return }
        let player = AVPlayer(url: url)
        player.isMuted = true
        preview = player
        if !snapshot.paused { player.play() }
    }
}
