package com.midnightdancer.shared

import org.koin.mp.KoinPlatform

/** iOS bootstrap hook — Xcode shell (C-07) calls this at app launch. */
fun initKoinIos() {
    if (KoinPlatform.getKoinOrNull() == null) initKoin()
}
