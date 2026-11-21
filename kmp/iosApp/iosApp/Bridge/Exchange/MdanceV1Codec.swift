import Foundation
import CryptoKit
import CryptoSwift
import Security
import Darwin
import Shared

/// V1 wire-compatible streaming adapter. Not installed in the app's exchange flow yet.
/// CryptoSwift source dependency is pinned to 1.10.0. Keep its license/acknowledgement in distribution.
/// This class uses no private Apple APIs and never publishes plaintext before GCM final verification.
final class MdanceV1Codec: NSObject, IosArchiveContainerCodec {
    static let maximumPlainBytes: UInt64 = 300 * 1024 * 1024
    private static let blockBytes = 64 * 1024
    private static let magic = Data("MDANCE1".utf8)
    private let privateRoot: URL

    init(privateRoot: URL = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]) {
        self.privateRoot = privateRoot.standardizedFileURL.resolvingSymlinksInPath()
        super.init()
    }

    enum Failure: Error {
        case invalidInput, unsupportedVersion, sizeLimit, invalidDestination, sourceChanged
        case randomUnavailable, temporaryUnavailable, invalidTag, atomicPublicationFailed
    }

    // The Kotlin block is intentionally Boolean, not an exception-throwing Unit callback.
    func decryptToPrivateZip(containerPath: String, destinationPath: String, bundleId: String,
                             shouldCancel: @escaping () -> KotlinBoolean) throws {
        try decrypt(container: URL(fileURLWithPath: containerPath), destination: URL(fileURLWithPath: destinationPath),
                    bundleId: bundleId, shouldCancel: { shouldCancel().boolValue })
    }

    func encryptPrivateZip(zipPath: String, destinationPath: String, bundleId: String,
                           shouldCancel: @escaping () -> KotlinBoolean) throws {
        try encrypt(zip: URL(fileURLWithPath: zipPath), destination: URL(fileURLWithPath: destinationPath),
                    bundleId: bundleId, shouldCancel: { shouldCancel().boolValue })
    }

    func encrypt(zip: URL, destination: URL, bundleId: String, shouldCancel: () -> Bool = { false }) throws {
        let size = try regularFileSize(zip)
        guard size > 0, size <= Self.maximumPlainBytes else { throw Failure.sizeLimit }
        var nonce = [UInt8](repeating: 0, count: 12)
        guard nonce.withUnsafeMutableBytes({ SecRandomCopyBytes(kSecRandomDefault, 12, $0.baseAddress!) }) == errSecSuccess
        else { throw Failure.randomUnavailable }
        let mode = GCM(iv: nonce, tagLength: 16, mode: .detached)
        var cryptor = try CryptoSwift.AES(key: Self.key(bundleId), blockMode: mode, padding: .noPadding).makeEncryptor()
        let input = try FileHandle(forReadingFrom: zip)
        defer { try? input.close() }
        try publish(destination: destination, source: zip, shouldCancel: shouldCancel) { output in
            try output.write(contentsOf: Self.magic)
            try output.write(contentsOf: Data([1]) + Data(nonce))
            var read: UInt64 = 0
            var written: UInt64 = 0
            while true {
                let count: Int = try autoreleasepool {
                    try Self.check(shouldCancel)
                    let block = try input.read(upToCount: Self.blockBytes) ?? Data()
                    if block.isEmpty { return 0 }
                    read += UInt64(block.count)
                    guard read <= size else { throw Failure.sourceChanged }
                    let encrypted = try cryptor.update(withBytes: [UInt8](block))
                    guard encrypted.count <= Self.blockBytes + 32 else { throw Failure.sizeLimit }
                    written += UInt64(encrypted.count)
                    try output.write(contentsOf: Data(encrypted))
                    return block.count
                }
                if count == 0 { break }
            }
            guard read == size else { throw Failure.sourceChanged }
            let final = try cryptor.finish()
            guard final.count <= 32 else { throw Failure.sizeLimit }
            written += UInt64(final.count)
            try output.write(contentsOf: Data(final))
            guard written == size, let tag = mode.authenticationTag, tag.count == 16 else { throw Failure.invalidTag }
            try output.write(contentsOf: Data(tag))
        }
    }

    func decrypt(container: URL, destination: URL, bundleId: String, shouldCancel: () -> Bool = { false }) throws {
        let size = try regularFileSize(container)
        guard size >= 37, size <= Self.maximumPlainBytes + 36 else { throw Failure.sizeLimit }
        let input = try FileHandle(forReadingFrom: container)
        defer { try? input.close() }
        let header = try Self.readExactly(input, count: 20)
        guard header.prefix(7) == Self.magic else { throw Failure.invalidInput }
        guard header[7] == 1 else { throw Failure.unsupportedVersion }
        try input.seek(toOffset: size - 16)
        let tag = try Self.readExactly(input, count: 16)
        try input.seek(toOffset: 20)
        let mode = GCM(iv: [UInt8](header.suffix(12)), authenticationTag: [UInt8](tag), mode: .detached)
        var cryptor = try CryptoSwift.AES(key: Self.key(bundleId), blockMode: mode, padding: .noPadding).makeDecryptor()
        try publish(destination: destination, source: container, shouldCancel: shouldCancel) { output in
            var remaining = size - 36
            var written: UInt64 = 0
            while remaining > 0 {
                try autoreleasepool {
                    try Self.check(shouldCancel)
                    let block = try Self.readExactly(input, count: Int(min(UInt64(Self.blockBytes), remaining)))
                    remaining -= UInt64(block.count)
                    let plaintext = try cryptor.update(withBytes: [UInt8](block))
                    guard plaintext.count <= Self.blockBytes + 32 else { throw Failure.sizeLimit }
                    written += UInt64(plaintext.count)
                    guard written <= size - 36 else { throw Failure.sizeLimit }
                    // Private 0600 temporary only. The caller's destination remains unchanged here.
                    try output.write(contentsOf: Data(plaintext))
                }
            }
            // Authentication failure MUST escape. Never publish partial plaintext from update().
            let final = try cryptor.finish()
            guard final.count <= 32 else { throw Failure.sizeLimit }
            written += UInt64(final.count)
            guard written == size - 36 else { throw Failure.sourceChanged }
            try output.write(contentsOf: Data(final))
            guard try regularFileSize(container) == size else { throw Failure.sourceChanged }
        }
    }

    private func publish(destination: URL, source: URL, shouldCancel: () -> Bool,
                         write: (FileHandle) throws -> Void) throws {
        try Self.check(shouldCancel)
        let target = destination.standardizedFileURL
        let parent = target.deletingLastPathComponent()
        guard target.isFileURL, parent.path == privateRoot.path || parent.path.hasPrefix(privateRoot.path + "/"),
              source.standardizedFileURL.resolvingSymlinksInPath() != target.resolvingSymlinksInPath()
        else { throw Failure.invalidDestination }
        var ancestor = parent
        var isDirectory: ObjCBool = false
        while !FileManager.default.fileExists(atPath: ancestor.path, isDirectory: &isDirectory) {
            guard ancestor.path != "/" else { throw Failure.invalidDestination }
            ancestor.deleteLastPathComponent()
        }
        let resolved = ancestor.resolvingSymlinksInPath().path
        guard resolved == privateRoot.path || resolved.hasPrefix(privateRoot.path + "/") else { throw Failure.invalidDestination }
        try FileManager.default.createDirectory(at: parent, withIntermediateDirectories: true)
        let resolvedParent = parent.resolvingSymlinksInPath().path
        guard resolvedParent == privateRoot.path || resolvedParent.hasPrefix(privateRoot.path + "/") else { throw Failure.invalidDestination }
        if let attributes = try? FileManager.default.attributesOfItem(atPath: target.path),
           attributes[.type] as? FileAttributeType == .typeSymbolicLink { throw Failure.invalidDestination }
        let temporary = parent.appendingPathComponent(".mdance-\(UUID().uuidString).tmp")
        let fd = Darwin.open(temporary.path, O_WRONLY | O_CREAT | O_EXCL | O_NOFOLLOW, mode_t(S_IRUSR | S_IWUSR))
        guard fd >= 0 else { throw Failure.temporaryUnavailable }
        let output = FileHandle(fileDescriptor: fd, closeOnDealloc: true)
        defer { try? output.close(); try? FileManager.default.removeItem(at: temporary) }
        try write(output)
        try Self.check(shouldCancel)
        try output.synchronize()
        try output.close()
        // Same-filesystem rename is atomic; never truncate/remove an existing destination first.
        guard Darwin.rename(temporary.path, target.path) == 0 else { throw Failure.atomicPublicationFailed }
    }

    private func regularFileSize(_ url: URL) throws -> UInt64 {
        let attributes = try FileManager.default.attributesOfItem(atPath: url.path)
        guard attributes[.type] as? FileAttributeType == .typeRegular,
              let bytes = attributes[.size] as? NSNumber else { throw Failure.invalidInput }
        return bytes.uint64Value
    }
    private static func readExactly(_ handle: FileHandle, count: Int) throws -> Data {
        var result = Data()
        while result.count < count {
            guard let block = try handle.read(upToCount: count - result.count), !block.isEmpty else { throw Failure.invalidInput }
            result.append(block)
        }
        return result
    }
    private static func check(_ shouldCancel: () -> Bool) throws {
        if shouldCancel() || Task<Never, Never>.isCancelled { throw CancellationError() }
    }
    private static func key(_ bundleId: String) -> [UInt8] {
        var material = Data(bundleId.utf8)
        material.append(contentsOf: [0x4d, 0x44, 0x2d, 0x4e, 0x31, 0x67, 0x68, 0x74])
        material.append(contentsOf: [0x44, 0x61, 0x6e, 0x63, 0x65, 0x72, 0x23, 0x39])
        return [UInt8](CryptoKit.SHA256.hash(data: material))
    }
}
