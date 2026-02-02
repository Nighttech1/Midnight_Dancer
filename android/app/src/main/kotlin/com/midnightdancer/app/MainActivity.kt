package com.midnightdancer.app

import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Base64
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class MainActivity: FlutterActivity() {

    private val CHANNEL = "com.midnightdancer.app/file_copy"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
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
