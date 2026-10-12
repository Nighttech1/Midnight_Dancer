package com.midnightdancer.core.data.files

/**
 * Path helpers for the MidnightDancer documents tree (no I/O).
 *
 * @param documentsPath absolute application documents directory
 */
class AppFiles(
    private val documentsPath: () -> String,
) {
    constructor(documentsPath: String) : this({ documentsPath })

    fun documentsDirectory(): String = documentsPath().replace('\\', '/').trimEnd('/')

    fun rootDirectory(): String = joinPath(documentsDirectory(), AppFileLayout.ROOT_FOLDER)

    fun musicDirectory(): String = joinPath(rootDirectory(), AppFileLayout.MUSIC_FOLDER)

    fun videosDirectory(): String = joinPath(rootDirectory(), AppFileLayout.VIDEOS_FOLDER)

    fun metadataFilePath(): String = joinPath(rootDirectory(), AppFileLayout.METADATA_FILE)

    /**
     * Resolve media file path by id + kind.
     * Music: `{id}.{extension}` (default mp3); video: `{id}.mp4`.
     *
     * [id] and music extension segments are sanitized (S-06): reject `/`, `\`, `..`, blank;
     * allow only alnum + `_-.`; music extensions are allowlisted; result must stay under
     * music/ or videos/.
     */
    fun mediaFilePath(
        id: String,
        kind: MediaFileKind,
        musicExtension: String? = null,
    ): String {
        val safeId = sanitizeMediaPathSegment(id, "media id")
        val base =
            when (kind) {
                MediaFileKind.MUSIC -> musicDirectory()
                MediaFileKind.VIDEO -> videosDirectory()
            }
        val ext =
            when (kind) {
                MediaFileKind.MUSIC -> sanitizeMusicExtension(musicExtension)
                MediaFileKind.VIDEO -> AppFileLayout.DEFAULT_VIDEO_EXTENSION
            }
        val result = joinPath(base, "$safeId.$ext")
        val basePrefix = base.trimEnd('/') + "/"
        require(result.startsWith(basePrefix)) {
            "media path escaped kind directory: $result"
        }
        return result
    }

    companion object {
        /** Safe path segment charset: letters, digits, underscore, hyphen, dot. */
        private val SAFE_SEGMENT: Regex = Regex("^[A-Za-z0-9_\\-.]+$")

        val ALLOWED_MUSIC_EXTENSIONS: Set<String> =
            setOf("mp3", "m4a", "wav", "aac", "ogg", "flac")

        fun sanitizeMediaPathSegment(
            raw: String,
            label: String = "path segment",
        ): String {
            require(raw.isNotBlank()) { "$label must not be blank" }
            require('/' !in raw && '\\' !in raw) { "$label must not contain path separators" }
            require(".." !in raw) { "$label must not contain .." }
            require('\u0000' !in raw) { "$label must not contain null bytes" }
            require(SAFE_SEGMENT.matches(raw)) {
                "$label must match [A-Za-z0-9_\\-.]+ (got: $raw)"
            }
            return raw
        }

        fun sanitizeMusicExtension(musicExtension: String?): String {
            val ext =
                (musicExtension ?: AppFileLayout.DEFAULT_MUSIC_EXTENSION)
                    .trimStart('.')
                    .lowercase()
                    .ifEmpty { AppFileLayout.DEFAULT_MUSIC_EXTENSION }
            require(ext in ALLOWED_MUSIC_EXTENSIONS) {
                "music extension not allowlisted: $ext"
            }
            require('/' !in ext && '\\' !in ext && ".." !in ext) {
                "music extension must not contain path segments"
            }
            return ext
        }
    }
}