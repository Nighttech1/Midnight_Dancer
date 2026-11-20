import SwiftUI

/// Choreography preview and a simple sequence editor for iOS.
struct ProEditorShellView: View {
    let snapshot: SequenceEditorSnapshot
    var onBack: () -> Void
    var onTogglePlay: () -> Void
    var onSetTitle: (String) -> Void
    var onSetStyle: (String) -> Void
    var onSave: () -> Void
    var elementChoices: () -> [SequenceElementChoice]
    var onAddElement: (String) -> Void

    @State private var glowPhase = false
    @State private var selectingElement = false
    @State private var options: [SequenceElementChoice] = []
    @State private var editingTitle = false
    @State private var titleDraft = ""
    @State private var savedMessage = false

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
                sequenceControls
                Spacer(minLength: 0)
            }
        }
        .sheet(isPresented: $selectingElement) {
            NavigationStack {
                List {
                    if options.isEmpty {
                        Text("В этом стиле пока нет элементов с видео.")
                            .foregroundStyle(.secondary)
                    }
                    ForEach(options) { option in
                        Button(option.title) {
                            clearedSavedMessage()
                            onAddElement(option.id)
                            selectingElement = false
                        }
                        .accessibilityIdentifier("choreo.choice.\(option.id)")
                    }
                }
                .navigationTitle("Добавить элемент")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Закрыть") { selectingElement = false }
                    }
                }
            }
        }
        .alert("Название хореографии", isPresented: $editingTitle) {
            TextField("Название", text: $titleDraft)
            Button("Отмена", role: .cancel) {}
            Button("Готово") {
                let title = titleDraft.trimmingCharacters(in: .whitespacesAndNewlines)
                if !title.isEmpty {
                    clearedSavedMessage()
                    onSetTitle(title)
                }
            }
        }
        .onChange(of: snapshot.saveCount) { _ in savedMessage = true }
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

            Button {
                titleDraft = snapshot.title
                editingTitle = true
            } label: {
                HStack {
                    Text(snapshot.title)
                        .font(MdType.titleLarge)
                        .foregroundStyle(MdColor.textPrimary)
                        .lineLimit(1)
                    Image(systemName: "pencil")
                        .foregroundStyle(MdColor.neonOrange)
                }
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("choreo.rename")
            Spacer()
            Button("Сохранить") {
                clearedSavedMessage()
                onSave()
            }
            .foregroundStyle(MdColor.neonOrange)
            .accessibilityIdentifier("choreo.save")
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

    private var sequenceControls: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                if snapshot.elementClips.isEmpty && !snapshot.availableStyles.isEmpty {
                    Menu {
                        ForEach(snapshot.availableStyles, id: \.self) { style in
                            Button(style) {
                                clearedSavedMessage()
                                onSetStyle(style)
                            }
                        }
                    } label: {
                        Label(snapshot.styleName, systemImage: "chevron.up.chevron.down")
                    }
                    .accessibilityIdentifier("choreo.style")
                } else {
                    Text(snapshot.styleName).foregroundStyle(MdColor.textSecondary)
                }
                Spacer()
                Button {
                    options = elementChoices()
                    selectingElement = true
                } label: {
                    Label("Элемент", systemImage: "plus.circle")
                }
                .accessibilityIdentifier("choreo.addElement")
            }
            .foregroundStyle(MdColor.neonOrange)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack {
                    ForEach(snapshot.elementClips.indices, id: \.self) { index in
                        Text(snapshot.elementClips[index].label)
                            .padding(9)
                            .background(MdColor.surfaceElevated, in: RoundedRectangle(cornerRadius: 8))
                    }
                }
            }
            if let error = snapshot.saveError {
                Text(error).foregroundStyle(.red)
                    .accessibilityIdentifier("choreo.saveError")
            } else if savedMessage {
                Text("Хореография сохранена").foregroundStyle(MdColor.textSecondary)
                    .accessibilityIdentifier("choreo.saved")
            }
        }
        .padding(.horizontal, MdChrome.contentHorizontalPadding)
        .padding(.bottom, 80)
    }

    private func clearedSavedMessage() {
        savedMessage = false
    }
}
