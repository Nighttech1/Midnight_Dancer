import SwiftUI

/// Elements catalog — style split + LazyVGrid (Android Kinetic Atelier feel).
struct ElementsAtelierView: View {
    let snapshot: ElementsFeatureSnapshot
    var onStylePrevious: () -> Void
    var onStyleNext: () -> Void
    var onAdd: () -> Void
    var onSearch: (String) -> Void
    var onFilter: (String?) -> Void
    var onPlay: (String) -> Void
    var onOpenDetail: (String) -> Void
    var onCloseDetail: () -> Void
    var onSave: (ElementEditorSnapshot) -> Void

    private let columns = [
        GridItem(.flexible(), spacing: 12),
        GridItem(.flexible(), spacing: 12),
    ]

    var body: some View {
        ZStack {
            MdNeonAtmosphere(accent: MdColor.neonOrange)
            VStack(spacing: 0) {
                styleBar
                searchBar
                categoryRow
                ScrollView {
                    LazyVGrid(columns: columns, spacing: 12) {
                        ForEach(snapshot.cards) { card in
                            elementCard(card)
                        }
                    }
                    .padding(.horizontal, MdChrome.contentHorizontalPadding)
                    .padding(.vertical, 12)
                    .padding(.bottom, 88)
                }
            }
        }
        .sheet(isPresented: editorBinding) {
            if let draft = snapshot.editor {
                ElementEditSheet(
                    initialDraft: draft,
                    categories: snapshot.categories,
                    saving: snapshot.saving,
                    saveError: snapshot.saveError,
                    onSave: onSave,
                    onClose: onCloseDetail
                )
            }
        }
    }

    private var editorBinding: Binding<Bool> {
        Binding(
            get: { snapshot.editor != nil },
            set: { if !$0 { onCloseDetail() } }
        )
    }

    private var styleBar: some View {
        HStack(spacing: 10) {
            Button(action: onStylePrevious) {
                Image(systemName: "chevron.left")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(MdColor.neonOrange)
                    .frame(width: 44, height: 44)
            }
            .buttonStyle(.plain)
            Text(snapshot.styleName)
                .font(MdType.titleLarge)
                .foregroundStyle(MdColor.textPrimary)
                .frame(maxWidth: .infinity)
                .lineLimit(1)
            Button(action: onStyleNext) {
                Image(systemName: "chevron.right")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(MdColor.neonOrange)
                    .frame(width: 44, height: 44)
            }
            .buttonStyle(.plain)
            Button(action: onAdd) {
                Image(systemName: "plus")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundStyle(MdColor.oledTrueBlack)
                    .frame(width: 40, height: 40)
                    .background(RoundedRectangle(cornerRadius: 10).fill(MdColor.neonOrange))
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Добавить элемент")
        }
        .padding(.horizontal, MdChrome.contentHorizontalPadding)
        .padding(.top, 8)
    }

    private var searchBar: some View {
        HStack {
            Image(systemName: "magnifyingglass")
                .foregroundStyle(MdColor.textSecondary)
            TextField("Поиск", text: Binding(
                get: { snapshot.searchQuery },
                set: onSearch
            ))
            .foregroundStyle(MdColor.textPrimary)
        }
        .padding(12)
        .background(RoundedRectangle(cornerRadius: 12).fill(MdColor.surfaceElevated))
        .padding(.horizontal, MdChrome.contentHorizontalPadding)
        .padding(.top, 8)
    }

    private var categoryRow: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                filterChip("Все", selected: snapshot.filterCategory == nil) {
                    onFilter(nil)
                }
                ForEach(snapshot.categories, id: \.self) { cat in
                    filterChip(cat, selected: snapshot.filterCategory == cat) {
                        onFilter(cat)
                    }
                }
            }
            .padding(.horizontal, MdChrome.contentHorizontalPadding)
            .padding(.vertical, 10)
        }
    }

    private func filterChip(_ title: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(selected ? MdColor.oledTrueBlack : MdColor.textPrimary)
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(
                    Capsule().fill(selected ? MdColor.neonOrange : MdColor.surfaceElevated)
                )
        }
        .buttonStyle(.plain)
    }

    private func elementCard(_ card: ElementCardSnapshot) -> some View {
        let playing = snapshot.playingCardId == card.id
        return Button {
            onOpenDetail(card.id)
        } label: {
            VStack(alignment: .leading, spacing: 8) {
                ZStack {
                    RoundedRectangle(cornerRadius: 12)
                        .fill(MdColor.surfaceElevated)
                        .aspectRatio(1, contentMode: .fit)
                    if let urlStr = card.coverUrl, let url = URL(string: urlStr),
                       url.scheme == "https" || url.scheme == "http" {
                        MdRemoteImage(url: url)
                            .aspectRatio(contentMode: .fill)
                            .clipped()
                    } else {
                        Image(systemName: "figure.dance")
                            .font(.system(size: 28))
                            .foregroundStyle(MdColor.neonOrange.opacity(0.7))
                    }
                    if playing {
                        Circle()
                            .fill(MdColor.neonOrange.opacity(0.9))
                            .frame(width: 36, height: 36)
                            .overlay(Image(systemName: "pause.fill").foregroundStyle(.black).font(.system(size: 12)))
                    }
                }
                .clipShape(RoundedRectangle(cornerRadius: 12))
                Text(card.title)
                    .font(MdType.bodyMedium.weight(.semibold))
                    .foregroundStyle(MdColor.textPrimary)
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)
                HStack {
                    Text(card.tag)
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(MdColor.neonOrange)
                    Spacer()
                    Text(card.durationLabel)
                        .font(.system(size: 11))
                        .foregroundStyle(MdColor.textSecondary)
                }
            }
            .padding(10)
            .background(
                RoundedRectangle(cornerRadius: 16)
                    .fill(MdColor.surfaceElevated)
                    .overlay(
                        RoundedRectangle(cornerRadius: 16)
                            .stroke(playing ? MdColor.neonOrange.opacity(0.6) : MdColor.surfaceBorder, lineWidth: 1)
                    )
            )
        }
        .buttonStyle(.plain)
        .simultaneousGesture(
            LongPressGesture(minimumDuration: 0.35).onEnded { _ in
                onPlay(card.id)
                MdHaptics.perform(.selection)
            }
        )
    }
}
