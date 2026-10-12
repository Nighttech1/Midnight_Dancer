package com.midnightdancer.core.data.files

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import org.koin.test.get
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.midnightdancer.core.data.files.di.mediaFilesKoinModule
import kotlin.test.assertTrue
import java.io.File

/**
 * C-14 Android smoke: documents path non-empty + MidnightDancer layout under filesDir.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class MediaStorageAndroidSmokeTest : KoinTest {
    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        bindMediaFilesContext(context)
        startKoin {
            modules(mediaFilesKoinModule())
        }
    }

    @After
    fun tearDown() {
        stopKoin()
        MediaFilesAndroidContext.applicationContext = null
    }

    @Test
    fun platformDocuments_and_root_nonEmpty() {
        val docs = platformDocumentsDirectoryPath()
        assertTrue(docs.isNotBlank(), "documents path must be non-empty")

        val storage: MediaStorage = get()
        assertTrue(storage.rootDirectory().isNotBlank())
        assertTrue(storage.rootDirectory().endsWith("/MidnightDancer") ||
            storage.rootDirectory().endsWith("\\MidnightDancer") ||
            storage.rootDirectory().endsWith("MidnightDancer"))

        storage.ensureLayout()
        assertTrue(File(storage.musicDirectory()).isDirectory)
        assertTrue(File(storage.videosDirectory()).isDirectory)
        assertTrue(storage.metadataFilePath().endsWith("metadata.json"))
    }
}
