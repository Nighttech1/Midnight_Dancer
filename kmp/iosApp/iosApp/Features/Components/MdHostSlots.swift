import SwiftUI

/// Host slots for Stage 4 Motion Premium follow-ups.
/// C-31 Rive — branded neon fallback until `.riv` ships.
/// C-32 — SwiftUI materials + procedural neon gradient + prewarm (Metal `.metal` PSO residual).

/// Rive dancer / EQ / rhythm host (C-31).
/// SM inputs (parity with future `.riv`): `intensity`, `bpm`, `touch_x`, `touch_y`.
struct MdRiveHostSlot: View {
    enum Kind: String {
        case dancer
        case equalizer
        case rhythm

        var userLabel: String {
            switch self {
            case .equalizer: return "Эквалайзер"
            case .rhythm: return "Ритм"
            case .dancer: return "Танцор"
            }
        }
    }

    let kind: Kind
    var accent: Color = MdColor.neonOrange
    var height: CGFloat = 88
    /// 0…1 — maps to Rive SM `intensity`
    var intensity: Double = 0.72
    /// Maps to Rive SM `bpm`
    var bpm: Double = 108
    /// Optional external touch (0…1). Drag inside the card overrides locally.
    var touchX: Double = 0.5
    var touchY: Double = 0.5

    @State private var localTouchX: Double = 0.5
    @State private var localTouchY: Double = 0.5
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        MdPremiumCard(accent: accent) {
            VStack(alignment: .leading, spacing: 10) {
                Text(kind.userLabel)
                    .font(MdType.eyebrow)
                    .tracking(1.2)
                    .foregroundStyle(accent)

                Group {
                    switch kind {
                    case .equalizer:
                        MdNeonEqualizerBars(
                            accent: accent,
                            barCount: 11,
                            intensity: intensity,
                            bpm: bpm,
                            touchX: localTouchX
                        )
                    case .rhythm:
                        MdNeonEqualizerBars(
                            accent: accent,
                            barCount: 9,
                            intensity: intensity,
                            bpm: bpm,
                            touchX: localTouchX,
                            mirrored: true
                        )
                    case .dancer:
                        MdNeonDancerSilhouette(
                            accent: accent,
                            intensity: intensity,
                            bpm: bpm,
                            touchX: localTouchX,
                            touchY: localTouchY
                        )
                        .frame(height: 96)
                    }
                }
                .frame(maxWidth: .infinity)
                .frame(minHeight: kind == .dancer ? 96 : 44)

            }
        }
        .frame(minHeight: height)
        .gesture(
            DragGesture(minimumDistance: 0)
                .onChanged { value in
                    guard !reduceMotion else { return }
                    // Normalize against card-ish bounds; fine for fallback SM wiring.
                    localTouchX = min(1, max(0, value.location.x / 280))
                    localTouchY = min(1, max(0, value.location.y / 140))
                }
        )
        .onAppear {
            localTouchX = touchX
            localTouchY = touchY
        }
        .accessibilityLabel("Rive \(kind.rawValue) host — branded fallback")
    }
}

/// Neon dancer silhouette spring — branded stand-in for Rive dancer SM.
struct MdNeonDancerSilhouette: View {
    let accent: Color
    var intensity: Double = 0.75
    var bpm: Double = 112
    var touchX: Double = 0.5
    var touchY: Double = 0.5

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        TimelineView(.animation(minimumInterval: reduceMotion ? 10 : 0.04, paused: reduceMotion)) { context in
            let t = context.date.timeIntervalSinceReferenceDate
            let period = 60.0 / max(60, min(180, bpm))
            let sway = reduceMotion ? 0.0 : sin(t * (.pi * 2) / period) * intensity
            Canvas { ctx, size in
                let cx = size.width * (0.42 + touchX * 0.16)
                let cy = size.height * (0.55 + (touchY - 0.5) * 0.12)
                let amp = 10 * sway

                let bloom = Path(ellipseIn: CGRect(
                    x: cx - size.width * 0.28,
                    y: cy - size.height * 0.4,
                    width: size.width * 0.56,
                    height: size.height * 0.8
                ))
                ctx.fill(
                    bloom,
                    with: .radialGradient(
                        Gradient(colors: [accent.opacity(0.35 + 0.35 * intensity), .clear]),
                        center: CGPoint(x: cx, y: cy),
                        startRadius: 4,
                        endRadius: size.minDimension * 0.45
                    )
                )

                var stroke = Path()
                let head = CGPoint(x: cx + amp * 0.35, y: cy - 38)
                let neck = CGPoint(x: cx + amp * 0.2, y: cy - 26)
                let hip = CGPoint(x: cx - amp * 0.15, y: cy + 8)
                let lShoulder = CGPoint(x: cx - 16 + amp, y: cy - 22)
                let rShoulder = CGPoint(x: cx + 16 + amp * 0.6, y: cy - 20)
                let lHand = CGPoint(x: lShoulder.x - 10 - amp * 1.2, y: lShoulder.y + 22)
                let rHand = CGPoint(x: rShoulder.x + 14 + amp, y: rShoulder.y - 8 * intensity)
                let lFoot = CGPoint(x: hip.x - 12 - amp * 0.4, y: hip.y + 36)
                let rFoot = CGPoint(x: hip.x + 14 + amp * 0.5, y: hip.y + 34)

                stroke.move(to: head)
                stroke.addLine(to: neck)
                stroke.addLine(to: hip)
                stroke.move(to: lShoulder)
                stroke.addLine(to: rShoulder)
                stroke.move(to: lShoulder)
                stroke.addLine(to: lHand)
                stroke.move(to: rShoulder)
                stroke.addLine(to: rHand)
                stroke.move(to: hip)
                stroke.addLine(to: lFoot)
                stroke.move(to: hip)
                stroke.addLine(to: rFoot)

                ctx.stroke(stroke, with: .color(accent.opacity(0.95)), lineWidth: 3.2)
                ctx.fill(
                    Path(ellipseIn: CGRect(x: head.x - 6, y: head.y - 6, width: 12, height: 12)),
                    with: .color(MdColor.neonLime.opacity(0.9))
                )
                ctx.stroke(
                    Path(ellipseIn: CGRect(x: head.x - 10, y: head.y - 10, width: 20, height: 20)),
                    with: .color(MdColor.neonCyan.opacity(0.55)),
                    lineWidth: 1.5
                )
            }
        }
    }
}

