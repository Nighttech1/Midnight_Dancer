package com.midnightdancer.app.ui.elements

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Process-scoped first-frame cache for element card covers (C-183).
 * Warm once at app start; Elements tab remounts hit memory instead of re-decoding.
 */
object ElementThumbCache {
    private val frames = ConcurrentHashMap<String, Bitmap>()

    fun get(uri: String): Bitmap? = runCatching { frames[com.midnightdancer.core.data.files.OwnedMediaRuntime.resolveReference(uri)] }.getOrNull()

    fun put(uri: String, bitmap: Bitmap) {
        frames[com.midnightdancer.core.data.files.OwnedMediaRuntime.resolveReference(uri)] = bitmap
    }

    suspend fun loadOrFetch(context: Context, uri: String): Bitmap? {
        val effective = runCatching { com.midnightdancer.core.data.files.OwnedMediaRuntime.resolveReference(uri) }.getOrNull() ?: return null
        frames[effective]?.let { return it }
        return withContext(Dispatchers.IO) {
            frames[effective]?.let { return@withContext it }
            val bmp = runCatching {
                MediaMetadataRetriever().use { retriever ->
                    retriever.setDataSource(context, Uri.parse(effective))
                    retriever.getFrameAtTime(0L)
                }
            }.getOrNull()
            if (bmp != null) frames[effective] = bmp
            bmp
        }
    }

    /** Stream-warm known video uris without blocking UI. */
    suspend fun warmAll(context: Context, uris: Collection<String>) {
        uris.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .forEach { uri ->
                if (get(uri) == null) {
                    loadOrFetch(context, uri)
                }
            }
    }
}
