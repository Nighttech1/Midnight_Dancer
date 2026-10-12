package com.midnightdancer.core.data.files

/**
 * Media subfolder kind — mirrors Flutter `MediaType` (`'music' | 'video'`).
 */
enum class MediaFileKind {
    MUSIC,
    VIDEO,
    ;

    val folderName: String
        get() =
            when (this) {
                MUSIC -> AppFileLayout.MUSIC_FOLDER
                VIDEO -> AppFileLayout.VIDEOS_FOLDER
            }

    companion object {
        fun fromFlutterType(type: String): MediaFileKind =
            when (type.lowercase()) {
                "music" -> MUSIC
                "video", "videos" -> VIDEO
                else -> error("Unknown media type: $type (expected music|video)")
            }
    }
}
