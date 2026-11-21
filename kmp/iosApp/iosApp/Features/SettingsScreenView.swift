import SwiftUI

struct SettingsScreenView: View {
    let snapshot: SettingsShellSnapshot
    var onBack: () -> Void
    var onSetLanguage: (String) -> Void
    var onSetReminder: (Bool) -> Void
    var onSetStyleDraft: (String) -> Void
    var onAddStyle: () -> Void
    var onRemoveStyle: (String) -> Void
    var onSetCategoryDraft: (String) -> Void
    var onAddCategory: () -> Void
    var onRemoveCategory: (String) -> Void
    var onSetLowDiskGib: (Int) -> Void
    var onSetLowDiskEnabled: (Bool) -> Void
    var onRenameStyle: ((String, String) -> Void)? = nil
    var stylesBusy = false
    var stylesError: String? = nil

    @State private var styleDialog: StyleDialog?
    @State private var stylePendingDelete: String?
    @State private var styleName = ""

    private let languages = [(code: "ru", name: "Русский"), (code: "en", name: "English"), (code: "es", name: "Español")]
    private let permanentStyle = "Без стиля"
    private let styleNameLimit = 24
    private let cardTitle = Font.custom("NotoSerif-Bold", size: 18, relativeTo: .headline)

    var body: some View {
        VStack(spacing: 0) {
            topBar
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    languageBlock
                    reminderBlock
                    stylesBlock
                    mediaBlock
                    tagsBlock
                    lowDiskBlock
                    if !snapshot.prefsLoaded {
                        ProgressView("Загрузка настроек…")
                            .tint(MdColor.neonOrange)
                    }
                }
                .padding(.horizontal, MdChrome.contentHorizontalPadding)
                .padding(.vertical, MdChrome.contentVerticalPadding)
                .padding(.bottom, 26)
            }
            .scrollDismissesKeyboard(.interactively)
        }
        .background(MdColor.oledTrueBlack.ignoresSafeArea())
        .preferredColorScheme(.dark)
        .sheet(item: $styleDialog) { dialog in
            styleNameForm(dialog)
                .presentationDetents([.medium])
        }
        .alert("Удалить стиль?", isPresented: Binding(
            get: { stylePendingDelete != nil },
            set: { if !$0 { stylePendingDelete = nil } }
        ), presenting: stylePendingDelete) { name in
            Button("Удалить", role: .destructive) { onRemoveStyle(name) }
            Button("Отмена", role: .cancel) {}
        } message: { _ in
            Text("Элементы, музыка и хореографии перейдут в «Без стиля». Материалы сохранятся.")
        }
    }

    private func styleNameForm(_ dialog: StyleDialog) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(dialog.title)
                .font(MdType.titleLarge)
                .foregroundStyle(.white)
                .accessibilityAddTraits(.isHeader)
            TextField("Имя стиля", text: $styleName)
                .font(MdType.brandedBody)
                .foregroundStyle(.white)
                .textFieldStyle(.plain)
                .padding(12)
                .modifier(SettingsChrome.Row())
                .submitLabel(.done)
                .onSubmit { commitStyleName(dialog) }
                .accessibilityIdentifier("settings.style.name")
            detail("До \(styleNameLimit) символов.")
            Spacer(minLength: 0)
            HStack {
                Button("Отмена", role: .cancel) { styleDialog = nil }
                    .foregroundStyle(MdColor.textSecondary)
                Spacer()
                Button(dialog == .add ? "Добавить" : "Сохранить") { commitStyleName(dialog) }
                    .disabled(!isValidStyleName(dialog))
                    .accessibilityIdentifier("settings.style.confirm")
            }
            .font(MdType.brandedControl)
            .tint(MdColor.neonOrange)
        }
        .padding(20)
        .background(MdColor.oledTrueBlack.ignoresSafeArea())
    }

    private var topBar: some View {
        HStack(spacing: 10) {
            Button(action: onBack) {
                Image(systemName: "arrow.left")
                    .font(.system(size: 23, weight: .medium))
                    .foregroundStyle(MdColor.neonOrange)
                    .frame(width: 44, height: 44, alignment: .leading)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Назад")
            .accessibilityIdentifier("settings.close")
            Text(snapshot.title.isEmpty ? "Настройки" : snapshot.title)
                .font(MdType.titleLarge)
                .foregroundStyle(.white)
                .accessibilityAddTraits(.isHeader)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, MdChrome.contentHorizontalPadding)
        .frame(minHeight: MdChrome.topAppBarHeight)
        .background(MdColor.oledTrueBlack)
    }

    private var languageBlock: some View {
        let name = languages.first(where: { $0.code == snapshot.languageCode })?.name
            ?? snapshot.languageCode.uppercased()
        return HStack(alignment: .center, spacing: 8) {
            title("Язык интерфейса")
            Spacer(minLength: 0)
            Menu {
                ForEach(languages, id: \.code) { option in
                    Button {
                        onSetLanguage(option.code)
                    } label: {
                        if snapshot.languageCode == option.code {
                            Label(option.name, systemImage: "checkmark")
                        } else {
                            Text(option.name)
                        }
                    }
                }
            } label: {
                HStack(spacing: 8) {
                    Text(name)
                        .lineLimit(1)
                    Image(systemName: "chevron.down")
                        .font(.system(size: 12, weight: .semibold))
                }
                .font(MdType.brandedControl)
                .foregroundStyle(.white)
                .padding(.horizontal, 12)
                .frame(minHeight: 44)
                .background(SettingsChrome.hot, in: RoundedRectangle(cornerRadius: 12))
                .overlay(RoundedRectangle(cornerRadius: 12).strokeBorder(.white.opacity(0.23)))
            }
            .accessibilityLabel("Язык интерфейса")
            .accessibilityValue(name)
            .accessibilityIdentifier("settings.language")
        }
        .modifier(SettingsChrome.Card())
    }

    private var reminderBlock: some View {
        Toggle(isOn: Binding(get: { snapshot.reminderEnabled }, set: onSetReminder)) {
            VStack(alignment: .leading, spacing: 5) {
                title("Напоминание о танце")
                detail(snapshot.reminderEnabled ? "Включено" : "Выключено")
            }
        }
        .tint(MdColor.neonOrange)
        .accessibilityIdentifier("settings.reminder")
        .modifier(SettingsChrome.Card())
    }

    private var stylesBlock: some View {
        VStack(alignment: .leading, spacing: 9) {
            HStack(alignment: .center, spacing: 10) {
                VStack(alignment: .leading, spacing: 6) {
                    title("Стили")
                    detail("Стили появляются в переключателе на экране «Элементы».")
                }
                Spacer(minLength: 3)
                Button {
                    styleName = snapshot.newStyleDraft
                    styleDialog = .add
                } label: {
                    Image(systemName: "plus")
                        .font(.system(size: 20, weight: .semibold))
                        .foregroundStyle(.white)
                        .frame(width: 44, height: 44)
                        .background(MdColor.neonOrange, in: RoundedRectangle(cornerRadius: 13))
                }
                .buttonStyle(.plain)
                .disabled(stylesBusy)
                .accessibilityLabel("Добавить стиль")
                .accessibilityIdentifier("settings.style.add")
            }
            if stylesBusy {
                ProgressView("Обновление стилей…")
                    .font(MdType.brandedBody)
                    .tint(MdColor.neonOrange)
            }
            if let stylesError {
                Text(stylesError)
                    .font(MdType.brandedBody)
                    .foregroundStyle(MdColor.danger)
                    .accessibilityIdentifier("settings.style.error")
            }
            if !snapshot.danceStyles.isEmpty {
                ScrollView {
                    LazyVStack(spacing: 4) {
                        ForEach(snapshot.danceStyles, id: \.self) { style in
                            styleRow(style)
                        }
                    }
                }
                .frame(maxHeight: 255)
                .accessibilityElement(children: .contain)
                .accessibilityIdentifier("settings.styles.list")
            }
        }
        .modifier(SettingsChrome.Card())
    }

    private func styleRow(_ style: String) -> some View {
        HStack(spacing: 0) {
            Text(style)
                .font(MdType.brandedBody)
                .foregroundStyle(.white)
                .lineLimit(1)
                .frame(maxWidth: .infinity, alignment: .leading)
            if style == permanentStyle {
                Text("Постоянный")
                    .font(.caption)
                    .foregroundStyle(MdColor.textSecondary)
                    .lineLimit(1)
                    .fixedSize(horizontal: true, vertical: false)
                    .padding(.trailing, 10)
            } else {
                if onRenameStyle != nil {
                    rowAction("pencil", label: "Переименовать стиль \(style)") {
                        styleName = style
                        styleDialog = .rename(style)
                    }
                    .disabled(stylesBusy)
                    .accessibilityIdentifier("settings.style.rename.\(style)")
                }
                rowAction("xmark", label: "Удалить стиль \(style)") {
                    stylePendingDelete = style
                }
                .disabled(stylesBusy)
                .accessibilityIdentifier("settings.style.delete.\(style)")
            }
        }
        .padding(.leading, 12)
        .frame(minHeight: style == permanentStyle ? 36 : 49)
        .modifier(SettingsChrome.Row())
    }

    private var mediaBlock: some View {
        VStack(alignment: .leading, spacing: 8) {
            title("Хранение медиа")
            detail("Импортированные видео и музыка сохраняются отдельными копиями в Midnight Dancer. Удаление исходника из «Фото» или «Файлов» на эти копии не влияет.")
            detail("Копии занимают место на iPhone.")
        }
        .modifier(SettingsChrome.Card())
    }

    private var tagsBlock: some View {
        VStack(alignment: .leading, spacing: 8) {
            title("Теги элементов")
            ForEach(snapshot.elementCategories, id: \.self) { tag in
                HStack {
                    Text(tag)
                        .font(MdType.brandedBody)
                        .foregroundStyle(.white)
                    Spacer()
                    rowAction("trash", label: "Удалить тег \(tag)") { onRemoveCategory(tag) }
                }
                .padding(.leading, 12)
                .modifier(SettingsChrome.Row())
            }
            HStack(spacing: 8) {
                TextField("Новый тег", text: Binding(
                    get: { snapshot.newCategoryDraft },
                    set: onSetCategoryDraft
                ))
                .foregroundStyle(.white)
                .font(MdType.brandedBody)
                .textFieldStyle(.plain)
                .padding(.horizontal, 12)
                .frame(minHeight: 44)
                .modifier(SettingsChrome.Row())
                .accessibilityIdentifier("settings.tag.name")
                Button("Добавить", action: onAddCategory)
                    .font(MdType.brandedControl)
                    .foregroundStyle(MdColor.neonOrange)
                    .frame(minHeight: 44)
                    .accessibilityIdentifier("settings.tag.add")
            }
        }
        .modifier(SettingsChrome.Card())
    }

    private var lowDiskBlock: some View {
        VStack(alignment: .leading, spacing: 8) {
            Toggle(isOn: Binding(get: { snapshot.lowDiskWarnEnabled }, set: onSetLowDiskEnabled)) {
                title("Предупреждение о месте")
            }
            .tint(MdColor.neonOrange)
            .accessibilityIdentifier("settings.disk.warning")
            detail("Порог: \(snapshot.lowDiskWarnGib) ГБ")
            HStack(spacing: 5) {
                ForEach(snapshot.lowDiskPresetsGib, id: \.self) { gib in
                    let selected = gib == snapshot.lowDiskWarnGib
                    Button {
                        onSetLowDiskGib(gib)
                    } label: {
                        Text("\(gib)")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundStyle(selected ? .black : .white)
                            .frame(maxWidth: .infinity, minHeight: 44)
                            .background(selected ? MdColor.neonOrange : SettingsChrome.row,
                                        in: RoundedRectangle(cornerRadius: 8))
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("\(gib) ГБ")
                    .accessibilityAddTraits(selected ? .isSelected : [])
                }
            }
        }
        .modifier(SettingsChrome.Card())
    }

    private func title(_ text: String) -> some View {
        Text(text)
            .font(cardTitle)
            .foregroundStyle(.white)
            .accessibilityAddTraits(.isHeader)
    }

    private func detail(_ text: String) -> some View {
        Text(text)
            .font(MdType.brandedBody)
            .foregroundStyle(MdColor.textSecondary)
    }

    private func rowAction(_ icon: String, label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: icon)
                .font(.system(size: 17, weight: .medium))
                .foregroundStyle(MdColor.neonOrange)
                .frame(width: 44, height: 44)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }

    private func isValidStyleName(_ dialog: StyleDialog) -> Bool {
        let name = styleName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !stylesBusy, !name.isEmpty, name.utf16.count <= styleNameLimit else { return false }
        if case .rename(let current) = dialog { return current != name }
        return true
    }

    private func commitStyleName(_ dialog: StyleDialog) {
        guard isValidStyleName(dialog) else { return }
        let name = styleName.trimmingCharacters(in: .whitespacesAndNewlines)
        styleDialog = nil
        switch dialog {
        case .add:
            onSetStyleDraft(name)
            onAddStyle()
        case .rename(let current):
            onRenameStyle?(current, name)
        }
    }

    private enum StyleDialog: Equatable, Identifiable {
        case add
        case rename(String)

        var id: String {
            switch self {
            case .add: "add"
            case .rename(let name): "rename-" + name
            }
        }

        var title: String {
            switch self {
            case .add: "Добавить стиль"
            case .rename: "Переименовать стиль"
            }
        }
    }
}

private enum SettingsChrome {
    static let card = Color(white: 0.047)
    static let row = Color(white: 0.075)
    static let hot = Color(red: 1, green: 85.0 / 255, blue: 0)

    struct Card: ViewModifier {
        func body(content: Content) -> some View {
            let shape = RoundedRectangle(cornerRadius: 17)
            content
                .padding(15)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(card, in: shape)
                .overlay {
                    shape.strokeBorder(LinearGradient(
                        colors: [MdColor.neonOrange.opacity(0.52), .white.opacity(0.09), MdColor.neonOrange.opacity(0.27)],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    ))
                    .allowsHitTesting(false)
                }
        }
    }

    struct Row: ViewModifier {
        func body(content: Content) -> some View {
            let shape = RoundedRectangle(cornerRadius: 12)
            content
                .background(row, in: shape)
                .overlay { shape.strokeBorder(.white.opacity(0.08)).allowsHitTesting(false) }
        }
    }
}
