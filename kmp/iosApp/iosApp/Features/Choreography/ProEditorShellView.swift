import SwiftUI

/// Choreography preview and timeline controls backed by the sequence editor.
struct ProEditorShellView: View {
    let snapshot: SequenceEditorSnapshot
    var onBack: () -> Void
    var onTogglePlay: () -> Void
    var onSetTitle: (String) -> Void
    var onSetStyle: (String) -> Void
    var onSave: () -> Void
    var elementChoices: () -> [SequenceElementChoice]
    var onAddElement: (String) -> Void
    var timeline: ProEditorTimeline? = nil

    @State private var pickerOpen = false
    @State private var pickerLane = ProEditorLane.elements
    @State private var options: [SequenceElementChoice] = []
    @State private var editingTitle = false
    @State private var titleDraft = ""
    @State private var savedMessage = false
    @State private var scrubFraction: Double?
    @State private var seekRequest: ChoreoPlaybackSeek?
    @State private var playbackAvailable = false

    private let flame = Color(red: 1, green: 1.0 / 3, blue: 0)
    private let panel = Color(red: 20.0 / 255.0, green: 21.0 / 255.0, blue: 25.0 / 255.0)

    var body: some View {
        VStack(spacing: 8) {
            header
            GeometryReader { space in
                ScrollView {
                    VStack(spacing: 10) {
                        preview
                            .frame(height: max(160, min(350, space.size.height * 0.55)))
                        transportBar
                        sequenceControls
                        if let timeline {
                            ProEditorTimelineView(
                                timeline: timeline,
                                position: position,
                                onScrub: seek,
                                onAddElement: openElementPicker,
                                onAddMusic: canAddMusic ? openMusicPicker : nil
                            )
                        }
                        saveStatus
                    }
                    .padding(.horizontal, MdChrome.contentHorizontalPadding)
                    .padding(.bottom, 10)
                }
            }
            if let timeline {
                toolShelf(timeline)
            }
        }
        .background(MdColor.oledTrueBlack.ignoresSafeArea())
        .sheet(isPresented: $pickerOpen) { catalogPicker }
        .alert("Название хореографии", isPresented: $editingTitle) {
            TextField("Название", text: $titleDraft)
            Button("Отмена", role: .cancel) {}
            Button("Готово") {
                let title = titleDraft.trimmingCharacters(in: .whitespacesAndNewlines)
                if !title.isEmpty {
                    savedMessage = false
                    onSetTitle(title)
                }
            }
        }
        .onChange(of: timeline?.elementClips) { _ in savedMessage = false }
        .onChange(of: timeline?.musicClips) { _ in savedMessage = false }
        .onChange(of: snapshot.saveCount) { _ in savedMessage = true }
    }

    private var position: Double {
        let fraction = scrubFraction ?? timeline?.playheadFraction ?? 0
        return fraction.isFinite ? min(max(fraction, 0), 1) : 0
    }

    private var preview: some View {
        let scrubMs = currentScrubPositionMs
        let elementsMs = snapshot.elementsDurationMs
        let clampedMs = scrubMs.map { min($0, elementsMs) } ?? snapshot.playheadInElementsMs
        let empty = scrubMs.map { snapshot.elementClips.isEmpty || $0 >= elementsMs } ?? snapshot.previewIsEmpty
        let clipAtPlayhead: String? = scrubMs.flatMap { offset in
            let clips = timeline?.elementClips ?? []
            let index = ChoreoCompositionTimelineMath.clipIndexAt(clips: clips, positionMs: offset)
            return clips.indices.contains(index) ? clips[index].label : nil
        }
        return ChoreoCompositionPreviewView(
            isPlaying: snapshot.isPlaying,
            isEmpty: empty,
            currentElementLabel: empty ? nil : (clipAtPlayhead ?? snapshot.previewElementLabel),
            elementClips: snapshot.elementClips,
            playheadInElementsMs: clampedMs,
            scrubbing: scrubFraction != nil,
            glow: snapshot.isPlaying ? 0.65 : 0.25,
            musicClips: timeline?.musicSourceClips ?? [],
            songSpeed: timeline?.songPlaybackSpeed ?? 1,
            musicVolume: timeline?.musicVolume ?? 1,
            globalPlayheadMs: timeline.map { timelinePositionMs(position, duration: $0.durationMs) },
            seekRequest: seekRequest,
            onPlaybackPosition: timeline == nil ? nil : reportPlaybackPosition,
            onPlaybackEnded: playbackEnded,
            onPlaybackAvailability: { playbackAvailable = $0 },
            onPlaybackFailed: stopPlayback
        )
    }

