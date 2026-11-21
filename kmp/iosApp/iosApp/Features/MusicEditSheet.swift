import SwiftUI

struct MusicEditSheet: View {
    let track: MusicTrackSnapshot
    let styles: [String]
    let tags: [String]
    let saving: Bool
    let error: String?
    var onSave: (MusicEditDraft) -> Void
    var onClose: () -> Void
    @State private var title: String
    @State private var style: String
    @State private var tag: String
    @State private var didChangeTag = false
    @State private var speed: Double

    init(track: MusicTrackSnapshot, styles: [String], tags: [String], saving: Bool, error: String?,
         onSave: @escaping (MusicEditDraft) -> Void, onClose: @escaping () -> Void) {
        self.track = track; self.styles = styles; self.tags = tags; self.saving = saving
        self.error = error; self.onSave = onSave; self.onClose = onClose
        _title = State(initialValue: track.title)
        _style = State(initialValue: track.style)
        _tag = State(initialValue: track.tags.first(where: { !$0.isEmpty }) ?? "")
        _speed = State(initialValue: min(2, max(0.4, track.basePlaybackSpeed)))
    }

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    Text("Редактировать трек")
                        .font(MdType.titleLarge)
                        .accessibilityAddTraits(.isHeader)
                    if let error {
                        Text(error).foregroundStyle(MdColor.danger)
                            .accessibilityIdentifier("music.edit.error")
                    }
                    if saving { ProgressView("Сохраняем…") }

                    fieldLabel("Название")
                    TextField("Имя трека", text: $title)
                        .font(MdType.bodyLarge)
                        .textFieldStyle(.plain)
                        .padding(.horizontal, 14)
                        .frame(minHeight: 46)
                        .background(fieldBackground)
                        .onChange(of: title) { value in
                            if value.count > 80 { title = String(value.prefix(80)) }
                        }
                        .accessibilityIdentifier("music.edit.title")

                    fieldLabel("Стиль")
                    selection("Стиль", value: style,
                              options: stableChoices(styles, keeping: style)) { style = $0 }
                        .accessibilityIdentifier("music.edit.style")

                    fieldLabel("Тег")
                    selection("Тег", value: tag,
                              options: stableChoices([""] + tags, keeping: tag)) { value in
                        if value != tag { didChangeTag = true }
                        tag = value
                    }
                    .accessibilityIdentifier("music.edit.tag")

                    fieldLabel("Скорость прослушивания: \(String(format: "%.1f", speed))×")
                    Slider(value: $speed, in: 0.4...2)
                        .accessibilityIdentifier("music.edit.speed")
                }
                .padding(20)
            }
            .scrollDismissesKeyboard(.interactively)
            HStack(spacing: 16) {
                Button("Отмена", role: .cancel, action: onClose)
                    .foregroundStyle(MdColor.textSecondary)
                Spacer()
                Button("Сохранить", action: save)
                    .foregroundStyle(MdColor.neonOrange)
                    .accessibilityIdentifier("music.edit.save")
            }
            .buttonStyle(.plain)
            .font(MdType.brandedControl)
            .padding(.horizontal, 20)
            .padding(.vertical, 16)
        }
        .disabled(saving)
        .foregroundStyle(MdColor.textPrimary)
        .tint(MdColor.neonOrange)
        .background(MdColor.oledTrueBlack.ignoresSafeArea())
        .preferredColorScheme(.dark)
        .presentationDetents([.large])
        .interactiveDismissDisabled(saving)
    }

    private var fieldBackground: some View {
        RoundedRectangle(cornerRadius: 12)
            .fill(MdColor.surfaceElevated)
            .overlay {
                RoundedRectangle(cornerRadius: 12).strokeBorder(MdColor.neonOrange.opacity(0.45))
            }
    }

    private func fieldLabel(_ text: String) -> some View {
        Text(text)
            .font(MdType.brandedBody)
            .foregroundStyle(MdColor.textSecondary)
    }

    private func selection(_ label: String, value: String, options: [String],
                           onChoose: @escaping (String) -> Void) -> some View {
        Menu {
            ForEach(options, id: \.self) { option in
                Button { onChoose(option) } label: {
                    if option == value { Label(option.isEmpty ? "Без тега" : option, systemImage: "checkmark") }
                    else { Text(option.isEmpty ? "Без тега" : option) }
                }
            }
        } label: {
            HStack {
                Text(value.isEmpty ? "Выбрать" : value)
                Spacer()
                Image(systemName: "chevron.down")
                    .foregroundStyle(MdColor.neonOrange)
            }
            .font(MdType.brandedControl)
            .foregroundStyle(MdColor.textPrimary)
            .padding(.horizontal, 14)
            .frame(minHeight: 46)
            .background(fieldBackground)
        }
        .accessibilityLabel(label)
        .accessibilityValue(value)
    }

    private func stableChoices(_ options: [String], keeping value: String) -> [String] {
        var seen = Set<String>()
        return (options + [value]).filter { seen.insert($0).inserted }
    }

    private func save() {
        let name = title.trimmingCharacters(in: .whitespacesAndNewlines)
        let savedTags = didChangeTag ? (tag.isEmpty ? [] : [tag]) : track.tags
        onSave(MusicEditDraft(id: track.id, title: name.isEmpty ? track.title : name,
                             style: style, tags: savedTags, speed: speed))
    }
}
