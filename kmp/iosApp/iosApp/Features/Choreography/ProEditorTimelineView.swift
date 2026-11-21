import SwiftUI

enum ProEditorLane: Equatable {
    case elements
    case music
}

struct ProEditorTimelineActions {
    var seek: (Double) -> Void
    var select: (ProEditorLane, Int) -> Void
    var split: () -> Void
    var delete: () -> Void
    var reorder: (ProEditorLane, Int, Int) -> Void
    var undo: () -> Void
    var redo: () -> Void
    var duplicate: (() -> Void)? = nil
    var musicChoices: (() -> [SequenceElementChoice])? = nil
    var addMusic: ((String) -> Void)? = nil
}

/// Clip durations in these lists are the effective timeline lengths from the editor state.
struct ProEditorTimeline {
    var elementClips: [EditorClipSnapshot]
    var musicClips: [EditorClipSnapshot]
    var durationMs: Int64
    var playheadFraction: Double
    var timecode: String
    var durationTimecode: String
    var selectedElementIndex: Int
    var selectedMusicIndex: Int
    var canUndo: Bool
    var canRedo: Bool
    var actions: ProEditorTimelineActions
    var musicSourceClips: [EditorClipSnapshot] = []
    var songPlaybackSpeed: Double = 1
    var musicVolume: Float = 1

    var hasSelection: Bool {
        elementClips.indices.contains(selectedElementIndex) || musicClips.indices.contains(selectedMusicIndex)
    }

    var canSplit: Bool {
        if elementClips.indices.contains(selectedElementIndex) {
            return playheadInsideClip(elementClips, index: selectedElementIndex)
        }
        if musicClips.indices.contains(selectedMusicIndex) {
            return playheadInsideClip(musicClips, index: selectedMusicIndex)
        }
        return false
    }

    private func playheadInsideClip(_ clips: [EditorClipSnapshot], index: Int) -> Bool {
        let start = clips.prefix(index).reduce(0.0) { $0 + Double($1.durationMs) }
        let end = start + Double(clips[index].durationMs)
        let position = Double(durationMs) * playheadFraction
        return position > start && position < end
    }
}

struct ProEditorTimelineView: View {
    let timeline: ProEditorTimeline
    let position: Double
    var onScrub: (Double, Bool) -> Void
    var onAddElement: () -> Void
    var onAddMusic: (() -> Void)?

    private let flame = Color(red: 1, green: 1.0 / 3, blue: 0)
    private let rowHeight: CGFloat = 36

    var body: some View {
        HStack(spacing: 10) {
            laneHeaders
            GeometryReader { viewport in
                let width = max(viewport.size.width, min(8_192, CGFloat(timeline.durationMs) / 1_000 * 24))
                ScrollView(.horizontal) {
                    timelineStrip(width: width)
                }
            }
        }
        .frame(height: 126)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("choreo.timeline")
    }

    private var laneHeaders: some View {
        VStack(spacing: 6) {
            Color.clear.frame(height: 12)
                .accessibilityHidden(true)
            laneButton(.elements, action: onAddElement)
            laneButton(.music, action: onAddMusic)
            Color.clear.frame(height: 24)
                .accessibilityHidden(true)
        }
    }

    @ViewBuilder
    private func laneButton(_ lane: ProEditorLane, action: (() -> Void)?) -> some View {
        let isMusic = lane == .music
        let symbol = isMusic ? "music.note" : "film"
        if let action {
            Button(action: action) {
                Image(systemName: "plus")
                    .font(.system(size: 19, weight: .semibold))
                    .foregroundStyle(isMusic ? Color.black : Color.white)
                    .frame(width: 42, height: rowHeight)
                    .background(isMusic ? flame : MdColor.surfaceElevated, in: RoundedRectangle(cornerRadius: 9))
                    .overlay {
                        RoundedRectangle(cornerRadius: 9)
                            .strokeBorder(.white.opacity(isMusic ? 0 : 0.20))
                    }
            }
            .buttonStyle(.plain)
            .accessibilityLabel(isMusic ? "Добавить музыку" : "Добавить элемент")
            .accessibilityIdentifier(isMusic ? "choreo.addMusic" : "choreo.addElement")
        } else {
            Image(systemName: symbol)
                .foregroundStyle(MdColor.textSecondary)
                .frame(width: 42, height: rowHeight)
                .accessibilityLabel(isMusic ? "Музыка" : "Элементы")
        }
    }

