package com.midnightdancer.core.data.files

import android.media.MediaExtractor
import com.midnightdancer.core.domain.media.OwnedMediaKind
import com.midnightdancer.core.domain.media.OwnedMediaException
import com.midnightdancer.core.domain.media.OwnedMediaFailure
import java.io.File

/** Validate the actual container/tracks, never a provider's display name or extension. */
fun interface OwnedMediaValidator {
    fun validatedExtension(file: File, kind: OwnedMediaKind): String
}

internal class AndroidOwnedMediaValidator : OwnedMediaValidator {
    override fun validatedExtension(file: File, kind: OwnedMediaKind): String {
        val header = ByteArray(16)
        val count = file.inputStream().use { it.read(header) }
        fun tag(offset: Int, value: String) = count >= offset + value.length &&
            value.indices.all { header[offset + it] == value[it].code.toByte() }
        val iso = tag(4, "ftyp")
        val extension = when {
            iso -> if (kind == OwnedMediaKind.Audio) "m4a" else if (tag(8, "qt  ")) "mov" else "mp4"
            tag(0, "RIFF") && tag(8, "WAVE") -> "wav"
            tag(0, "OggS") -> "ogg"
            tag(0, "fLaC") -> "flac"
            tag(0, "ID3") -> "mp3"
            count >= 2 && (header[0].toInt() and 0xff) == 0xff && (header[1].toInt() and 0xf6) == 0xf0 -> "aac"
            count >= 2 && (header[0].toInt() and 0xff) == 0xff && (header[1].toInt() and 0xe0) == 0xe0 -> "mp3"
            else -> throw OwnedMediaException(OwnedMediaFailure.Unsupported)
        }
        if (kind == OwnedMediaKind.Video && !iso) throw OwnedMediaException(OwnedMediaFailure.Unsupported)
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            var video = false
            var audio = false
            repeat(extractor.trackCount) { index ->
                val mime = extractor.getTrackFormat(index).getString("mime").orEmpty()
                video = video || mime.startsWith("video/")
                audio = audio || mime.startsWith("audio/")
            }
            // An audiovisual MP4 is video; do not silently import it as an audio-only library track.
            if ((kind == OwnedMediaKind.Video && !video) || (kind == OwnedMediaKind.Audio && (!audio || video)))
                throw OwnedMediaException(OwnedMediaFailure.Unsupported)
            return extension
        } catch (e: OwnedMediaException) { throw e }
        catch (e: Exception) { throw OwnedMediaException(OwnedMediaFailure.Unsupported, e) }
        finally { extractor.release() }
    }
}
