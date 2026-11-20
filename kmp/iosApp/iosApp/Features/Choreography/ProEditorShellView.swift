import SwiftUI

/// Pro Editor shell (C-300 scaffolding): composition preview + transport until full timeline lands.
struct ProEditorShellView: View {
    let snapshot: SequenceEditorSnapshot
    var onBack: () -> Void
    var onTogglePlay: () -> Void

    @State private var glowPhase = false

    var body: some View {
        ZStack {
            MdNeonAtmosphere(accent: MdColor.neonMagenta)
            VStack(spacing: 12) {
                header
                ChoreoCompositionPreviewView(
                    isPlaying: snapshot.isPlaying,
                    isEmpty: snapshot.previewIsEmpty,
                    currentElementLabel: snapshot.previewIsEmpty ? nil : snapshot.previewElementLabel,
                    elementClips: snapshot.elementClips,
                    playheadInElementsMs: snapshot.playheadInElementsMs,
                    scrubbing: false,
                    glow: glowPhase ? 0.65 : 0.25
                )
                .frame(maxHeight: 320)
                .padding(.horizontal, 14)

                transportBar
                Spacer(minLength: 0)
                timelinePlaceholder
            }
        }
        .onAppear {
            withAnimation(.easeInOut(duration: 0.9).repeatForever(autoreverses: true)) {
                glowPhase = true
            }
        }
    }

    private var header: some View {
        HStack {
            Button(action: onBack) {
                Image(systemName: "chevron.left")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(MdColor.textPrimary)
                    .frame(width: 44, height: 44)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Назад")

            Text(snapshot.title)
                .font(MdType.titleLarge)
                .foregroundStyle(MdColor.textPrimary)
                .lineLimit(1)
            Spacer()
        }
        .padding(.horizontal, MdChrome.contentHorizontalPadding)
        .padding(.top, 8)
    }

    private var transportBar: some View {
        HStack(spacing: 24) {
            Spacer()
            Button(action: onTogglePlay) {
                Image(systemName: snapshot.isPlaying ? "pause.fill" : "play.fill")
                    .font(.system(size: 28, weight: .bold))
                    .foregroundStyle(MdColor.oledTrueBlack)
                    .frame(width: 56, height: 56)
                    .background(Circle().fill(MdColor.neonOrange))
            }
            .buttonStyle(.plain)
            .accessibilityLabel(snapshot.isPlaying ? "Пауза" : "Воспроизведение")
            Spacer()
        }
    }

    private var timelinePlaceholder: some View {
        Text("Таймлайн и инструменты — следующий этап паритета с Android Pro Editor.")
            .font(.system(size: 13))
            .foregroundStyle(MdColor.textSecondary)
            .multilineTextAlignment(.center)
            .padding(.horizontal, 24)
            .padding(.bottom, 96)
    }
}
