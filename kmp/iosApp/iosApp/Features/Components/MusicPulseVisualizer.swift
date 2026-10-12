import SwiftUI

// MARK: - Music Pulse Waveform (C-101)

/// Discrete vertical bars on a **single** `Canvas` — parity with Android Music Pulse Waveform.
/// Not N Views per bar; spring heights + shimmer gradient + soft glow under strokes.
struct MusicPulseVisualizer: View {
    var isPlaying: Bool
    var height: CGFloat = 72
    var barCount: Int = 50
    var bpm: Double = 128

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var dynamics = BarDynamics.empty

    /// Spec: `.spring(response: 0.25, dampingFraction: 0.65)` toward target heights.
    private static let springResponse: CGFloat = 0.25
    private static let springDamping: CGFloat = 0.65
    private static let shimmerPeriod: TimeInterval = 4.0

    private var clampedBarCount: Int {
        min(60, max(40, barCount))
    }

    var body: some View {
        let count = clampedBarCount
        let tickInterval = reduceMotion ? (1.0 / 12.0) : (1.0 / 60.0)
        TimelineView(.animation(minimumInterval: tickInterval, paused: false)) { context in
            let date = context.date
            Canvas { ctx, size in
                drawPulse(
                    context: ctx,
                    size: size,
                    count: count,
                    heights: dynamics.heights,
                    time: date.timeIntervalSinceReferenceDate
                )
            }
            .onAppear {
                ensureCapacity(count)
                tick(at: date, count: count)
            }
            .onChange(of: date) { newDate in
                ensureCapacity(count)
                tick(at: newDate, count: count)
            }
            .onChange(of: count) { newCount in
                ensureCapacity(newCount)
            }
        }
        .frame(height: height)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(isPlaying ? "Music pulse playing" : "Music pulse paused")
    }

    // MARK: - Dynamics

    private func ensureCapacity(_ count: Int) {
        if dynamics.heights.count != count {
            dynamics = BarDynamics.seeded(count: count)
        }
    }

    private func tick(at date: Date, count: Int) {
        var next = dynamics
        if next.heights.count != count {
            next = BarDynamics.seeded(count: count)
        }

        let t = date.timeIntervalSinceReferenceDate
        let dt = next.lastTick.map { max(1.0 / 120.0, min(1.0 / 20.0, CGFloat(t - $0))) } ?? (1.0 / 60.0)
        next.lastTick = t

        let targets = targetHeights(count: count, time: t, canvasHeight: height)
        for i in 0 ..< count {
            next.heights[i] = springStep(
                current: next.heights[i],
                velocity: &next.velocities[i],
                target: targets[i],
                dt: dt
            )
        }
        dynamics = next
    }

    /// Quadratic edge taper — peaks at center: `1 - (2*i/n - 1)²`.
    private func edgeFactor(index: Int, count: Int) -> CGFloat {
        let n = CGFloat(max(count, 1))
        let x = (2 * CGFloat(index) / n) - 1
        return max(0, 1 - x * x)
    }

    private func targetHeights(count: Int, time: TimeInterval, canvasHeight: CGFloat) -> [CGFloat] {
        let maxBar = max(8, canvasHeight * 0.92)
        let clampedBpm = min(180, max(60, bpm))
        let period = 60.0 / clampedBpm

        return (0 ..< count).map { i in
            let envelope = edgeFactor(index: i, count: count)
            if reduceMotion {
                let staticLevel: CGFloat = isPlaying ? (0.35 + CGFloat(i % 5) * 0.08) : 0.08
                return max(2, min(maxBar, maxBar * staticLevel * envelope))
            }

            if isPlaying {
                // BPM-ish staggered pulse across the bank.
                let phase = time * (.pi * 2) / period + Double(i) * 0.42
                let beat = abs(sin(phase))
                let secondary = abs(sin(phase * 0.5 + 0.7)) * 0.35
                let level = 0.12 + beat * 0.78 + secondary * 0.1
                return max(3, min(maxBar, maxBar * CGFloat(level) * envelope))
            } else {
                // Paused / idle: min 2–4pt + ambient jitter.
                let jitter = sin(time * 1.7 + Double(i) * 0.61) * 0.5
                    + sin(time * 0.9 + Double(i) * 1.13) * 0.35
                let base: CGFloat = 2.5 + CGFloat((i % 3)) * 0.4
                return max(2, min(4.5, base + CGFloat(jitter) * envelope))
            }
        }
    }

