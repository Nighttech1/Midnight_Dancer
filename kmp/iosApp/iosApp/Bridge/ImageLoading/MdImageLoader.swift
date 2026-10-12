import Foundation
import UIKit

/// Native iOS image loader for Stage 3 shells (R4 close-out).
/// Uses `URLSession` + `URLCache` tuned to C-18 limits — **not** Coil on iOS.
@MainActor
final class MdImageLoader: ObservableObject {
    static let shared = MdImageLoader()

    private let session: URLSession

    private init() {
        MdImageCacheConfig.installSharedURLCacheIfNeeded()
        let config = URLSessionConfiguration.default
        config.urlCache = URLCache.shared
        config.requestCachePolicy = .returnCacheDataElseLoad
        session = URLSession(configuration: config)
    }

    func load(url: URL) async -> UIImage? {
        do {
            let (data, response) = try await session.data(from: url)
            if let http = response as? HTTPURLResponse, !(200 ..< 300).contains(http.statusCode) {
                return nil
            }
            return UIImage(data: data)
        } catch {
            return nil
        }
    }
}
