package com.midnightdancer.core.data.db.di

import android.content.Context
import com.midnightdancer.core.data.db.catalogDatabaseBuilder
import org.koin.core.module.Module

/**
 * File-backed Room catalog for Android app process.
 * Keeps [androidx.room.RoomDatabase.Builder] off androidApp/shared call sites (classpath gate).
 */
fun androidFileCatalogKoinModule(context: Context): Module =
    catalogDatabaseModule { catalogDatabaseBuilder(context.applicationContext) }
