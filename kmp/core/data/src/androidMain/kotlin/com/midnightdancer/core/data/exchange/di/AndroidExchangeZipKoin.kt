package com.midnightdancer.core.data.exchange.di

import android.content.Context
import com.midnightdancer.core.data.exchange.AndroidDebugExchangeZipUseCase
import com.midnightdancer.core.data.storage.createDeviceStorage
import com.midnightdancer.core.data.zip.AndroidZipMetadataReader
import com.midnightdancer.core.data.zip.SecureZipWhitelist
import com.midnightdancer.core.data.zip.di.secureZipKoinModule
import com.midnightdancer.core.domain.storage.DeviceStorage
import com.midnightdancer.core.domain.usecase.ExchangeZipUseCase
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * C-107/C-108: `.mdance` export + secure import; DeviceStorage for C-109/C-110.
 * Load after [com.midnightdancer.core.data.db.di.androidFileCatalogKoinModule].
 */
fun androidExchangeZipKoinModule(): List<Module> =
    listOf(
        secureZipKoinModule(
            metadataReader = AndroidZipMetadataReader(),
            whitelist = SecureZipWhitelist.MidnightDancerFullBackup,
        ),
        module {
            single<DeviceStorage> { createDeviceStorage() }
            single<ExchangeZipUseCase> {
                val ctx = get<Context>()
                AndroidDebugExchangeZipUseCase(
                    catalogExporter = get(),
                    mediaStorage = get(),
                    importSecureZip = get(),
                    saveAppData = get(),
                    packageName = ctx.packageName,
                    deviceStorage = get(),
                )
            }
        },
    )
