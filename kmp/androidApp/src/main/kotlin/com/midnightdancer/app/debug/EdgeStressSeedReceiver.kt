package com.midnightdancer.app.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.midnightdancer.core.data.debug.EdgeStressCatalogSeeder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * adb: see ticket README. Registered only when the process is debuggable.
 */
class EdgeStressSeedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != EdgeStressSeedCoordinator.ACTION) return
        if (!EdgeStressSeedCoordinator.isDebuggable(context)) {
            Log.w(TAG, "ignored: not debuggable")
            return
        }
        val count =
            intent.getIntExtra(
                EdgeStressSeedCoordinator.EXTRA_COUNT,
                EdgeStressCatalogSeeder.DEFAULT_COUNT,
            )
        val pending = goAsync()
        val app = context.applicationContext
        scope.launch {
            try {
                val result = EdgeStressSeedCoordinator.seed(app, count)
                Toast.makeText(
                    app,
                    "Edge seed: ${result.moveCount} el / ${result.songCount} tracks",
                    Toast.LENGTH_LONG,
                ).show()
            } catch (t: Throwable) {
                Log.e(TAG, "seed failed", t)
                Toast.makeText(app, "Edge seed FAILED: ${t.message}", Toast.LENGTH_LONG).show()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "EdgeStressSeed"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }
}
