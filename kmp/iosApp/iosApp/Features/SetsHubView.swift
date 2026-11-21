import SwiftUI

enum SetsHubTab: Equatable {
    case mine
    case imported

    var title: String { self == .mine ? "Мои" : "Добавленные" }
}

enum SetsEditorMode: Equatable {
    case create
    case edit
    case archive

    var title: String {
        switch self {
        case .create: "Создать сет"
        case .edit: "Редактировать сет"
        case .archive: "Просмотр сета"
        }
    }
}

enum SetsSection: String, CaseIterable {
    case elements
    case music
    case choreographies

    var title: String {
        switch self {
        case .elements: "Элементы"
        case .music: "Музыка"
        case .choreographies: "Хореографии"
        }
    }

    var icon: String {
        switch self {
        case .elements: "list.bullet"
        case .music: "music.note"
        case .choreographies: "person.3.fill"
        }
    }
}

struct SetsChoiceSnapshot: Equatable, Identifiable {
    let id: String
    let title: String
    var subtitle = ""
}

struct SetsCardSnapshot: Equatable, Identifiable {
    let id: String
    let name: String
    let style: String
    let tags: [String]
    let estimatedBytes: Int64
    let elements: Int
    let music: Int
    let choreographies: Int
    let isArchiveOnly: Bool

    var detail: String {
        let megabytes = Double(max(estimatedBytes, 0)) / 1_048_576
        let weight = megabytes > 0 && megabytes < 0.1
            ? "<0.1 МБ" : String(format: "%.1f МБ", (megabytes * 10).rounded(.down) / 10)
        let description = isArchiveOnly ? "Архив сета" : "\(elements) эл. · \(music) муз. · \(choreographies) хор."
        return "\(style.isEmpty ? "—" : style) · \(description) · \(weight)"
    }
}

struct SetsPickerSnapshot: Equatable {
    let kind: SetsSection
    let rows: [SetsChoiceSnapshot]
    let selectedIDs: Set<String>
}

struct SetsEditorSnapshot: Equatable {
    let name: String
    let styleID: String
    let styleName: String
    let styles: [SetsChoiceSnapshot]
    let mode: SetsEditorMode
    let elements: Int
    let music: Int
    let choreographies: Int
    let weight: String
    let loading: Bool
    let error: String?
    let invalidSelection: Bool
    let picker: SetsPickerSnapshot?
    var canOpenSections = true
    var overweight = false
    var weightFraction: Double? = nil
    var limits: String? = nil

    func itemCount(in section: SetsSection) -> Int {
        switch section {
        case .elements: elements
        case .music: music
        case .choreographies: choreographies
        }
    }
}

struct SetsHubSnapshot: Equatable {
    let tab: SetsHubTab
    let cards: [SetsCardSnapshot]
    let searchQuery: String
    let filter: String
    let tags: [String]
    let alphabetical: Bool
    let busy: Bool
    let error: String?
    let editor: SetsEditorSnapshot?
}

struct SetsHubActions {
    var selectTab: (SetsHubTab) -> Void
    var search: (String) -> Void
    var filter: (String?) -> Void
    var sortAlphabetical: (Bool) -> Void
    var create: () -> Void
    var open: (String) -> Void
    var delete: (String) -> Void
    var closeEditor: () -> Void
    var setName: (String) -> Void
    var selectStyle: (String) -> Void
    var openSection: (SetsSection) -> Void
    var closeSection: () -> Void
    var toggleItem: (String) -> Void
    var confirmSection: () -> Void
    var save: () -> Void
    var loadArchive: (() -> Void)? = nil
    var share: ((String) -> Void)? = nil
    var retryArchive: (() -> Void)? = nil
    var removeInvalidSelection: (() -> Void)? = nil
}

struct SetsHubView: View {
    let snapshot: SetsHubSnapshot
    let actions: SetsHubActions

