package com.midnightdancer.shared

import com.arkivanov.decompose.ComponentContext
import com.midnightdancer.core.data.DataRepositoryStub
import com.midnightdancer.core.data.InMemoryDataRepositoryStub
import com.midnightdancer.core.navigation.DefaultRootComponent
import com.midnightdancer.core.navigation.RootComponent
import org.koin.core.module.Module
import org.koin.dsl.module

fun sharedCommonModule(): Module = module {
    single<DataRepositoryStub> { InMemoryDataRepositoryStub() }
    factory<RootComponent> { (componentContext: ComponentContext) ->
        DefaultRootComponent(componentContext)
    }
}

fun sharedModules(): List<Module> = listOf(sharedCommonModule())
