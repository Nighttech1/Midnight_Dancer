import SwiftUI

/// Settings overlay — premium OLED cards. Prefs from SKIE `SettingsComponent.uiState`.
struct SettingsShellView: View {
    let snapshot: SettingsShellSnapshot
    let onBack: () -> Void

    var body: some View {
        ZStack {
            MdNeonAtmosphere(accent: MdColor.neonOrange)
            VStack(spacing: 0) {
                topBar
                ScrollView {
                    VStack(alignment: .leading, spacing: 14) {
                        Text("Studio preferences")
                            .font(MdType.bodyMedium)
                            .foregroundStyle(MdColor.textSecondary)

                        settingsCard(
                            icon: "globe",
                            label: "Language",
                            value: snapshot.languageCode.uppercased(),
                            accent: MdColor.neonCyan
                        )
                        settingsCard(
                            icon: "bell.badge",
                            label: "Reminder",
                            value: reminderValue,
                            accent: MdColor.neonMagenta
                        )
                        settingsCard(
                            icon: "externaldrive",
                            label: "Prefs",
                            value: snapshot.prefsLoaded ? "Loaded" : "Loading…",
                            accent: MdColor.neonLime
                        )

                        MdMetalShaderHostSlot(accent: MdColor.neonOrange, height: 68)

                        Text("Settings · Decompose overlay · C-29")
                            .font(MdType.eyebrow)
                            .foregroundStyle(MdColor.textSecondary.opacity(0.8))
                            .padding(.top, 4)
                    }
                    .padding(.horizontal, MdChrome.contentHorizontalPadding)
                    .padding(.vertical, MdChrome.contentVerticalPadding)
                }
                Spacer(minLength: 0)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private var topBar: some View {
        HStack {
            Button(action: onBack) {
                HStack(spacing: 6) {
                    Image(systemName: "chevron.left")
                    Text("Back")
                }
                .font(MdType.bodyLarge.weight(.semibold))
                .foregroundStyle(MdColor.neonOrange)
                .frame(minHeight: MdChrome.minTouchTarget)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            Spacer()
            Text(snapshot.title)
                .font(MdType.titleLarge)
                .foregroundStyle(MdColor.textPrimary)
            Spacer()
            Color.clear.frame(width: 64, height: MdChrome.minTouchTarget)
        }
        .padding(.horizontal, MdChrome.contentHorizontalPadding)
        .frame(height: MdChrome.topAppBarHeight)
        .background(MdColor.oledTrueBlack.opacity(0.92))
        .overlay(alignment: .bottom) {
            LinearGradient(
                colors: [MdColor.neonOrange.opacity(0.55), MdColor.surfaceBorder],
                startPoint: .leading,
                endPoint: .trailing
            )
            .frame(height: 1)
        }
    }

    private var reminderValue: String {
        if snapshot.reminderEnabled {
            return String(format: "%02d:%02d", snapshot.reminderHour, snapshot.reminderMinute)
        }
        return "Off"
    }

    private func settingsCard(
        icon: String,
        label: String,
        value: String,
        accent: Color
    ) -> some View {
        MdPremiumCard(accent: accent) {
            HStack(spacing: 14) {
                ZStack {
                    Circle()
                        .fill(accent.opacity(0.18))
                        .frame(width: 40, height: 40)
                    Image(systemName: icon)
                        .foregroundStyle(accent)
                }
                VStack(alignment: .leading, spacing: 2) {
                    Text(label)
                        .font(MdType.bodyMedium)
                        .foregroundStyle(MdColor.textSecondary)
                    Text(value)
                        .font(MdType.bodyLarge.weight(.semibold))
                        .foregroundStyle(MdColor.textPrimary)
                }
                Spacer(minLength: 0)
            }
        }
    }
}
