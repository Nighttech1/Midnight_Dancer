package com.midnightdancer.app

import android.media.MediaMetadataRetriever
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.midnightdancer.app.ui.splash.SplashBootAspectRatio
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** The pre-sized first-frame viewport must match the actual packaged asset, including rotation. */
@RunWith(AndroidJUnit4::class)
class SplashAssetGeometryTest {
    @Test fun packagedIntroMatchesFirstFrameViewportAspect() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.resources.openRawResourceFd(R.raw.splash_boot).use { asset ->
            MediaMetadataRetriever().use { reader ->
                reader.setDataSource(asset.fileDescriptor, asset.startOffset, asset.length)
                val width = requireNotNull(reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)).toInt()
                val height = requireNotNull(reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)).toInt()
                val rotation = reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toInt() ?: 0
                val aspect = if (rotation % 180 == 0) width.toFloat() / height else height.toFloat() / width
                assertTrue(width > 0 && height > 0)
                assertEquals("Packaged intro changed: update the first-frame viewport before shipping", aspect, SplashBootAspectRatio, 0.0001f)
            }
        }
    }
}