    private func timelineStrip(width: CGFloat) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            ticks(width: width)
            laneTrack(timeline.elementClips, lane: .elements, selected: timeline.selectedElementIndex, width: width)
            laneTrack(timeline.musicClips, lane: .music, selected: timeline.selectedMusicIndex, width: width)
            ruler(width: width)
        }
        .overlay(alignment: .topLeading) {
            if timeline.durationMs > 0 {
                Rectangle()
                    .fill(flame)
                    .frame(width: 2, height: 99)
                    .offset(x: CGFloat(position) * max(width - 2, 0))
                    .allowsHitTesting(false)
                    .accessibilityHidden(true)
            }
        }
        .frame(width: width)
    }

    private func ticks(width: CGFloat) -> some View {
        HStack {
            ForEach(0..<4) { index in
                Text(tickLabel(index))
                    .font(.caption2.monospacedDigit())
                    .foregroundStyle(MdColor.textSecondary)
                if index < 3 { Spacer(minLength: 0) }
            }
        }
        .frame(width: width, height: 12)
        .accessibilityHidden(true)
    }

    private func tickLabel(_ index: Int) -> String {
        let seconds = (timeline.durationMs / 1_000) * Int64(index) / 3
        return String(format: "%02lld:%02lld", seconds / 60, seconds % 60)
    }

    private func laneTrack(
        _ clips: [EditorClipSnapshot],
        lane: ProEditorLane,
        selected: Int,
        width: CGFloat
    ) -> some View {
        let shape = RoundedRectangle(cornerRadius: 8)
        return HStack(spacing: 0) {
            ForEach(clips.indices, id: \.self) { index in
                clipButton(clips[index], index: index, lane: lane, selected: selected == index, count: clips.count)
                    .frame(width: clipWidth(clips[index], trackWidth: width))
                    .clipped()
            }
            Spacer(minLength: 0)
        }
        .frame(width: width, height: rowHeight, alignment: .leading)
        .background(MdColor.surfaceElevated, in: shape)
        .overlay { shape.strokeBorder(.white.opacity(0.12)).allowsHitTesting(false) }
        .clipShape(shape)
        .accessibilityLabel(lane == .music ? "Дорожка музыки" : "Дорожка элементов")
    }

    private func clipWidth(_ clip: EditorClipSnapshot, trackWidth: CGFloat) -> CGFloat {
        let duration = max(timeline.durationMs, 1)
        return trackWidth * CGFloat(max(clip.durationMs, 0)) / CGFloat(duration)
    }

    private func clipButton(
        _ clip: EditorClipSnapshot,
        index: Int,
        lane: ProEditorLane,
        selected: Bool,
        count: Int
    ) -> some View {
        let isMusic = lane == .music
        let shape = RoundedRectangle(cornerRadius: 7)
        return Button { timeline.actions.select(lane, index) } label: {
            Text(clip.label)
                .font(.caption.weight(.semibold))
                .lineLimit(1)
                .padding(.horizontal, 4)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
                .foregroundStyle(isMusic ? Color.black : Color.white)
                .background(isMusic ? flame.opacity(selected ? 1 : 0.76) : Color.white.opacity(selected ? 0.20 : 0.08), in: shape)
                .overlay {
                    shape.strokeBorder(selected ? flame : .white.opacity(0.17), lineWidth: selected ? 2 : 1)
                }
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
        .accessibilityIdentifier("choreo.clip.\(isMusic ? "music" : "elements").\(index)")
        .contextMenu {
            if index > 0 {
                Button { timeline.actions.reorder(lane, index, index - 1) } label: {
                    Label("Передвинуть раньше", systemImage: "arrow.left")
                }
            }
            if index + 1 < count {
                Button { timeline.actions.reorder(lane, index, index + 1) } label: {
                    Label("Передвинуть позже", systemImage: "arrow.right")
                }
            }
        }
    }

    private func ruler(width: CGFloat) -> some View {
        ZStack(alignment: .leading) {
            Color.clear
            Circle()
                .fill(flame)
                .overlay { Circle().strokeBorder(.white, lineWidth: 1.5) }
                .frame(width: 13, height: 13)
                .offset(x: CGFloat(position) * max(width - 13, 0))
        }
        .frame(width: width, height: 24)
        .contentShape(Rectangle())
        .highPriorityGesture(
            DragGesture(minimumDistance: 0)
                .onChanged { value in scrub(at: value.location.x, width: width, ended: false) }
                .onEnded { value in scrub(at: value.location.x, width: width, ended: true) }
        )
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Позиция воспроизведения")
        .accessibilityValue(timeline.timecode)
        .accessibilityIdentifier("choreo.timeline.ruler")
        .accessibilityAdjustableAction { adjustment in
            switch adjustment {
            case .increment: onScrub(min(position + 0.05, 1), true)
            case .decrement: onScrub(max(position - 0.05, 0), true)
            @unknown default: break
            }
        }
        .opacity(timeline.durationMs > 0 ? 1 : 0.35)
        .disabled(timeline.durationMs <= 0)
    }

    private func scrub(at x: CGFloat, width: CGFloat, ended: Bool) {
        guard timeline.durationMs > 0, width > 0 else { return }
        onScrub(min(max(Double(x / width), 0), 1), ended)
    }
}
