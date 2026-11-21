import AVFoundation
import Foundation

enum ChoreoCompositionBuilder {
    enum BuildError: Error {
        case noMedia
        case noMediaTrack
        case missingMedia
        case invalidSourceRange
        case insertFailed
    }

    /// Use one composition and clock for the silent element video and soundtrack.
    /// Each input duration is in source time; track placement uses its effective timeline length.
    @MainActor
    static func makeCompositionItem(
        elementClips: [EditorClipSnapshot],
        musicClips: [EditorClipSnapshot],
        songSpeed: Double
    ) async throws -> (item: AVPlayerItem, durationMs: Int64) {
        let hasVideo = elementClips.contains { mediaReference($0) != nil }
        let hasAudio = musicClips.contains { mediaReference($0) != nil }
        guard hasVideo || hasAudio else { throw BuildError.noMedia }

        let composition = AVMutableComposition()
        let videoDuration = try await appendLane(
            elementClips, type: .video, speedOverride: nil, to: composition, hasMedia: hasVideo
        )
        let musicDuration = try await appendLane(
            musicClips, type: .audio, speedOverride: songSpeed, to: composition, hasMedia: hasAudio
        )
        let durationMs = max(videoDuration, musicDuration)
        guard durationMs > 0 else { throw BuildError.noMedia }
        let intendedDuration = CMTime(value: durationMs, timescale: 1_000)
        if CMTimeCompare(intendedDuration, composition.duration) > 0 {
            composition.insertEmptyTimeRange(
                CMTimeRange(start: composition.duration, end: intendedDuration)
            )
        }

        let item = AVPlayerItem(asset: composition)
        item.audioTimePitchAlgorithm = .spectral
        return (item, durationMs)
    }

    private struct Source {
        let asset: AVURLAsset
        let track: AVAssetTrack
        let range: CMTimeRange
    }

    @MainActor
    private static func appendLane(
        _ clips: [EditorClipSnapshot],
        type: AVMediaType,
        speedOverride: Double?,
        to composition: AVMutableComposition,
        hasMedia: Bool
    ) async throws -> Int64 {
        let destination = hasMedia ? composition.addMutableTrack(
            withMediaType: type,
            preferredTrackID: kCMPersistentTrackID_Invalid
        ) : nil
        if hasMedia && destination == nil { throw BuildError.noMediaTrack }

        var sources: [URL: Source] = [:]
        var cursorMs: Int64 = 0
        for clip in clips {
            try Task.checkCancellation()
            let rate = speedOverride ?? clip.speed
            let speed = rate.isFinite && rate > 0 ? rate : 1
            let rawDurationMs = max(clip.durationMs, 0)
            let effectiveDurationMs = ChoreoCompositionTimelineMath.effectiveMs(
                durationMs: rawDurationMs, speed: speed
            )
            guard effectiveDurationMs > 0 else { continue }
            let placement = CMTime(value: cursorMs, timescale: 1_000)
            let effectiveDuration = CMTime(value: effectiveDurationMs, timescale: 1_000)
            let (nextPosition, overflow) = cursorMs.addingReportingOverflow(effectiveDurationMs)
            guard !overflow else { throw BuildError.invalidSourceRange }
            cursorMs = nextPosition
            guard let destination else { continue }

            guard let reference = mediaReference(clip) else {
                destination.insertEmptyTimeRange(CMTimeRange(start: placement, duration: effectiveDuration))
                continue
            }
            guard let url = OwnedElementVideo.resolve(reference) else { throw BuildError.missingMedia }

            let source: Source
            if let previous = sources[url] {
                source = previous
            } else {
                let asset = AVURLAsset(url: url)
                guard let track = try await asset.loadTracks(withMediaType: type).first else {
                    throw BuildError.noMediaTrack
                }
                source = Source(asset: asset, track: track, range: try await track.load(.timeRange))
                if type == .video && sources.isEmpty {
                    destination.preferredTransform = try await track.load(.preferredTransform)
                }
                sources[url] = source
            }

            let sourceOffset = CMTime(value: max(clip.sourceStartMs, 0), timescale: 1_000)
            let sourceStart = source.range.start + sourceOffset
            let available = source.range.duration - sourceOffset
            let desired = CMTime(value: rawDurationMs, timescale: 1_000)
            guard sourceStart.isNumeric, available.isNumeric, CMTimeCompare(available, .zero) > 0 else {
                throw BuildError.invalidSourceRange
            }
            let actual = CMTimeCompare(available, desired) < 0 ? available : desired
            do {
                try destination.insertTimeRange(
                    CMTimeRange(start: sourceStart, duration: actual), of: source.track, at: placement
                )
            } catch {
                throw BuildError.insertFailed
            }

            // Legacy metadata can outlast a source file. Leave the missing tail silent or blank
            // so later clips retain their original positions and audio stays aligned with video.
            let portion = min(max(actual.seconds / desired.seconds, 0), 1)
            let scaled = CMTimeMultiplyByFloat64(effectiveDuration, multiplier: portion)
            if CMTimeCompare(scaled, actual) != 0 {
                destination.scaleTimeRange(CMTimeRange(start: placement, duration: actual), toDuration: scaled)
            }
            if CMTimeCompare(scaled, effectiveDuration) < 0 {
                destination.insertEmptyTimeRange(
                    CMTimeRange(start: placement + scaled, duration: effectiveDuration - scaled)
                )
            }
        }
        return cursorMs
    }

    static func mediaReference(_ clip: EditorClipSnapshot) -> String? {
        let reference = clip.mediaUri?.trimmingCharacters(in: .whitespacesAndNewlines)
        return reference?.isEmpty == false ? reference : nil
    }
}
