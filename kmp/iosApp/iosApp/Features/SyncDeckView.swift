import SwiftUI

/// Exchange / Sync deck — export & import with 500 MB limits + style ▼ dropdowns.
struct SyncDeckView: View {
    let snapshot: ExchangeFeatureSnapshot
    var onOpenExport: () -> Void
    var onDismissExport: () -> Void
    var onSelectExportStyle: (String) -> Void
    var onConfirmExport: () -> Void
    var onOpenImport: () -> Void
    var onDismissImport: () -> Void
    var onSetImportStyle: (String) -> Void
    var onConfirmImport: () -> Void

    @State private var exportStyleExpanded = false
    @State private var importStyleExpanded = false

    var body: some View {
        ZStack {
            MdNeonAtmosphere(accent: MdColor.neonLime)
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text(snapshot.title)
                        .font(MdType.titleLarge)
                        .foregroundStyle(MdColor.textPrimary)

                    actionCard(
                        title: "Поделиться пакетом",
                        subtitle: "Экспорт .mdance одного стиля",
                        accent: MdColor.neonOrange,
                        action: {
                            exportStyleExpanded = false
                            onOpenExport()
                        }
                    )
                    actionCard(
                        title: "Импорт пакета",
                        subtitle: snapshot.importEnabled
                            ? "Места хватает · \(snapshot.freeLabel)"
                            : "Нужно \(snapshot.requiredLabel)",
                        accent: MdColor.neonCyan,
                        action: {
                            importStyleExpanded = false
                            onOpenImport()
                        }
                    )

                    if let last = snapshot.lastActionLabel {
                        Text(last)
                            .font(MdType.bodyMedium)
                            .foregroundStyle(MdColor.textSecondary)
                    }
                    if snapshot.busy {
                        ProgressView().tint(MdColor.neonOrange)
                    }
                }
                .padding(.horizontal, MdChrome.contentHorizontalPadding)
                .padding(.top, 8)
                .padding(.bottom, 96)
            }
        }
        .sheet(isPresented: Binding(
            get: { snapshot.showExportDialog },
            set: { if !$0 { onDismissExport(); exportStyleExpanded = false } }
        )) {
            exportSheet
        }
        .sheet(isPresented: Binding(
            get: { snapshot.showImportParams },
            set: { if !$0 { onDismissImport(); importStyleExpanded = false } }
        )) {
            importSheet
        }
    }

    private func actionCard(
        title: String,
        subtitle: String,
        accent: Color,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 6) {
                Text(title)
                    .font(MdType.bodyLarge.weight(.bold))
                    .foregroundStyle(MdColor.textPrimary)
                Text(subtitle)
                    .font(MdType.bodyMedium)
                    .foregroundStyle(MdColor.textSecondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(18)
            .background(
                RoundedRectangle(cornerRadius: 16)
                    .fill(MdColor.surfaceElevated)
                    .overlay(
                        RoundedRectangle(cornerRadius: 16)
                            .stroke(accent.opacity(0.5), lineWidth: 1)
                    )
            )
        }
        .buttonStyle(.plain)
    }

    private var exportSheet: some View {
        NavigationStack {
            GeometryReader { geo in
                let fieldWidth = geo.size.width * 0.5
                ScrollView {
                    VStack(alignment: .leading, spacing: 14) {
                        Text("Поделиться пакетом")
                            .font(MdType.titleLarge)
                            .foregroundStyle(MdColor.textPrimary)

                        Text("Стиль")
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundStyle(MdColor.textSecondary)

                        styleDropdown(
                            width: fieldWidth,
                            label: snapshot.exportStyleName.isEmpty ? "Выберите стиль" : snapshot.exportStyleName,
                            expanded: $exportStyleExpanded,
                            options: snapshot.exportStyles,
                            selectedId: snapshot.exportStyleId,
                            onSelect: onSelectExportStyle
                        )

                        Text(snapshot.exportLimitsText)
                            .font(MdType.bodyMedium)
                            .foregroundStyle(MdColor.textSecondary)
                            .fixedSize(horizontal: false, vertical: true)

                        Text("Выбрано: \(snapshot.exportSelectedMoveCount) эл. · \(snapshot.exportSelectedSongCount) муз. · \(snapshot.exportSelectedChoreoCount) хорео")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(MdColor.neonOrange)

                        Button(action: onConfirmExport) {
                            Text("Экспорт")
                                .font(.system(size: 16, weight: .bold))
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 14)
                                .background(RoundedRectangle(cornerRadius: 14).fill(MdColor.neonOrange))
                                .foregroundStyle(MdColor.oledTrueBlack)
                        }
                        .buttonStyle(.plain)
                        .disabled(snapshot.exportStyleId.isEmpty || snapshot.busy)
                    }
                    .padding(20)
                }
            }
            .background(MdColor.oledTrueBlack.ignoresSafeArea())
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Закрыть") { onDismissExport() }
                        .foregroundStyle(MdColor.neonOrange)
                }
            }
        }
        .presentationDetents([.medium, .large])
    }

    private var importSheet: some View {
        NavigationStack {
            GeometryReader { geo in
                let fieldWidth = geo.size.width * 0.5
                ScrollView {
                    VStack(alignment: .leading, spacing: 14) {
                        Text("Параметры импорта")
                            .font(MdType.titleLarge)
                            .foregroundStyle(MdColor.textPrimary)

                        Text("В приложении")
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundStyle(MdColor.textSecondary)

                        let selectedName = snapshot.importLocalStyles
                            .first(where: { $0.id == snapshot.importTargetStyleId })?.name
                            ?? "Выберите стиль"

                        styleDropdown(
                            width: fieldWidth,
                            label: selectedName,
                            expanded: $importStyleExpanded,
                            options: snapshot.importLocalStyles,
                            selectedId: snapshot.importTargetStyleId,
                            onSelect: onSetImportStyle
                        )

                        Text("Пакет: \(snapshot.lastImportName)")
                            .font(MdType.bodyMedium)
                            .foregroundStyle(MdColor.textSecondary)

                        Button(action: onConfirmImport) {
                            Text("Импорт")
                                .font(.system(size: 16, weight: .bold))
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 14)
                                .background(RoundedRectangle(cornerRadius: 14).fill(MdColor.neonOrange))
                                .foregroundStyle(MdColor.oledTrueBlack)
                        }
                        .buttonStyle(.plain)
                        .disabled(snapshot.importTargetStyleId.isEmpty || snapshot.busy)
                    }
                    .padding(20)
                }
            }
            .background(MdColor.oledTrueBlack.ignoresSafeArea())
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Закрыть") { onDismissImport() }
                        .foregroundStyle(MdColor.neonOrange)
                }
            }
        }
        .presentationDetents([.medium, .large])
    }

    private func styleDropdown(
        width: CGFloat,
        label: String,
        expanded: Binding<Bool>,
        options: [StyleRefSnapshot],
        selectedId: String,
        onSelect: @escaping (String) -> Void
    ) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Button {
                withAnimation(MdSpring.interactiveUnderdamped) {
                    expanded.wrappedValue.toggle()
                }
            } label: {
                HStack {
                    Text(label)
                        .font(MdType.bodyMedium.weight(.semibold))
                        .foregroundStyle(MdColor.textPrimary)
                        .lineLimit(1)
                    Spacer(minLength: 4)
                    Image(systemName: "arrowtriangle.down.fill")
                        .font(.system(size: 10, weight: .bold))
                        .foregroundStyle(MdColor.neonOrange)
                }
                .padding(.horizontal, 12)
                .frame(width: width, height: 44, alignment: .leading)
                .background(
                    RoundedRectangle(cornerRadius: 12)
                        .fill(MdColor.surfaceElevated)
                        .overlay(
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(MdColor.neonOrange.opacity(0.55), lineWidth: 1)
                        )
                )
            }
            .buttonStyle(.plain)

            if expanded.wrappedValue {
                VStack(alignment: .leading, spacing: 0) {
                    ForEach(options) { opt in
                        Button {
                            onSelect(opt.id)
                            withAnimation { expanded.wrappedValue = false }
                        } label: {
                            Text(opt.name)
                                .font(MdType.bodyMedium)
                                .foregroundStyle(
                                    opt.id == selectedId ? MdColor.neonOrange : MdColor.textPrimary
                                )
                                .frame(width: width, alignment: .leading)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 12)
                        }
                        .buttonStyle(.plain)
                    }
                }
                .background(
                    RoundedRectangle(cornerRadius: 12)
                        .fill(MdColor.surfaceElevated)
                        .overlay(
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(MdColor.surfaceBorder, lineWidth: 1)
                        )
                )
                .frame(width: width)
            }
        }
    }
}
