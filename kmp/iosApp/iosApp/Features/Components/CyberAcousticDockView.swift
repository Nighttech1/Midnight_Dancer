import SwiftUI

/// Glass / Flame bottom dock — icon-only, selected = Flame tint (no under-dot).
struct CyberAcousticDockView: View {
    let tabs: [TabShellSnapshot]
    let selectedIndex: Int
    var onSelect: (Int) -> Void

    var body: some View {
        HStack(spacing: 0) {
            ForEach(tabs) { tab in
                let selected = tab.id == selectedIndex
                Button {
                    onSelect(tab.id)
                    MdHaptics.perform(.selection)
                } label: {
                    Image(systemName: tab.systemImage)
                        .font(.system(size: 30, weight: selected ? .semibold : .regular))
                        .foregroundStyle(selected ? MdColor.neonOrange : MdColor.textSecondary)
                        .frame(maxWidth: .infinity)
                        .frame(height: 56)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(tab.title)
            }
        }
        .padding(.horizontal, 8)
        .padding(.top, 6)
        .padding(.bottom, 4)
        .background(
            ZStack {
                RoundedRectangle(cornerRadius: 22, style: .continuous)
                    .fill(.ultraThinMaterial)
                    .environment(\.colorScheme, .dark)
                RoundedRectangle(cornerRadius: 22, style: .continuous)
                    .fill(MdColor.oledTrueBlack.opacity(0.55))
                RoundedRectangle(cornerRadius: 22, style: .continuous)
                    .stroke(MdColor.neonOrange.opacity(0.22), lineWidth: 1)
            }
            .padding(.horizontal, 10)
            .shadow(color: MdColor.neonOrange.opacity(0.12), radius: 16, y: -2)
        )
    }
}
