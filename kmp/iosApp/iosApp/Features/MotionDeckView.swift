import SwiftUI

/// Music Motion Deck — track list + pulse visualizer for playing track.
struct MotionDeckView: View {
    let snapshot: MusicFeatureSnapshot
    var onSelect: (String) -> Void
    var onSearch: (String) -> Void
    var onUpload: () -> Void
    var onEdit: (String) -> Void
    var onDelete: (String) -> Void

    var body: some View {
        ZStack {
            MdNeonAtmosphere(accent: MdColor.neonCyan)
            VStack(spacing: 0) {
                HStack {
                    Text(snapshot.title)
                        .font(MdType.titleLarge)
                        .foregroundStyle(MdColor.textPrimary)
                    Spacer()
                    Button(action: onUpload) {
                        Image(systemName: "plus")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundStyle(MdColor.oledTrueBlack)
                            .frame(width: 40, height: 40)
                            .background(RoundedRectangle(cornerRadius: 10).fill(MdColor.neonOrange))
                    }
                    .buttonStyle(.plain)
                }
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
    }

    private func trackRow(_ track: MusicTrackSnapshot) -> some View {
        Button {
            onSelect(track.id)
            MdHaptics.perform(.selection)
        } label: {
            VStack(alignment: .leading, spacing: 8) {
                HStack(alignment: .firstTextBaseline) {
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
                if track.isPlaying {
                    ProgressView(value: Double(track.progress))
                        .tint(MdColor.neonOrange)
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
        }
        .buttonStyle(.plain)
        .contextMenu {
            Button("Удалить", role: .destructive) { onDelete(track.id) }
        }
    }
}
