import SwiftUI

struct SetsEditorView: View {
    let snapshot: SetsEditorSnapshot
    let actions: SetsHubActions
    let busy: Bool

    @FocusState private var nameFocused: Bool
    @State private var styleExpanded = false

    private var readOnly: Bool { snapshot.mode == .archive }
    private var saveEnabled: Bool {
        !busy && !snapshot.loading && !snapshot.invalidSelection && !snapshot.overweight
    }

    var body: some View {
        Group {
            if let picker = snapshot.picker {
                SetsSectionPickerView(
                    picker: picker,
                    style: snapshot.styleName,
                    weight: snapshot.weight,
                    readOnly: readOnly,
                    actions: actions
                )
                .id(picker.kind)
            } else {
                editorBody
            }
        }
        .background(MdColor.oledTrueBlack.ignoresSafeArea())
        .preferredColorScheme(.dark)
    }

    private var editorBody: some View {
        VStack(spacing: 0) {
            header
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    nameBlock
                    styleBlock
                    status
                    sectionList
                    if !readOnly { storage }
                }
                .padding(.horizontal, MdChrome.contentHorizontalPadding)
                .padding(.vertical, 14)
            }
            .scrollDismissesKeyboard(.interactively)
            if !readOnly {
                SetsChrome.primaryButton(saveTitle, enabled: saveEnabled) {
                    nameFocused = false
                    styleExpanded = false
                    actions.save()
                }
                .accessibilityIdentifier("sets.save")
                .padding(.horizontal, MdChrome.contentHorizontalPadding)
                .padding(.vertical, 10)
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("sets.editor")
    }

