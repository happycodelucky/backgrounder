package dev.backgrounder.android.di

import androidx.work.WorkManager
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import dev.backgrounder.EphemeralRegistry
import dev.backgrounder.Scheduler
import dev.backgrounder.android.AndroidScheduledTaskQuery
import dev.backgrounder.android.RegistryDispatchWorker
import dev.backgrounder.android.WorkManagerScheduler
import dev.backgrounder.di.SettingsQualifier
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.dsl.workerOf
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Android-specific Koin module. Adds:
 *
 * - A platform [Settings] (NSUserDefaults equivalent on iOS / macOS, here a
 *   private `SharedPreferences`) under [SettingsQualifier].
 * - The [WorkManager] singleton (resolved from `androidContext()`).
 * - [AndroidScheduledTaskQuery] over [WorkManager] + [EphemeralRegistry].
 * - The [Scheduler] binding, resolving to [WorkManagerScheduler].
 * - [RegistryDispatchWorker] declared via Koin's `workerOf` so the
 *   `koin-androidx-workmanager`-provided `KoinWorkerFactory` can build it
 *   with our DI deps when WorkManager fires it.
 *
 * Usage from `Application.onCreate`:
 * ```
 * startKoin {
 *     androidContext(this@MyApp)
 *     workManagerFactory()
 *     modules(backgrounderCommonModule, backgrounderAndroidModule, /* yours */)
 * }
 * ```
 */
public val backgrounderAndroidModule: Module = module {
    single<Settings>(qualifier = SettingsQualifier) {
        SharedPreferencesSettings(
            androidContext()
                .getSharedPreferences("backgrounder.prefs", android.content.Context.MODE_PRIVATE),
        )
    }

    single { WorkManager.getInstance(androidContext()) }

    single { AndroidScheduledTaskQuery(get(), get<EphemeralRegistry>()) }

    single<Scheduler> {
        WorkManagerScheduler(
            workManager = get(),
            ephemeral = get(),
            eventListener = get(),
            scheduledTaskQuery = get(),
        )
    }

    workerOf(::RegistryDispatchWorker)
}
