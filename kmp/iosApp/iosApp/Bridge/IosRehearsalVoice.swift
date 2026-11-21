import AVFoundation
import Foundation
@preconcurrency import Shared

/// Actual system speech fallback. These are not bundled Piper voices or proof of Pro entitlement.
@MainActor
final class IosRehearsalVoice: NSObject, IosRehearsalVoiceDriver {
    private let synthesizer = AVSpeechSynthesizer()
    private var language: String
    private var voice: AVSpeechSynthesisVoice?
    private var failure: String?
    private var ready = false

    init(selectedVoiceIndex: Int) {
        language = selectedVoiceIndex == 2 ? "en-US" : "ru-RU"
        super.init()
    }
    func selectVoice(index: Int) {
        stop()
        language = index == 2 ? "en-US" : "ru-RU"
        ready = false; voice = nil; failure = nil
    }
    func prepareVoice() {
        synthesizer.stopSpeaking(at: .immediate)
        failure = nil
        voice = AVSpeechSynthesisVoice(language: language)
        ready = voice != nil
        if !ready { failure = "Системный голос недоступен. Проверьте голоса iOS или отключите подсказки." }
    }
    func isPrepared() -> Bool { ready }
    func errorMessage() -> String? { failure }
    func speak(label: String) {
        guard ready, failure == nil, !label.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
        synthesizer.stopSpeaking(at: .immediate)
        let utterance = AVSpeechUtterance(string: label)
        utterance.voice = voice
        utterance.rate = AVSpeechUtteranceDefaultSpeechRate
        synthesizer.speak(utterance)
    }
    func stop() { synthesizer.stopSpeaking(at: .immediate) }
}