    private var header: some View {
        HStack(spacing: 10) {
            RoundedRectangle(cornerRadius: 2)
                .fill(SetsChrome.flame)
                .frame(width: 4, height: 22)
                .accessibilityHidden(true)
            Text(snapshot.mode.title)
                .font(MdType.titleLarge)
                .foregroundStyle(.white)
            Spacer(minLength: 6)
            Button(action: actions.closeEditor) {
                Image(systemName: "xmark")
                    .font(.system(size: 17, weight: .medium))
                    .foregroundStyle(MdColor.textSecondary)
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Закрыть")
            .accessibilityIdentifier("sets.closeEditor")
        }
        .padding(.leading, MdChrome.contentHorizontalPadding)
        .padding(.trailing, 8)
        .padding(.vertical, 3)
    }

    private var nameBlock: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                fieldLabel("ИМЯ СЕТА")
                Spacer()
                if !readOnly {
                    Text("ОБЯЗАТЕЛЬНО")
                        .font(.caption2)
                        .tracking(0.6)
                        .foregroundStyle(MdColor.textSecondary)
                        .accessibilityHidden(true)
                }
            }
            HStack {
                if readOnly {
                    Text(snapshot.name)
                        .accessibilityIdentifier("sets.name")
                } else {
                    TextField(
                        "Имя сета",
                        text: Binding(get: { snapshot.name }, set: actions.setName)
                    )
                    .textFieldStyle(.plain)
                    .focused($nameFocused)
                    .submitLabel(.done)
                    .onSubmit { nameFocused = false }
                    .accessibilityIdentifier("sets.name")
                    Image(systemName: "pencil")
                        .foregroundStyle(SetsChrome.flame)
                        .accessibilityHidden(true)
                }
            }
            .font(MdType.brandedBody)
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity, minHeight: 46, alignment: .leading)
            .padding(.horizontal, 12)
            .background(MdColor.oledTrueBlack, in: RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(SetsChrome.border))
        }
    }

    private var chosenStyle: String {
        if !snapshot.styleName.isEmpty { return snapshot.styleName }
        if !snapshot.styleID.isEmpty { return snapshot.styleID }
        return snapshot.mode == .create ? "Выберите стиль" : "Стиль не указан"
    }

    private var styleBlock: some View {
        VStack(alignment: .leading, spacing: 6) {
            fieldLabel("СТИЛЬ СЕТА")
            if snapshot.mode == .create {
                Button {
                    nameFocused = false
                    styleExpanded.toggle()
                } label: {
                    HStack {
                        Text(chosenStyle)
                        Spacer()
                        Image(systemName: styleExpanded ? "chevron.up" : "chevron.down")
                            .foregroundStyle(SetsChrome.flame)
                            .accessibilityHidden(true)
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .modifier(SetsStyleFieldChrome())
                .disabled(snapshot.styles.isEmpty || busy || snapshot.loading)
                .accessibilityIdentifier("sets.style")
                if styleExpanded { styleChoices }
            } else {
                Text(chosenStyle)
                    .modifier(SetsStyleFieldChrome())
                    .accessibilityIdentifier("sets.style")
            }
        }
    }

    private var styleChoices: some View {
        ScrollView {
            LazyVStack(spacing: 0) {
                ForEach(snapshot.styles) { style in
                    Button {
                        styleExpanded = false
                        actions.selectStyle(style.id)
                    } label: {
                        HStack {
                            Text(style.title)
                                .font(MdType.brandedBody)
                                .foregroundStyle(.white)
                            Spacer()
                            if style.id == snapshot.styleID {
                                Image(systemName: "checkmark")
                                    .foregroundStyle(SetsChrome.flame)
                            }
                        }
                        .padding(.horizontal, 12)
                        .frame(minHeight: 44)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("sets.style.\(style.id)")
                    Divider().overlay(SetsChrome.border)
                }
            }
        }
        .frame(height: CGFloat(min(max(snapshot.styles.count, 1), 4)) * 45)
        .background(SetsChrome.panel, in: RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(SetsChrome.border))
    }

    private var sectionList: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("СОДЕРЖИМОЕ СЕТА")
                .font(.caption2.weight(.semibold))
                .tracking(0.7)
                .foregroundStyle(MdColor.textSecondary)
            ForEach(SetsSection.allCases, id: \.self) { section in
                Button {
                    nameFocused = false
                    styleExpanded = false
                    actions.openSection(section)
                } label: {
                    HStack(spacing: 12) {
                        Image(systemName: section.icon)
                            .font(.system(size: 19, weight: .medium))
                            .frame(width: 36)
                            .foregroundStyle(SetsChrome.flame)
                            .accessibilityHidden(true)
                        VStack(alignment: .leading, spacing: 4) {
                            Text(section.title)
                                .font(MdType.brandedControl)
                                .foregroundStyle(.white)
                            Text(sectionStatus(section))
                                .font(.caption)
                                .foregroundStyle(MdColor.textSecondary)
                        }
                        Spacer()
                        Image(systemName: readOnly ? "chevron.right" : "plus")
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundStyle(SetsChrome.flame)
                            .accessibilityHidden(true)
                    }
                    .padding(.horizontal, 12)
                    .frame(minHeight: 58)
                    .background(SetsChrome.panel, in: RoundedRectangle(cornerRadius: 14))
                    .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(SetsChrome.border))
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .disabled(busy || snapshot.loading || !snapshot.canOpenSections)
                .accessibilityIdentifier("sets.section.\(section.rawValue)")
            }
        }
    }

    private func sectionStatus(_ section: SetsSection) -> String {
        if snapshot.loading { return "Загрузка…" }
        if !snapshot.canOpenSections {
            if readOnly { return "Состав недоступен" }
            if snapshot.styleID.isEmpty { return "Сначала выберите стиль" }
            return "Список недоступен"
        }
        let count = snapshot.itemCount(in: section)
        return readOnly ? "В сете: \(count)" : "Выбрано: \(count)"
    }

    private var storage: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("Хранилище сета")
                    .foregroundStyle(.white)
                Spacer(minLength: 8)
                Text(snapshot.weight)
                    .foregroundStyle(snapshot.overweight ? MdColor.danger : SetsChrome.flame)
            }
            .font(.caption.weight(.semibold))
            if let fraction = snapshot.weightFraction, fraction.isFinite {
                ProgressView(value: min(max(fraction, 0), 1))
                    .tint(snapshot.overweight ? MdColor.danger : SetsChrome.flame)
            }
            if let limits = snapshot.limits, !limits.isEmpty {
                Text(limits)
                    .font(.caption2)
                    .foregroundStyle(MdColor.textSecondary)
            }
        }
        .padding(12)
        .background(SetsChrome.panel, in: RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(SetsChrome.border))
        .accessibilityIdentifier("sets.storage")
    }

    @ViewBuilder private var status: some View {
        if let error = snapshot.error {
            Text(error)
                .font(MdType.brandedBody)
                .foregroundStyle(MdColor.danger)
                .accessibilityIdentifier("sets.editor.error")
            if readOnly, let retry = actions.retryArchive {
                Button("Повторить", action: retry)
                    .font(MdType.brandedControl)
                    .tint(SetsChrome.flame)
                    .disabled(snapshot.loading || busy)
            }
        }
        if snapshot.invalidSelection && !readOnly {
            Text("В сете есть недоступные материалы или материалы другого стиля.")
                .font(MdType.brandedBody)
                .foregroundStyle(MdColor.danger)
            if let remove = actions.removeInvalidSelection {
                Button("Убрать недоступные материалы", action: remove)
                    .font(MdType.brandedControl)
                    .tint(SetsChrome.flame)
                    .disabled(snapshot.loading || busy)
            }
        }
    }

    private var saveTitle: String {
        if busy { return "Подождите…" }
        if snapshot.loading { return "Загрузка материалов…" }
        return snapshot.mode == .create ? "Создать сет" : "Сохранить изменения"
    }

    private func fieldLabel(_ title: String) -> some View {
        Text(title)
            .font(.caption2.weight(.semibold))
            .tracking(0.7)
            .foregroundStyle(SetsChrome.flame)
    }
}

