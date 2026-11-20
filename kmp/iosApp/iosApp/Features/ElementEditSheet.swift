import AVKit
import PhotosUI
import SwiftUI
import UniformTypeIdentifiers

struct ElementEditSheet: View {
    let categories: [String]
    let saving: Bool
    let saveError: String?
    var onSave: (ElementEditorSnapshot) -> Void
    var onClose: () -> Void

    @State private var draft: ElementEditorSnapshot
    @State private var photo: PhotosPickerItem?
    @State private var choosingFile = false
    @State private var importing = false
    @State private var submitted = false
    @State private var importTask: Task<Void, Never>?
    @State private var importError: String?
    @State private var pendingVideo: OwnedElementVideo?

    init(
        initialDraft: ElementEditorSnapshot,
        categories: [String],
        saving: Bool,
        saveError: String?,
        onSave: @escaping (ElementEditorSnapshot) -> Void,
        onClose: @escaping () -> Void
    ) {
        self.categories = categories
        self.saving = saving
        self.saveError = saveError
        self.onSave = onSave
        self.onClose = onClose
        _draft = State(initialValue: initialDraft)
    }

    private var busy: Bool { importing || saving || submitted }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Название", text: $draft.title)
                        .accessibilityIdentifier("element.name")
                    LabeledContent("Стиль", value: draft.styleName)
                    Picker("Категория", selection: $draft.tag) {
                        Text("Без категории").tag(nil as String?)
                        ForEach(categories, id: .self) { category in
                            Text(category).tag(Optional(category))
                        }
                    }
                    TextField("Описание", text: $draft.description, axis: .vertical)
                    VStack(alignment: .leading) {
                        Text("Прогресс изучения: \(draft.masteryPercent)%")
                        Slider(value: Binding(
                            get: { Double(draft.masteryPercent) },
                            set: { draft.masteryPercent = Int($0.rounded()) }
                        ), in: 0...100, step: 1)
                    }
                }

                Section("Видео") {
                    if let media = draft.videoUri, let url = OwnedElementVideo.resolve(media) {
                        ElementVideoPreview(url: url)
                            .id(url)
                            .frame(minHeight: 190)
                            .accessibilityIdentifier("element.player")
                    } else if draft.videoUri != nil {
                        Text("Видео недоступно. Можно выбрать другой файл.")
                            .foregroundStyle(.secondary)
                    }
                    PhotosPicker(selection: $photo, matching: .videos, photoLibrary: .shared()) {
                        Label("Выбрать из Фото", systemImage: "photo.on.rectangle")
                    }
                    .accessibilityIdentifier("element.choosePhoto")
                    Button {
                        choosingFile = true
                    } label: {
                        Label("Выбрать из Файлов", systemImage: "folder")
                    }
                    .accessibilityIdentifier("element.chooseFile")
                }

                if busy {
                    ProgressView(importing ? "Копирую видео…" : "Сохраняю элемент…")
                }
                if let message = importError ?? saveError {
                    Text(message)
                        .foregroundStyle(.red)
                        .accessibilityIdentifier("element.error")
                }
            }
            .disabled(busy)
            .navigationTitle(draft.cardId == nil ? "Новый элемент" : "Элемент")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Закрыть", action: cancel)
                        .disabled(busy)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Сохранить", action: save)
                        .disabled(busy || draft.title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                        .accessibilityIdentifier("element.save")
                }
            }
        }
        .tint(MdColor.neonOrange)
        .preferredColorScheme(.dark)
        .interactiveDismissDisabled()
        .fileImporter(isPresented: $choosingFile, allowedContentTypes: [.movie]) { result in
            switch result {
            case .success(let source):
                startImport {
                    try await OwnedElementVideo.importFile(source, requiresSecurityScope: true)
                }
            case .failure(let error):
                if (error as? CocoaError)?.code != .userCancelled {
                    importError = "Не удалось открыть файл."
                }
            }
        }
        .onChange(of: photo) { item in
            guard let item else { return }
            photo = nil
            startImport {
                guard let transfer = try await item.loadTransferable(type: PickedElementVideo.self) else {
                    throw OwnedElementVideo.ImportError.invalidVideo
                }
                defer { try? FileManager.default.removeItem(at: transfer.url) }
                return try await OwnedElementVideo.importFile(transfer.url, requiresSecurityScope: false)
            }
        }
        .onChange(of: saving) { value in
            if !value { submitted = false }
        }
        .onChange(of: saveError) { value in
            if value != nil && !saving { submitted = false }
        }
        .onDisappear {
            importTask?.cancel()
            // Do not discard a file after the save was handed to the catalog.
            // The catalog transaction may commit while this sheet disappears.
        }
    }

    private func cancel() {
        guard !busy else { return }
        pendingVideo?.removeUncommittedFile()
        pendingVideo = nil
        onClose()
    }

    private func save() {
        guard !busy else { return }
        submitted = true
        importError = nil
        onSave(draft)
    }

    private func startImport(_ operation: @escaping @Sendable () async throws -> OwnedElementVideo) {
        guard !busy else { return }
        importing = true
        importError = nil
        importTask = Task { @MainActor in
            defer { importing = false }
            do {
                let transfer = Task.detached(priority: .userInitiated, operation: operation)
                let video = try await withTaskCancellationHandler {
                    try await transfer.value
                } onCancel: {
                    transfer.cancel()
                }
                guard !Task.isCancelled else {
                    video.removeUncommittedFile()
                    return
                }
                pendingVideo?.removeUncommittedFile()
                pendingVideo = video
                draft.videoUri = video.reference
                draft.durationMs = video.durationMs
            } catch is CancellationError {
                // A temporary transfer was cancelled; keep the previous draft.
            } catch {
                if !Task.isCancelled {
                    importError = "Не удалось скопировать видео. Проверь файл и свободное место."
                }
            }
        }
    }
}

private struct ElementVideoPreview: View {
    @State private var player: AVPlayer

    init(url: URL) {
        _player = State(initialValue: AVPlayer(url: url))
    }

    var body: some View {
        VideoPlayer(player: player)
            .onDisappear { player.pause() }
    }
}
