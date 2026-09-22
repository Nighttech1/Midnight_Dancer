package com.midnightdancer.app

import android.app.Application
import com.midnightdancer.shared.initKoinAndroid

class MidnightDancerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoinAndroid(this)
    }
}
