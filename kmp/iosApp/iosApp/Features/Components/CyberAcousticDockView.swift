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
                let kind = ShellTabKind(rawValue: tab.id)
                Button {
                    onSelect(tab.id)
                    MdHaptics.perform(.selection)
                } label: {
                    Image(systemName: tab.systemImage)
                        .font(.system(size: MdChrome.tabIconSize, weight: selected ? .semibold : .regular))
                        .foregroundStyle(selected ? MdColor.neonOrange : Color.white.opacity(0.45))
                        .frame(maxWidth: .infinity)
                        .frame(height: 56)
                        .background {
                            if selected {
                                RoundedRectangle(cornerRadius: 16)
                                    .fill(MdColor.neonOrange.opacity(0.16))
                            }
                        }
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(kind?.dockLabel ?? tab.title)
                .accessibilityIdentifier("tab.\(kind?.automationName ?? String(tab.id))")
                .accessibilityAddTraits(selected ? [.isSelected] : [])
            }
        }
        .padding(.horizontal, 6)
        .padding(.vertical, 4)
        .background {
            RoundedRectangle(cornerRadius: 26)
                .fill(Color(red: 12.0 / 255, green: 13.0 / 255, blue: 18.0 / 255).opacity(0.90))
                .overlay {
                    RoundedRectangle(cornerRadius: 26)
                        .strokeBorder(Color.white.opacity(0.10), lineWidth: 1)
                }
                .shadow(color: .black, radius: 16)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 6)
    }
}