    @State private var pendingDelete: SetsCardSnapshot?

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 14) {
                tabSelector
                primaryAction
                controls
                if snapshot.busy {
                    ProgressView().tint(SetsChrome.flame)
                        .frame(maxWidth: .infinity)
                }
                if let error = snapshot.error {
                    Text(error).font(MdType.brandedBody).foregroundStyle(MdColor.danger)
                        .accessibilityIdentifier("sets.error")
                }
                if snapshot.cards.isEmpty && !snapshot.busy { emptyState }
                ForEach(snapshot.cards) { card in setCard(card) }
            }
            .padding(.horizontal, MdChrome.contentHorizontalPadding)
            .padding(.top, 8)
            .padding(.bottom, 20)
        }
        .scrollDismissesKeyboard(.interactively)
        .background(MdColor.oledTrueBlack)
        .accessibilityIdentifier("sets.hub")
        .fullScreenCover(isPresented: Binding(
            get: { snapshot.editor != nil },
            set: { if !$0 && snapshot.editor != nil { actions.closeEditor() } }
        )) {
            if let editor = snapshot.editor {
                SetsEditorView(snapshot: editor, actions: actions, busy: snapshot.busy)
                    .interactiveDismissDisabled()
            }
        }
        .alert("Удалить сет?", isPresented: Binding(
            get: { pendingDelete != nil },
            set: { if !$0 { pendingDelete = nil } }
        ), presenting: pendingDelete) { card in
            Button("Удалить", role: .destructive) { actions.delete(card.id) }
            Button("Отмена", role: .cancel) {}
        } message: { card in
            Text("Сет «\(card.name)» будет удалён без возможности восстановления.")
        }
    }

    private var tabSelector: some View {
        HStack(spacing: 4) {
            tabButton(.mine, id: "sets.mine")
            tabButton(.imported, id: "sets.imported")
        }
        .padding(4)
        .background(SetsChrome.panel, in: RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(SetsChrome.border))
    }

    private func tabButton(_ tab: SetsHubTab, id: String) -> some View {
        let selected = snapshot.tab == tab
        return Button {
            actions.selectTab(tab)
        } label: {
            Text(tab.title)
                .font(MdType.brandedControl)
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity, minHeight: 45)
                .background {
                    if selected {
                        SetsChrome.gradient.clipShape(RoundedRectangle(cornerRadius: 12))
                    }
                }
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
        .accessibilityIdentifier(id)
    }

    private var primaryAction: some View {
        let isMine = snapshot.tab == .mine
        return SetsChrome.primaryButton(
            isMine ? "Создать сет" : "Загрузить сет",
            enabled: !snapshot.busy && (isMine || actions.loadArchive != nil)
        ) {
            if isMine { actions.create() } else { actions.loadArchive?() }
        }
        .accessibilityIdentifier(isMine ? "sets.create" : "sets.load")
    }

    private var controls: some View {
        HStack(spacing: 8) {
            Menu {
                Button("Все теги") { actions.filter(nil) }
                ForEach(snapshot.tags, id: \.self) { tag in
                    Button(tag) { actions.filter(tag) }
                }
            } label: {
                controlIcon("tag.fill", accent: SetsChrome.flame)
            }
            .accessibilityLabel("Фильтр")
            .accessibilityValue(snapshot.filter)
            .accessibilityIdentifier("sets.filter")

            Menu {
                Button {
                    actions.sortAlphabetical(false)
                } label: {
                    if snapshot.alphabetical { Text("По стилю") }
                    else { Label("По стилю", systemImage: "checkmark") }
                }
                Button {
                    actions.sortAlphabetical(true)
                } label: {
                    if snapshot.alphabetical { Label("По алфавиту", systemImage: "checkmark") }
                    else { Text("По алфавиту") }
                }
            } label: {
                controlIcon("line.3.horizontal.decrease", accent: MdColor.textPrimary)
            }
            .accessibilityLabel("Сортировка")
            .accessibilityIdentifier("sets.sort")
            SetsSearchField(
                prompt: "Поиск сетов…",
                text: Binding(get: { snapshot.searchQuery }, set: actions.search)
            )
            .accessibilityIdentifier("sets.search")
        }
    }

    private func controlIcon(_ icon: String, accent: Color) -> some View {
        Image(systemName: icon)
            .font(.system(size: 17, weight: .semibold))
            .foregroundStyle(accent)
            .frame(width: 44, height: 44)
            .background(SetsChrome.panel, in: RoundedRectangle(cornerRadius: 13))
            .overlay(RoundedRectangle(cornerRadius: 13).strokeBorder(SetsChrome.border))
    }

    private var emptyState: some View {
        let isFiltering = !snapshot.searchQuery.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            || (!snapshot.filter.isEmpty && !["Все", "Все теги"].contains(snapshot.filter))
        let message: String
        if isFiltering { message = "Сеты не найдены." }
        else if snapshot.tab == .mine { message = "Пока нет своих сетов. Нажмите «Создать сет»." }
        else if actions.loadArchive != nil { message = "Пока нет добавленных сетов. Нажмите «Загрузить сет»." }
        else { message = "Пока нет добавленных сетов. Загрузка архивов недоступна." }
        return Text(message)
            .font(MdType.brandedBody)
            .foregroundStyle(MdColor.textSecondary)
            .padding(.vertical, 23)
            .accessibilityIdentifier("sets.empty")
    }

    private func setCard(_ card: SetsCardSnapshot) -> some View {
        HStack(spacing: 0) {
            Button {
                actions.open(card.id)
            } label: {
                VStack(alignment: .leading, spacing: 5) {
                    Text(card.name)
                        .font(MdType.brandedControl)
                        .foregroundStyle(.white)
                        .lineLimit(1)
                    Text(card.detail)
                        .font(.system(size: 12))
                        .foregroundStyle(MdColor.textSecondary)
                        .lineLimit(2)
                    if let tag = card.tags.first {
                        Text(tag)
                            .font(.caption2)
                            .foregroundStyle(SetsChrome.flame)
                            .padding(.horizontal, 7)
                            .padding(.vertical, 3)
                            .background(SetsChrome.flame.opacity(0.13), in: Capsule())
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
                .padding(.vertical, 12)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("sets.card.\(card.id)")
            iconButton(
                snapshot.tab == .mine ? "pencil" : "chevron.right",
                label: snapshot.tab == .mine ? "Редактировать" : "Смотреть состав"
            ) { actions.open(card.id) }
            if let share = actions.share {
                iconButton("square.and.arrow.up", label: "Поделиться") { share(card.id) }
            }
            iconButton("trash", label: "Удалить") { pendingDelete = card }
        }
        .padding(.horizontal, 12)
        .background(SetsChrome.panel, in: RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(SetsChrome.border))
    }

    private func iconButton(_ icon: String, label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: icon)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(SetsChrome.flame)
                .frame(width: 44, height: 44)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }
}

enum SetsChrome {
    static let flame = Color(red: 1, green: 85.0 / 255, blue: 0)
    static let panel = Color(red: 11.0 / 255, green: 12.0 / 255, blue: 17.0 / 255)
    static let border = Color.white.opacity(0.14)
    static let gradient = LinearGradient(
        colors: [flame, Color(red: 1, green: 166.0 / 255, blue: 37.0 / 255)],
        startPoint: .leading, endPoint: .trailing
    )

    static func primaryButton(_ title: String, enabled: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(MdType.brandedControl)
                .foregroundStyle(enabled ? .white : MdColor.textSecondary)
                .frame(maxWidth: .infinity, minHeight: 46)
                .background {
                    let shape = RoundedRectangle(cornerRadius: 14)
                    if enabled { gradient.clipShape(shape) }
                    else { shape.fill(Color.white.opacity(0.12)) }
                }
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }
}

struct SetsSearchField: View {
    let prompt: String
    @Binding var text: String

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: "magnifyingglass")
                .foregroundStyle(MdColor.textSecondary)
                .accessibilityHidden(true)
            TextField(prompt, text: $text)
                .font(MdType.brandedBody)
                .foregroundStyle(.white)
                .textFieldStyle(.plain)
                .autocorrectionDisabled()
                .submitLabel(.search)
        }
        .padding(.horizontal, 12)
        .frame(minHeight: 44)
        .background(SetsChrome.panel, in: RoundedRectangle(cornerRadius: 13))
        .overlay(RoundedRectangle(cornerRadius: 13).strokeBorder(SetsChrome.border))
    }
}
