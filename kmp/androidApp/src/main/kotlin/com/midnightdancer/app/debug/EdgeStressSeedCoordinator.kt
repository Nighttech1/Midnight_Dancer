package com.midnightdancer.app.debug

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import com.midnightdancer.core.data.debug.EdgeStressCatalogSeeder
import com.midnightdancer.core.data.db.RoomCatalogRepository
import com.midnightdancer.core.data.files.MediaFileKind
import com.midnightdancer.core.data.files.MediaStorage
import com.midnightdancer.core.domain.debug.EdgeStressUiBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext
import java.io.File

/**
 * DEBUG-only: Room upsert (when Koin has catalog) + tiny media + UI bridge publish.
 */
object EdgeStressSeedCoordinator {
    const val ACTION: String = "com.midnightdancer.app.DEBUG_SEED_EDGE_STRESS"
    const val EXTRA_COUNT: String = "count"
    private const val TAG = "EdgeStressSeed"

    fun isDebuggable(context: Context): Boolean =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    suspend fun seed(
        context: Context,
        count: Int = EdgeStressCatalogSeeder.DEFAULT_COUNT,
    ): EdgeStressCatalogSeeder.Result {
        check(isDebuggable(context)) {
            "Edge-stress seed is DEBUG-only (process not debuggable)"
        }
        val n = count.coerceIn(1, 500)
        val koin = GlobalContext.get()
        val repository = koin.getOrNull<RoomCatalogRepository>()
        val media = koin.getOrNull<MediaStorage>()
        val result =
            withContext(Dispatchers.IO) {
                if (media != null) {
                    runCatching { writeTinyPlaceholders(media, n) }
                        .onFailure { Log.w(TAG, "placeholder media write failed", it) }
                }
                if (repository != null) {
                    EdgeStressCatalogSeeder.seedInto(
                        repository = repository,
                        count = n,
                        choreographyCount = EdgeStressCatalogSeeder.DEFAULT_CHOREO_COUNT,
                    )
                } else {
                    Log.w(TAG, "RoomCatalogRepository missing — UI-only seed")
                    EdgeStressCatalogSeeder.Result(
                        moveCount = n,
                        songCount = n,
                        choreographyCount = EdgeStressCatalogSeeder.DEFAULT_CHOREO_COUNT,
                    )
                }
            }
        EdgeStressUiBridge.publish(
            elementCount = result.moveCount,
            trackCount = result.songCount,
            choreographyCount = result.choreographyCount,
        )
        Log.i(
            TAG,
            "seeded moves=${result.moveCount} songs=${result.songCount} " +
                "choreos=${result.choreographyCount} room=${repository != null}",
        )
        return result
    }

    private fun writeTinyPlaceholders(media: MediaStorage, count: Int) {
        media.ensureLayout()
        val blob = ByteArray(32) { idx -> (idx * 17).toByte() }
        for (i in 1..count) {
            val video =
                File(
                    media.mediaFilePath(
                        id = EdgeStressCatalogSeeder.moveId(i),
                        kind = MediaFileKind.VIDEO,
                    ),
                )
            val audio =
                File(
                    media.mediaFilePath(
                        id = EdgeStressCatalogSeeder.songId(i),
                        kind = MediaFileKind.MUSIC,
                    ),
                )
            if (!video.exists()) {
                video.parentFile?.mkdirs()
                video.writeBytes(blob)
            }
            if (!audio.exists()) {
                audio.parentFile?.mkdirs()
                audio.writeBytes(blob)
            }
        }
    }
}
