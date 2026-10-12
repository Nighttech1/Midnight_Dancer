import SwiftUI

/// SwiftUI mapping of C-20 shared design tokens (`ColorTokens`, `ShellChromeTokens`,
/// `SpringTokens`, `TypographyTokens`). Values mirrored from KMP commonMain — no Compose types.
enum MdColor {
    /// `ColorTokens.OledTrueBlack`
    static let oledTrueBlack = Color(red: 0, green: 0, blue: 0)
    /// `ColorTokens.SurfaceElevated`
    static let surfaceElevated = Color(red: 10 / 255, green: 10 / 255, blue: 10 / 255)
    /// `ColorTokens.SurfaceBorder`
    static let surfaceBorder = Color(red: 26 / 255, green: 26 / 255, blue: 26 / 255)
    /// `ColorTokens.NeonOrange`
    static let neonOrange = Color(red: 249 / 255, green: 115 / 255, blue: 22 / 255)
    /// `ColorTokens.NeonOrangeHot`
    static let neonOrangeHot = Color(red: 1, green: 107 / 255, blue: 53 / 255)
    /// `ColorTokens.NeonCyan`
    static let neonCyan = Color(red: 0, green: 229 / 255, blue: 1)
    /// `ColorTokens.NeonMagenta`
    static let neonMagenta = Color(red: 1, green: 45 / 255, blue: 149 / 255)
    /// `ColorTokens.NeonLime`
    static let neonLime = Color(red: 184 / 255, green: 1, blue: 60 / 255)
    /// `ColorTokens.TextPrimary`
    static let textPrimary = Color.white
    /// `ColorTokens.TextSecondary`
    static let textSecondary = Color(red: 176 / 255, green: 176 / 255, blue: 176 / 255)
    /// `ColorTokens.Danger`
    static let danger = Color(red: 1, green: 107 / 255, blue: 107 / 255)
    /// `ColorTokens.MidnightNavy`
    static let midnightNavy = Color(red: 11 / 255, green: 18 / 255, blue: 32 / 255)
    /// `ColorTokens.AccentGold`
    static let accentGold = Color(red: 201 / 255, green: 162 / 255, blue: 39 / 255)
}

enum MdChrome {
    /// `ShellChromeTokens.TopAppBarHeightDp`
    static let topAppBarHeight: CGFloat = 56
    /// `ShellChromeTokens.BottomTabBarHeightDp`
    static let bottomTabBarHeight: CGFloat = 64
    /// `ShellChromeTokens.ContentHorizontalPaddingDp`
    static let contentHorizontalPadding: CGFloat = 16
    /// `ShellChromeTokens.ContentVerticalPaddingDp`
    static let contentVerticalPadding: CGFloat = 12
    /// `ShellChromeTokens.TabIconSizeDp`
    static let tabIconSize: CGFloat = 24
    /// `ShellChromeTokens.MinTouchTargetDp`
    static let minTouchTarget: CGFloat = 48
    /// Card corner radius — premium shells (C-29).
    static let cardCornerRadius: CGFloat = 14
    /// Neon hairline thickness.
    static let neonHairline: CGFloat = 1
}

enum MdSpring {
    /// C-20 `SpringTokens.NavigationCritical` → `.spring(response: 0.35, dampingFraction: 1.0)`.
    /// Used by C-24 tab/content + Settings push — critical damping, no overshoot.
    static let navigationCritical = Animation.spring(response: 0.35, dampingFraction: 1.0)
    /// C-20 `SpringTokens.InteractiveUnderdamped` — buttons/cards (C-30 velocity + haptic settle).
    static let interactiveUnderdamped = Animation.spring(response: 0.28, dampingFraction: 0.72)
    /// Soft appear for staggered list rows.
    static let listAppear = Animation.spring(response: 0.42, dampingFraction: 0.86)
}

enum MdType {
    /// `TypographyTokens.TitleLarge` (~22sp)
    static let titleLarge = Font.system(size: 22, weight: .semibold)
    /// `TypographyTokens.BodyLarge` (~16sp)
    static let bodyLarge = Font.system(size: 16, weight: .regular)
    /// `TypographyTokens.BodyMedium` (~14sp)
    static let bodyMedium = Font.system(size: 14, weight: .regular)
    /// `TypographyTokens.TabLabel` (~11sp)
    static let tabLabel = Font.system(size: 11, weight: .medium)
    /// Premium section eyebrow.
    static let eyebrow = Font.system(size: 12, weight: .semibold)
    /// Speak zone headline.
    static let speakHeadline = Font.system(size: 18, weight: .semibold)
}
