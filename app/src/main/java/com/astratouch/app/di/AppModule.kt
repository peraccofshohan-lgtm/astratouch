package com.astratouch.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.astratouch.app.data.local.AstraDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AstraDatabase =
        Room.databaseBuilder(context, AstraDatabase::class.java, AstraDatabase.NAME)
            .addMigrations(*AstraDatabase.ALL_MIGRATIONS)
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()

    /**
     * Process-wide DataStore instance.
     *
     * DataStore enforces a single active instance per file. Creating one per Hilt
     * component looks correct (`@Singleton` in `SingletonComponent`) but every
     * test/`HiltTestActivity` component built on top of it would open the same
     * `astratouch_prefs.preferences_pb` again and crash at runtime with
     * "There are multiple DataStores active for the same file". Caching the
     * instance in a process-level holder makes the invariant hold everywhere,
     * including instrumentation tests that create several components in one run.
     */
    @Provides
    @Singleton
    fun provideDataStore(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
    ): DataStore<Preferences> = DataStoreHolder.get(context, scope)

    @Provides
    @Singleton
    fun provideContext(@ApplicationContext context: Context): Context = context

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

/**
 * Owns the single [DataStore] for `astratouch_prefs`.
 *
 * Keyed by absolute file path so a test that uses a different `Context` (or a
 * different app instance after a process restart) still gets exactly one
 * instance per file.
 */
internal object DataStoreHolder {
    private val lock = Any()
    private val stores = mutableMapOf<String, DataStore<Preferences>>()

    fun get(context: Context, scope: CoroutineScope): DataStore<Preferences> {
        val file = context.applicationContext.preferencesDataStoreFile(DATASTORE_NAME)
        synchronized(lock) {
            return stores.getOrPut(file.absolutePath) {
                PreferenceDataStoreFactory.create(
                    scope = scope,
                    produceFile = { file },
                )
            }
        }
    }

    private const val DATASTORE_NAME = "astratouch_prefs"
}
