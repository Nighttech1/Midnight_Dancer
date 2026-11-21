import AVFoundation
import UIKit
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
            ElementVideoPreview(url: url, tint: flame, suspended: busy || choosingFile)
                .id(url)
                .accessibilityElement(children: .contain)
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
    let url: URL
    let tint: Color
    let suspended: Bool

    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var playback = ElementPreviewPlayback()

    private let speeds: [(rate: Float, title: String)] = [
        (0.5, "0.5×"), (0.75, "0.75×"), (1, "1×"), (1.25, "1.25×"), (1.5, "1.5×")
    ]

    var body: some View {
        ZStack(alignment: .bottom) {
            ElementPreviewSurface(player: playback.player)
                .accessibilityHidden(true)
            if playback.failed {
                Label("Видео недоступно", systemImage: "exclamationmark.triangle")
                    .font(MdType.bodyMedium)
                    .foregroundStyle(MdColor.textSecondary)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .padding()
            } else {
                Button {
                    playback.toggle()
                } label: {
                    Image(systemName: playback.wantsToPlay ? "pause.fill" : "play.fill")
                        .font(.system(size: 24, weight: .semibold))
                        .foregroundStyle(.white)
                        .frame(width: 62, height: 62)
                        .background(.black.opacity(0.52), in: Circle())
                        .overlay { Circle().strokeBorder(tint.opacity(0.72)).allowsHitTesting(false) }
                }
                .buttonStyle(.plain)
                .disabled(!playback.ready || suspended || scenePhase != .active)
                .accessibilityLabel(playback.wantsToPlay ? "Пауза" : "Воспроизвести видео")
                .accessibilityIdentifier("element.preview.play")
                .frame(maxWidth: .infinity, maxHeight: .infinity)

                controls
            }
        }
        .onAppear { playback.load(url) }
        .onChange(of: scenePhase) { if $0 != .active { playback.pause() } }
        .onChange(of: suspended) { if $0 { playback.pause() } }
        .onDisappear { playback.unload() }
    }

    private var controls: some View {
        VStack(spacing: 1) {
            HStack(spacing: 6) {
                Text(timeLabel(playback.position))
                Slider(
                    value: Binding(get: { playback.position }, set: playback.seek),
                    in: 0...max(playback.duration, 0.001),
                    onEditingChanged: playback.setScrubbing
                )
                .tint(.white)
                .disabled(playback.duration <= 0)
                .accessibilityLabel("Позиция видео")
                .accessibilityValue(timeLabel(playback.position))
                .accessibilityIdentifier("element.preview.seek")
                Text(timeLabel(playback.duration))
            }
            .font(.caption.monospacedDigit())
            .foregroundStyle(.white.opacity(0.85))

            HStack(spacing: 5) {
                ForEach(speeds.indices, id: \.self) { index in
                    let speed = speeds[index]
                    let selected = playback.speed == speed.rate
                    Button {
                        playback.setSpeed(speed.rate)
                    } label: {
                        Text(speed.title)
                            .font(.system(size: 12, weight: .semibold).monospacedDigit())
                            .foregroundStyle(.white)
                            .lineLimit(1)
                            .minimumScaleFactor(0.7)
                            .frame(maxWidth: .infinity, minHeight: 27)
                            .background(selected ? tint : .black.opacity(0.65), in: Capsule())
                            .overlay { Capsule().strokeBorder(.white.opacity(selected ? 0 : 0.24)).allowsHitTesting(false) }
                            .frame(minHeight: 44)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Скорость \(speed.title)")
                    .accessibilityAddTraits(selected ? .isSelected : [])
                    .accessibilityIdentifier("element.preview.speed.\(index)")
                }
            }
        }
        .padding(.horizontal, 10)
        .padding(.top, 8)
        .padding(.bottom, 3)
        .background {
            LinearGradient(colors: [.clear, .black.opacity(0.92)], startPoint: .top, endPoint: .bottom)
                .allowsHitTesting(false)
        }
    }

    private func timeLabel(_ seconds: Double) -> String {
        guard seconds.isFinite, seconds >= 0, seconds < Double(Int.max) else { return "00:00" }
        let whole = Int64(seconds)
        return String(format: "%02lld:%02lld", whole / 60, whole % 60)
    }
}

@MainActor
private final class ElementPreviewPlayback: ObservableObject {
    let player = AVPlayer()
    @Published private(set) var ready = false
    @Published private(set) var failed = false
    @Published private(set) var wantsToPlay = false
    @Published private(set) var duration = 0.0
    @Published private(set) var position = 0.0
    @Published private(set) var speed: Float = 1

    private var timeObserver: Any?
    private var statusObserver: NSKeyValueObservation?
    private var endObserver: NSObjectProtocol?
    private var failureObserver: NSObjectProtocol?
    private var scrubbing = false
    private var seeking = false
    private var seekGeneration = 0
    private var ended = false

