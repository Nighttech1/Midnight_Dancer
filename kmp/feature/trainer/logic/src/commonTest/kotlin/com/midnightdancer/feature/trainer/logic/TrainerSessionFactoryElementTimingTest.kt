package com.midnightdancer.feature.trainer.logic

import com.midnightdancer.core.domain.entity.Move
import com.midnightdancer.core.domain.media.ElementDurationCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrainerSessionFactoryElementTimingTest {

    @Test
    fun storylineTitles_areSentenceCase() {
        val titles = FREESTYLE_STORYLINE.map { it.title } + CHOREOGRAPHY_STORYLINE.map { it.title }
        assertTrue(titles.contains("Выбор элементов и интервала"))
        titles.forEach { title ->
            val words = title.split(' ')
            // First word may be capital; no Title-Case every word (e.g. "И " mid-phrase).
            assertTrue(
                words.drop(1).none { it.length > 1 && it[0].isUpperCase() && it != "·" },
                "Expected sentence case, got: $title",
            )
        }
    }

    @Test
    fun cueStepSec_usesElementDuration_whenFlagOn() {
        val withDur = Move(
            id = "1",
            name = "Basic",
            level = "A",
            description = ElementDurationCodec.pack("", 4_500L),
        )
        val noDur = Move(id = "2", name = "Wave", level = "A", description = "no video")

        assertEquals(4.5f, TrainerSessionFactory.cueStepSec(withDur, fallbackIntervalSec = 8f, useElementTiming = true))
        assertEquals(8f, TrainerSessionFactory.cueStepSec(noDur, fallbackIntervalSec = 8f, useElementTiming = true))
        assertEquals(8f, TrainerSessionFactory.cueStepSec(withDur, fallbackIntervalSec = 8f, useElementTiming = false))
    }

    @Test
    fun buildCueSchedule_spacesByElementDuration_whenUseElementTiming() {
        val move = Move(
            id = "1",
            name = "Cross",
            level = "A",
            description = ElementDurationCodec.pack("", 3_000L),
            videoUri = "content://el/1",
        )
        val cues = TrainerSessionFactory.buildCueSchedule(
            pool = listOf(move),
            interval = 8f,
            sessionSec = 9f,
            fallbackLabel = "Все",
            useElementTiming = true,
        )
        // 0s, 3s, 6s, 9s → at least 4 cues spanning ~9s with 3s steps
        assertTrue(cues.size >= 3)
        assertEquals(0f, cues[0].sec)
        assertEquals(3f, cues[1].sec)
        assertEquals(6f, cues[2].sec)
        assertEquals("Cross", cues[0].label)
    }

    @Test
    fun buildCueSchedule_fixedInterval_whenFlagOff() {
        val move = Move(
            id = "1",
            name = "Cross",
            level = "A",
            description = ElementDurationCodec.pack("", 3_000L),
        )
        val cues = TrainerSessionFactory.buildCueSchedule(
            pool = listOf(move),
            interval = 5f,
            sessionSec = 10f,
            fallbackLabel = "Все",
            useElementTiming = false,
        )
        assertEquals(0f, cues[0].sec)
        assertEquals(5f, cues[1].sec)
        assertEquals(10f, cues[2].sec)
    }
}
