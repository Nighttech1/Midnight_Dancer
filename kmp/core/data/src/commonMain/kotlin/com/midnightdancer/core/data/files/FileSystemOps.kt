package com.midnightdancer.core.data.files

/** Minimal directory ops for media layout (no SAF / content://). */
interface FileSystemOps {
    fun ensureDirectory(absolutePath: String)
}

expect fun platformFileSystemOps(): FileSystemOps
