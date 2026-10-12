import SwiftUI

/// Trainer hub — storyline steps + speak zone + Dance CTA.
struct TrainerHubView: View {
    let snapshot: TrainerFeatureSnapshot
    var onSelectVoice: (Int) -> Void
    var onSpeak: () -> Void
    var onStartDance: () -> Void
    var onSetStep: (Int) -> Void
    var onSetStyle: (String) -> Void
    var onSetTrack: (String) -> Void
    var onSetTag: (String) -> Void
    var onSetInterval: (Float) -> Void

    var body: some View {
        ZStack {
            MdNeonAtmosphere(accent: MdColor.accentGold)
            VStack(spacing: 0) {
                ScrollView {
                    VStack(alignment: .leading, spacing: 14) {
                        Text(snapshot.title)
                            .font(MdType.titleLarge)
                            .foregroundStyle(MdColor.textPrimary)

                        Text("Режим · \(snapshot.modeLabel)")
                            .font(MdType.eyebrow)
                            .foregroundStyle(MdColor.textSecondary)

                        ForEach(Array(snapshot.storylineTitles.enumerated()), id: \.offset) { idx, title in
                            let step = idx + 1
                            let filled = stepGlow(step)
                            Button {
                                onSetStep(step)
                            } label: {
                                HStack {
                                    Text(String(format: "%02d", step))
                                        .font(.system(size: 13, weight: .bold))
                                        .foregroundStyle(MdColor.neonOrange)
                                    Text(title)
                                        .font(MdType.bodyLarge.weight(.semibold))
                                        .foregroundStyle(MdColor.textPrimary)
                                    Spacer()
                                    if snapshot.expandedStep == step {
                                        Image(systemName: "chevron.up")
                                            .foregroundStyle(MdColor.textSecondary)
                                    }
                                }
                                .padding(14)
                                .background(
                                    RoundedRectangle(cornerRadius: 14)
                                        .fill(MdColor.surfaceElevated)
                                        .shadow(
                                            color: filled ? MdColor.neonOrange.opacity(0.45) : .clear,
                                            radius: filled ? 10 : 0
                                        )
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 14)
                                                .stroke(
                                                    filled ? MdColor.neonOrange : MdColor.surfaceBorder,
                                                    lineWidth: filled ? 1.5 : 1
                                                )
                                        )
                                )
                            }
                            .buttonStyle(.plain)

                            if snapshot.expandedStep == step {
                                stepEditor(step)
                            }
                        }

                        MdSpeakZoneView(
                            speakLabel: snapshot.speakLabel,
                            accent: MdColor.accentGold,
                            selectedVoiceIndex: snapshot.selectedVoiceIndex,
                            onSelectVoice: onSelectVoice,
                            onSpeak: onSpeak
                        )

                        Text("Готовность \(snapshot.readyPercent)%")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(MdColor.textSecondary)
                    }
                    .padding(.horizontal, MdChrome.contentHorizontalPadding)
                    .padding(.top, 8)
                    .padding(.bottom, 120)
                }

                Button(action: onStartDance) {
                    Text("Танцуем")
                        .font(.system(size: 17, weight: .bold))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 16)
                        .background(
                            RoundedRectangle(cornerRadius: 16)
                                .fill(snapshot.readyPercent >= 100
                                      ? MdColor.neonOrange
                                      : MdColor.neonOrange.opacity(0.35))
                        )
                        .foregroundStyle(MdColor.oledTrueBlack)
                }
                .buttonStyle(.plain)
                .disabled(snapshot.readyPercent < 100)
                .padding(.horizontal, MdChrome.contentHorizontalPadding)
                .padding(.bottom, 12)
            }
        }
    }

    private func stepGlow(_ step: Int) -> Bool {
        switch step {
        case 1: return !snapshot.styleName.isEmpty
        case 2: return !snapshot.trackTitle.isEmpty
        case 3: return !snapshot.elementTag.isEmpty
        case 4: return snapshot.intervalSec >= 2
        default: return false
        }
    }

    @ViewBuilder
    private func stepEditor(_ step: Int) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            switch step {
            case 1:
                TextField("Стиль", text: Binding(
                    get: { snapshot.styleName },
                    set: onSetStyle
                ))
                .textFieldStyle(.roundedBorder)
            case 2:
                TextField("Трек", text: Binding(
                    get: { snapshot.trackTitle },
                    set: onSetTrack
                ))
                .textFieldStyle(.roundedBorder)
            case 3:
                TextField("Тег", text: Binding(
                    get: { snapshot.elementTag },
                    set: onSetTag
                ))
                .textFieldStyle(.roundedBorder)
            case 4:
                HStack {
                    Text("Интервал \(Int(snapshot.intervalSec))с")
                        .foregroundStyle(MdColor.textSecondary)
                    Slider(
                        value: Binding(
                            get: { Double(snapshot.intervalSec) },
                            set: { onSetInterval(Float($0)) }
                        ),
                        in: 2...15,
                        step: 1
                    )
                    .tint(MdColor.neonOrange)
                }
            default:
                EmptyView()
            }
        }
        .padding(.horizontal, 4)
        .padding(.bottom, 8)
    }
}
