package com.midnightdancer.core.domain.serialization

import com.midnightdancer.core.domain.entity.Move
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Flutter `Move.fromJson` priority: `videoUri` → `videoFileName` → `videoRef`.
 * Plain `@JsonNames` overwrites primary when both keys are present — this serializer preserves priority.
 * Encode writes only `videoUri` (legacy keys are read-only).
 */
object MoveSerializer : KSerializer<Move> {
    @Serializable
    private data class Surrogate(
        val id: String,
        val name: String,
        val level: String,
        val description: String? = null,
        val videoUri: String? = null,
        val videoFileName: String? = null,
        val videoRef: String? = null,
        val masteryPercent: Int = 0,
    )

    private val surrogateSerializer = Surrogate.serializer()

    override val descriptor: SerialDescriptor = surrogateSerializer.descriptor

    override fun serialize(encoder: Encoder, value: Move) {
        surrogateSerializer.serialize(
            encoder,
            Surrogate(
                id = value.id,
                name = value.name,
                level = value.level,
                description = value.description,
                videoUri = value.videoUri,
                masteryPercent = value.masteryPercent,
            ),
        )
    }

    override fun deserialize(decoder: Decoder): Move {
        val s = surrogateSerializer.deserialize(decoder)
        return Move(
            id = s.id,
            name = s.name,
            level = s.level,
            description = s.description,
            videoUri = resolveVideoUri(s.videoUri, s.videoFileName, s.videoRef),
            masteryPercent = s.masteryPercent,
        )
    }

    /** First non-empty among Flutter alias keys. */
    internal fun resolveVideoUri(
        videoUri: String?,
        videoFileName: String?,
        videoRef: String?,
    ): String? =
        sequenceOf(videoUri, videoFileName, videoRef).firstOrNull { !it.isNullOrEmpty() }
}
