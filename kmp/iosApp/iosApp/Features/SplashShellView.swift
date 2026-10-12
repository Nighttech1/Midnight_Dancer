import SwiftUI

struct SplashShellView: View {
    let snapshot: SplashShellSnapshot
    var onContinue: () -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var glow = false

    var body: some View {
        ZStack {
            MdNeonAtmosphere(accent: MdColor.neonOrange)
            VStack(spacing: 20) {
                ZStack {
                    Circle()
                        .fill(MdColor.neonOrange.opacity(glow ? 0.22 : 0.1))
                        .frame(width: 120, height: 120)
                        .blur(radius: 18)
                    Image(systemName: "figure.dance")
                        .font(.system(size: 44, weight: .light))
                        .foregroundStyle(MdColor.neonOrange)
                        .shadow(color: MdColor.neonOrange.opacity(0.55), radius: glow ? 16 : 6)
                        .scaleEffect(glow && !reduceMotion ? 1.06 : 1)
                }
                Text(snapshot.title)
                    .font(MdType.titleLarge)
                    .foregroundStyle(MdColor.textPrimary)
                Text(snapshot.prepareFailed
                     ? "Голос: системный запасной"
                     : (snapshot.bootReady ? "Готово" : "Загрузка голоса…"))
                    .font(MdType.bodyMedium)
                    .foregroundStyle(MdColor.textSecondary)

                if !snapshot.bootReady {
                    ProgressView()
                        .tint(MdColor.neonOrange)
                }

                MdNeonEqualizerBars(accent: MdColor.neonCyan, barCount: 5)
                    .padding(.top, 8)

                if snapshot.bootReady {
                    Button(action: onContinue) {
                        Text("Пропустить")
                            .font(.system(size: 16, weight: .bold))
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                            .background(RoundedRectangle(cornerRadius: 14).fill(MdColor.neonOrange))
                            .foregroundStyle(MdColor.oledTrueBlack)
                    }
                    .buttonStyle(.plain)
                    .padding(.top, 12)
                    .accessibilityLabel("Пропустить заставку")
                }
            }
            .padding(24)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .onAppear {
            guard !reduceMotion else { return }
            withAnimation(.easeInOut(duration: 1.4).repeatForever(autoreverses: true)) {
                glow = true
            }
        }
    }
}
