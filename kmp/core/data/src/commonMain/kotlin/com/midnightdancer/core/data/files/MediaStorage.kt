package com.midnightdancer.core.data.files

/**
 * Media file storage API — Flutter [StorageService] filesystem layout (paths only).
 *
 * Does **not** implement SAF / content:// pickers (Stage 3 UI) or zip extract (C-15).
 * Callers may pass an already-known absolute path / uri string when copying later.
 */
interface MediaStorage {
    fun documentsDirectory(): String

    fun rootDirectory(): String

    fun musicDirectory(): String

    fun videosDirectory(): String

    fun metadataFilePath(): String

    fun mediaFilePath(
        id: String,
        kind: MediaFileKind,
        musicExtension: String? = null,
    ): String

    /** Creates `MidnightDancer/`, `music/`, `videos/` if missing. */
    fun ensureLayout()
}

class DefaultMediaStorage(
    private val appFiles: AppFiles,
    private val fileSystem: FileSystemOps = platformFileSystemOps(),
) : MediaStorage {
    constructor(
        documentsPath: () -> String,
        fileSystem: FileSystemOps = platformFileSystemOps(),
    ) : this(AppFiles(documentsPath), fileSystem)

    constructor(
        documentsPath: String,
        fileSystem: FileSystemOps = platformFileSystemOps(),
    ) : this(AppFiles(documentsPath), fileSystem)

    override fun documentsDirectory(): String = appFiles.documentsDirectory()

    override fun rootDirectory(): String = appFiles.rootDirectory()

    override fun musicDirectory(): String = appFiles.musicDirectory()

    override fun videosDirectory(): String = appFiles.videosDirectory()

    override fun metadataFilePath(): String = appFiles.metadataFilePath()

    override fun mediaFilePath(
        id: String,
        kind: MediaFileKind,
        musicExtension: String?,
    ): String = appFiles.mediaFilePath(id, kind, musicExtension)

    override fun ensureLayout() {
        fileSystem.ensureDirectory(rootDirectory())
        fileSystem.ensureDirectory(musicDirectory())
        fileSystem.ensureDirectory(videosDirectory())
    }
}
