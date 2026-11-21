package com.midnightdancer.shared

import com.midnightdancer.feature.choreography.logic.ChoreographyComponent
import com.midnightdancer.feature.choreography.logic.ChoreographyIntent
import com.midnightdancer.feature.choreography.logic.ChoreographySortMode
import com.midnightdancer.feature.elements.logic.ElementsComponent
import com.midnightdancer.feature.elements.logic.ElementsIntent
import com.midnightdancer.feature.elements.logic.ElementsSortMode
import com.midnightdancer.feature.music.logic.MusicComponent
import com.midnightdancer.feature.music.logic.MusicIntent
import com.midnightdancer.feature.music.logic.MusicSortMode

/** Connect the iOS catalog toolbar to the same intents as Android. */
object IosCatalogActions {
    private const val ALL_TAGS = "Все теги"

    fun sortElements(component: ElementsComponent, alphabetical: Boolean) {
        component.onIntent(
            ElementsIntent.SetSortMode(
                if (alphabetical) ElementsSortMode.Alphabetical else ElementsSortMode.ByLevel,
            ),
        )
    }

    fun filterMusic(component: MusicComponent, tag: String?) {
        component.onIntent(MusicIntent.SetLevelFilter(tag ?: ALL_TAGS))
    }

    fun sortMusic(component: MusicComponent, alphabetical: Boolean) {
        component.onIntent(
            MusicIntent.SetSortMode(
                if (alphabetical) MusicSortMode.Alphabetical else MusicSortMode.ByTag,
            ),
        )
    }

    fun filterChoreography(component: ChoreographyComponent, tag: String?) {
        component.onIntent(ChoreographyIntent.SetTagFilter(tag ?: ALL_TAGS))
    }

    fun sortChoreography(component: ChoreographyComponent, alphabetical: Boolean) {
        component.onIntent(
            ChoreographyIntent.SetSortMode(
                if (alphabetical) ChoreographySortMode.Alphabetical else ChoreographySortMode.ByTag,
            ),
        )
    }
}