private struct SetsStyleFieldChrome: ViewModifier {
    func body(content: Content) -> some View {
        content
            .font(MdType.brandedBody)
            .foregroundStyle(.white)
            .padding(.horizontal, 12)
            .frame(maxWidth: .infinity, minHeight: 46, alignment: .leading)
            .background(MdColor.oledTrueBlack, in: RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(SetsChrome.flame.opacity(0.45)))
    }
}

private struct SetsSectionPickerView: View {
    let picker: SetsPickerSnapshot
    let style: String
    let weight: String
    let readOnly: Bool
    let actions: SetsHubActions

    @State private var query = ""
    @State private var filter: String?
    @State private var descending = false

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(picker.kind.title).font(MdType.titleLarge).foregroundStyle(.white)
                    if !style.isEmpty {
                        Text(style).font(MdType.brandedBody).foregroundStyle(SetsChrome.flame)
                    }
                    if !readOnly, !weight.isEmpty {
                        Text(weight).font(.caption).foregroundStyle(MdColor.textSecondary)
                    }
                }
                Spacer()
                Button(readOnly ? "Назад" : "Отмена", action: actions.closeSection)
                    .font(MdType.brandedControl)
                    .foregroundStyle(MdColor.textSecondary)
                    .frame(minHeight: 44)
                    .accessibilityIdentifier("sets.picker.close")
            }
            .padding(.top, 8)
            pickerControls
            ScrollView {
                LazyVStack(spacing: 8) {
                    if visibleRows.isEmpty {
                        Text(picker.rows.isEmpty ? "В этом разделе пока нет материалов." : "Материалы не найдены.")
                            .font(MdType.brandedBody)
                            .foregroundStyle(MdColor.textSecondary)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.vertical, 20)
                    }
                    ForEach(visibleRows) { row in
                        if readOnly {
                            rowLabel(row, selected: false)
                        } else {
                            let selected = picker.selectedIDs.contains(row.id)
                            Button { actions.toggleItem(row.id) } label: { rowLabel(row, selected: selected) }
                                .buttonStyle(.plain)
                                .accessibilityAddTraits(selected ? .isSelected : [])
                                .accessibilityIdentifier("sets.picker.item.\(row.id)")
                        }
                    }
                }
            }
            .scrollDismissesKeyboard(.interactively)
            if !readOnly {
                SetsChrome.primaryButton("Выбрано \(picker.selectedIDs.count)", enabled: true, action: actions.confirmSection)
                    .accessibilityIdentifier("sets.picker.confirm")
                    .padding(.bottom, 10)
            }
        }
        .padding(.horizontal, MdChrome.contentHorizontalPadding)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("sets.picker.\(picker.kind.rawValue)")
    }

    private var pickerControls: some View {
        HStack(spacing: 8) {
            if !subtitles.isEmpty {
                Menu {
                    Button("Все") { filter = nil }
                    ForEach(subtitles, id: \.self) { subtitle in
                        Button(subtitle) { filter = subtitle }
                    }
                } label: {
                    controlIcon("tag.fill")
                }
                .accessibilityLabel("Фильтр")
                .accessibilityValue(filter ?? "Все")
            }
            Button {
                descending.toggle()
            } label: {
                controlIcon(descending ? "arrow.down" : "arrow.up")
            }
            .buttonStyle(.plain)
            .accessibilityLabel(descending ? "Сортировка по убыванию" : "Сортировка по возрастанию")
            SetsSearchField(prompt: "Поиск…", text: $query)
                .accessibilityIdentifier("sets.picker.search")
        }
    }

    private var subtitles: [String] {
        Array(Set(picker.rows.map(\.subtitle).filter { !$0.isEmpty })).sorted()
    }

    private var visibleRows: [SetsChoiceSnapshot] {
        let search = query.trimmingCharacters(in: .whitespacesAndNewlines)
        return picker.rows.filter { row in
            if let filter, row.subtitle != filter { return false }
            return search.isEmpty || row.title.localizedCaseInsensitiveContains(search)
                || row.subtitle.localizedCaseInsensitiveContains(search)
        }.sorted { a, b in
            let order = a.title.localizedCaseInsensitiveCompare(b.title)
            if order == .orderedSame { return a.id < b.id }
            return descending ? order == .orderedDescending : order == .orderedAscending
        }
    }

    private func rowLabel(_ row: SetsChoiceSnapshot, selected: Bool) -> some View {
        HStack(spacing: 10) {
            VStack(alignment: .leading, spacing: 3) {
                Text(row.title).font(MdType.brandedControl).foregroundStyle(.white)
                if !row.subtitle.isEmpty {
                    Text(row.subtitle).font(.caption).foregroundStyle(MdColor.textSecondary)
                }
            }
            Spacer(minLength: 8)
            if !readOnly {
                Image(systemName: selected ? "checkmark.circle.fill" : "circle")
                    .foregroundStyle(selected ? SetsChrome.flame : MdColor.textSecondary)
                    .accessibilityHidden(true)
            }
        }
        .padding(12)
        .frame(minHeight: 48)
        .background(SetsChrome.panel, in: RoundedRectangle(cornerRadius: 13))
        .overlay(
            RoundedRectangle(cornerRadius: 13)
                .strokeBorder(selected ? SetsChrome.flame : SetsChrome.border, lineWidth: selected ? 2 : 1)
        )
        .contentShape(Rectangle())
    }

    private func controlIcon(_ icon: String) -> some View {
        Image(systemName: icon)
            .font(.system(size: 16, weight: .semibold))
            .foregroundStyle(SetsChrome.flame)
            .frame(width: 44, height: 44)
            .background(SetsChrome.panel, in: RoundedRectangle(cornerRadius: 13))
            .overlay(RoundedRectangle(cornerRadius: 13).strokeBorder(SetsChrome.border))
    }
}
