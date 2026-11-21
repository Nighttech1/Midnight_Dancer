import AVFoundation
import Foundation

/// Relocatable reference to a private copy. Never removes the provider's original.
struct OwnedMusicAudio: Sendable {
    static let prefix = "md-owned-audio/"
    let reference: String
    let title: String
    let durationMs: Int64

    private static func folder() throws -> URL {
        try FileManager.default.url(for: .applicationSupportDirectory, in: .userDomainMask,
            appropriateFor: nil, create: true)
            .appendingPathComponent("MidnightDancer/OwnedAudio", isDirectory: true)
    }

    static func resolve(_ reference: String) -> URL? {
        guard reference.hasPrefix(prefix) else { return OwnedElementVideo.resolve(reference) }
        let name = String(reference.dropFirst(prefix.count))
        let parts = name.split(separator: ".", omittingEmptySubsequences: false)
        guard parts.count == 2, UUID(uuidString: String(parts[0])) != nil,
              validExtension(String(parts[1])), let directory = try? folder() else { return nil }
        let url = directory.appendingPathComponent(name)
        return FileManager.default.fileExists(atPath: url.path) ? url : nil
    }

    private static func validExtension(_ value: String) -> Bool {
        (1...10).contains(value.utf8.count) && value.utf8.allSatisfy {
            (48...57).contains($0) || (97...122).contains($0)
        }
    }

    nonisolated static func importFile(_ source: URL) async throws -> OwnedMusicAudio {
        try Task.checkCancellation()
        let scoped = source.startAccessingSecurityScopedResource()
        defer { if scoped { source.stopAccessingSecurityScopedResource() } }
        let values = try source.resourceValues(forKeys: [.isRegularFileKey, .isSymbolicLinkKey])
        guard values.isRegularFile == true, values.isSymbolicLink != true else { throw AudioError.invalid }
        let directory = try folder()
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let ext = source.pathExtension.lowercased()
        let name = UUID().uuidString.lowercased() + "." + (validExtension(ext) ? ext : "audio")
        let destination = directory.appendingPathComponent(name)
        do {
            try FileManager.default.copyItem(at: source, to: destination)
            try FileManager.default.setAttributes([.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication],
                ofItemAtPath: destination.path)
            try Task.checkCancellation()
            let asset = AVURLAsset(url: destination)
            guard try await asset.load(.isPlayable),
                  !(try await asset.loadTracks(withMediaType: .audio)).isEmpty else { throw AudioError.invalid }
            let duration = try await asset.load(.duration).seconds * 1000
            guard duration.isFinite, duration >= 1, duration < Double(Int64.max) else { throw AudioError.invalid }
            try Task.checkCancellation()
            return OwnedMusicAudio(reference: prefix + name,
                title: source.deletingPathExtension().lastPathComponent, durationMs: Int64(duration.rounded(.down)))
        } catch {
            try? FileManager.default.removeItem(at: destination)
            throw error
        }
    }

    enum AudioError: LocalizedError {
        case invalid
        var errorDescription: String? { "Этот файл не удалось открыть как музыку." }
    }
}
