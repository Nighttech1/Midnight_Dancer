import SwiftUI
@preconcurrency import Shared

/// Trainer speak zone — C-33 wires Piper/System TTS via Decompose Intent.
struct MdSpeakZoneView: View {
    let speakLabel: String
    let accent: Color
    var selectedVoiceIndex: Int = 0
    var onSelectVoice: ((Int) -> Void)?
    var onSpeak: (() -> Void)?

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var breath = false

    private let voices = ["Руслан", "Ирина", "Камила"]

    var body: some View {
        MdPremiumCard(accent: accent, glow: true) {
            VStack(alignment: .leading, spacing: 14) {
                HStack {
                    Text("VOICE")
                        .font(MdType.eyebrow)
                        .tracking(1.4)
                        .foregroundStyle(accent)
                    Spacer()
                    Circle()
                        .fill(accent)
                        .frame(width: 8, height: 8)
                        .shadow(color: accent.opacity(0.8), radius: breath ? 8 : 3)
                        .scaleEffect(breath && !reduceMotion ? 1.25 : 1)
                }

                Text("Голос тренера")
                    .font(MdType.speakHeadline)
                    .foregroundStyle(MdColor.textPrimary)

                Text(displayCaption)
                    .font(MdType.bodyMedium)
                    .foregroundStyle(MdColor.textSecondary)

                HStack(spacing: 8) {
                    ForEach(Array(voices.enumerated()), id: \.offset) { index, name in
                        let selected = index == selectedVoiceIndex
                        Button {
                            onSelectVoice?(index)
                        } label: {
                            Text(name)
                                .font(MdType.eyebrow)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 10)
                                .background(
                                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                                        .fill(selected ? accent.opacity(0.22) : MdColor.oledTrueBlack)
                                )
                                .overlay(
                                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                                        .stroke(selected ? accent : MdColor.surfaceBorder, lineWidth: 1)
                                )
                                .foregroundStyle(selected ? accent : MdColor.textSecondary)
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("Voice \(name)")
                    }
                }

                MdNeonEqualizerBars(accent: accent)
                    .frame(maxWidth: .infinity, alignment: .leading)

                MdNeonButton(
                    title: speakLabel.isEmpty ? "Speak · \(voices[safeIndex])" : speakLabel,
                    accent: accent,
                    systemImage: "waveform",
                    isEnabled: onSpeak != nil,
                    action: { onSpeak?() }
                )
                .accessibilityHint("Speaks with Ruslan, Irina, or Kamila (Piper or system TTS)")
            }
        }
        .onAppear {
            guard !reduceMotion else { return }
            withAnimation(.easeInOut(duration: 1.6).repeatForever(autoreverses: true)) {
                breath = true
            }
        }
    }

    private var safeIndex: Int {
        min(max(selectedVoiceIndex, 0), voices.count - 1)
    }

    private var displayCaption: String {
        let raw = speakLabel.trimmingCharacters(in: .whitespacesAndNewlines)
        if raw.isEmpty { return "Piper on-demand or System TTS fallback" }
        if raw.localizedCaseInsensitiveContains("stub") {
            return "Piper · Ruslan / Irina / Kamila"
        }
        return raw
    }
}
