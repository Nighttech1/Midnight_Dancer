import AVFoundation
import SwiftUI

struct TrainerChoreographySessionView: View {
    let snapshot: TrainerChoreographySession
    var action: (String, Int64) -> Void
    @Environment(\.scenePhase) private var scenePhase
    @State private var confirmFinish = false

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                Text(snapshot.title).font(MdType.titleLarge)
                ChoreoCompositionPreviewView(isPlaying: snapshot.playing, isEmpty: false,
                    currentElementLabel: snapshot.currentLabel,
                    elementClips: snapshot.elements, playheadInElementsMs: snapshot.positionMs, scrubbing: false,
                    musicClips: snapshot.music, songSpeed: snapshot.songSpeed, musicVolume: snapshot.volume,
                    globalPlayheadMs: snapshot.positionMs, seekRequest: snapshot.seek,
                    onPlaybackPosition: { action("position", $0) },
                    onPlaybackEnded: { action("end", snapshot.durationMs) },
                    onPlaybackFailed: { action("error", 0) })
                    .frame(height: 340)
                    .accessibilityIdentifier("trainer.choreography.preview")
                Text(snapshot.currentLabel).font(.title3.bold())
                Slider(value: Binding(get: { Double(snapshot.positionMs) }, set: { action("seek", Int64($0)) }),
                    in: 0...Double(max(snapshot.durationMs, 1)))
                    .accessibilityIdentifier("trainer.choreography.seek")
                Text(String(format: "%02lld:%02lld / %02lld:%02lld",
                    snapshot.positionMs / 60000, snapshot.positionMs / 1000 % 60,
                    snapshot.durationMs / 60000, snapshot.durationMs / 1000 % 60)).monospacedDigit()
                if let error = snapshot.error { Text(error).foregroundStyle(.red) }
                Button(snapshot.playing ? "Пауза" : "Продолжить") { action("toggle", 0) }
                    .buttonStyle(.borderedProminent).disabled(snapshot.error != nil)
                    .accessibilityIdentifier("trainer.choreography.pause")
                Button("Завершить", role: .destructive) { confirmFinish = true }
                    .accessibilityIdentifier("trainer.choreography.finish")
            }.padding(20).padding(.bottom, 100)
        }
        .background(MdColor.oledTrueBlack)
        .foregroundStyle(MdColor.textPrimary)
        .tint(MdColor.neonOrange)
        .onChange(of: scenePhase) { if $0 != .active { action("pause", 0) } }
        .onReceive(NotificationCenter.default.publisher(for: AVAudioSession.interruptionNotification)) { notification in
            if notification.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt == AVAudioSession.InterruptionType.began.rawValue {
                action("pause", 0)
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: AVAudioSession.routeChangeNotification)) { notification in
            if notification.userInfo?[AVAudioSessionRouteChangeReasonKey] as? UInt == AVAudioSession.RouteChangeReason.oldDeviceUnavailable.rawValue {
                action("pause", 0)
            }
        }
        .onDisappear { action("pause", 0) }
        .confirmationDialog("Завершить тренировку?", isPresented: $confirmFinish, titleVisibility: .visible) {
            Button("Завершить", role: .destructive) { action("finish", 0) }
            Button("Отмена", role: .cancel) {}
        }
    }
}
