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

    private var styleTrackSubtitle: String {
        let selected = [snapshot.styleName, snapshot.trackTitle].filter { !$0.isEmpty }.joined(separator: " · ")
        return selected.isEmpty ? "Стиль · Трек" : selected
    }

    var body: some View {
        ZStack {
            MdNeonAtmosphere(accent: MdColor.accentGold)
            VStack(spacing: 0) {
                ScrollView {
                    VStack(spacing: 10) {
                        stepCard(1, title: "Режим сессии", subtitle: snapshot.choreography.enabled ? "Хореография" : "Фристайл") {
                            modeControl
                        }
                        if snapshot.choreography.enabled {
                            stepCard(2, title: "Выбор хореографии", subtitle: snapshot.choreography.selectedTitle.isEmpty ? "Часть хореографии" : snapshot.choreography.selectedTitle) {
                                TrainerChoreographyPanel(snapshot: snapshot.choreography, actions: choreographyActions)
                                MdSpeakZoneView(speakLabel: snapshot.speakLabel, accent: MdColor.accentGold,
                                    selectedVoiceIndex: snapshot.selectedVoiceIndex, onSelectVoice: onSelectVoice, onSpeak: onSpeak)
                                    .disabled(snapshot.choreography.active)
                            }
                        } else {
                            stepCard(2, title: "Стиль и трек", subtitle: styleTrackSubtitle) {
                                stepEditor(2)
                            }
                            stepCard(3, title: "Выбор элементов и интервала", subtitle: snapshot.elementTag.isEmpty ? "Тег · Интервал" : "\(snapshot.elementTag) · \(Int(snapshot.intervalSec)) с") {
                                stepEditor(3)
                            }
                            stepCard(4, title: "Голосовой тренер", subtitle: ["Руслан", "Ирина", "Камила"][min(max(snapshot.selectedVoiceIndex, 0), 2)]) {
                                stepEditor(4)
                            }
                        }
                    }
                    .padding(.horizontal, MdChrome.contentHorizontalPadding)
                    .padding(.top, 8)
                    .padding(.bottom, 28)
                }
                Button(action: onStartDance) {
                    HStack(spacing: 8) {
                        Image(systemName: "play.fill").font(.system(size: 10, weight: .bold))
                        Text("Танцуем!").font(.system(size: 16, weight: .bold, design: .serif))
                    }
                    .frame(maxWidth: .infinity)
                    .frame(height: 48)
                    .background(RoundedRectangle(cornerRadius: 12)
                        .fill(ready ? MdColor.neonOrange : Color.white.opacity(0.16)))
                    .foregroundStyle(ready ? MdColor.oledTrueBlack : MdColor.textSecondary.opacity(0.65))
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

    private var modeControl: some View {
        HStack(spacing: 4) {
            modeButton("Фристайл", selected: !snapshot.choreography.enabled, enabled: false)
            modeButton("Хореография", selected: snapshot.choreography.enabled, enabled: true)
        }
        .padding(4)
        .background(RoundedRectangle(cornerRadius: 12).fill(Color.black.opacity(0.25))
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(MdColor.surfaceBorder)))
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("trainer.mode")
    }

    private func modeButton(_ title: String, selected: Bool, enabled: Bool) -> some View {
        Button { choreographyActions.setMode(enabled) } label: {
            Text(title)
                .font(.system(size: 13, weight: .semibold, design: .serif))
                .frame(maxWidth: .infinity)
                .frame(minHeight: 40)
                .foregroundStyle(selected ? Color.white : MdColor.textPrimary)
                .background(RoundedRectangle(cornerRadius: 9).fill(selected ? MdColor.neonOrange : .clear))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
        .accessibilityIdentifier(enabled ? "trainer.mode.choreography" : "trainer.mode.freestyle")
    }

    private func stepCard<Content: View>(_ step: Int, title: String, subtitle: String,
                                         @ViewBuilder content: () -> Content) -> some View {
        let expanded = snapshot.expandedStep == step
        let filled = stepGlow(step)
        return VStack(spacing: 0) {
            Button { onSetStep(step) } label: {
                HStack(alignment: .center, spacing: 10) {
                    Text(String(format: "%02d", step))
                        .font(.system(size: 10, weight: .bold))
                        .foregroundStyle(step == 1 ? Color.white : MdColor.textSecondary)
                        .frame(width: 24, height: 24)
                        .background(RoundedRectangle(cornerRadius: 6)
                            .fill(step == 1 ? MdColor.neonOrange : Color.white.opacity(0.08)))
                    VStack(alignment: .leading, spacing: 3) {
                        Text(title)
                            .font(.system(size: 14, weight: .semibold, design: .serif))
                            .foregroundStyle(MdColor.textPrimary)
                        Text("› \(subtitle)")
                            .font(.system(size: 11))
                            .foregroundStyle(MdColor.textSecondary)
                            .lineLimit(1)
                    }
                    Spacer(minLength: 2)
                    Image(systemName: expanded ? "chevron.up" : "chevron.down")
                        .font(.system(size: 10, weight: .semibold))
                        .foregroundStyle(MdColor.textSecondary)
                }
                .padding(12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("trainer.step.\(step)")
            if expanded {
                VStack(alignment: .leading, spacing: 10, content: content)
                    .padding(.horizontal, 10)
                    .padding(.bottom, 10)
            }
        }
        .background(RoundedRectangle(cornerRadius: 16).fill(MdColor.surfaceElevated))
        .overlay(RoundedRectangle(cornerRadius: 16)
            .stroke(filled ? MdColor.neonOrange.opacity(0.85) : MdColor.surfaceBorder, lineWidth: 1))
        .shadow(color: filled ? MdColor.neonOrange.opacity(0.16) : .clear, radius: 8, y: 3)
    }

    private func stepGlow(_ step: Int) -> Bool {
        switch step {
        case 1: return true
        case 2: return snapshot.choreography.enabled ? !snapshot.choreography.selectedID.isEmpty : !snapshot.styleName.isEmpty && !snapshot.trackTitle.isEmpty
        case 3: return !snapshot.elementTag.isEmpty && snapshot.intervalSec >= 2
        case 4: return true
        default: return false
        }
    }

    @ViewBuilder
    private func stepEditor(_ step: Int) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            switch step {
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
