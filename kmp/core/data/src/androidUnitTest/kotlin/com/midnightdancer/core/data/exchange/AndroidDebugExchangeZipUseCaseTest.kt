package com.midnightdancer.core.data.exchange

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.midnightdancer.core.data.db.bindCatalogDatabaseContext
import com.midnightdancer.core.data.db.di.inMemoryCatalogDatabaseModule
import com.midnightdancer.core.data.files.MediaStorage
import com.midnightdancer.core.data.files.bindMediaFilesContext
import com.midnightdancer.core.data.files.di.mediaFilesKoinModule
import com.midnightdancer.core.data.migrate.catalog.CatalogExporter
import com.midnightdancer.core.data.zip.AndroidZipMetadataReader
import com.midnightdancer.core.data.zip.SecureZipWhitelist
import com.midnightdancer.core.data.zip.di.secureZipKoinModule
import com.midnightdancer.core.domain.entity.AppData
import com.midnightdancer.core.domain.storage.DeviceStorage
import com.midnightdancer.core.domain.usecase.ExchangeZipResult
import com.midnightdancer.core.domain.usecase.ExchangeZipUseCase
import com.midnightdancer.core.domain.usecase.SaveAppData
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.get
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class AndroidDebugExchangeZipUseCaseTest : KoinTest {

    private val ampleStorage =
        object : DeviceStorage {
            override fun freeBytes(): Long = 50L * 1024L * 1024L * 1024L
            override fun totalBytes(): Long = 128L * 1024L * 1024L * 1024L
        }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        bindCatalogDatabaseContext(context)
        bindMediaFilesContext(context)
        startKoin {
            modules(
                inMemoryCatalogDatabaseModule("exchange_zip_test.db"),
                mediaFilesKoinModule(),
                secureZipKoinModule(
                    metadataReader = AndroidZipMetadataReader(),
                    whitelist = SecureZipWhitelist.MidnightDancerFullBackup,
                ),
                module {
                    single<DeviceStorage> { ampleStorage }
                    single<ExchangeZipUseCase> {
                        AndroidDebugExchangeZipUseCase(
                            catalogExporter = get(),
                            mediaStorage = get(),
                            importSecureZip = get(),
                            saveAppData = get(),
                            packageName = context.packageName,
                            deviceStorage = get(),
                        )
                    }
                },
            )
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun export_creates_real_zip_and_import_roundtrip() =
        runTest {
            val useCase = get<ExchangeZipUseCase>()
            val exporter = get<CatalogExporter>()
            get<SaveAppData>().save(AppData())

            val exported = useCase.export()
            val ok = assertIs<ExchangeZipResult.Ok>(exported)
            assertTrue(ok.archivePath.endsWith("debug_export.mdance"), ok.archivePath)
            assertTrue(File(ok.archivePath).isFile)
            assertTrue(File(ok.archivePath).length() > 0L)
            assertTrue(MdanceContainer.isMdance(File(ok.archivePath)))

            val imported = useCase.import(ok.archivePath)
            assertIs<ExchangeZipResult.Ok>(imported)
            exporter.exportAppData()
        }

    @Test
    fun import_corrupt_zip_rejects_without_throw() =
        runTest {
            val useCase = get<ExchangeZipUseCase>()
            get<SaveAppData>().save(AppData())

            val exchangeDir =
                File(get<MediaStorage>().rootDirectory(), ExchangeZipLimits.EXCHANGE_DIR)
            exchangeDir.mkdirs()
            val corrupt = File(exchangeDir, ExchangeZipLimits.IMPORT_INBOX_FILE_NAME)
            corrupt.writeBytes(byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04))

            val result = useCase.import(corrupt.absolutePath)
            val failed = assertIs<ExchangeZipResult.Failed>(result)
            assertTrue(
                failed.reasons.any { it.contains("cannot_list_archive") } ||
                    failed.message.contains("reject", ignoreCase = true) ||
                    failed.message.contains("fail", ignoreCase = true),
                failed.toString(),
            )
        }

    @Test
    fun import_zip_missing_metadata_rejected() =
        runTest {
            val useCase = get<ExchangeZipUseCase>()
            get<SaveAppData>().save(AppData())
            val exchangeDir =
                File(get<MediaStorage>().rootDirectory(), ExchangeZipLimits.EXCHANGE_DIR)
            exchangeDir.mkdirs()
            val bad = File(exchangeDir, "no_meta.zip")
            ZipOutputStream(bad.outputStream()).use { zos ->
                zos.putNextEntry(ZipEntry("readme.txt"))
                zos.write("nope".encodeToByteArray())
                zos.closeEntry()
            }

            val result = useCase.import(bad.absolutePath)
            val failed = assertIs<ExchangeZipResult.Failed>(result)
            assertTrue(failed.reasons.isNotEmpty(), failed.toString())
        }
}
