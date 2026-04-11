package com.midnightdancer.app

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

class MainActivity: FlutterActivity() {

    private val CHANNEL = "com.midnightdancer.app/file_copy"
    private val BACKUP_CHANNEL = "com.midnightdancer.app/backup_export"
    /** Имя SharedPreferences плагина flutter_local_notifications для кэша zonedSchedule (см. SCHEDULED_NOTIFICATIONS в плагине). */
    private val NOTIFICATION_CACHE_CHANNEL = "com.midnightdancer.app/notification_cache"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, NOTIFICATION_CACHE_CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "clearFlutterLocalNotificationsScheduleCache" -> {
                    try {
                        applicationContext
                            .getSharedPreferences("scheduled_notifications", Context.MODE_PRIVATE)
                            .edit()
                            .clear()
                            .apply()
                        result.success(true)
                    } catch (e: Exception) {
                        result.error("CLEAR_FAILED", e.message, null)
                    }
                }
                else -> result.notImplemented()
            }
        }
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, BACKUP_CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "saveZipToDownloads" -> {
                    val tempPath = call.argument<String>("tempPath")
                    val fileName = call.argument<String>("fileName")
                    if (tempPath.isNullOrEmpty() || fileName.isNullOrEmpty()) {
                        result.error("INVALID_ARG", "tempPath and fileName required", null)
                        return@setMethodCallHandler
                    }
                    val src = File(tempPath)
                    if (!src.exists()) {
                        result.error("NOT_FOUND", "Temp file missing", null)
                        return@setMethodCallHandler
                    }
                    Thread {
                        try {
                            val folderPath = saveZipToPublicDownloads(fileName, src)
                            runOnUiThread {
                                result.success(
                                    mapOf(
                                        "folderPath" to folderPath,
                                        "fileName" to fileName,
                                    ),
                                )
                            }
                        } catch (e: Exception) {
                            runOnUiThread {
                                result.error("SAVE_FAILED", e.message, null)
                            }
                        }
                    }.start()
                }
                else -> result.notImplemented()
            }
        }
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            try {
                when (call.method) {
                    "takeUriPermission" -> {
                        val uriStr = call.argument<String>("uri")
                        if (uriStr == null || uriStr.isEmpty()) {
                            result.error("INVALID_ARG", "URI is required", null)
                            return@setMethodCallHandler
                        }
                        if (!uriStr.startsWith("content:")) {
                            result.success(true)
                            return@setMethodCallHandler
                        }
                        try {
                            val uri = Uri.parse(uriStr)
                            contentResolver.takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                            result.success(true)
                        } catch (e: Exception) {
                            result.error("PERMISSION_FAILED", e.message, null)
                        }
                    }
                    "getVideoThumbnail" -> {
                        val uriOrPath = call.argument<String>("uri")
                        if (uriOrPath == null || uriOrPath.isEmpty()) {
                            result.error("INVALID_ARG", "URI or path is required", null)
                            return@setMethodCallHandler
                        }
                        try {
                            val base64 = getVideoThumbnailBase64(uriOrPath)
                            result.success(base64)
                        } catch (e: Exception) {
                            result.error("THUMBNAIL_FAILED", e.message, null)
                        }
                    }
                    "copyToCache" -> {
                        val uriOrPath = call.argument<String>("uri")
                        if (uriOrPath == null || uriOrPath.isEmpty()) {
                            result.error("INVALID_ARG", "URI or path is required", null)
                            return@setMethodCallHandler
                        }
                        try {
                            val destPath = copyToAppCache(uriOrPath)
                            result.success(destPath)
                        } catch (e: Exception) {
                            result.error("COPY_FAILED", e.message, null)
                        }
                    }
                    else -> result.notImplemented()
                }
            } catch (e: Exception) {
                result.error("UNEXPECTED", e.message, null)
            }
        }
    }

    /**
     * Копирует ZIP в папку «Загрузки» (MediaStore на API 29+, иначе прямой путь).
     * @return абсолютный путь к каталогу загрузок для отображения пользователю
     */
    private fun saveZipToPublicDownloads(fileName: String, src: File): String {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val folderPath = downloadsDir.absolutePath

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val resolver = applicationContext.contentResolver
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = resolver.insert(collection, values)
                ?: throw Exception("Не удалось создать файл в Загрузках")
            resolver.openOutputStream(uri)?.use { output ->
                FileInputStream(src).use { input -> input.copyTo(output) }
            } ?: throw Exception("Не удалось записать архив")
            return folderPath
        }

        if (!downloadsDir.exists()) {
            if (!downloadsDir.mkdirs()) throw Exception("Не удалось создать папку Загрузки")
        }
        val dest = File(downloadsDir, fileName)
        if (dest.exists()) dest.delete()
        FileInputStream(src).use { input ->
            FileOutputStream(dest).use { output -> input.copyTo(output) }
        }
        return folderPath
    }

    private fun copyToAppCache(uriOrPath: String): String {
        val cacheDir = File(cacheDir, "video_picker")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        val destFile = File(cacheDir, "vid_${System.currentTimeMillis()}.mp4")

        val inputStream: InputStream = if (uriOrPath.startsWith("content:")) {
            contentResolver.openInputStream(Uri.parse(uriOrPath))
                ?: throw Exception("Cannot open content URI")
        } else {
            val src = File(uriOrPath)
            if (!src.exists()) throw Exception("File not found: $uriOrPath")
            src.inputStream()
        }

        inputStream.use { input ->
            FileOutputStream(destFile).use { output ->
                val buffer = ByteArray(64 * 1024)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                }
            }
        }
        return destFile.absolutePath
    }

    private fun getVideoThumbnailBase64(uriOrPath: String): String? {
        val retriever = MediaMetadataRetriever()
        try {
            if (uriOrPath.startsWith("content:")) {
                retriever.setDataSource(applicationContext, Uri.parse(uriOrPath))
            } else {
                retriever.setDataSource(uriOrPath)
            }
            val bitmap = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            return bitmap?.let { bmp ->
                val maxSize = 512
                val scale = minOf(maxSize.toFloat() / bmp.width, maxSize.toFloat() / bmp.height, 1f)
                val w = (bmp.width * scale).toInt()
                val h = (bmp.height * scale).toInt()
                val scaled = if (scale < 1f) Bitmap.createScaledBitmap(bmp, w, h, true) else bmp
                val stream = java.io.ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                if (scaled != bmp) scaled.recycle()
                Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
            }
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }
}
