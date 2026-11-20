package com.midnightdancer.app.access

import android.content.Context
import com.midnightdancer.app.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class ProductAccessState(
    val testPro: Boolean = false,
    val legacyProtected: Boolean = false,
) {
    val canRehearse: Boolean get() = BuildConfig.DEBUG && testPro
    val label: String get() = when {
        canRehearse -> "Тестовый Pro · не подписка"
        legacyProtected -> "Прежние возможности сохранены"
        else -> "Midnight Dancer Free"
    }
}

/** No paid entitlement is fabricated. A verified billing adapter is a commercial-release gate. */
object ProductAccessRuntime {
    private val mutable = MutableStateFlow(ProductAccessState())
    val state = mutable.asStateFlow()

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences("product_access_rollout_v1", Context.MODE_PRIVATE)
        if (!prefs.contains("legacy_protected")) {
            // Preserve pre-existing installs until their paid rights have been migrated/verified.
            val legacy = File(context.filesDir, "choreography_vault_v1.json").exists() ||
                File(context.filesDir.parentFile, "app_flutter/MidnightDancer").exists()
            check(prefs.edit().putBoolean("legacy_protected", legacy).commit())
        }
        mutable.value = ProductAccessState(legacyProtected = prefs.getBoolean("legacy_protected", false))
    }

    fun canCreate(count: Int): Boolean =
        state.value.canRehearse || state.value.legacyProtected || count < 1

    fun setTestPro(enabled: Boolean) {
        check(BuildConfig.DEBUG) { "Test access is unavailable in release builds" }
        mutable.value = mutable.value.copy(testPro = enabled)
    }
}
