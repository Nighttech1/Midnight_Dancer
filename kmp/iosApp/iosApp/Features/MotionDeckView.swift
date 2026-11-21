import SwiftUI
import UniformTypeIdentifiers

/// Music Motion Deck — track list + pulse visualizer for playing track.
struct MotionDeckView: View {
    let snapshot: MusicFeatureSnapshot
    var onSelect: (String) -> Void
    var onSearch: (String) -> Void
    var onUpload: () -> Void
    var onEdit: (String) -> Void
    var onDelete: (String) -> Void
    var onImport: (OwnedMusicAudio) async -> Bool
    var onSave: (MusicEditDraft) -> Void
    var onCloseEdit: () -> Void
    var onStop: (String) -> Void
    var onSeek: (String, Float) -> Void

    @StateObject private var playback = MusicPlaybackController()
    @State private var choosingFile = false
    @State private var importing = false
    @State private var importError: String?
    @State private var pendingAudio: OwnedMusicAudio?
    @State private var importTask: Task<Void, Never>?
    @Environment(\.scenePhase) private var scenePhase

    private var playbackKey: String {
        guard let track = snapshot.selectedTrack else { return "" }
        return "\(track.id)|\(track.mediaUri ?? "")|\(track.isPlaying)|\(track.basePlaybackSpeed)"
    }

    private func syncPlayback() {
        playback.update(snapshot.selectedTrack, position: onSeek, stop: onStop)
    }

    private func importAudio(_ url: URL) {
        importing = true
        importError = nil
        importTask = Task { @MainActor in
            do {
                let audio = try await Task.detached { try await OwnedMusicAudio.importFile(url) }.value
                pendingAudio = audio
                let saved = await onImport(audio)
                if saved { pendingAudio = nil }
                else { importError = "Не удалось сохранить музыку. Повторите сохранение." }
            } catch {
                importError = error.localizedDescription
            }
            importing = false
        }
    }

    var body: some View {
        ZStack {
            MdNeonAtmosphere(accent: MdColor.neonCyan)
            VStack(spacing: 0) {
                MdCatalogAction(title: "Загрузить трек", action: { choosingFile = true })
                    .accessibilityIdentifier("music.import")
                .disabled(importing || snapshot.saving || pendingAudio != nil)
                    .padding(.horizontal, MdChrome.contentHorizontalPadding)
                    .padding(.top, 8)

                HStack {
                    Image(systemName: "magnifyingglass").foregroundStyle(MdColor.textSecondary)
                    TextField("Поиск трека", text: Binding(
                        get: { snapshot.searchQuery },
                        set: onSearch
                    ))
                    .foregroundStyle(MdColor.textPrimary)
                }
                .padding(12)
                .background(RoundedRectangle(cornerRadius: 12).fill(MdColor.surfaceElevated))
                .padding(.horizontal, MdChrome.contentHorizontalPadding)
                .padding(.vertical, 8)

                if let playing = snapshot.tracks.first(where: { $0.isPlaying }) {
                    MusicPulseVisualizer(isPlaying: true, height: 56, bpm: Double(playing.bpm))
                        .padding(.horizontal, MdChrome.contentHorizontalPadding)
                        .padding(.bottom, 8)
                }

                if importing || snapshot.saving { ProgressView("Сохранение…").tint(MdColor.neonOrange) }
                if let error = importError ?? playback.error ?? snapshot.error {
                    Text(error).foregroundStyle(.red).font(.footnote).padding(.horizontal)
                        .accessibilityIdentifier("music.error")
                }
                if let audio = pendingAudio, !importing {
                    Button("Повторить сохранение") {
                        importing = true
                        importTask = Task { @MainActor in
                            if await onImport(audio) { pendingAudio = nil; importError = nil }
                            importing = false
                        }
                    }
                }
                if snapshot.tracks.isEmpty {
                    Text("Добавьте музыку кнопкой +").foregroundStyle(MdColor.textSecondary).padding()
                }
                ScrollView {
                    LazyVStack(spacing: 10) {
                        ForEach(snapshot.tracks) { track in
                            trackRow(track)
                        }
                    }
                    .padding(.horizontal, MdChrome.contentHorizontalPadding)
                    .padding(.bottom, 96)
                }
            }
        }
        .fileImporter(isPresented: $choosingFile, allowedContentTypes: [.audio], allowsMultipleSelection: false) { result in
            switch result {
            case .success(let urls): if let url = urls.first { importAudio(url) }
            case .failure(let error): importError = error.localizedDescription
            }
        }
        .sheet(isPresented: Binding(get: { snapshot.editingTrack != nil }, set: { if !$0 { onCloseEdit() } })) {
            if let track = snapshot.editingTrack {
                MusicEditSheet(track: track, styles: snapshot.styles, tags: snapshot.tags,
                    saving: snapshot.saving, error: snapshot.error, onSave: onSave, onClose: onCloseEdit)
                    .id(track.id)
            }
        }
        .onAppear { syncPlayback() }
        .onChange(of: playbackKey) { _ in syncPlayback() }
        .onChange(of: scenePhase) { phase in if phase != .active { playback.pause() } }
        .onDisappear { playback.pause(); playback.close() }
    }

