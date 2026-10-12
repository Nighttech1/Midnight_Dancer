package com.midnightdancer.core.data.debug

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EdgeStressCatalogSeederTest {
    @Test
    fun buildStubCatalog_defaultCount_hasFiftyPlusMovesAndSongs() {
        val data = EdgeStressCatalogSeeder.buildStubCatalog()
        assertEquals(1, data.danceStyles.size)
        assertEquals(EdgeStressCatalogSeeder.STYLE_ID, data.danceStyles.single().id)
        assertTrue(data.danceStyles.single().moves.size >= 50)
        assertTrue(data.songs.size >= 50)
        assertEquals(data.danceStyles.single().moves.size, data.songs.size)
        assertTrue(data.choreographies.isNotEmpty())
        assertTrue(data.danceStyles.single().moves.all { it.id.startsWith(EdgeStressCatalogSeeder.ID_PREFIX) })
        assertTrue(data.songs.all { it.id.startsWith(EdgeStressCatalogSeeder.ID_PREFIX) })
        assertTrue(data.songs.all { it.fileName.endsWith(".mp3") })
        assertTrue(data.danceStyles.single().moves.all { it.videoUri!!.endsWith(".mp4") })
    }

    @Test
    fun buildStubCatalog_customCount() {
        val data = EdgeStressCatalogSeeder.buildStubCatalog(count = 3, choreographyCount = 2)
        assertEquals(3, data.danceStyles.single().moves.size)
        assertEquals(3, data.songs.size)
        assertEquals(2, data.choreographies.size)
        assertEquals(EdgeStressCatalogSeeder.moveId(1), data.danceStyles.single().moves.first().id)
        assertEquals(EdgeStressCatalogSeeder.songId(3), data.songs.last().id)
    }
}
