import SwiftUI

struct MdCatalogAction: View {
    let title: String
    let action: () -> Void
    @Environment(\.isEnabled) private var enabled

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(MdType.brandedControl)
                .foregroundStyle(.white)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 12)
                .frame(maxWidth: .infinity, minHeight: 44)
                .background {
                    RoundedRectangle(cornerRadius: 14)
                        .fill(LinearGradient(
                            colors: [Color(red: 1, green: 0.32, blue: 0), Color(red: 1, green: 0.69, blue: 0.18)],
                            startPoint: .leading,
                            endPoint: .trailing
                        ))
                }
                .opacity(enabled ? 1 : 0.5)
        }
        .buttonStyle(.plain)
    }
}
