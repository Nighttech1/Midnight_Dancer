package com.midnightdancer.shared

import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

/**
 * Common Koin entry — platforms call [initKoin] / [initKoinAndroid] / [initKoinIos].
 */
fun initKoin(appDeclaration: KoinAppDeclaration = {}): KoinApplication =
    startKoin {
        appDeclaration()
        modules(sharedModules())
    }
