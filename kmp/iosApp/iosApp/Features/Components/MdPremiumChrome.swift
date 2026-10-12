import SwiftUI

// MARK: - Tab accent (parity feel with C-28 neon spectrum — not pixel-match)

enum MdTabAccent {
    static func color(for tabId: Int) -> Color {
        switch ShellTabKind(rawValue: tabId) {
        case .elements: return MdColor.neonOrange
        case .music: return MdColor.neonCyan
        case .choreography: return MdColor.neonMagenta
        case .trainer: return MdColor.neonLime
        case .exchange: return MdColor.accentGold
        case .none: return MdColor.neonOrange
        }
    }

    static func subtitle(for tabId: Int) -> String {
        switch ShellTabKind(rawValue: tabId) {
        case .elements: return "Style catalog"
        case .music: return "Tracks · no player yet"
        case .choreography: return "Sequences"
        case .trainer: return "Session · voice zone ready"
        case .exchange: return "Import / export"
        case .none: return "Midnight Dancer"
        }
    }
}

// MARK: - Atmosphere (OLED + soft neon wash)

struct MdNeonAtmosphere: View {
    let accent: Color
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var pulse = false

    var body: some View {
        ZStack {
            MdColor.oledTrueBlack
            RadialGradient(
                colors: [
                    accent.opacity(pulse && !reduceMotion ? 0.18 : 0.10),
                    MdColor.midnightNavy.opacity(0.35),
                    Color.clear,
                ],
                center: .topTrailing,
                startRadius: 20,
                endRadius: 320
            )
            RadialGradient(
                colors: [
                    MdColor.neonCyan.opacity(0.06),
                    Color.clear,
                ],
                center: .bottomLeading,
                startRadius: 10,
                endRadius: 260
            )
        }
        .ignoresSafeArea()
        .onAppear {
            guard !reduceMotion else { return }
            withAnimation(.easeInOut(duration: 3.2).repeatForever(autoreverses: true)) {
                pulse = true
            }
        }
    }
}

// MARK: - Interactive spring press (C-30)

/// Underdamped scale press with gesture impulse + haptic at approx spring zero-velocity.
struct MdInteractiveSpringPress: ViewModifier {
    var enabled: Bool = true
    var pressedScale: CGFloat = 0.97
    var haptic: MdHapticEvent? = .lightImpact

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var scale: CGFloat = 1

    func body(content: Content) -> some View {
        content
            .scaleEffect(scale)
            .simultaneousGesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { _ in
                        guard enabled, !reduceMotion else { return }
                        withAnimation(MdSpring.interactiveUnderdamped) {
                            scale = pressedScale
                        }
                    }
                    .onEnded { value in
                        guard enabled else { return }
                        if reduceMotion {
                            scale = 1
                            if let haptic { MdHaptics.perform(haptic) }
                            return
                        }
                        // Impulse from predicted fling → slight overshoot feel on release.
                        let projected = hypot(
                            value.predictedEndTranslation.width - value.translation.width,
                            value.predictedEndTranslation.height - value.translation.height
                        )
                        let overshootBoost: CGFloat = projected > 24 ? 1.02 : 1.0
                        withAnimation(MdSpring.interactiveUnderdamped) {
                            scale = overshootBoost
                        }
                        withAnimation(MdSpring.interactiveUnderdamped.delay(0.02)) {
                            scale = 1
                        }
                        if let haptic {
                            MdHaptics.performAfterSpringSettle(haptic)
                        }
                    }
            )
    }
}

extension View {
    func mdInteractiveSpringPress(
        enabled: Bool = true,
        pressedScale: CGFloat = 0.97,
        haptic: MdHapticEvent? = .lightImpact
    ) -> some View {
        modifier(
            MdInteractiveSpringPress(
                enabled: enabled,
                pressedScale: pressedScale,
                haptic: haptic
            )
        )
    }
}

// MARK: - Pressable spring card

struct MdPremiumCard<Content: View>: View {
    let accent: Color
    var glow: Bool = false
    @ViewBuilder var content: () -> Content

    var body: some View {
        content()
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: MdChrome.cardCornerRadius, style: .continuous)
                    .fill(MdColor.surfaceElevated)
            )
            .overlay(
                RoundedRectangle(cornerRadius: MdChrome.cardCornerRadius, style: .continuous)
                    .stroke(
                        LinearGradient(
                            colors: [
                                accent.opacity(glow ? 0.85 : 0.45),
                                MdColor.surfaceBorder,
                                accent.opacity(0.25),
                            ],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        ),
                        lineWidth: MdChrome.neonHairline
                    )
            )
            .shadow(color: accent.opacity(glow ? 0.28 : 0.12), radius: glow ? 14 : 6, y: 2)
            .mdInteractiveSpringPress(pressedScale: 0.97, haptic: .lightImpact)
    }
}

// MARK: - Neon CTA button