    /// Underdamped spring step matching `.spring(response: 0.25, dampingFraction: 0.65)`.
    private func springStep(
        current: CGFloat,
        velocity: inout CGFloat,
        target: CGFloat,
        dt: CGFloat
    ) -> CGFloat {
        let response = Self.springResponse
        let dampingFraction = Self.springDamping
        let omega = (2 * CGFloat.pi) / response
        let stiffness = omega * omega
        let damping = 2 * dampingFraction * omega
        let force = (target - current) * stiffness - velocity * damping
        velocity += force * dt
        return current + velocity * dt
    }

    // MARK: - Canvas draw

    private func drawPulse(
        context: GraphicsContext,
        size: CGSize,
        count: Int,
        heights: [CGFloat],
        time: TimeInterval
    ) {
        guard count > 0, size.width > 0, size.height > 0 else { return }

        let gap: CGFloat = 2.5
        let usable = max(0, size.width - gap * CGFloat(count - 1))
        let barWidth = min(4, max(2, usable / CGFloat(count)))
        let totalWidth = CGFloat(count) * barWidth + CGFloat(count - 1) * gap
        let originX = (size.width - totalWidth) / 2
        let baseline = size.height

        var bars = Path()
        for i in 0 ..< count {
            let h = i < heights.count ? max(2, heights[i]) : 2
            let x = originX + CGFloat(i) * (barWidth + gap) + barWidth / 2
            bars.move(to: CGPoint(x: x, y: baseline))
            bars.addLine(to: CGPoint(x: x, y: baseline - h))
        }

        // Soft glow — second wider stroke, low opacity (no heavy blur).
        let glowColor = Color(red: 1, green: 85 / 255, blue: 0).opacity(isPlaying ? 0.28 : 0.12)
        context.stroke(
            bars,
            with: .color(glowColor),
            style: StrokeStyle(lineWidth: barWidth * 2.4, lineCap: .round, lineJoin: .round)
        )

        // Shimmer: #050505 → #FF5500 → #FFD600 → #FFFFFF → mirror; phase ~4s.
        let phase: CGFloat = reduceMotion
            ? 0
            : CGFloat(time.truncatingRemainder(dividingBy: Self.shimmerPeriod) / Self.shimmerPeriod)
        let shimmer = shimmerGradient(phase: phase)

        context.stroke(
            bars,
            with: .linearGradient(
                shimmer,
                startPoint: CGPoint(x: size.width * (-0.2 + phase), y: size.height * 0.5),
                endPoint: CGPoint(x: size.width * (0.8 + phase), y: size.height * 0.5)
            ),
            style: StrokeStyle(lineWidth: barWidth, lineCap: .round, lineJoin: .round)
        )
    }

    private func shimmerGradient(phase: CGFloat) -> Gradient {
        // Shift stop locations gently with phase for a traveling highlight.
        let shift = phase * 0.35
        func loc(_ base: CGFloat) -> CGFloat {
            min(1, max(0, base + shift - 0.15))
        }
        return Gradient(stops: [
            .init(color: Color(red: 5 / 255, green: 5 / 255, blue: 5 / 255), location: loc(0)),
            .init(color: Color(red: 1, green: 85 / 255, blue: 0), location: loc(0.22)),
            .init(color: Color(red: 1, green: 214 / 255, blue: 0), location: loc(0.42)),
            .init(color: Color.white, location: loc(0.5)),
            .init(color: Color(red: 1, green: 214 / 255, blue: 0), location: loc(0.58)),
            .init(color: Color(red: 1, green: 85 / 255, blue: 0), location: loc(0.78)),
            .init(color: Color(red: 5 / 255, green: 5 / 255, blue: 5 / 255), location: loc(1)),
        ])
    }
}

// MARK: - Per-bar spring state

private struct BarDynamics {
    var heights: [CGFloat]
    var velocities: [CGFloat]
    var lastTick: TimeInterval?

    static let empty = BarDynamics(heights: [], velocities: [], lastTick: nil)

    static func seeded(count: Int) -> BarDynamics {
        BarDynamics(
            heights: Array(repeating: 3, count: count),
            velocities: Array(repeating: 0, count: count),
            lastTick: nil
        )
    }
}

#if DEBUG
struct MusicPulseVisualizer_Previews: PreviewProvider {
    static var previews: some View {
        VStack(spacing: 24) {
            MusicPulseVisualizer(isPlaying: true, height: 72, barCount: 50, bpm: 128)
            MusicPulseVisualizer(isPlaying: false, height: 72, barCount: 48, bpm: 100)
        }
        .padding()
        .background(MdColor.oledTrueBlack)
        .preferredColorScheme(.dark)
    }
}
#endif
