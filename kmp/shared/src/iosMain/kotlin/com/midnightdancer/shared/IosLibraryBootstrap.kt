package com.midnightdancer.shared

import com.arkivanov.decompose.ComponentContext
import com.midnightdancer.core.data.db.RoomCatalogRepository
import com.midnightdancer.core.data.db.catalogDatabaseBuilder
import com.midnightdancer.core.data.db.di.catalogDatabaseModule
import com.midnightdancer.core.data.files.platformDocumentsDirectoryPath
import com.midnightdancer.core.data.files.IosLegacyMetadataReader
import com.midnightdancer.core.data.files.IosDurableTextFile
import com.midnightdancer.core.data.migrate.PrefsMigrator
import com.midnightdancer.core.data.migrate.migrateIosLegacyCatalog
import com.midnightdancer.core.data.prefs.USER_PREFS_DATA_STORE_FILE_NAME
import com.midnightdancer.core.data.prefs.di.prefsKoinModule
import com.midnightdancer.core.data.storage.createDeviceStorage
import com.midnightdancer.core.domain.settings.CreationAccessPolicy
import com.midnightdancer.core.domain.storage.DeviceStorage
import com.midnightdancer.core.domain.styles.ManageDanceStyles
import com.midnightdancer.core.domain.usecase.ReadWriteUserPrefs
import com.midnightdancer.core.navigation.RootComponent
import com.midnightdancer.feature.choreography.logic.*
import com.midnightdancer.feature.exchange.logic.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.mp.KoinPlatform
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module

data class IosLibraryBootstrapResult(val ready: Boolean, val error: String? = null)

/** Swift awaits prepare before constructing a Root. The same graph survives retries and UI recreation. */
object IosLibraryBootstrap {
    private val lock = Mutex()
    private val prepared = MutableStateFlow(false)
    private var modulesLoaded = false
    private var creationPolicy: IosCreationPolicy? = null

    @Throws(CancellationException::class)
    suspend fun prepare(): IosLibraryBootstrapResult = withContext(Dispatchers.Default) {
        lock.withLock {
            if (prepared.value) return@withLock IosLibraryBootstrapResult(true)
            try {
                initKoinIos()
                val koin = KoinPlatform.getKoin()
                if (!modulesLoaded) {
                    val documents = platformDocumentsDirectoryPath()
                    koin.loadModules(listOf(
                        catalogDatabaseModule { catalogDatabaseBuilder() },
                        prefsKoinModule { "$documents/$USER_PREFS_DATA_STORE_FILE_NAME" },
                        module {
                            single<CreationAccessPolicy> { checkNotNull(creationPolicy) }
                            single<ChoreographyVaultPersister> { IosChoreographyVaultPersister(get()) }
                            single<SavedSetsPersister> { IosSavedSetsPersister(get()) }
                            single<DeviceStorage> { createDeviceStorage() }
                            single<LocalSetCatalogLoader> { IosLocalSetCatalogLoader(get(), get(), get()) }
                        },
                    ))
                    modulesLoaded = true
                }
                val repository = koin.get<RoomCatalogRepository>()
                val styles = koin.get<ManageDanceStyles>()
                val prefs = koin.get<ReadWriteUserPrefs>()
                val vault = koin.get<ChoreographyVaultPersister>()
                val sets = koin.get<SavedSetsPersister>()
                // Validate every existing private file before migrating the catalog or publishing defaults.
                vault.readRaw()?.let(::decodeVaultItems)
                sets.read()
                migrateIosLegacyCatalog(repository)
                PrefsMigrator(IosLegacyMetadataReader(), prefs).migrateOnce()
                projectIosLegacyVault(repository.load(), vault)
                if (vault.readRaw() == null) vault.writeRaw(encodeVaultItems(emptyList()))
                if (sets.read() == null) sets.write(emptyList())
                val items = vault.readRaw()?.let(::decodeVaultItems).orEmpty()
                val cards = sets.read().orEmpty()
                val names = prefs.readDanceStyles().names + items.flatMap { listOfNotNull(it.style, it.snapshot?.style) } +
                    cards.filter { it.origin == SavedSetOrigin.Mine }.map { it.styleName.ifBlank { it.styleId } }
                styles.bootstrap(names)
                vault.refreshStyles()
                sets.refreshStyles()
                val catalog = repository.load()
                val readyItems = vault.readRaw()?.let(::decodeVaultItems).orEmpty()
                val readyCards = sets.read().orEmpty()
                // Establish rollout eligibility once, before any newly created iOS choreography.
                val accessFlag = IosDurableTextFile("creation_access_rollout_v1.txt")
                val recorded = accessFlag.readOrNull()
                val preserveCreationAccess = when (recorded) {
                    "legacy" -> true
                    "free" -> false
                    null -> ownChoreographyCount(readyItems) > 0
                    else -> error("invalid_creation_access_flag")
                }
                if (recorded == null) accessFlag.write(if (preserveCreationAccess) "legacy" else "free")
                creationPolicy = IosCreationPolicy(preserveCreationAccess)
                withContext(Dispatchers.Main) {
                    ChoreoCatalog.replaceStyleCatalog(checkNotNull(styles.state.value))
                    ChoreoCatalog.replaceFromAppData(catalog)
                    ChoreographyVaultStore.replaceAll(readyItems)
                    SavedSetsStore.replaceAll(readyCards)
                    SavedSetsStore.syncSeqFrom(readyCards.map { it.id })
                }
                prepared.value = true
                IosLibraryBootstrapResult(true)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                // No paths, titles or raw persisted data escape into the UI. Existing files are retained.
                IosLibraryBootstrapResult(false, "Не удалось открыть сохранённую библиотеку. Данные не очищены. Повторите попытку.")
            }
        }
    }

    /** Call from Swift MainActor only after ready=true. Uses production DI factories, not demo defaults. */
    fun createRoot(componentContext: ComponentContext, startAtMain: Boolean): RootComponent {
        check(prepared.value) { "iOS library must be prepared before creating the root" }
        return KoinPlatform.getKoin().get { parametersOf(componentContext, startAtMain) }
    }
}
