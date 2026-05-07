// ExperimentalForeignApi: required by `NSUserDefaults(suiteName:)` cinterop call.
@file:OptIn(ExperimentalForeignApi::class)

package dev.backgrounder.ios.di

import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import dev.backgrounder.EphemeralRegistry
import dev.backgrounder.Scheduler
import dev.backgrounder.di.SettingsQualifier
import dev.backgrounder.ios.BGTaskBackedScheduler
import dev.backgrounder.ios.BGTaskHandlerRegistration
import dev.backgrounder.ios.IOSCoroutineBridge
import dev.backgrounder.ios.IOSEphemeralSweep
import dev.backgrounder.ios.IOSStateStore
import dev.backgrounder.ios.IOSTaskMutexes
import kotlinx.cinterop.ExperimentalForeignApi
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSUserDefaults

/**
 * iOS-specific Koin module. Wire alongside `backgrounderCommonModule`:
 *
 * ```
 * KoinKt.startKoin {
 *     modules(backgrounderCommonModule, backgrounderIOSModule, /* yours */)
 * }
 * ```
 */
public val backgrounderIOSModule: Module =
    module {
        single<Settings>(qualifier = SettingsQualifier) {
            NSUserDefaultsSettings(
                NSUserDefaults(suiteName = "dev.backgrounder.shared"),
            )
        }
        single { IOSStateStore(get<Settings>(qualifier = SettingsQualifier)) }
        single { IOSTaskMutexes() }

        single<BGTaskBackedScheduler> {
            BGTaskBackedScheduler(
                state = get(),
                mutexes = get(),
                ephemeral = get<EphemeralRegistry>(),
                eventListener = get(),
            )
        }
        single<Scheduler> { get<BGTaskBackedScheduler>() }

        single {
            IOSCoroutineBridge(
                registry = get(),
                state = get(),
                mutexes = get(),
                eventListener = get(),
                applyResult = { task, taskId, attempt, result ->
                    get<BGTaskBackedScheduler>().applyResult(task, taskId, attempt, result)
                },
            )
        }

        single { IOSEphemeralSweep(get<EphemeralRegistry>(), get<IOSStateStore>()) }
        single { BGTaskHandlerRegistration(get(), get(), get()) }
    }
