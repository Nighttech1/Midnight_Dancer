import AVFoundation
import SwiftUI

struct ChoreoPlaybackSeek: Equatable {
    let positionMs: Int64
    private let request = UUID()

    init(positionMs: Int64) {
        self.positionMs = positionMs
    }
}

private struct ChoreoPlaybackSource: Equatable {
    var elements: [EditorClipSnapshot]
    var music: [EditorClipSnapshot]
    var songSpeed: Double
}

/// An element preview and soundtrack share one AVPlayerItem, including during scrubbing.
struct ChoreoCompositionPreviewView: View {
    let isPlaying: Bool
    let isEmpty: Bool
    let currentElementLabel: String?
    let elementClips: [EditorClipSnapshot]
    let playheadInElementsMs: Int64
    let scrubbing: Bool
    var glow: CGFloat = 0.4
    var musicClips: [EditorClipSnapshot] = []
    var songSpeed: Double = 1
    var musicVolume: Float = 1
    var globalPlayheadMs: Int64? = nil
    var seekRequest: ChoreoPlaybackSeek? = nil
    var onPlaybackPosition: ((Int64) -> Void)? = nil
    var onPlaybackEnded: (() -> Void)? = nil
    var onPlaybackAvailability: ((Bool) -> Void)? = nil
    var onPlaybackFailed: (() -> Void)? = nil

    @StateObject private var playback = ChoreoCompositionPlaybackController()

    private var source: ChoreoPlaybackSource {
        ChoreoPlaybackSource(elements: elementClips, music: musicClips, songSpeed: songSpeed)
    }

    private var targetMs: Int64 { globalPlayheadMs ?? playheadInElementsMs }
    private var wantsPlayback: Bool { isPlaying && !scrubbing }
    private var isAudioOnly: Bool {
        let hasVideo = elementClips.contains { ChoreoCompositionBuilder.mediaReference($0) != nil }
        let hasAudio = musicClips.contains { ChoreoCompositionBuilder.mediaReference($0) != nil }
        return !hasVideo && hasAudio
    }

    var body: some View {
        GeometryReader { geo in
            let frameWidth = min(geo.size.width, geo.size.height * (9.0 / 16.0))
            let frameHeight = min(geo.size.height, frameWidth * (16.0 / 9.0))
            ZStack {
                RoundedRectangle(cornerRadius: 14)
                    .fill(Color(red: 0.05, green: 0.055, blue: 0.07))
                stage
                if let label = currentElementLabel, !label.isEmpty, !isEmpty,
                   !playback.playbackFailed, !isAudioOnly {
                    VStack {
                        Spacer(minLength: 0)
                        Text(label)
                            .font(MdType.bodyLarge.weight(.bold))
                            .foregroundStyle(MdColor.textPrimary)
                            .multilineTextAlignment(.center)
                            .lineLimit(2)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 8)
                            .background(
                                RoundedRectangle(cornerRadius: 10)
                                    .fill(Color(red: 0.04, green: 0.04, blue: 0.05))
                            )
                            .padding(.bottom, 16)
                    }
                }
            }
            .frame(width: frameWidth, height: frameHeight)
            .clipShape(RoundedRectangle(cornerRadius: 14))
            .overlay {
                RoundedRectangle(cornerRadius: 14)
                    .strokeBorder(MdColor.neonOrange.opacity(isEmpty ? 0 : 0.15 + Double(glow) * 0.45))
                    .allowsHitTesting(false)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .task(id: source) {
            onPlaybackAvailability?(false)
            playback.setVolume(musicVolume)
            playback.setPlaying(wantsPlayback, at: targetMs)
            await playback.rebind(elementClips: elementClips, musicClips: musicClips, songSpeed: songSpeed)
            if !Task.isCancelled {
                onPlaybackAvailability?(playback.player != nil && !playback.playbackFailed)
            }
        }
        .onDisappear {
            playback.teardown()
            onPlaybackAvailability?(false)
        }
        .onChange(of: musicVolume) { playback.setVolume($0) }
        .onChange(of: wantsPlayback) { playback.setPlaying($0, at: targetMs) }
        .onChange(of: seekRequest) {
            if let request = $0 { playback.seek(to: request.positionMs) }
        }
        .onChange(of: targetMs) {
            // On the clock-backed path, transport commands carry a distinct seek request.
            if onPlaybackPosition == nil && !isPlaying { playback.seek(to: $0) }
        }
        .onChange(of: playback.player != nil) { onPlaybackAvailability?($0) }
        .onChange(of: playback.reportedPositionMs) {
            if let position = $0, wantsPlayback { onPlaybackPosition?(position) }
        }
        .onChange(of: playback.reachedEnd) {
            if $0 { onPlaybackEnded?() }
        }
        .onChange(of: playback.playbackFailed) {
            if $0 {
                onPlaybackAvailability?(false)
                onPlaybackFailed?()
            }
        }
    }

    @ViewBuilder private var stage: some View {
        if playback.playbackFailed {
            Text("Не удалось открыть один из файлов хореографии.")
                .font(MdType.bodyMedium)
                .foregroundStyle(MdColor.textSecondary)
                .multilineTextAlignment(.center)
                .padding(16)
                .accessibilityIdentifier("choreo.playbackError")
        } else if playback.isBuilding {
            ProgressView().tint(MdColor.neonOrange)
        } else if isAudioOnly {
            Label("Музыкальная дорожка", systemImage: "music.note")
                .font(MdType.bodyMedium)
                .foregroundStyle(MdColor.textSecondary)
                .multilineTextAlignment(.center)
                .padding(12)
        } else if isEmpty {
            emptyPlaceholder
        } else if let player = playback.player {
            ChoreoPlayerLayerView(player: player)
        } else {
            emptyPlaceholder
        }
    }

    private var emptyPlaceholder: some View {
        VStack(spacing: 6) {
            Text("Начнем творить!")
                .font(MdType.titleLarge.weight(.bold))
                .foregroundStyle(MdColor.textPrimary)
            Text("Загрузи твой первый элемент")
                .font(.system(size: 13))
                .foregroundStyle(MdColor.textSecondary)
        }
        .multilineTextAlignment(.center)
        .padding(16)
    }
}

private struct ChoreoPlayerLayerView: UIViewRepresentable {
    let player: AVPlayer

    func makeUIView(context: Context) -> ChoreoPlayerUIView {
        let view = ChoreoPlayerUIView()
        view.playerLayer.videoGravity = .resizeAspect
        view.backgroundColor = .clear
        return view
    }

    func updateUIView(_ uiView: ChoreoPlayerUIView, context: Context) {
        uiView.playerLayer.player = player
    }
}

private final class ChoreoPlayerUIView: UIView {
    override class var layerClass: AnyClass { AVPlayerLayer.self }

    var playerLayer: AVPlayerLayer { layer as! AVPlayerLayer }
}
