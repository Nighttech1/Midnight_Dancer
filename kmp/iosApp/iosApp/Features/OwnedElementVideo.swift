import AVFoundation
import CoreTransferable
import Foundation
import UniformTypeIdentifiers

/// Element references use a file name relative to app-owned storage. The iOS
/// container's absolute path can change after an app update or a restore.
struct OwnedElementVideo: Sendable {
    static let referencePrefix = "md-owned-video/"
    let reference: String
    let url: URL
    let durationMs: Int64

    private static func folder() throws -> URL {
        try FileManager.default.url(
            for: .applicationSupportDirectory, in: .userDomainMask,
            appropriateFor: nil, create: false
        )
        .appendingPathComponent("MidnightDancer", isDirectory: true)
        .appendingPathComponent("OwnedVideo", isDirectory: true)
    }

    static func resolve(_ reference: String) -> URL? {
        if reference.hasPrefix(referencePrefix) {
            let fileName = String(reference.dropFirst(referencePrefix.count))
            let parts = fileName.split(separator: ".", omittingEmptySubsequences: false)
            guard parts.count == 2,
                  UUID(uuidString: String(parts[0])) != nil,
                  validExtension(String(parts[1])),
                  let folder = try? folder() else { return nil }
            let url = folder.appendingPathComponent(fileName, isDirectory: false)
            return FileManager.default.fileExists(atPath: url.path) ? url : nil
        }
        let legacyFolders = [("md-legacy-video/", "videos"), ("md-legacy-music/", "music")]
        for (prefix, subfolder) in legacyFolders where reference.hasPrefix(prefix) {
            let name = String(reference.dropFirst(prefix.count))
            guard !name.isEmpty, name != ".", name != "..",
                  !name.contains("/"), !name.contains("\\"),
                  let documents = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first
            else { return nil }
            let url = documents
                .appendingPathComponent("MidnightDancer", isDirectory: true)
                .appendingPathComponent(subfolder, isDirectory: true)
                .appendingPathComponent(name, isDirectory: false)
            return FileManager.default.fileExists(atPath: url.path) ? url : nil
        }
        if let url = URL(string: reference), let scheme = url.scheme?.lowercased() {
            if scheme == "file" {
                return FileManager.default.fileExists(atPath: url.path) ? url : nil
            }
            return (scheme == "https" || scheme == "http") ? url : nil
        }
        guard reference.hasPrefix("/"),
              FileManager.default.fileExists(atPath: reference) else { return nil }
        return URL(fileURLWithPath: reference)
    }

    private static func validExtension(_ value: String) -> Bool {
        (1...10).contains(value.utf8.count) && value.utf8.allSatisfy {
            (48...57).contains($0) || (65...90).contains($0) || (97...122).contains($0)
        }
    }

    static func uniqueFileName(for source: URL) -> String {
        let ext = source.pathExtension.lowercased()
        return UUID().uuidString.lowercased() + "." + (validExtension(ext) ? ext : "mov")
    }

    nonisolated static func importFile(_ source: URL, requiresSecurityScope: Bool) async throws -> OwnedElementVideo {
        try Task.checkCancellation()
        let scoped = requiresSecurityScope && source.startAccessingSecurityScopedResource()
        defer { if scoped { source.stopAccessingSecurityScopedResource() } }

        let manager = FileManager.default
        let values = try source.resourceValues(forKeys: [.isRegularFileKey, .isSymbolicLinkKey])
        guard values.isRegularFile == true, values.isSymbolicLink != true else {
            throw ImportError.invalidVideo
        }
        let folder = try folder()
        try manager.createDirectory(at: folder, withIntermediateDirectories: true)
        let fileName = uniqueFileName(for: source)
        let url = folder.appendingPathComponent(fileName, isDirectory: false)
        do {
            try manager.copyItem(at: source, to: url)
            try Task.checkCancellation()

            let asset = AVURLAsset(url: url)
            guard try await asset.load(.isPlayable),
                  let videoTrack = try await asset.loadTracks(withMediaType: .video).first else {
                throw ImportError.invalidVideo
            }
            let duration = try await videoTrack.load(.timeRange).duration.seconds
            let millis = (duration * 1000).rounded(.down)
            guard millis.isFinite, millis >= 1, millis < Double(Int64.max) else {
                throw ImportError.invalidVideo
            }
            try Task.checkCancellation()
            return OwnedElementVideo(
                reference: referencePrefix + fileName, url: url, durationMs: Int64(millis)
            )
        } catch {
            try? manager.removeItem(at: url)
            throw error
        }
    }

    func removeUncommittedFile() {
        try? FileManager.default.removeItem(at: url)
    }

    enum ImportError: LocalizedError {
        case invalidVideo

        var errorDescription: String? {
            "Этот файл не удалось открыть как видео."
        }
    }
}

/// Photos transfers a temporary file. Copy it before the provider callback returns.
struct PickedElementVideo: Transferable {
    let url: URL

    static var transferRepresentation: some TransferRepresentation {
        FileRepresentation(importedContentType: .movie) { received in
            let url = FileManager.default.temporaryDirectory
                .appendingPathComponent(OwnedElementVideo.uniqueFileName(for: received.file), isDirectory: false)
            do {
                try FileManager.default.copyItem(at: received.file, to: url)
                return PickedElementVideo(url: url)
            } catch {
                try? FileManager.default.removeItem(at: url)
                throw error
            }
        }
    }
}