    private var currentScrubPositionMs: Int64? {
        guard let timeline, scrubFraction != nil else { return nil }
        return timelinePositionMs(position, duration: timeline.durationMs)
    }

    private func timelinePositionMs(_ fraction: Double, duration: Int64) -> Int64 {
        guard fraction.isFinite, duration > 0 else { return 0 }
        let value = Double(duration) * min(max(fraction, 0), 1)
        return value < Double(Int64.max) ? Int64(value) : duration
    }

    private func seek(_ fraction: Double, ended: Bool) {
        guard let timeline, timeline.durationMs > 0 else { return }
        scrubFraction = ended ? nil : fraction
        seekRequest = ChoreoPlaybackSeek(positionMs: timelinePositionMs(fraction, duration: timeline.durationMs))
        timeline.actions.seek(fraction)
    }

    private func reportPlaybackPosition(_ positionMs: Int64) {
        guard let timeline, timeline.durationMs > 0 else { return }
        timeline.actions.seek(min(Double(positionMs) / Double(timeline.durationMs), 1))
    }

    private func playbackEnded() {
        if let timeline {
            timeline.actions.seek(1)
        } else {
            stopPlayback()
        }
    }

    private func stopPlayback() {
        if snapshot.isPlaying { onTogglePlay() }
    }

