import SwiftUI

/// One row for the elements, music and choreography catalogs. Selections live in Shared.
struct MdCatalogToolbar: View {
    let prefix: String
    let searchPlaceholder: String
    let query: String
    let tags: [String]
    let selectedTag: String?
    let alphabetical: Bool
    var tagTint = MdColor.neonOrange
    let onSearch: (String) -> Void
    let onFilter: (String?) -> Void
    let onSort: (Bool) -> Void

    var body: some View {
        HStack(spacing: 8) {
            Menu {
                menuOption("Все теги", selected: selectedTag == nil) { onFilter(nil) }
                ForEach(tags, id: \.self) { tag in
                    menuOption(tag, selected: isSelected(tag)) { onFilter(tag) }
                }
            } label: {
                icon("tag.fill", tint: tagTint, active: selectedTag != nil)
            }
            .accessibilityLabel("Фильтр по тегу")
            .accessibilityValue(selectedTag ?? "Все теги")
            .accessibilityIdentifier("\(prefix).filter")

            Menu {
                menuOption("По тегу", selected: !alphabetical) { onSort(false) }
                menuOption("По алфавиту", selected: alphabetical) { onSort(true) }
            } label: {
                icon("line.3.horizontal.decrease", tint: alphabetical ? MdColor.neonOrange : .white,
                     active: alphabetical)
            }
            .accessibilityLabel("Сортировка")
            .accessibilityValue(alphabetical ? "По алфавиту" : "По тегу")
            .accessibilityIdentifier("\(prefix).sort")

            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass")
                    .foregroundStyle(MdColor.textSecondary)
                    .accessibilityHidden(true)
                TextField(searchPlaceholder, text: Binding(get: { query }, set: onSearch))
                    .font(MdType.brandedBody)
                    .foregroundStyle(.white)
                    .textFieldStyle(.plain)
                    .autocorrectionDisabled()
                    .submitLabel(.search)
                    .accessibilityIdentifier("\(prefix).search")
            }
            .padding(.horizontal, 12)
            .frame(minHeight: 44)
            .background(MdColor.surfaceElevated, in: RoundedRectangle(cornerRadius: 13))
            .overlay {
                RoundedRectangle(cornerRadius: 13)
                    .strokeBorder(.white.opacity(0.13))
                    .allowsHitTesting(false)
            }
        }
        .padding(.horizontal, MdChrome.contentHorizontalPadding)
        .padding(.top, 12)
        .padding(.bottom, 8)
    }

    private func isSelected(_ tag: String) -> Bool {
        guard let selectedTag else { return false }
        return tag.caseInsensitiveCompare(selectedTag) == .orderedSame
    }

    private func menuOption(_ title: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            if selected {
                Label(title, systemImage: "checkmark")
            } else {
                Text(title)
            }
        }
    }

    private func icon(_ symbol: String, tint: Color, active: Bool) -> some View {
        let shape = RoundedRectangle(cornerRadius: 13)
        return Image(systemName: symbol)
            .font(.system(size: 17, weight: .semibold))
            .foregroundStyle(tint)
            .frame(width: 44, height: 44)
            .background(active ? MdColor.neonOrange.opacity(0.13) : MdColor.surfaceElevated, in: shape)
            .overlay { shape.strokeBorder(active ? MdColor.neonOrange.opacity(0.57) : .white.opacity(0.15)) }
    }
}
