import SwiftUI

/// Choreography vault hub — open editor via Shared stack.
struct KineticVaultView: View {
    let snapshot: ChoreographyFeatureSnapshot
    var onSearch: (String) -> Void
    var onCreate: () -> Void
    var onOpen: (String) -> Void
    var onDelete: (String) -> Void

    var body: some View {
        ZStack {
            MdNeonAtmosphere(accent: MdColor.neonMagenta)
            VStack(spacing: 0) {
                HStack {
                    Text(snapshot.title)
                        .font(MdType.titleLarge)
                        .foregroundStyle(MdColor.textPrimary)
                    Spacer()
                    Button(action: onCreate) {
                        Image(systemName: "plus")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundStyle(MdColor.oledTrueBlack)
                            .frame(width: 40, height: 40)
                            .background(RoundedRectangle(cornerRadius: 10).fill(MdColor.neonOrange))
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Создать хореографию")
                }
                .padding(.horizontal, MdChrome.contentHorizontalPadding)
                .padding(.top, 8)

                HStack {
                    Image(systemName: "magnifyingglass").foregroundStyle(MdColor.textSecondary)
                    TextField("Поиск", text: Binding(
                        get: { snapshot.searchQuery },
                        set: onSearch
                    ))
                    .foregroundStyle(MdColor.textPrimary)
                }
                .padding(12)
                .background(RoundedRectangle(cornerRadius: 12).fill(MdColor.surfaceElevated))
                .padding(.horizontal, MdChrome.contentHorizontalPadding)
                .padding(.vertical, 8)

                ScrollView {
                    LazyVStack(spacing: 10) {
                        ForEach(snapshot.items) { item in
                            Button {
                                onOpen(item.id)
                                MdHaptics.perform(.mediumImpact)
                            } label: {
                                VStack(alignment: .leading, spacing: 6) {
                                    Text(item.title)
                                        .font(MdType.bodyLarge.weight(.semibold))
                                        .foregroundStyle(MdColor.textPrimary)
                                        .frame(maxWidth: .infinity, alignment: .leading)
                                    HStack {
                                        Text("\(item.style) · \(item.trackName) · \(item.durationLabel)")
                                            .font(.system(size: 12))
                                            .foregroundStyle(MdColor.textSecondary)
                                            .lineLimit(1)
                                        Spacer()
                                        if !item.tag.isEmpty {
                                            Text(item.tag)
                                                .font(.system(size: 11, weight: .semibold))
                                                .foregroundStyle(MdColor.neonOrange)
                                        }
                                    }
                                }
                                .padding(14)
                                .background(
                                    RoundedRectangle(cornerRadius: 14)
                                        .fill(MdColor.surfaceElevated)
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 14)
                                                .stroke(MdColor.surfaceBorder, lineWidth: 1)
                                        )
                                )
                            }
                            .buttonStyle(.plain)
                            .contextMenu {
                                Button("Удалить", role: .destructive) { onDelete(item.id) }
                            }
                        }
                    }
                    .padding(.horizontal, MdChrome.contentHorizontalPadding)
                    .padding(.bottom, 96)
                }
            }
        }
    }
}
