import Foundation

/// Mirrors `ImageCacheConfig.Default` / `ImageCacheDefaults` from C-18 (shared Kotlin).
/// Used by the SwiftUI image path — not Coil Multiplatform on iOS (see IMAGE_STRATEGY.md).
enum MdImageCacheConfig {
    static let diskMaxSizeBytes: Int = 128 * 1024 * 1024
    static let memoryMaxSizePercent: Double = 0.25
    static let diskCacheDirectoryName = "image_cache"

    /// Configures process-wide `URLCache` once to honor C-18 disk/memory limits.
    static func installSharedURLCacheIfNeeded() {
        guard !installed else { return }
        installed = true

        let memoryBytes = Int(
            Double(ProcessInfo.processInfo.physicalMemory) * memoryMaxSizePercent
        )
        let caches = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first
        let diskPath = caches?
            .appendingPathComponent(diskCacheDirectoryName, isDirectory: true)
            .path

        URLCache.shared = URLCache(
            memoryCapacity: max(memoryBytes, 16 * 1024 * 1024),
            diskCapacity: diskMaxSizeBytes,
            diskPath: diskPath
        )
    }

    private static var installed = false
}