struct MdNeonButton: View {
    let title: String
    let accent: Color
    var systemImage: String? = nil
    var isEnabled: Bool = true
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                if let systemImage {
                    Image(systemName: systemImage)
                        .font(.system(size: 15, weight: .semibold))
                }
                Text(title)
                    .font(MdType.bodyLarge.weight(.semibold))
            }
            .foregroundStyle(isEnabled ? MdColor.oledTrueBlack : MdColor.textSecondary)
            .frame(maxWidth: .infinity)
            .frame(minHeight: MdChrome.minTouchTarget)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(
                        LinearGradient(
                            colors: isEnabled
                                ? [accent, accent.opacity(0.75)]
                                : [MdColor.surfaceElevated, MdColor.surfaceBorder]
                            ,
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
            )
            .overlay(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .stroke(accent.opacity(isEnabled ? 0.9 : 0.3), lineWidth: 1)
            )
            .shadow(color: accent.opacity(isEnabled ? 0.35 : 0), radius: 10, y: 2)
        }
        .buttonStyle(.plain)
        .disabled(!isEnabled)
        .mdInteractiveSpringPress(
            enabled: isEnabled,
            pressedScale: 0.94,
            haptic: .mediumImpact
        )
        .accessibilityLabel(title)
    }
}

// MARK: - Animated list rows

struct MdAnimatedItemList: View {
    let items: [String]
    let accent: Color
    var emptyLabel: String = "Nothing here yet"

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var revealed = false

    var body: some View {
        if items.isEmpty {
            Text(emptyLabel)
                .font(MdType.bodyLarge)
                .foregroundStyle(MdColor.textSecondary)
                .frame(maxWidth: .infinity, minHeight: 80, alignment: .center)
        } else {
            LazyVStack(spacing: 10) {
                ForEach(Array(items.enumerated()), id: \.offset) { index, line in
                    MdPremiumCard(accent: accent, glow: index == 0) {
                        HStack(spacing: 12) {
                            Circle()
                                .fill(accent.opacity(0.9))
                                .frame(width: 8, height: 8)
                                .shadow(color: accent.opacity(0.6), radius: 4)
                            Text(line)
                                .font(MdType.bodyLarge)
                                .foregroundStyle(MdColor.textPrimary)
                                .multilineTextAlignment(.leading)
                            Spacer(minLength: 0)
                            Image(systemName: "chevron.right")
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundStyle(accent.opacity(0.7))
                        }
                    }
                    .opacity(rowOpacity(index))
                    .offset(y: rowOffset(index))
                }
            }
            .onAppear {
                guard !reduceMotion else {
                    revealed = true
                    return
                }
                withAnimation(MdSpring.listAppear) {
                    revealed = true
                }
            }
            .onChange(of: items.count) { _ in
                revealed = false
                if reduceMotion {
                    revealed = true
                } else {
                    withAnimation(MdSpring.listAppear) {
                        revealed = true
                    }
                }
            }
        }
    }

    private func rowOpacity(_ index: Int) -> Double {
        guard revealed || reduceMotion else { return 0 }
        return 1
    }

    private func rowOffset(_ index: Int) -> CGFloat {
        guard !reduceMotion else { return 0 }
        return revealed ? 0 : CGFloat(8 + index * 4)
    }
}

// MARK: - Neon equalizer bars (branded fallback until Rive C-31)

struct MdNeonEqualizerBars: View {
    let accent: Color
    var barCount: Int = 7
    /// 0…1 — Rive SM `intensity`
    var intensity: Double = 0.75
    /// Rive SM `bpm` — drives pulse period
    var bpm: Double = 108
    /// 0…1 — Rive SM `touch_x` focus bias
    var touchX: Double = 0.5
    var mirrored: Bool = false

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        let interval = reduceMotion ? 10.0 : max(0.04, 60.0 / max(60, min(180, bpm)) / 8)
        TimelineView(.animation(minimumInterval: interval, paused: reduceMotion)) { context in
            let t = context.date.timeIntervalSinceReferenceDate
            let period = 60.0 / max(60, min(180, bpm))
            HStack(alignment: .bottom, spacing: 5) {
                ForEach(0 ..< barCount, id: \.self) { i in
                    let focus = 1 - abs((Double(i) + 0.5) / Double(barCount) - touchX) * 1.4
                    let phase = t * (.pi * 2) / period + Double(i) * 0.55 + (mirrored ? Double(i) * 0.2 : 0)
                    let wave = abs(sin(phase))
                    let level: Double = {
                        if reduceMotion { return (0.35 + Double(i % 3) * 0.2) * intensity }
                        return (0.18 + wave * 0.82) * intensity * (0.55 + 0.45 * max(0.2, min(1, focus)))
                    }()
                    RoundedRectangle(cornerRadius: 3, style: .continuous)
                        .fill(
                            LinearGradient(
                                colors: [
                                    accent,
                                    MdColor.neonCyan.opacity(0.7),
                                    MdColor.neonMagenta.opacity(0.35),
                                ],
                                startPoint: .bottom,
                                endPoint: .top
                            )
                        )
                        .frame(width: 8, height: 12 + CGFloat(level) * 36)
                        .shadow(color: accent.opacity(0.45), radius: 5, y: 0)
                }
            }
            .frame(height: 52)
            .animation(MdSpring.interactiveUnderdamped, value: t)
        }
    }
}
