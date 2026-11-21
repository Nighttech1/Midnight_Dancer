import SwiftUI

/// Choreography vault hub — open editor via Shared stack.
struct KineticVaultView: View {
    let snapshot: ChoreographyFeatureSnapshot
    var onSearch: (String) -> Void
    var onCreate: () -> Void
    var onDismissCreationMessage: () -> Void
    var onOpen: (String) -> Void
    var onDelete: (String) -> Void

    var body: some View {
        ZStack {
            MdNeonAtmosphere(accent: MdColor.neonMagenta)
            VStack(spacing: 0) {
                MdCatalogAction(title: "Создать хореографию", action: onCreate)
                    .accessibilityIdentifier("choreo.add")
                    .padding(.horizontal, MdChrome.contentHorizontalPadding)
                    .padding(.top, 8)

                if let message = snapshot.creationBlockedMessage {
                    HStack(alignment: .top, spacing: 12) {
                        Text(message)
                            .font(MdType.bodyMedium)
                            .foregroundStyle(MdColor.textPrimary)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .accessibilityIdentifier("choreo.creation-message")
                        Button("Закрыть", action: onDismissCreationMessage)
                            .foregroundStyle(MdColor.neonOrange)
                    }
                    .padding(12)
                    .background(MdColor.surfaceElevated, in: RoundedRectangle(cornerRadius: 12))
                    .padding(.horizontal, MdChrome.contentHorizontalPadding)
                    .padding(.top, 8)
                }

                HStack {
                    Image(systemName: "magnifyingglass").foregroundStyle(MdColor.textSecondary)
                    TextField("Поиск хореографий…", text: Binding(
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
