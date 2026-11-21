import Foundation
@preconcurrency import Shared

/// One Main owner survives tab/form recreation. Snapshot references never resolve by title.
@MainActor
final class TrainerChoreographyCoordinator {
    var onChange: (() -> Void)?
    private(set) var snapshot = TrainerChoreographyState()
    private let access: IosRehearsalAccessProvider
    private var audio: IosRehearsalPlayback?
    private var voice: IosRehearsalVoice?
    private var runtime: IosRehearsalRuntime?
    private var catalogTask: Task<Void, Never>?
    private var stateTask: Task<Void, Never>?
    private var loadTask: Task<Void, Never>?
    private var accessTask: Task<Void, Never>?
    private var token = UUID()
    private var voiceIndex = 0
    private var preparationMs: Int64 = 3000
    private var voiceEnabled = true
    private var lastCueIndex: Int?
    private var settingsLoading = false
    private var selectionRevision: String?
    private var pendingOrdinarySeek: Int64?

    init(access: IosRehearsalAccessProvider = IosUnavailableRehearsalAccess.shared) { self.access = access }
    deinit {
        catalogTask?.cancel(); stateTask?.cancel(); loadTask?.cancel(); accessTask?.cancel()
        let ownedRuntime = runtime
        Task { @MainActor in ownedRuntime?.close() }
    }
    func begin() {
        guard catalogTask == nil else { return }
        if runtime == nil && !snapshot.selectedID.isEmpty { select(snapshot.selectedID) }
        catalogTask = Task { @MainActor [weak self] in
            for await _ in ChoreographyVaultStore.shared.items {
                guard let self, !Task.isCancelled else { return }
                self.snapshot.choices = IosTrainerChoreographies.shared.choices().map {
                    SequenceElementChoice(id: $0.id, title: $0.displayTitle)
                }
                if !self.snapshot.selectedID.isEmpty && !self.snapshot.choices.contains(where: { $0.id == self.snapshot.selectedID }) {
                    self.select("")
                }
                self.changed()
            }
        }
        refreshAccess()
    }
    func setMode(_ enabled: Bool) {
        pause()
        snapshot.enabled = enabled
        changed()
    }
    func setVoice(_ index: Int) {
        guard !snapshot.active else { return }
        guard index != voiceIndex else { return }
        voiceIndex = index
        voice?.selectVoice(index: index)
    }
    func select(_ id: String) {
        token = UUID()
        let current = token
        loadTask?.cancel(); stateTask?.cancel()
        runtime?.close(); voice?.stop()
        runtime = nil; audio = nil; voice = nil
        snapshot.ordinary = nil
        snapshot.active = false; snapshot.paused = false; snapshot.loading = false
        snapshot.pass = 0; snapshot.totalPasses = 0; snapshot.currentLabel = ""
        snapshot.remainingMs = 0; snapshot.phase = "Готово"
        settingsLoading = false; selectionRevision = nil
        snapshot.selectedID = id; snapshot.selectedTitle = ""
        snapshot.durationMs = 0; snapshot.message = nil
        snapshot.revisionNeedsConfirmation = false
        snapshot.start = "0:00.0"; snapshot.end = "0:00.0"
        snapshot.passes = "5"; snapshot.rest = "5"
        preparationMs = 3000; voiceEnabled = true
        guard !id.isEmpty else { changed(); return }
        let audio = IosRehearsalPlayback()
        let voice = IosRehearsalVoice(selectedVoiceIndex: voiceIndex)
        let runtime = IosRehearsalRuntime(audio: audio, voice: voice, access: access)
        self.audio = audio; self.voice = voice; self.runtime = runtime
        let selection = runtime.selection(id: id)
        snapshot.selectedTitle = selection.title
        snapshot.durationMs = selection.planResult.plan?.durationMs ?? 0
        selectionRevision = selection.planResult.plan?.revision
        snapshot.end = snapshot.durationMs > 0 ? "Конец" : "0:00.0"
        settingsLoading = true
        snapshot.loading = true
        stateTask = Task { @MainActor [weak self] in
            for await state in runtime.state {
                guard let self, self.token == current, !Task.isCancelled else { return }
                self.snapshot.active = state.phase == .preparing || state.phase == .playing || state.phase == .resting || state.phase == .paused
                self.snapshot.paused = state.phase == .paused
                self.snapshot.loading = self.settingsLoading || state.isWaitingForPlayer
                self.snapshot.phase = Self.phase(state.phase)
                self.snapshot.pass = Int(state.pass); self.snapshot.totalPasses = Int(state.totalPasses)
                self.snapshot.currentLabel = state.currentLabel; self.snapshot.remainingMs = state.remainingMs
                if let error = state.error { self.snapshot.message = error }
                if let status = state.access { self.applyAccess(status) }
                self.changed()
            }
        }
        loadTask = Task { @MainActor [weak self] in
            do {
                let saved = try await runtime.savedSettings(id: id)
                guard let self, self.token == current, !Task.isCancelled else { return }
                if let saved {
                    self.snapshot.start = Self.time(saved.startMs)
                    self.snapshot.end = saved.endMs == self.snapshot.durationMs ? "Конец" : Self.time(saved.endMs)
                    self.snapshot.passes = String(saved.totalPasses); self.snapshot.rest = String(saved.restMs / 1000)
                    self.preparationMs = saved.preparationMs; self.voiceEnabled = saved.voiceEnabled
                    self.snapshot.revisionNeedsConfirmation = selection.planResult.plan.map { $0.revision != saved.choreographyRevision } ?? false
                }
                self.settingsLoading = false
                self.snapshot.loading = false
                self.changed()
            } catch {
                guard let self, self.token == current else { return }
                self.settingsLoading = false
                self.snapshot.loading = false
                self.snapshot.message = "Не удалось прочитать настройки. Прежние данные сохранены."
                self.changed()
            }
        }
        changed()
    }
    func field(_ key: String, _ text: String) {
        guard snapshot.accessGranted, !snapshot.active, !snapshot.loading else { return }
        switch key {
        case "start": snapshot.start = text
        case "end": snapshot.end = text
        case "passes": snapshot.passes = text
        case "rest": snapshot.rest = text
        default: return
        }
        snapshot.message = nil
        changed()
    }
    func part(_ action: String) {
        switch action {
        case "toggle": snapshot.partExpanded.toggle()
        case "offer": snapshot.message = "Отработка участка доступна по подписке. Проверка и покупка подписки пока не подключены на iOS."
        case "confirm": if !snapshot.active { snapshot.revisionNeedsConfirmation = false }
        case "end": if snapshot.accessGranted && !snapshot.active { snapshot.end = "Конец" }
        case "pause": runtime?.pause()
        case "resume": runtime?.resume()
        case "stop": runtime?.stop()
        case "start": startPart()
        case "reset": reset()
        default: break
        }
        changed()
    }
    func startOrdinary() {
        runtime?.stop()
        let selection = IosTrainerChoreographies.shared.resolve(id: snapshot.selectedID)
        guard let source = selection.snapshot, !source.musicClips.isEmpty else {
            snapshot.message = selection.error ?? "Добавьте музыку и сохраните эту хореографию."
            changed(); return
        }
        func clips(_ list: [EditorClip]) -> [EditorClipSnapshot] {
            list.map { EditorClipSnapshot(label: $0.label, durationMs: $0.durationMs,
                mediaUri: $0.mediaUri, speed: $0.speed, sourceStartMs: $0.sourceStartMs) }
        }
        let elements = clips(source.elementClips); let music = clips(source.musicClips)
        guard source.songPlaybackSpeed.isFinite, source.songPlaybackSpeed > 0, source.musicVolume.isFinite,
              music.allSatisfy({ $0.mediaUri.flatMap(OwnedMusicAudio.resolve) != nil }),
              let elementDuration = Self.duration(elements, speed: nil),
              let musicDuration = Self.duration(music, speed: source.songPlaybackSpeed),
              max(elementDuration, musicDuration) > 0 else {
            snapshot.message = "Проверьте файлы, длительности и скорость этой хореографии."
            changed(); return
        }
        voice?.prepareVoice()
        lastCueIndex = nil; pendingOrdinarySeek = nil
        snapshot.message = nil
        snapshot.ordinary = TrainerChoreographySession(title: selection.title, elements: elements,
            music: music, songSpeed: source.songPlaybackSpeed, volume: source.musicVolume,
            durationMs: max(elementDuration, musicDuration))
        changed()
    }
    func ordinary(_ action: String, _ position: Int64) {
        guard var session = snapshot.ordinary else { return }
        switch action {
        case "pause": session.playing = false; voice?.stop()
        case "toggle": session.playing.toggle(); if !session.playing { voice?.stop() }
        case "seek": session.positionMs = min(max(0, position), session.durationMs); session.seek = ChoreoPlaybackSeek(positionMs: session.positionMs); pendingOrdinarySeek = session.positionMs; voice?.stop(); lastCueIndex = nil
        case "position":
            if let target = pendingOrdinarySeek {
                guard abs(Double(position) - Double(target)) <= 200 else { return }
                pendingOrdinarySeek = nil
            }
            session.positionMs = min(max(0, position), session.durationMs)
            if session.playing && session.currentIndex != lastCueIndex {
                if !session.currentLabel.isEmpty { voice?.speak(label: session.currentLabel) }
                else { voice?.stop() }
                lastCueIndex = session.currentIndex
            }
        case "end": pendingOrdinarySeek = nil; session.positionMs = session.durationMs; session.playing = false; voice?.stop()
        case "error": session.playing = false; session.error = "Не удалось открыть один из файлов хореографии."; voice?.stop()
        case "finish": snapshot.ordinary = nil; voice?.stop(); changed(); return
        default: return
        }
        snapshot.ordinary = session
        changed()
    }
    func pause() { runtime?.pause(); if snapshot.ordinary != nil { ordinary("pause", 0) } }
    func close() {
        pause(); token = UUID()
        catalogTask?.cancel(); catalogTask = nil
        loadTask?.cancel(); stateTask?.cancel(); accessTask?.cancel(); accessTask = nil
        runtime?.close(); runtime = nil; audio = nil; voice?.stop(); voice = nil
        snapshot.ordinary = nil
    }
    private func startPart() {
        guard snapshot.accessGranted else { snapshot.message = snapshot.accessLabel; return }
        guard !snapshot.active, !snapshot.loading, let runtime else { return }
        let selection = runtime.selection(id: snapshot.selectedID)
        guard let plan = selection.planResult.plan else { snapshot.message = selection.planResult.error; return }
        if selectionRevision != plan.revision {
            selectionRevision = plan.revision
            snapshot.durationMs = plan.durationMs
            snapshot.revisionNeedsConfirmation = true
        }
        guard !snapshot.revisionNeedsConfirmation else { snapshot.message = "Проверьте и подтвердите границы изменённой хореографии."; return }
        let endValue = snapshot.end.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() == "конец" ? plan.durationMs : Self.parse(snapshot.end)
        guard let start = Self.parse(snapshot.start), let end = endValue,
              let passes = Int32(snapshot.passes), let rest = Int64(snapshot.rest), rest >= 0, rest <= 120 else {
            snapshot.message = "Проверьте начало, конец, число проходов и отдых."; return
        }
        let settings = RehearsalSettings(choreographyId: plan.choreographyId, choreographyRevision: plan.revision,
            startMs: start, endMs: end, totalPasses: passes, restMs: rest * 1000,
            preparationMs: preparationMs, voiceEnabled: voiceEnabled, schemaVersion: 1)
        snapshot.message = runtime.start(settings: settings)
        if snapshot.message == nil { snapshot.active = true; snapshot.loading = true }
    }
    private func reset() {
        guard !snapshot.active, !snapshot.loading, let runtime else { return }
        let id = snapshot.selectedID; let current = token
        settingsLoading = true; snapshot.loading = true
        loadTask = Task { @MainActor [weak self] in
            do {
                try await runtime.deleteSettings(id: id)
                guard let self, self.token == current else { return }
                self.select(id)
            } catch {
                guard let self, self.token == current else { return }
                self.settingsLoading = false; self.snapshot.loading = false
                self.snapshot.message = "Не удалось сбросить настройки. Прежние данные сохранены."; self.changed()
            }
        }
    }
    private func refreshAccess() {
        accessTask?.cancel()
        accessTask = Task { @MainActor [weak self] in
            guard let self else { return }
            do {
                let access = try await self.access.access()
                guard !Task.isCancelled else { return }
                self.applyAccess(access)
            } catch {
                guard !Task.isCancelled else { return }
                self.applyAccess(.unavailable)
            }
            self.changed()
        }
    }
    private func applyAccess(_ access: RehearsalAccess) {
        snapshot.accessGranted = access == .granted
        snapshot.accessLabel = access == .granted ? "Pro" : access == .free ? "Доступно по подписке" : "Проверка подписки недоступна"
    }
    private func changed() { onChange?() }
    private static func phase(_ phase: RehearsalPhase) -> String {
        switch phase {
        case .preparing: return "Подготовка"
        case .playing: return "Отработка"
        case .resting: return "Отдых"
        case .paused: return "Пауза"
        case .completed: return "Готово"
        case .error: return "Ошибка"
        default: return "Готово"
        }
    }
    private static func time(_ milliseconds: Int64) -> String { String(format: "%lld:%04.1f", milliseconds / 60000, Double(milliseconds % 60000) / 1000) }
    private static func parse(_ text: String) -> Int64? {
        let parts = text.replacingOccurrences(of: ",", with: ".").split(separator: ":", omittingEmptySubsequences: false)
        guard (1...2).contains(parts.count), let seconds = Double(parts.last!), seconds.isFinite, seconds >= 0 else { return nil }
        let minutes = parts.count == 2 ? Double(parts[0]) : 0
        guard let minutes, minutes.isFinite, minutes >= 0, (parts.count == 1 || seconds < 60) else { return nil }
        let value = (minutes * 60 + seconds) * 1000
        guard value < Double(Int64.max), value >= 0 else { return nil }
        return Int64(value.rounded())
    }
    private static func duration(_ clips: [EditorClipSnapshot], speed: Double?) -> Int64? {
        var total: Int64 = 0
        for clip in clips {
            let rate = speed ?? clip.speed
            let value = Double(clip.durationMs) / rate
            guard rate.isFinite, rate > 0, clip.sourceStartMs >= 0, clip.durationMs > 0,
                  value.isFinite, value >= 1, value < Double(Int64.max - total) else { return nil }
            total += Int64(value)
        }
        return total
    }
}