    private func trackRow(_ track: MusicTrackSnapshot) -> some View {
        VStack(alignment: .leading, spacing: 8) {
                HStack(alignment: .firstTextBaseline) {
                    Button {
                        onSelect(track.id)
                        MdHaptics.perform(.selection)
                    } label: {
                        Image(systemName: track.isPlaying ? "pause.fill" : "play.fill")
                            .foregroundStyle(MdColor.neonOrange)
                            .frame(width: 36, height: 36)
                    }
                    .accessibilityLabel(track.isPlaying ? "Приостановить" : "Воспроизвести")
                    .accessibilityIdentifier("music.play.\(track.id)")
                    Text(track.title)
                        .font(MdType.bodyLarge.weight(.semibold))
                        .foregroundStyle(MdColor.textPrimary)
                        .lineLimit(1)
                    Spacer()
                    Button {
                        onEdit(track.id)
                    } label: {
                        Image(systemName: "pencil")
                            .foregroundStyle(MdColor.neonOrange)
                            .frame(width: 36, height: 36)
                    }
                    .buttonStyle(.plain)
                    .disabled(snapshot.saving)
                    .accessibilityIdentifier("music.edit.\(track.id)")
                }
                HStack {
                    Text("\(track.style) · \(track.durationLabel) · \(track.weightLabel)")
                        .font(.system(size: 12))
                        .foregroundStyle(MdColor.textSecondary)
                        .lineLimit(1)
                    Spacer()
                    Text(track.tag)
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(MdColor.neonOrange)
                }
                if snapshot.selectedTrack?.id == track.id {
                    Slider(value: Binding(get: { Double(playback.progress) }, set: { playback.seek(Float($0)) }), in: 0...1)
                        .tint(MdColor.neonOrange)
                        .accessibilityIdentifier("music.seek.\(track.id)")
                    Text("\(track.positionLabel) · \(String(format: "%.1f", track.basePlaybackSpeed))×")
                        .font(.system(size: 11))
                        .foregroundStyle(MdColor.textSecondary)
                }
            }
            .padding(14)
            .background(
                RoundedRectangle(cornerRadius: 14)
                    .fill(MdColor.surfaceElevated)
                    .overlay(
                        RoundedRectangle(cornerRadius: 14)
                            .stroke(
                                track.isPlaying ? MdColor.neonOrange.opacity(0.55) : MdColor.surfaceBorder,
                                lineWidth: 1
                            )
                    )
            )
        .buttonStyle(.plain)
        .accessibilityIdentifier("music.track.\(track.id)")
        .contextMenu {
            Button("Удалить", role: .destructive) { onDelete(track.id) }.disabled(snapshot.saving)
        }
    }
}
