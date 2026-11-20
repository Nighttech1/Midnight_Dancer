import AVFoundation
import SwiftUI

/// Multi-clip Pro Editor preview — AVMutableComposition + AVPlayer (C-300 / Android ChoreoCompositionPreview).
struct ChoreoCompositionPreviewView: View {
    let isPlaying: Bool
    let isEmpty: Bool
    let currentElementLabel: String?
    let elementClips: [EditorClipSnapshot]
    let playheadInElementsMs: Int64
    let scrubbing: Bool
    var glow: CGFloat = 0.4

    @StateObject private var playback = ChoreoCompositionPlaybackController()
    @State private var scrubEndTask: Task<Void, Never>?

    private var compositionKey: String {
        ChoreoCompositionTimelineMath.compositionKey(for: elementClips)
    }

    var body: some View {
        GeometryReader { geo in
            let frameWidth = min(geo.size.width, geo.size.height * (9.0 / 16.0))
            let frameHeight = min(geo.size.height, frameWidth * (16.0 / 9.0))
            ZStack {
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .fill(Color(red: 0.05, green: 0.055, blue: 0.07))
                    .overlay {
                        if !isEmpty {
                            RoundedRectangle(cornerRadius: 14, style: .continuous)
                                .stroke(
                                    MdColor.neonOrange.opacity(0.15 + Double(glow) * 0.45),
                                    lineWidth: 1
                                )
                        }
                    }
                    .frame(width: frameWidth, height: frameHeight)

                if isEmpty {
                    emptyPlaceholder
                } else if playback.player != nil {
                    ChoreoPlayerLayerView(player: playback.player)
                        .frame(width: frameWidth, height: frameHeight)
                        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                }

                if let label = currentElementLabel, !label.isEmpty, !isEmpty {
                    VStack {
                        Spacer()
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
                    .frame(width: frameWidth, height: frameHeight)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .onAppear {
            playback.rebind(clips: elementClips, compositionKey: compositionKey)
            if isPlaying, !scrubbing {
                playback.applyPlayEdge(targetMs: playheadInElementsMs)
            } else if !scrubbing {
                playback.applyPauseSync(targetMs: playheadInElementsMs)
            }
        }
        .onDisappear { playback.teardown() }
        .onChange(of: compositionKey) { newKey in
            playback.rebind(clips: elementClips, compositionKey: newKey)
            if isPlaying, !scrubbing {
                playback.applyPlayEdge(targetMs: playheadInElementsMs)
            } else if !scrubbing {
                playback.applyPauseSync(targetMs: playheadInElementsMs)
            }
        }
        .onChange(of: isPlaying) { playing in
            if scrubbing { return }
            if playing {
                playback.applyPlayEdge(targetMs: playheadInElementsMs)
            } else {
                playback.applyPauseSync(targetMs: playheadInElementsMs)
            }
        }
        .onChange(of: scrubbing) { scrub in
            if scrub {
                scrubEndTask?.cancel()
                playback.applyScrubbing()
            } else {
                scrubEndTask?.cancel()
                scrubEndTask = Task { @MainActor in
                    try? await Task.sleep(nanoseconds: 40_000_000)
                    guard !Task.isCancelled else { return }
                    playback.scrubEnded(isPlaying: isPlaying, targetMs: playheadInElementsMs)
                }
            }
        }
        .onChange(of: playheadInElementsMs) { target in
            if scrubbing { return }
            if isPlaying {
                playback.applyPlayheadWhilePlaying(targetMs: target)
            } else {
                playback.applyPauseSync(targetMs: target)
            }
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
    let player: AVPlayer?

    func makeUIView(context: Context) -> ChoreoPlayerUIView {
        let view = ChoreoPlayerUIView()
        view.playerLayer.videoGravity = .resizeAspectFill
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