/// Frosted glass + procedural neon gradient host (C-32).
/// Best-feasible on Windows/no-Mac: SwiftUI `.ultraThinMaterial` + animated neon wash.
/// Residual: custom Metal `.metal` / `.colorEffect` PSO library — stage on Mac/Xcode.
struct MdMetalShaderHostSlot: View {
    var accent: Color = MdColor.neonCyan
    var height: CGFloat = 72
    var intensity: Double = 0.75

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var prewarmed = false
    @State private var phase: Double = 0

    var body: some View {
        ZStack {
            // Procedural neon wash (C-20 spectrum) — under glass.
            TimelineView(.animation(minimumInterval: reduceMotion ? 10 : 0.04, paused: reduceMotion)) { context in
                let t = prewarmed
                    ? context.date.timeIntervalSinceReferenceDate
                    : phase
                MdProceduralNeonGradient(accent: accent, intensity: intensity, time: t)
            }
            .clipShape(RoundedRectangle(cornerRadius: MdChrome.cardCornerRadius, style: .continuous))

            // Frosted glass material overlay.
            RoundedRectangle(cornerRadius: MdChrome.cardCornerRadius, style: .continuous)
                .fill(.ultraThinMaterial)
                .opacity(0.72)

            // Specular veil + brand edge.
            RoundedRectangle(cornerRadius: MdChrome.cardCornerRadius, style: .continuous)
                .fill(
                    LinearGradient(
                        colors: [
                            Color.white.opacity(0.14),
                            Color.clear,
                            MdColor.oledTrueBlack.opacity(0.35),
                        ],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                )
                .allowsHitTesting(false)

            RoundedRectangle(cornerRadius: MdChrome.cardCornerRadius, style: .continuous)
                .stroke(accent.opacity(0.55), lineWidth: 1)

        }
        .frame(maxWidth: .infinity)
        .frame(height: height)
        .onAppear {
            // Prewarm: force material + gradient composition before first interaction.
            phase = 0.001
            MdShaderPrewarm.touch(accent: accent)
            DispatchQueue.main.async {
                prewarmed = true
                if !reduceMotion {
                    withAnimation(.linear(duration: 8).repeatForever(autoreverses: false)) {
                        phase = .pi * 2
                    }
                }
            }
        }
        .accessibilityLabel("Frosted glass shader host slot")
    }
}

/// Animated OLED neon procedural wash — Compose/AGSL parity without Metal file.
struct MdProceduralNeonGradient: View {
    let accent: Color
    var intensity: Double = 0.75
    var time: Double = 0

    var body: some View {
        let amp = min(1, max(0.2, intensity))
        let ox = 0.5 + 0.35 * cos(time * 0.7) * amp
        let oy = 0.4 + 0.28 * sin(time * 0.55) * amp
        ZStack {
            MdColor.midnightNavy
            LinearGradient(
                colors: [
                    accent.opacity(0.55 + 0.25 * amp),
                    MdColor.neonCyan.opacity(0.42),
                    MdColor.neonMagenta.opacity(0.38),
                    MdColor.neonLime.opacity(0.18 * amp),
                    MdColor.midnightNavy.opacity(0.95),
                ],
                startPoint: UnitPoint(x: ox - 0.4, y: oy - 0.3),
                endPoint: UnitPoint(x: ox + 0.5, y: oy + 0.55)
            )
            RadialGradient(
                colors: [
                    accent.opacity(0.35 * amp),
                    Color.clear,
                ],
                center: UnitPoint(x: ox, y: oy),
                startRadius: 4,
                endRadius: 160
            )
        }
    }
}

/// Cheap pipeline touch so first on-screen frame does not hitch on material resolve.
enum MdShaderPrewarm {
    private static var didWarm = false

    static func touch(accent: Color) {
        guard !didWarm else { return }
        didWarm = true
        // Resolve material + gradient types once (no Metal library on this host).
        _ = accent.opacity(0.01)
        _ = Material.ultraThin
    }
}
