package com.midnightdancer.core.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okio.Path.Companion.toPath

/** DataStore file name — must end with `.preferences_pb`. */
const val USER_PREFS_DATA_STORE_FILE_NAME: String = "midnight_dancer_user.preferences_pb"

/**
 * Creates a Preferences DataStore for hot user prefs (Flutter settings keys).
 *
 * Platform code supplies [producePath] (Android `filesDir`, iOS documents, tests: temp dir).
 * Never create two instances for the same path.
 */
fun createUserPrefsDataStore(
    producePath: () -> String,
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(
        corruptionHandler = null,
        migrations = emptyList(),
        scope = scope,
        produceFile = { producePath().toPath() },
    )
