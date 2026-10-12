package com.midnightdancer.core.data.files

import android.content.Context

/**
 * Holds [Context] for documents path on Android
 * (Flutter `getApplicationDocumentsDirectory` → [Context.getFilesDir]).
 */
object MediaFilesAndroidContext {
    @Volatile
    var applicationContext: Context? = null
}

/** Call from app / test bootstrap before resolving [platformDocumentsDirectoryPath]. */
fun bindMediaFilesContext(context: Context) {
    MediaFilesAndroidContext.applicationContext = context.applicationContext
}

/** Alias for Platform Ops / older call sites. */
fun bindMediaStorageContext(context: Context) = bindMediaFilesContext(context)

actual fun platformDocumentsDirectoryPath(): String {
    val ctx =
        MediaFilesAndroidContext.applicationContext
            ?: error("bindMediaFilesContext() must be called before media documents path on Android")
    return ctx.filesDir.absolutePath
}

actual fun platformFileSystemOps(): FileSystemOps = AndroidFileSystemOps

private object AndroidFileSystemOps : FileSystemOps {
    override fun ensureDirectory(absolutePath: String) {
        val dir = java.io.File(absolutePath)
        if (!dir.exists()) {
            check(dir.mkdirs() || dir.isDirectory) {
                "Failed to create directory: $absolutePath"
            }
        }
    }
}
