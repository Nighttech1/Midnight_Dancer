import SwiftUI

/// Same four editable values as Android: A/B, passes/rest. Voice/preparation remain saved internals.
struct TrainerChoreographyPanel: View {
    let snapshot: TrainerChoreographyState
    let actions: TrainerChoreographyActions
    private var editable: Bool { snapshot.accessGranted && !snapshot.active && !snapshot.loading }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Picker("Хореография", selection: Binding(get: { snapshot.selectedID }, set: actions.select)) {
                Text("Выберите хореографию").tag("")
                ForEach(snapshot.choices) { Text($0.title).tag($0.id) }
            }
            .disabled(snapshot.active)
            .accessibilityIdentifier("trainer.choreography.choice")
            Button { actions.part("toggle") } label: {
                HStack {
                    Text("Часть хореографии")
                    Spacer()
                    Text("Pro").font(.caption.bold())
                    Image(systemName: snapshot.partExpanded ? "chevron.up" : "chevron.down")
                }
                .font(.system(size: 14, weight: .semibold))
                .padding(12)
                .frame(minHeight: 44)
                .background(RoundedRectangle(cornerRadius: 12).fill(MdColor.surfaceElevated)
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(MdColor.neonOrange.opacity(0.5))))
            }
            .accessibilityIdentifier("trainer.part.open")
            if snapshot.partExpanded {
                VStack(spacing: 10) {
                    HStack(spacing: 10) {
                        field("Начало", key: "start", value: snapshot.start)
                        field("Конец", key: "end", value: snapshot.end)
                    }
                    HStack(spacing: 10) {
                        field("Проходы", key: "passes", value: snapshot.passes)
                        field("Отдых, с", key: "rest", value: snapshot.rest)
                    }
                    .opacity(editable ? 1 : 0.45)
                    HStack {
                        Button("До конца") { actions.part("end") }.disabled(!editable)
                        Spacer()
                        Text("1–99 проходов · отдых 0–120 с").font(.caption2)
                    }
                    if snapshot.revisionNeedsConfirmation {
                        Text("Постановка изменена. Проверьте границы участка.").font(.footnote)
                        Button("Границы проверены") { actions.part("confirm") }.disabled(!editable)
                    }
                    if snapshot.loading { ProgressView("Подготовка…") }
                    if snapshot.active || snapshot.pass > 0 {
                        Text(snapshot.pass > 0 ? "Проход \(snapshot.pass) из \(snapshot.totalPasses)" : snapshot.phase)
                            .font(.headline)
                        Text(snapshot.currentLabel.isEmpty ? snapshot.phase : snapshot.currentLabel)
                        if snapshot.remainingMs > 0 { Text("\((snapshot.remainingMs + 999) / 1000) с").monospacedDigit() }
                    }
                    if snapshot.active {
                        HStack {
                            Button(snapshot.paused ? "Продолжить" : "Пауза") { actions.part(snapshot.paused ? "resume" : "pause") }
                            Button("Стоп") { actions.part("stop") }
                        }
                    } else {
                        HStack {
                            Button("Начать отработку") { actions.part("start") }
                                .disabled(!editable || snapshot.selectedID.isEmpty)
                                .accessibilityIdentifier("trainer.part.start")
                            Button("Сбросить") { actions.part("reset") }
                                .disabled(!editable || snapshot.selectedID.isEmpty)
                        }
                    }
                    if !snapshot.accessGranted {
                        Text(snapshot.accessLabel).font(.footnote).foregroundStyle(MdColor.textSecondary)
                        Button("О подписке") { actions.part("offer") }
                    }
                }
                .padding(12)
                .background(RoundedRectangle(cornerRadius: 12).fill(MdColor.surfaceElevated))
            }
            if let message = snapshot.message {
                Text(message).font(.footnote).foregroundStyle(MdColor.neonOrange)
                    .accessibilityIdentifier("trainer.choreography.message")
            }
        }
        .tint(MdColor.neonOrange)
    }
    private func field(_ label: String, key: String, value: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label).font(.caption).foregroundStyle(MdColor.textSecondary)
            TextField(label, text: Binding(get: { value }, set: { actions.field(key, $0) }))
                .keyboardType(.numbersAndPunctuation)
                .textFieldStyle(.roundedBorder)
                .disabled(!editable)
                .accessibilityIdentifier("trainer.part.\(key)")
        }.frame(maxWidth: .infinity)
        .opacity(editable ? 1 : 0.45)
    }
}
