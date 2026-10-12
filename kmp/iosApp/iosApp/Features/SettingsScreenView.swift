import SwiftUI

/// Full Settings — language, styles (delete warn), tags, low-disk.
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

    @State private var stylePendingDelete: String?
    @State private var languageExpanded = false

    private let languages = [("ru", "Русский"), ("en", "English"), ("es", "Español")]

    var body: some View {
        ZStack {
            MdNeonAtmosphere(accent: MdColor.neonOrange)
            VStack(spacing: 0) {
                topBar
                ScrollView {
                    VStack(alignment: .leading, spacing: 18) {
                        languageBlock
                        reminderBlock
                        stylesBlock
                        tagsBlock
                        lowDiskBlock
                        Text(snapshot.prefsLoaded ? "Настройки загружены" : "Загрузка…")
                            .font(MdType.eyebrow)
                            .foregroundStyle(MdColor.textSecondary.opacity(0.8))
                    }
                    .padding(.horizontal, MdChrome.contentHorizontalPadding)
                    .padding(.vertical, MdChrome.contentVerticalPadding)
                    .padding(.bottom, 40)
                }
            }
        }
        .alert(
            "Удалить стиль?",
            isPresented: Binding(
                get: { stylePendingDelete != nil },
                set: { if !$0 { stylePendingDelete = nil } }
            )
        ) {
            Button("Удалить", role: .destructive) {
                if let name = stylePendingDelete {
                    onRemoveStyle(name)
                }
                stylePendingDelete = nil
            }
            Button("Отмена", role: .cancel) { stylePendingDelete = nil }
        } message: {
            Text(
                "Будут удалены элементы, музыка и хореографии этого стиля. "
                    + "Действие нельзя отменить."
            )
        }
    }

    private var topBar: some View {
        HStack {
            Button(action: onBack) {
                HStack(spacing: 6) {
                    Image(systemName: "chevron.left")
                    Text("Назад")
                }
                .font(MdType.bodyLarge.weight(.semibold))
                .foregroundStyle(MdColor.neonOrange)
                .frame(minHeight: MdChrome.minTouchTarget)
            }
            .buttonStyle(.plain)
            Spacer()
            Text(snapshot.title.isEmpty ? "Настройки" : snapshot.title)
                .font(MdType.titleLarge)
                .foregroundStyle(MdColor.textPrimary)
            Spacer()
            Color.clear.frame(width: 72, height: MdChrome.minTouchTarget)
        }
        .padding(.horizontal, MdChrome.contentHorizontalPadding)
        .frame(height: MdChrome.topAppBarHeight)
        .background(MdColor.oledTrueBlack.opacity(0.92))
    }

    private var languageBlock: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Язык")
                .font(.system(size: 11, weight: .semibold))
                .foregroundStyle(MdColor.textSecondary)
            Button {
                withAnimation(MdSpring.interactiveUnderdamped) {
                    languageExpanded.toggle()
                }
            } label: {
                HStack {
                    Text(languages.first(where: { $0.0 == snapshot.languageCode })?.1
                          ?? snapshot.languageCode.uppercased())
                        .foregroundStyle(MdColor.textPrimary)
                    Spacer()
                    Image(systemName: "arrowtriangle.down.fill")
                        .font(.system(size: 10))
                        .foregroundStyle(MdColor.neonOrange)
                }
                .padding(14)
                .background(RoundedRectangle(cornerRadius: 12).fill(MdColor.surfaceElevated))
            }
            .buttonStyle(.plain)
            if languageExpanded {
                ForEach(languages, id: \.0) { code, name in
                    Button {
                        onSetLanguage(code)
                        languageExpanded = false
                    } label: {
                        Text(name)
                            .foregroundStyle(code == snapshot.languageCode
                                             ? MdColor.neonOrange : MdColor.textPrimary)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(12)
                    }
                    .buttonStyle(.plain)
                }
                .background(RoundedRectangle(cornerRadius: 12).fill(MdColor.surfaceElevated))
            }
        }
    }

    private var reminderBlock: some View {
        Toggle(isOn: Binding(
            get: { snapshot.reminderEnabled },
            set: onSetReminder
        )) {
            Text("Напоминание о тренировке")
                .foregroundStyle(MdColor.textPrimary)
        }
        .tint(MdColor.neonOrange)
        .padding(14)
        .background(RoundedRectangle(cornerRadius: 12).fill(MdColor.surfaceElevated))
    }

    private var stylesBlock: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Стили танца")
                .font(MdType.bodyLarge.weight(.semibold))
                .foregroundStyle(MdColor.textPrimary)
            ForEach(snapshot.danceStyles, id: \.self) { style in
                HStack {
                    Text(style).foregroundStyle(MdColor.textPrimary)
                    Spacer()
                    Button {
                        stylePendingDelete = style
                    } label: {
                        Image(systemName: "trash")
                            .foregroundStyle(MdColor.danger)
                            .frame(width: 40, height: 40)
                    }
                    .buttonStyle(.plain)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(RoundedRectangle(cornerRadius: 10).fill(MdColor.surfaceElevated))
            }
            HStack {
                TextField("Новый стиль", text: Binding(
                    get: { snapshot.newStyleDraft },
                    set: onSetStyleDraft
                ))
                .textFieldStyle(.roundedBorder)
                Button("Добавить", action: onAddStyle)
                    .foregroundStyle(MdColor.neonOrange)
            }
        }
    }

    private var tagsBlock: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Теги элементов")
                .font(MdType.bodyLarge.weight(.semibold))
                .foregroundStyle(MdColor.textPrimary)
            ForEach(snapshot.elementCategories, id: \.self) { tag in
                HStack {
                    Text(tag).foregroundStyle(MdColor.textPrimary)
                    Spacer()
                    Button {
                        onRemoveCategory(tag)
                    } label: {
                        Image(systemName: "trash")
                            .foregroundStyle(MdColor.danger)
                            .frame(width: 40, height: 40)
                    }
                    .buttonStyle(.plain)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(RoundedRectangle(cornerRadius: 10).fill(MdColor.surfaceElevated))
            }
            HStack {
                TextField("Новый тег", text: Binding(
                    get: { snapshot.newCategoryDraft },
                    set: onSetCategoryDraft
                ))
                .textFieldStyle(.roundedBorder)
                Button("Добавить", action: onAddCategory)
                    .foregroundStyle(MdColor.neonOrange)
            }
        }
    }

    private var lowDiskBlock: some View {
        VStack(alignment: .leading, spacing: 10) {
            Toggle(isOn: Binding(
                get: { snapshot.lowDiskWarnEnabled },
                set: onSetLowDiskEnabled
            )) {
                Text("Предупреждение о месте")
                    .foregroundStyle(MdColor.textPrimary)
            }
            .tint(MdColor.neonOrange)
            Text("Порог: \(snapshot.lowDiskWarnGib) ГБ")
                .font(MdType.bodyMedium)
                .foregroundStyle(MdColor.textSecondary)
            HStack {
                ForEach(snapshot.lowDiskPresetsGib, id: \.self) { gib in
                    Button {
                        onSetLowDiskGib(gib)
                    } label: {
                        Text("\(gib)")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundStyle(
                                gib == snapshot.lowDiskWarnGib
                                ? MdColor.oledTrueBlack : MdColor.textPrimary
                            )
                            .frame(width: 44, height: 36)
                            .background(
                                RoundedRectangle(cornerRadius: 8)
                                    .fill(gib == snapshot.lowDiskWarnGib
                                          ? MdColor.neonOrange : MdColor.surfaceElevated)
                            )
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .padding(14)
        .background(RoundedRectangle(cornerRadius: 12).fill(MdColor.surfaceElevated.opacity(0.6)))
    }
}
