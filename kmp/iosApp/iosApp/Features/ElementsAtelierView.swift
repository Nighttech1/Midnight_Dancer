import SwiftUI

/// Elements catalog — style split + LazyVGrid (Android Kinetic Atelier feel).
struct ElementsAtelierView: View {
    let snapshot: ElementsFeatureSnapshot
    var onStylePrevious: () -> Void
    var onStyleNext: () -> Void
    var onAdd: () -> Void
    var onSearch: (String) -> Void
    var onFilter: (String?) -> Void
    var onSort: (Bool) -> Void
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
                MdCatalogToolbar(
                    prefix: "element", searchPlaceholder: "Поиск элементов…", query: snapshot.searchQuery,
                    tags: snapshot.categories, selectedTag: snapshot.filterCategory,
                    alphabetical: snapshot.sortAlphabetically, tagTint: .white,
                    onSearch: onSearch, onFilter: onFilter, onSort: onSort
                )
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
        .fullScreenCover(isPresented: editorBinding) {
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
            HStack(spacing: 0) {
                styleArrow("chevron.left", label: "Предыдущий стиль", action: onStylePrevious)
                Text(snapshot.styleName)
                    .font(MdType.brandedControl)
                    .foregroundStyle(MdColor.textPrimary)
                    .frame(maxWidth: .infinity)
                    .lineLimit(1)
                    .minimumScaleFactor(0.75)
                styleArrow("chevron.right", label: "Следующий стиль", action: onStyleNext)
            }
            .background {
                Capsule().fill(MdColor.surfaceElevated)
                    .overlay { Capsule().strokeBorder(.white.opacity(0.09)) }
            }
            Button(action: onAdd) {
                Text("Создать элемент")
                    .font(MdType.brandedControl)
                    .lineLimit(1)
                    .minimumScaleFactor(0.75)
                    .foregroundStyle(.white)
                    .padding(.horizontal, 13)
                    .frame(minHeight: 48)
                    .background(Capsule().fill(MdColor.neonOrange))
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("element.add")
        }
        .padding(.horizontal, MdChrome.contentHorizontalPadding)
        .padding(.top, 10)
    }

    private func styleArrow(_ symbol: String, label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: symbol)
                .font(.system(size: 14, weight: .bold))
                .foregroundStyle(MdColor.neonOrange)
                .frame(width: 42, height: 48)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
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
