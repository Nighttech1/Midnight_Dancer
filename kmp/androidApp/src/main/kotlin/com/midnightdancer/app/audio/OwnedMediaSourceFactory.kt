package com.midnightdancer.app.audio

import android.content.Context
import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.midnightdancer.core.data.files.OwnedMediaRuntime
import com.midnightdancer.core.domain.media.OwnedMediaException
import java.io.IOException

/** Resolve on every open, including later repeats/seeks of a playlist prepared before adoption. */
internal fun ownedMediaSourceFactory(context: Context, resolver: (String) -> String = OwnedMediaRuntime::resolveReference): DefaultMediaSourceFactory {
    val upstream = DefaultDataSource.Factory(context.applicationContext)
    return DefaultMediaSourceFactory(context.applicationContext).setDataSourceFactory(DataSource.Factory {
        val source = upstream.createDataSource()
        object : DataSource by source {
            override fun open(dataSpec: DataSpec): Long {
                val effective = try { resolveOwnedMediaDataSpec(dataSpec.uri, resolver)
                } catch (error: OwnedMediaException) { throw IOException("owned_media_unavailable", error) }
                return source.open(dataSpec.buildUpon().setUri(Uri.parse(effective)).build())
            }
        }
    })
}

/** Android players may turn an absolute path into file:// before the source is opened. */
internal fun resolveOwnedMediaDataSpec(uri: Uri, resolve: (String) -> String): String {
    val original = uri.toString()
    val exact = resolve(original)
    if (exact != original) return exact
    if (uri.scheme == "file") {
        val path = uri.path
        if (!path.isNullOrBlank()) {
            val mapped = resolve(path)
            if (mapped != path) return mapped
        }
    }
    return original
}
