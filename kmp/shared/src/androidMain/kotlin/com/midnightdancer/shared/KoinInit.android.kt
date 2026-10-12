package com.midnightdancer.shared

import android.content.Context
import com.midnightdancer.core.data.db.bindCatalogDatabaseContext
import com.midnightdancer.core.data.files.bindMediaFilesContext
import com.midnightdancer.core.tts.bindTtsAndroidContext
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.logger.Level

fun initKoinAndroid(context: Context) {
    bindCatalogDatabaseContext(context)
    bindMediaFilesContext(context)
    bindTtsAndroidContext(context)
    initKoin {
        androidLogger(Level.ERROR)
        androidContext(context)
    }
}
