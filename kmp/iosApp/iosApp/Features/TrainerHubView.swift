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
    var onSetElementTiming: (Bool) -> Void
    var choreographyActions: TrainerChoreographyActions
    @Environment(\.scenePhase) private var scenePhase
    private var ready: Bool {
        snapshot.choreography.enabled
            ? !snapshot.choreography.selectedID.isEmpty && !snapshot.choreography.loading && !snapshot.choreography.active
            : snapshot.readyPercent >= 100
    }

    var body: some View {
        ZStack {
            MdNeonAtmosphere(accent: MdColor.accentGold)
            VStack(spacing: 0) {
                ScrollView {
                    VStack(alignment: .leading, spacing: 14) {
                        Text("Режим · \(snapshot.modeLabel)")
                            .font(MdType.eyebrow)
                            .foregroundStyle(MdColor.textSecondary)

                        Picker("Режим", selection: Binding(get: { snapshot.choreography.enabled }, set: choreographyActions.setMode)) {
                            Text("Свободная").tag(false)
                            Text("Хореография").tag(true)
                        }
                        .pickerStyle(.segmented)
                        .accessibilityIdentifier("trainer.mode")
                        if snapshot.choreography.enabled {
                            TrainerChoreographyPanel(snapshot: snapshot.choreography, actions: choreographyActions)
                            MdSpeakZoneView(speakLabel: snapshot.speakLabel, accent: MdColor.accentGold,
                                selectedVoiceIndex: snapshot.selectedVoiceIndex, onSelectVoice: onSelectVoice, onSpeak: onSpeak)
                                .disabled(snapshot.choreography.active)
                        } else {
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

                        }

                        Text(snapshot.choreography.enabled ? snapshot.choreography.selectedTitle : "Готовность \(snapshot.readyPercent)%")
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
                                .fill(ready
                                      ? MdColor.neonOrange
                                      : MdColor.neonOrange.opacity(0.35))
                        )
                        .foregroundStyle(MdColor.oledTrueBlack)
                }
                .buttonStyle(.plain)
                .disabled(!ready)
                .accessibilityIdentifier("trainer.start")
                .padding(.horizontal, MdChrome.contentHorizontalPadding)
                .padding(.bottom, 12)
            }
        }
        .onDisappear { choreographyActions.part("pause") }
        .onChange(of: scenePhase) { if $0 != .active { choreographyActions.part("pause") } }
    }

    private func stepGlow(_ step: Int) -> Bool {
        switch step {
        case 1: return snapshot.modeLabel == "Свободная"
        case 2: return !snapshot.styleName.isEmpty && !snapshot.trackTitle.isEmpty
        case 3: return !snapshot.elementTag.isEmpty && snapshot.intervalSec >= 2
        case 4: return true
        default: return false
        }
    }

    @ViewBuilder
    private func stepEditor(_ step: Int) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            switch step {
            case 1:
                Text("Свободная тренировка")
                    .foregroundStyle(MdColor.textPrimary)
                Text("Отработка по подписке пока не подключена на iOS.")
                    .font(.footnote).foregroundStyle(MdColor.textSecondary)
            case 2:
                choice("Стиль", selected: snapshot.styleName, values: snapshot.styleOptions, change: onSetStyle)
                choice("Трек", selected: snapshot.trackTitle, values: snapshot.trackOptions, change: onSetTrack)
            case 3:
                choice("Тег", selected: snapshot.elementTag, values: snapshot.tagOptions, change: onSetTag)
                Toggle("Использовать длительность видео", isOn: Binding(
                    get: { snapshot.useElementTiming }, set: onSetElementTiming))
                    .tint(MdColor.neonOrange)
                Text(snapshot.useElementTiming ? "Интервал используется для элементов без видео." : "Смена элементов через выбранный интервал.")
                    .font(.footnote).foregroundStyle(MdColor.textSecondary)
                HStack {
                    Text("Интервал \(Int(max(2, snapshot.intervalSec)))с")
                        .foregroundStyle(MdColor.textSecondary)
                    Slider(value: Binding(get: { Double(max(2, snapshot.intervalSec)) },
                        set: { onSetInterval(Float($0)) }), in: 2...15, step: 1)
                        .tint(MdColor.neonOrange)
                }
                .onAppear { if snapshot.intervalSec <= 0 { onSetInterval(2) } }
            case 4:
                MdSpeakZoneView(speakLabel: snapshot.speakLabel, accent: MdColor.accentGold,
                    selectedVoiceIndex: snapshot.selectedVoiceIndex, onSelectVoice: onSelectVoice, onSpeak: onSpeak)
            default:
                EmptyView()
            }
        }
        .padding(.horizontal, 4)
        .padding(.bottom, 8)
    }
    private func choice(_ label: String, selected: String, values: [String], change: @escaping (String) -> Void) -> some View {
        Picker(label, selection: Binding(get: { values.contains(selected) ? selected : "" }, set: change)) {
            Text(values.isEmpty ? "Нет материалов" : "Выберите").tag("")
            ForEach(values, id: \.self) { Text($0).tag($0) }
        }
        .tint(MdColor.neonOrange)
        .disabled(values.isEmpty)
        .accessibilityIdentifier("trainer.choice.\(label == "Стиль" ? "style" : label == "Трек" ? "track" : "tag")")
    }

}
