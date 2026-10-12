package com.midnightdancer.core.data.files

/**
 * Application documents directory — Flutter `path_provider` getApplicationDocumentsDirectory().
 *
 * - Android: [android.content.Context.getFilesDir] (after [bindMediaFilesContext])
 * - iOS: NSDocumentDirectory
 */
expect fun platformDocumentsDirectoryPath(): String