    private var header: some View {
        HStack(spacing: 10) {
            Button(action: onBack) {
                Image(systemName: "arrow.left")
                    .font(.system(size: 19, weight: .semibold))
                    .foregroundStyle(MdColor.textPrimary)
                    .frame(width: 44, height: 44)
                    .background(panel, in: Circle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Назад")

            Button {
                titleDraft = snapshot.title
                editingTitle = true
            } label: {
                HStack(spacing: 5) {
                    Text(snapshot.title)
                        .font(MdType.titleLarge.weight(.bold))
                        .foregroundStyle(MdColor.textPrimary)
                        .lineLimit(1)
                    Image(systemName: "pencil")
                        .font(.caption)
                        .foregroundStyle(flame)
                }
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("choreo.rename")
            Spacer(minLength: 0)
            Button {
                savedMessage = false
                onSave()
            } label: {
                Image(systemName: "square.and.arrow.down")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(.black)
                    .frame(width: 48, height: 48)
                    .background(flame, in: RoundedRectangle(cornerRadius: 12))
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Сохранить")
            .accessibilityIdentifier("choreo.save")
        }
        .padding(.horizontal, MdChrome.contentHorizontalPadding)
        .padding(.top, 8)
    }

    private var transportBar: some View {
        HStack(spacing: 6) {
            if let timeline {
                VStack(alignment: .leading, spacing: 2) {
                    Text(timeline.timecode).font(.caption.weight(.semibold))
                    Text(timeline.durationTimecode).font(.caption2)
                }
                .monospacedDigit()
                .foregroundStyle(MdColor.textPrimary)
                .accessibilityLabel("Время \(timeline.timecode) из \(timeline.durationTimecode)")
                .accessibilityIdentifier("choreo.timeline.timecode")
                Spacer(minLength: 0)
                transportButton("Отменить", icon: "arrow.uturn.backward", enabled: timeline.canUndo) {
                    timeline.actions.undo()
                }
                transportButton("В начало", icon: "backward.end", enabled: timeline.durationMs > 0) {
                    seek(0, ended: true)
                }
            } else {
                Spacer()
            }

            playButton

            if let timeline {
                transportButton("В конец", icon: "forward.end", enabled: timeline.durationMs > 0) {
                    seek(1, ended: true)
                }
                transportButton("Повторить", icon: "arrow.uturn.forward", enabled: timeline.canRedo) {
                    timeline.actions.redo()
                }
            } else {
                Spacer()
            }
        }
        .padding(8)
        .background(panel, in: RoundedRectangle(cornerRadius: 14))
    }

    private var playButton: some View {
        let enabled = playbackAvailable || snapshot.isPlaying
        return Button(action: onTogglePlay) {
            Image(systemName: snapshot.isPlaying ? "pause.fill" : "play.fill")
                .font(.system(size: 20, weight: .bold))
                .foregroundStyle(MdColor.oledTrueBlack)
                .frame(width: 48, height: 48)
                .background(flame.opacity(enabled ? 1 : 0.35), in: Circle())
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .accessibilityLabel(snapshot.isPlaying ? "Пауза" : "Воспроизведение")
        .accessibilityIdentifier("choreo.play")
    }

    private func transportButton(
        _ label: String, icon: String, enabled: Bool, action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            Image(systemName: icon)
                .font(.system(size: 13, weight: .semibold))
                .frame(width: 44, height: 44)
                .foregroundStyle(.white.opacity(enabled ? 1 : 0.30))
                .background(.white.opacity(0.07), in: Circle())
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .accessibilityLabel(label)
    }

    private var sequenceControls: some View {
        HStack {
            if snapshot.elementClips.isEmpty && (timeline?.musicClips.isEmpty ?? true) && !snapshot.availableStyles.isEmpty {
                Menu {
                    ForEach(snapshot.availableStyles, id: \.self) { style in
                        Button(style) {
                            savedMessage = false
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

            if timeline == nil {
                Button(action: openElementPicker) {
                    Label("Элемент", systemImage: "plus.circle")
                }
                .accessibilityIdentifier("choreo.addElement")
            }
        }
        .font(MdType.bodyMedium)
        .foregroundStyle(flame)
    }

    private var canAddMusic: Bool {
        timeline?.actions.musicChoices != nil && timeline?.actions.addMusic != nil
    }

    private func openElementPicker() {
        pickerLane = .elements
        options = elementChoices()
        pickerOpen = true
    }

    private func openMusicPicker() {
        guard let choices = timeline?.actions.musicChoices else { return }
        pickerLane = .music
        options = choices()
        pickerOpen = true
    }

    private var catalogPicker: some View {
        NavigationStack {
            List {
                if options.isEmpty {
                    Text(pickerLane == .music ? "В этом стиле пока нет музыки." : "В этом стиле пока нет элементов с видео.")
                        .foregroundStyle(.secondary)
                }
                ForEach(options) { option in
                    Button(option.title) {
                        savedMessage = false
                        if pickerLane == .music {
                            timeline?.actions.addMusic?(option.id)
                        } else {
                            onAddElement(option.id)
                        }
                        pickerOpen = false
                    }
                    .accessibilityIdentifier(pickerLane == .music ? "choreo.musicChoice.\(option.id)" : "choreo.choice.\(option.id)")
                }
            }
            .navigationTitle(pickerLane == .music ? "Добавить музыку" : "Добавить элемент")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Закрыть") { pickerOpen = false }
                }
            }
        }
    }

    @ViewBuilder private var saveStatus: some View {
        if let error = snapshot.saveError {
            Text(error).foregroundStyle(MdColor.danger)
                .accessibilityIdentifier("choreo.saveError")
        } else if savedMessage {
            Text("Хореография сохранена").foregroundStyle(MdColor.textSecondary)
                .accessibilityIdentifier("choreo.saved")
        }
    }

    private func toolShelf(_ timeline: ProEditorTimeline) -> some View {
        HStack(spacing: 0) {
            tool("Разделить", icon: "scissors", id: "choreo.timeline.split", enabled: timeline.canSplit) {
                timeline.actions.split()
            }
            if let duplicate = timeline.actions.duplicate {
                tool("Дублировать", icon: "plus.square.on.square", id: "choreo.timeline.duplicate", enabled: timeline.hasSelection) {
                    duplicate()
                }
            }
            tool("Удалить", icon: "trash", id: "choreo.timeline.delete", enabled: timeline.hasSelection) {
                timeline.actions.delete()
            }
        }
        .padding(.vertical, 6)
        .background(panel, in: RoundedRectangle(cornerRadius: 14))
        .padding(.horizontal, 8)
        .padding(.bottom, 2)
    }

    private func tool(
        _ label: String, icon: String, id: String, enabled: Bool, action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            VStack(spacing: 5) {
                Image(systemName: icon).font(.system(size: 19, weight: .semibold))
                Text(label).font(.caption)
            }
            .frame(maxWidth: .infinity, minHeight: 44)
            .foregroundStyle(.white.opacity(enabled ? 1 : 0.33))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .accessibilityIdentifier(id)
    }
}
