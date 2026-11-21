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
        VStack(spacing: 0) {
            ScrollView {
                editorContent
                    .frame(maxWidth: 520)
                    .padding(.horizontal, 18)
                    .padding(.vertical, 14)
                    .frame(maxWidth: .infinity)
            }
            .scrollDismissesKeyboard(.interactively)

            editorFooter
        }
        .background(MdColor.oledTrueBlack.ignoresSafeArea())
        .tint(flame)
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

    // Match the Android element editor's action color on its OLED surface.
    private let flame = Color(red: 1, green: 1.0 / 3.0, blue: 0)
    private let videoSurface = Color(red: 5.0 / 255.0, green: 6.0 / 255.0, blue: 12.0 / 255.0)

    private var editorContent: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(draft.cardId == nil ? "Новый элемент" : "Редактировать элемент")
                .font(MdType.titleLarge.weight(.bold))
                .foregroundStyle(MdColor.textPrimary)
                .accessibilityAddTraits(.isHeader)
                .accessibilityIdentifier("editor.element")

            TextField("Название", text: $draft.title)
                .font(MdType.bodyLarge)
                .padding(.horizontal, 14)
                .frame(minHeight: 46)
                .background(fieldBackground)
                .accessibilityIdentifier("element.name")

            videoStage
            uploadActions
            descriptionFields
                .padding(.top, 4)
        }
        .foregroundStyle(MdColor.textPrimary)
        .disabled(busy)
    }

    private var videoStage: some View {
        let shape = RoundedRectangle(cornerRadius: 20)
        return ZStack {
            shape.fill(videoSurface)
            videoPreview
        }
        .aspectRatio(9.0 / 16.0, contentMode: .fit)
        .clipShape(shape)
        .overlay { shape.strokeBorder(.white.opacity(0.10), lineWidth: 1).allowsHitTesting(false) }
    }

    @ViewBuilder private var videoPreview: some View {
        if let media = draft.videoUri, let url = OwnedElementVideo.resolve(media) {
            ElementVideoPreview(url: url)
                .id(url)
                .accessibilityIdentifier("element.player")
        } else if draft.videoUri != nil {
            Label("Видео недоступно", systemImage: "exclamationmark.triangle")
                .font(MdType.bodyMedium)
                .foregroundStyle(MdColor.textSecondary)
                .padding()
        } else {
            Image(systemName: "video")
                .font(.system(size: 32, weight: .light))
                .foregroundStyle(.white.opacity(0.25))
                .accessibilityLabel("Видео не выбрано")
        }
    }

    private var uploadActions: some View {
        HStack(spacing: 8) {
            PhotosPicker(selection: $photo, matching: .videos, photoLibrary: .shared()) {
                uploadLabel("Выбрать из Фото", icon: "photo.on.rectangle")
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("element.choosePhoto")
            Button { choosingFile = true } label: {
                uploadLabel("Выбрать из Файлов", icon: "folder")
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("element.chooseFile")
        }
    }

    private func uploadLabel(_ title: String, icon: String) -> some View {
        Label(title, systemImage: icon)
            .font(MdType.bodyMedium.weight(.semibold))
            .multilineTextAlignment(.center)
            .padding(.horizontal, 8)
            .padding(.vertical, 8)
            .frame(maxWidth: .infinity, minHeight: 46)
            .foregroundStyle(flame)
            .background(flame.opacity(0.10), in: RoundedRectangle(cornerRadius: 12))
    }

    private var descriptionFields: some View {
        VStack(alignment: .leading, spacing: 14) {
            labeledField("Стиль") {
                Text(draft.styleName)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            labeledField("Категория") {
                Menu {
                    Button("Без категории") { draft.tag = nil }
                    ForEach(categories, id: \.self) { category in
                        Button(category) { draft.tag = category }
                    }
                } label: {
                    HStack {
                        Text(draft.tag ?? "Без категории")
                        Spacer()
                        Image(systemName: "chevron.down")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(flame)
                    }
                    .foregroundStyle(MdColor.textPrimary)
                    .contentShape(Rectangle())
                }
            }
            VStack(alignment: .leading, spacing: 5) {
                fieldLabel("Прогресс изучения: \(draft.masteryPercent)%")
                Slider(value: masteryValue, in: 0...100, step: 1)
            }
            labeledField("Описание") {
                TextField("Добавить описание", text: $draft.description, axis: .vertical)
            }
        }
    }

    private func labeledField<Content: View>(_ title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            fieldLabel(title)
            content()
                .font(MdType.bodyLarge)
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .frame(minHeight: 46)
                .background(fieldBackground)
        }
    }

    private func fieldLabel(_ title: String) -> some View {
        Text(title)
            .font(MdType.bodyMedium.weight(.semibold))
            .foregroundStyle(MdColor.textSecondary)
    }

    private var fieldBackground: some View {
        RoundedRectangle(cornerRadius: 14)
            .fill(.white.opacity(0.04))
            .overlay { RoundedRectangle(cornerRadius: 14).strokeBorder(.white.opacity(0.10), lineWidth: 1) }
    }

    private var masteryValue: Binding<Double> {
        Binding(get: { Double(draft.masteryPercent) }, set: { draft.masteryPercent = Int($0.rounded()) })
    }

    private var editorFooter: some View {
        VStack(alignment: .trailing, spacing: 4) {
            if busy {
                ProgressView(importing ? "Копирую видео…" : "Сохраняю элемент…")
                    .font(MdType.bodyMedium)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            if let message = importError ?? saveError {
                Text(message)
                    .font(MdType.bodyMedium)
                    .foregroundStyle(MdColor.danger)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .accessibilityIdentifier("element.error")
            }
            HStack(spacing: 12) {
                Spacer()
                Button("Отмена", action: cancel)
                    .buttonStyle(.plain)
                    .font(MdType.bodyLarge)
                    .foregroundStyle(.white.opacity(0.60))
                    .frame(minWidth: 44, minHeight: 44)
                    .disabled(busy)
                    .accessibilityIdentifier("element.cancel")
                Button("Сохранить", action: save)
                    .buttonStyle(.plain)
                    .font(MdType.bodyLarge.weight(.semibold))
                    .foregroundStyle(flame.opacity(canSave ? 1 : 0.40))
                    .frame(minWidth: 44, minHeight: 44)
                    .disabled(!canSave)
                    .accessibilityIdentifier("element.save")
            }
        }
        .padding(.horizontal, 18)
        .padding(.vertical, 6)
        .frame(maxWidth: 520)
        .frame(maxWidth: .infinity)
        .background(MdColor.oledTrueBlack)
    }

    private var canSave: Bool {
        !busy && !draft.title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
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
