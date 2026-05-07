package dev.backgrounder.di

import com.russhwolf.settings.Settings
import dev.backgrounder.BackgrounderEventListener
import dev.backgrounder.EphemeralRegistry
import dev.backgrounder.WorkerRegistry
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Cross-platform Koin module — declares the singletons that live in
 * [WorkerRegistry] and [EphemeralRegistry], plus a default no-op
 * [BackgrounderEventListener]. Platform-specific modules
 * ([androidBackgrounderModule], [iOSBackgrounderModule], etc.) bind the
 * `Scheduler` implementation and a [Settings] instance.
 *
 * Wire all three into your `startKoin { ... }` call:
 *
 * ```
 * startKoin {
 *     androidContext(this@MyApp)
 *     workManagerFactory()                          // koin-androidx-workmanager
 *     modules(
 *         backgrounderCommonModule,
 *         backgrounderAndroidModule,
 *         myAppModule,
 *     )
 * }
 * ```
 */
public val backgrounderCommonModule: Module =
    module {
        single { WorkerRegistry() }
        single<BackgrounderEventListener> { BackgrounderEventListener.Noop }

        // Platform module supplies a `Settings` (NSUserDefaults / SharedPreferences).
        single { EphemeralRegistry(get<Settings>(qualifier = SettingsQualifier)) }
    }

/** Distinguish the [Settings] used by Backgrounder from any the user defines. */
public val SettingsQualifier: org.koin.core.qualifier.StringQualifier =
    org.koin.core.qualifier
        .named("backgrounder.settings")
