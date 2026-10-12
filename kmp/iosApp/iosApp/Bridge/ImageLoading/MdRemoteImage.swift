import SwiftUI

/// SwiftUI remote image widget backed by [MdImageLoader] (C-18 cache limits via URLCache).
/// Feature screens should use this — not import Shared / Coil.
struct MdRemoteImage: View {
    let url: URL?
    var placeholderLabel: String = "Image"

    @State private var image: UIImage?
    @State private var failed = false

    var body: some View {
        Group {
            if let image {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFit()
            } else if failed || url == nil {
                placeholder
            } else {
                ProgressView()
                    .tint(MdColor.neonOrange)
            }
        }
        .task(id: url?.absoluteString) {
            guard let url else {
                failed = true
                return
            }
            failed = false
            image = await MdImageLoader.shared.load(url: url)
            if image == nil { failed = true }
        }
    }

    private var placeholder: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 8)
                .fill(MdColor.surfaceElevated)
                .overlay(
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(MdColor.surfaceBorder, lineWidth: 1)
                )
            VStack(spacing: 8) {
                Image(systemName: "photo")
                    .font(.system(size: 28))
                    .foregroundStyle(MdColor.neonCyan)
                Text(placeholderLabel)
                    .font(MdType.bodyMedium)
                    .foregroundStyle(MdColor.textSecondary)
            }
        }
    }
}
