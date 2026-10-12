package com.midnightdancer.core.tts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class TrainerTtsControllerTest {

    @Test
    fun selectAndSpeak_ruslan_recordsSpeak() = runTest {
        val engine = RecordingTtsEngine()
        val controller = TrainerTtsController(engine)

        val outcome = controller.selectAndSpeak(TrainerVoice.Ruslan)
        assertTrue(outcome is SpeakOutcome.Spoken)
        assertEquals(TrainerVoice.Ruslan, (outcome as SpeakOutcome.Spoken).voice)
        assertEquals(1, engine.speakCount)
        assertTrue(engine.lastText!!.contains("Руслан"))
    }

    @Test
    fun selectAndSpeak_allThreeVoices_distinctIds() = runTest {
        val engine = RecordingTtsEngine()
        val controller = TrainerTtsController(engine)
        for (voice in TrainerVoice.fullSet) {
            val outcome = controller.selectAndSpeak(voice)
            assertTrue(outcome is SpeakOutcome.Spoken)
            assertEquals(voice.id, (outcome as SpeakOutcome.Spoken).voice.id)
        }
        assertEquals(3, engine.speakCount)
    }

    @Test
    fun speakLabel_showsBackend() {
        val label =
            TrainerTtsController.speakLabel(
                TrainerVoice.Irina,
                TtsBackend.SystemFallback,
                speaking = false,
            )
        assertTrue(label.contains("Ирина"))
        assertTrue(label.contains("System TTS"))
    }

    @Test
    fun voiceAssetPolicy_onnxNames_matchFullLayout() {
        assertEquals("ru_RU-ruslan-medium.onnx", VoiceAssetPolicy.onnxFileName(TrainerVoice.Ruslan))
        assertEquals("ru_RU-irina-medium.onnx", VoiceAssetPolicy.onnxFileName(TrainerVoice.Irina))
        assertEquals("en_US-libritts_r-medium.onnx", VoiceAssetPolicy.onnxFileName(TrainerVoice.Kamila))
        assertEquals(205, VoiceAssetPolicy.APPROX_TOTAL_ONNX_MB)
    }
}
