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
    @State private var selectedTags: Set<String>
    @State private var speed: Double

    init(track: MusicTrackSnapshot, styles: [String], tags: [String], saving: Bool, error: String?,
         onSave: @escaping (MusicEditDraft) -> Void, onClose: @escaping () -> Void) {
        self.track = track; self.styles = styles; self.tags = tags; self.saving = saving
        self.error = error; self.onSave = onSave; self.onClose = onClose
        _title = State(initialValue: track.title)
        _style = State(initialValue: track.style)
        _selectedTags = State(initialValue: Set(track.tags))
        _speed = State(initialValue: track.basePlaybackSpeed)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Название") {
                    TextField("Название трека", text: $title).accessibilityIdentifier("music.edit.title")
                }
                Section("Стиль") {
                    Picker("Стиль", selection: $style) {
                        ForEach(styles, id: \.self) { Text($0).tag($0) }
                    }.accessibilityIdentifier("music.edit.style")
                }
                Section("Теги") {
                    ForEach(Array(Set(tags + track.tags)).sorted(), id: \.self) { tag in
                        Toggle(tag, isOn: Binding(get: { selectedTags.contains(tag) }, set: {
                            if $0 { selectedTags.insert(tag) } else { selectedTags.remove(tag) }
                        }))
                    }
                }
                Section("Скорость прослушивания") {
                    Slider(value: $speed, in: 0.4...2, step: 0.1)
                        .accessibilityIdentifier("music.edit.speed")
                    Text(String(format: "%.1f×", speed))
                }
                if let error { Text(error).foregroundStyle(.red) }
                if saving { ProgressView("Сохранение…") }
            }
            .disabled(saving)
            .navigationTitle("Редактировать трек")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Отмена", action: onClose).disabled(saving) }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Сохранить") {
                        onSave(MusicEditDraft(id: track.id, title: title, style: style,
                            tags: selectedTags.sorted(), speed: speed))
                    }.disabled(saving || title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || !styles.contains(style))
                    .accessibilityIdentifier("music.edit.save")
                }
            }
        }
        .tint(MdColor.neonOrange)
        .preferredColorScheme(.dark)
        .interactiveDismissDisabled(saving)
    }
}
