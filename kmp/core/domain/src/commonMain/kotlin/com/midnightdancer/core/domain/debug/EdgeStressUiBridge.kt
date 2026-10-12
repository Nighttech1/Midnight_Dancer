package com.midnightdancer.core.domain.debug

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Process-wide signal so feature shells (still on in-memory catalogs) can inflate
 * stub cards after a DEBUG edge-stress seed. Production UI never publishes here.
 */
data class EdgeStressUiSeedRequest(
    val elementCount: Int,
    val trackCount: Int,
    val choreographyCount: Int = 0,
    val generation: Long,
)

object EdgeStressUiBridge {
    private val _requests = MutableStateFlow<EdgeStressUiSeedRequest?>(null)
    val requests: StateFlow<EdgeStressUiSeedRequest?> = _requests.asStateFlow()

    fun publish(
        elementCount: Int,
        trackCount: Int,
        choreographyCount: Int = 0,
    ) {
        require(elementCount >= 0 && trackCount >= 0 && choreographyCount >= 0) {
            "edge-stress counts must be non-negative"
        }
        _requests.update {
            EdgeStressUiSeedRequest(
                elementCount = elementCount,
                trackCount = trackCount,
                choreographyCount = choreographyCount,
                generation = (it?.generation ?: 0L) + 1L,
            )
        }
    }

    /** Test / process cleanup only. */
    fun clear() {
        _requests.value = null
    }
}
