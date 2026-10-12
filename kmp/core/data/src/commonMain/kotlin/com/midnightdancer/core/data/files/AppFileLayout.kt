package com.midnightdancer.core.data.files

/**
 * Flutter [StorageService] layout lock — do not rename.
 *
 * On-disk under application documents:
 * `…/MidnightDancer/{music,videos,metadata.json}`
 *
 * Package / applicationId must stay `com.midnightdancer.app` so store updates
 * keep the same sandbox paths as Flutter full.
 */
object AppFileLayout {
    const val ROOT_FOLDER: String = "MidnightDancer"
    const val MUSIC_FOLDER: String = "music"
    const val VIDEOS_FOLDER: String = "videos"
    const val METADATA_FILE: String = "metadata.json"

    /** Locked product applicationId (Flutter full + KMP). */
    const val APPLICATION_ID: String = "com.midnightdancer.app"

    const val DEFAULT_MUSIC_EXTENSION: String = "mp3"
    const val DEFAULT_VIDEO_EXTENSION: String = "mp4"
}
