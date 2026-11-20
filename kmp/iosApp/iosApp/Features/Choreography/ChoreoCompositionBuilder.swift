import AVFoundation
import Foundation

enum ChoreoCompositionBuilder {
    enum BuildError: Error {
        case noVideoTrack
        case insertFailed
    }

    /// Builds one timeline [AVPlayerItem] for all element clips (Android CompositionPlayer parity).
    static func makeCompositionItem(
        from clips: [EditorClipSnapshot]
    ) throws -> (item: AVPlayerItem, durationMs: Int64) {
        let videoClips = clips.filter { resolveMediaURL($0.mediaUri) != nil }
        guard !videoClips.isEmpty else {
            throw BuildError.noVideoTrack
        }

        let composition = AVMutableComposition()
        guard let compositionVideoTrack = composition.addMutableTrack(
            withMediaType: .video,
            preferredTrackID: kCMPersistentTrackID_Invalid
        ) else {
            throw BuildError.noVideoTrack
        }

        var cursor = CMTime.zero
        var totalMs: Int64 = 0

        for clip in videoClips {
            guard let url = resolveMediaURL(clip.mediaUri) else { continue }
            let asset = AVURLAsset(url: url)
            guard let sourceTrack = asset.tracks(withMediaType: .video).first else { continue }

            let speed = clip.speed > 0 ? clip.speed : 1.0
            let rawDurMs = max(clip.durationMs, 1)
            let startMs = max(clip.sourceStartMs, 0)
            let sourceStart = CMTime(value: startMs, timescale: 1_000)
            let sourceDuration = CMTime(value: rawDurMs, timescale: 1_000)
            let sourceRange = CMTimeRange(start: sourceStart, duration: sourceDuration)

            do {
                try compositionVideoTrack.insertTimeRange(sourceRange, of: sourceTrack, at: cursor)
            } catch {
                throw BuildError.insertFailed
            }

            let effectiveMs = ChoreoCompositionTimelineMath.effectiveMs(
                durationMs: rawDurMs,
                speed: speed
            )
            if abs(speed - 1.0) > 0.01 {
                let scaledDuration = CMTime(value: effectiveMs, timescale: 1_000)
                compositionVideoTrack.scaleTimeRange(
                    CMTimeRange(start: cursor, duration: sourceDuration),
                    toDuration: scaledDuration
                )
                cursor = cursor + scaledDuration
            } else {
                cursor = cursor + sourceDuration
            }
            totalMs += effectiveMs
        }

        guard totalMs > 0 else { throw BuildError.noVideoTrack }

        let item = AVPlayerItem(asset: composition)
        item.audioTimePitchAlgorithm = .spectral
        return (item, totalMs)
    }

    static func resolveMediaURL(_ raw: String?) -> URL? {
        guard let raw else { return nil }
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        if let url = URL(string: trimmed), url.scheme != nil {
            return url
        }
        return URL(fileURLWithPath: trimmed)
    }
}