    func load(_ url: URL) {
        unload()
        let item = AVPlayerItem(url: url)
        item.audioTimePitchAlgorithm = .spectral
        player.actionAtItemEnd = .pause
        player.replaceCurrentItem(with: item)
        statusObserver = item.observe(\.status, options: [.initial, .new]) { [weak self] item, _ in
            Task { @MainActor in
                guard let self, self.player.currentItem === item else { return }
                if item.status == .failed { self.fail() }
                else if item.status == .readyToPlay {
                    self.updateDuration(from: item)
                    self.ready = true
                }
            }
        }
        timeObserver = player.addPeriodicTimeObserver(
            forInterval: CMTime(value: 1, timescale: 10), queue: .main
        ) { [weak self, weak item] time in
            Task { @MainActor in
                guard let self, let item, self.player.currentItem === item,
                      !self.scrubbing, !self.seeking, !self.ended else { return }
                self.updateDuration(from: item)
                let seconds = time.seconds
                if seconds.isFinite && seconds >= 0 { self.position = min(seconds, self.duration) }
            }
        }
        endObserver = NotificationCenter.default.addObserver(
            forName: AVPlayerItem.didPlayToEndTimeNotification, object: item, queue: .main
        ) { [weak self, weak item] _ in
            Task { @MainActor in
                guard let self, let item, self.player.currentItem === item,
                      !self.scrubbing, !self.seeking else { return }
                self.pause()
                self.ended = true
                self.position = self.duration
            }
        }
        failureObserver = NotificationCenter.default.addObserver(
            forName: AVPlayerItem.failedToPlayToEndTimeNotification, object: item, queue: .main
        ) { [weak self, weak item] _ in
            Task { @MainActor in
                guard let self, let item, self.player.currentItem === item else { return }
                self.fail()
            }
        }
    }

    func toggle() {
        guard ready else { return }
        if wantsToPlay {
            pause()
        } else {
            wantsToPlay = true
            if ended || (duration > 0 && position >= duration) { seek(0) }
            else { playIfReady() }
        }
    }

    func pause() {
        wantsToPlay = false
        player.pause()
    }

    func setSpeed(_ value: Float) {
        speed = value
        playIfReady()
    }

    func setScrubbing(_ editing: Bool) {
        scrubbing = editing
        if editing {
            player.pause()
        } else {
            playIfReady()
        }
    }

    func seek(_ seconds: Double) {
        guard ready, seconds.isFinite, duration > 0, let item = player.currentItem else { return }
        position = min(max(seconds, 0), duration)
        ended = position >= duration
        if ended { pause() }
        seeking = true
        seekGeneration += 1
        let generation = seekGeneration
        player.pause()
        item.cancelPendingSeeks()
        player.seek(to: CMTime(seconds: position, preferredTimescale: 600),
                    toleranceBefore: .zero, toleranceAfter: .zero) { [weak self, weak item] completed in
            Task { @MainActor in
                guard let self, let item, self.player.currentItem === item,
                      self.seekGeneration == generation else { return }
                self.seeking = false
                if completed { self.playIfReady() }
            }
        }
    }

    func unload() {
        pause()
        if let timeObserver { player.removeTimeObserver(timeObserver) }
        if let endObserver { NotificationCenter.default.removeObserver(endObserver) }
        if let failureObserver { NotificationCenter.default.removeObserver(failureObserver) }
        timeObserver = nil
        endObserver = nil
        failureObserver = nil
        statusObserver?.invalidate()
        statusObserver = nil
        player.currentItem?.cancelPendingSeeks()
        player.replaceCurrentItem(with: nil)
        ready = false
        failed = false
        ended = false
        seeking = false
        scrubbing = false
        position = 0
        duration = 0
    }

    private func playIfReady() {
        guard wantsToPlay, ready, !scrubbing, !seeking else { return }
        player.playImmediately(atRate: speed)
    }

    private func updateDuration(from item: AVPlayerItem) {
        let length = item.duration.seconds
        if length.isFinite && length > 0 && length != duration { duration = length }
    }

    private func fail() {
        pause()
        ready = false
        failed = true
    }
}

private struct ElementPreviewSurface: UIViewRepresentable {
    let player: AVPlayer

    func makeUIView(context: Context) -> ElementPreviewPlayerView {
        let view = ElementPreviewPlayerView()
        view.playerLayer.videoGravity = .resizeAspect
        view.playerLayer.player = player
        return view
    }

    func updateUIView(_ view: ElementPreviewPlayerView, context: Context) {
        if view.playerLayer.player !== player { view.playerLayer.player = player }
    }

    static func dismantleUIView(_ view: ElementPreviewPlayerView, coordinator: ()) {
        view.playerLayer.player = nil
    }
}

private final class ElementPreviewPlayerView: UIView {
    override class var layerClass: AnyClass { AVPlayerLayer.self }

    var playerLayer: AVPlayerLayer { layer as! AVPlayerLayer }
}
