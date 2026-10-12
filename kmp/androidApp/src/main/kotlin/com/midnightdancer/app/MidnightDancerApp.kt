package com.midnightdancer.app

import android.app.Application
import android.content.Context
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.midnightdancer.app.debug.EdgeStressSeedCoordinator
import com.midnightdancer.app.debug.EdgeStressSeedReceiver
import com.midnightdancer.app.di.androidUiModules
import com.midnightdancer.core.data.db.di.androidFileCatalogKoinModule
import com.midnightdancer.core.data.exchange.di.androidExchangeZipKoinModule
import com.midnightdancer.shared.initKoinAndroid
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules

class MidnightDancerApp : Application(), SingletonImageLoader.Factory {
    private var edgeStressReceiver: EdgeStressSeedReceiver? = null

    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            if (thread.name.contains("OkHttp", ignoreCase = true)) {
                Log.e("MdOkHttp", "background network failed (offline/emulator)", error)
                return@setDefaultUncaughtExceptionHandler
            }
            previous?.uncaughtException(thread, error)
        }
        initKoinAndroid(this)
        // C-13 / C-105: Room via core:data wrapper (no RoomDatabase.Builder in this module).
        // C-107: real exchange zip + secure import after catalog is on the graph.
        loadKoinModules(
            listOf(androidFileCatalogKoinModule(this)) +
                androidExchangeZipKoinModule() +
                androidUiModules(),
        )
        registerEdgeStressReceiverIfDebug()
    }

    /** DEBUG-only: adb broadcast seed for Client edge-stress QA (C-104). */
    private fun registerEdgeStressReceiverIfDebug() {
        if (!EdgeStressSeedCoordinator.isDebuggable(this)) return
        val receiver = EdgeStressSeedReceiver()
        val filter = IntentFilter(EdgeStressSeedCoordinator.ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(receiver, filter)
        }
        edgeStressReceiver = receiver
        Log.i("EdgeStressSeed", "receiver registered (debug)")
    }

    /** Process-wide Coil singleton — Compose AsyncImage uses this by default. */
    override fun newImageLoader(context: Context): ImageLoader =
        GlobalContext.get().get()
}
