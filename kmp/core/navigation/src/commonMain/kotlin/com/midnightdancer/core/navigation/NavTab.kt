package com.midnightdancer.core.navigation

/**
 * Main chrome tabs — order mirrors Flutter [NavTab]:
 * elements → music → choreography → trainer → exchange.
 * Settings is **not** a tab (Child Stack push from chrome).
 */
enum class NavTab {
    ELEMENTS,
    MUSIC,
    CHOREOGRAPHY,
    TRAINER,
    EXCHANGE,
    ;

    val title: String
        get() = when (this) {
            ELEMENTS -> "Элементы"
            MUSIC -> "Music"
            CHOREOGRAPHY -> "Choreography"
            TRAINER -> "Trainer"
            EXCHANGE -> "Exchange"
        }

    companion object {
        val ordered: List<NavTab> = entries.toList()

        fun fromIndex(index: Int): NavTab =
            ordered.getOrElse(index) { ELEMENTS }
    }
}
